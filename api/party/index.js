import crypto from 'node:crypto';
import { metric } from '@vercel/functions';
import { ensureSchema, getSql } from '../_lib/db.js';
import { jsonResponse, optionsResponse, readJsonBody } from '../_lib/auth.js';

const ROOM_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
const MEMBER_STALE_MS = 28 * 1000; // 28s heartbeat window
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateRoomCode() {
  let code = '';
  const bytes = crypto.randomBytes(6);
  for (let i = 0; i < 6; i++) {
    code += ALPHABET[bytes[i] % ALPHABET.length];
  }
  return code;
}

function cleanRoomCode(raw) {
  return String(raw || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
}

function cleanNick(raw) {
  const s = String(raw || '').trim().replace(/\s+/g, ' ').slice(0, 28);
  return s || 'Guest';
}

function parseMembers(raw) {
  try {
    const arr = Array.isArray(raw) ? raw : JSON.parse(raw || '[]');
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function upsertHeartbeat(members, { memberId, nickname, isHost, now }) {
  const fresh = members.filter((m) => m && m.id && now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS);
  if (memberId) {
    const idx = fresh.findIndex((m) => m.id === memberId);
    const entry = { id: memberId, name: cleanNick(nickname), isHost: Boolean(isHost), lastSeen: now };
    if (idx >= 0) fresh[idx] = entry;
    else if (fresh.length < 40) fresh.push(entry);
  }
  return fresh;
}

function formatPartySnapshot(row, messages, now) {
  const members = parseMembers(row.members_json).filter((m) => now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS);
  return {
    roomCode: row.room_code,
    hostName: row.host_name,
    movie: typeof row.movie_json === 'string' ? JSON.parse(row.movie_json) : (row.movie_json || {}),
    episode: typeof row.episode_json === 'string' ? JSON.parse(row.episode_json) : (row.episode_json || {}),
    isPlaying: Boolean(row.is_playing),
    positionSec: Number(row.position_sec || 0),
    playbackRate: Number(row.playback_rate || 1),
    stateUpdatedAt: Number(row.state_updated_at || now),
    serverTime: now,
    members: members.map((m) => ({ id: m.id, name: m.name, isHost: Boolean(m.isHost) })),
    viewerCount: Math.max(1, members.length),
    messages: messages.map((msg) => ({
      id: Number(msg.id),
      senderName: msg.sender_name,
      isHost: Boolean(msg.sender_is_host),
      text: msg.text,
      createdAt: Number(msg.created_at),
    })),
  };
}

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    const roomCode = cleanRoomCode(url.searchParams.get('room'));
    const sinceMsgId = Math.max(0, Number(url.searchParams.get('sinceMsgId') || 0));
    const memberId = String(url.searchParams.get('memberId') || '').trim().slice(0, 48);
    const nickname = cleanNick(url.searchParams.get('nickname') || 'Guest');
    const hostToken = String(url.searchParams.get('hostToken') || '').trim();

    if (!roomCode || roomCode.length < 4) {
      return jsonResponse({ error: 'Valid room code is required' }, 400);
    }

    await ensureSchema();
    const sql = getSql();
    const now = Date.now();

    const rows = await sql`
      SELECT room_code, host_token, host_name, movie_json, episode_json,
             is_playing, position_sec, playback_rate, state_updated_at,
             members_json, expires_at
      FROM nki_watch_parties
      WHERE room_code = ${roomCode}
      LIMIT 1
    `;
    if (!rows.length || Number(rows[0].expires_at) < now) {
      return jsonResponse({ error: 'Watch Party room not found or expired' }, 404);
    }

    const row = rows[0];
    const isHost = Boolean(hostToken && hostToken === row.host_token);
    if (memberId) {
      const nextMembers = upsertHeartbeat(parseMembers(row.members_json), { memberId, nickname, isHost, now });
      row.members_json = nextMembers;
      await sql`
        UPDATE nki_watch_parties
        SET members_json = ${JSON.stringify(nextMembers)}::jsonb
        WHERE room_code = ${roomCode}
      `;
    }

    const messages = sinceMsgId > 0
      ? await sql`
          SELECT id, sender_name, sender_is_host, text, created_at
          FROM nki_party_messages
          WHERE room_code = ${roomCode} AND id > ${sinceMsgId}
          ORDER BY id ASC
          LIMIT 50
        `
      : await sql`
          SELECT id, sender_name, sender_is_host, text, created_at
          FROM (
            SELECT id, sender_name, sender_is_host, text, created_at
            FROM nki_party_messages
            WHERE room_code = ${roomCode}
            ORDER BY id DESC
            LIMIT 40
          ) sub
          ORDER BY id ASC
        `;

    metric('party.action', 1, { op: 'poll', outcome: 'success' });
    return jsonResponse({ ok: true, party: formatPartySnapshot(row, messages, now) });
  } catch (err) {
    console.error('[api/party GET] error:', err);
    metric('party.action', 1, { op: 'poll', outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Watch Party poll failed' }, 500);
  } finally {
    metric('party.duration_ms', performance.now() - startedAt, { op: 'poll' });
  }
}

export async function POST(request) {
  const startedAt = performance.now();
  let op = 'unknown';
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);
    op = String(body.action || 'create').trim().toLowerCase();

    await ensureSchema();
    const sql = getSql();
    const now = Date.now();

    if (op === 'create') {
      // Opportunistically clean expired rooms
      await sql`DELETE FROM nki_party_messages WHERE room_code IN (SELECT room_code FROM nki_watch_parties WHERE expires_at < ${now})`;
      await sql`DELETE FROM nki_watch_parties WHERE expires_at < ${now}`;

      const movie = body.movie && typeof body.movie === 'object' ? {
        slug: String(body.movie.slug || '').slice(0, 140),
        source: String(body.movie.source || 'phimapi').slice(0, 24),
        sourceId: String(body.movie.sourceId || '').slice(0, 64),
        name: String(body.movie.name || 'ChillFilm Movie').slice(0, 180),
        poster: String(body.movie.poster || '').slice(0, 500),
        year: String(body.movie.year || '').slice(0, 10),
      } : null;
      const episode = body.episode && typeof body.episode === 'object' ? {
        name: String(body.episode.name || 'Full').slice(0, 80),
        slug: String(body.episode.slug || 'full').slice(0, 80),
        link_m3u8: String(body.episode.link_m3u8 || '').slice(0, 700),
      } : null;

      if (!movie?.slug || !episode?.link_m3u8) {
        return jsonResponse({ error: 'Active movie and episode are required to start a Watch Party' }, 400);
      }

      const hostName = cleanNick(body.nickname || 'Host');
      const memberId = String(body.memberId || crypto.randomUUID().slice(0, 12)).slice(0, 48);
      const roomCode = generateRoomCode();
      const hostToken = crypto.randomBytes(16).toString('base64url');
      const positionSec = Math.max(0, Number(body.positionSec || 0));
      const isPlaying = Boolean(body.isPlaying);
      const members = [{ id: memberId, name: hostName, isHost: true, lastSeen: now }];

      await sql`
        INSERT INTO nki_watch_parties (
          room_code, host_token, host_name, movie_json, episode_json,
          is_playing, position_sec, playback_rate, state_updated_at,
          members_json, created_at, expires_at
        )
        VALUES (
          ${roomCode}, ${hostToken}, ${hostName},
          ${JSON.stringify(movie)}::jsonb, ${JSON.stringify(episode)}::jsonb,
          ${isPlaying}, ${positionSec}, 1, ${now},
          ${JSON.stringify(members)}::jsonb, ${now}, ${now + ROOM_TTL_MS}
        )
      `;
      await sql`
        INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
        VALUES (${roomCode}, 'ChillFilm', TRUE, ${`🍿 ${hostName} created Watch Party for "${movie.name}"`}, ${now})
      `;

      metric('party.action', 1, { op: 'create', outcome: 'success' });
      return jsonResponse({
        ok: true,
        roomCode,
        hostToken,
        memberId,
        party: {
          roomCode,
          hostName,
          movie,
          episode,
          isPlaying,
          positionSec,
          playbackRate: 1,
          stateUpdatedAt: now,
          serverTime: now,
          members: [{ id: memberId, name: hostName, isHost: true }],
          viewerCount: 1,
          messages: [{ id: 1, senderName: 'ChillFilm', isHost: true, text: `🍿 ${hostName} created Watch Party for "${movie.name}"`, createdAt: now }],
        },
      }, 201);
    }

    const roomCode = cleanRoomCode(body.roomCode);
    if (!roomCode || roomCode.length < 4) {
      return jsonResponse({ error: 'Valid roomCode is required' }, 400);
    }

    const rows = await sql`
      SELECT room_code, host_token, host_name, movie_json, episode_json,
             is_playing, position_sec, playback_rate, state_updated_at,
             members_json, expires_at
      FROM nki_watch_parties
      WHERE room_code = ${roomCode}
      LIMIT 1
    `;
    if (!rows.length || Number(rows[0].expires_at) < now) {
      return jsonResponse({ error: 'Watch Party room not found or expired' }, 404);
    }
    const row = rows[0];

    if (op === 'join') {
      const nickname = cleanNick(body.nickname || 'Guest');
      const memberId = String(body.memberId || crypto.randomUUID().slice(0, 12)).slice(0, 48);
      const nextMembers = upsertHeartbeat(parseMembers(row.members_json), { memberId, nickname, isHost: false, now });
      row.members_json = nextMembers;
      await sql`
        UPDATE nki_watch_parties
        SET members_json = ${JSON.stringify(nextMembers)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      await sql`
        INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
        VALUES (${roomCode}, 'ChillFilm', FALSE, ${`👋 ${nickname} joined the room`}, ${now})
      `;
      const messages = await sql`
        SELECT id, sender_name, sender_is_host, text, created_at
        FROM (
          SELECT id, sender_name, sender_is_host, text, created_at
          FROM nki_party_messages
          WHERE room_code = ${roomCode}
          ORDER BY id DESC
          LIMIT 40
        ) sub
        ORDER BY id ASC
      `;
      metric('party.action', 1, { op: 'join', outcome: 'success' });
      return jsonResponse({ ok: true, memberId, party: formatPartySnapshot(row, messages, now) });
    }

    if (op === 'control') {
      const hostToken = String(body.hostToken || '').trim();
      if (!hostToken || hostToken !== row.host_token) {
        return jsonResponse({ error: 'Only the room Host can control synced playback' }, 403);
      }
      const isPlaying = typeof body.isPlaying === 'boolean' ? body.isPlaying : Boolean(row.is_playing);
      const positionSec = Number.isFinite(Number(body.positionSec))
        ? Math.max(0, Number(body.positionSec))
        : Number(row.position_sec || 0);
      const playbackRate = Number.isFinite(Number(body.playbackRate))
        ? Math.max(0.5, Math.min(2.0, Number(body.playbackRate)))
        : Number(row.playback_rate || 1);
      const nextEp = body.episode && body.episode.link_m3u8
        ? {
            name: String(body.episode.name || 'Full').slice(0, 80),
            slug: String(body.episode.slug || 'full').slice(0, 80),
            link_m3u8: String(body.episode.link_m3u8).slice(0, 700),
          }
        : (typeof row.episode_json === 'string' ? JSON.parse(row.episode_json) : row.episode_json);

      await sql`
        UPDATE nki_watch_parties
        SET is_playing = ${isPlaying},
            position_sec = ${positionSec},
            playback_rate = ${playbackRate},
            episode_json = ${JSON.stringify(nextEp)}::jsonb,
            state_updated_at = ${now},
            expires_at = ${now + ROOM_TTL_MS}
        WHERE room_code = ${roomCode}
      `;
      metric('party.action', 1, { op: 'control', outcome: 'success' });
      return jsonResponse({ ok: true, serverTime: now, isPlaying, positionSec, playbackRate });
    }

    if (op === 'chat') {
      const senderName = cleanNick(body.nickname || 'Guest');
      const text = String(body.text || '').trim().slice(0, 240);
      if (!text) return jsonResponse({ error: 'Message text required' }, 400);
      const isHost = Boolean(body.hostToken && body.hostToken === row.host_token);
      const inserted = await sql`
        INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
        VALUES (${roomCode}, ${senderName}, ${isHost}, ${text}, ${now})
        RETURNING id, sender_name, sender_is_host, text, created_at
      `;
      // Keep newest 120 messages per room
      await sql`
        DELETE FROM nki_party_messages
        WHERE room_code = ${roomCode}
          AND id NOT IN (
            SELECT id FROM nki_party_messages WHERE room_code = ${roomCode} ORDER BY id DESC LIMIT 120
          )
      `;
      const m = inserted[0];
      metric('party.action', 1, { op: 'chat', outcome: 'success' });
      return jsonResponse({
        ok: true,
        message: {
          id: Number(m.id),
          senderName: m.sender_name,
          isHost: Boolean(m.sender_is_host),
          text: m.text,
          createdAt: Number(m.created_at),
        },
      }, 201);
    }

    if (op === 'leave') {
      const memberId = String(body.memberId || '').trim();
      const nextMembers = parseMembers(row.members_json).filter((m) => m && m.id !== memberId);
      await sql`
        UPDATE nki_watch_parties
        SET members_json = ${JSON.stringify(nextMembers)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      return jsonResponse({ ok: true });
    }

    return jsonResponse({ error: `Unsupported Watch Party action: ${op}` }, 400);
  } catch (err) {
    console.error('[api/party POST] error:', err);
    metric('party.action', 1, { op, outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Watch Party action failed' }, 500);
  } finally {
    metric('party.duration_ms', performance.now() - startedAt, { op });
  }
}
