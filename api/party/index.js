import crypto from 'node:crypto';
import { metric, waitUntil } from '@vercel/functions';
import { ensureSchema, getSql } from '../_lib/db.js';
import { CORS_HEADERS, jsonResponse, optionsResponse, readJsonBody } from '../_lib/auth.js';

const ROOM_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours
const MEMBER_STALE_MS = 30 * 1000; // 30s heartbeat window
const HOST_INACTIVE_MS = 38 * 1000; // Auto-allow host claim if host vanishes > 38s
const REACTION_TTL_MS = 22 * 1000; // Keep floating emoji reactions for 22s
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const ALLOWED_EMOJIS = new Set(['😂', '🔥', '😱', '❤️', '👏', '😭', '💀', '🍿', '🤯', '😍']);

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
  return s || 'Khách';
}

function hashPasscode(raw) {
  const s = String(raw || '').trim();
  if (!s) return '';
  const salt = crypto.randomBytes(8).toString('hex');
  const h = crypto.createHash('sha256').update(`${salt}:${s}`).digest('hex');
  return `${salt}$${h}`;
}

function verifyPasscode(raw, stored) {
  if (!stored) return true;
  const parts = String(stored).split('$');
  if (parts.length !== 2) return false;
  const [salt, expected] = parts;
  const actual = crypto.createHash('sha256').update(`${salt}:${String(raw || '').trim()}`).digest('hex');
  const aBuf = Buffer.from(actual);
  const eBuf = Buffer.from(expected);
  return aBuf.length === eBuf.length && crypto.timingSafeEqual(aBuf, eBuf);
}

function parseJsonField(raw, fallback) {
  try {
    if (raw === null || raw === undefined) return fallback;
    const val = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return val ?? fallback;
  } catch {
    return fallback;
  }
}

function sanitizeMovie(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const slug = String(raw.slug || '').trim().slice(0, 140);
  if (!slug) return null;
  return {
    slug,
    source: String(raw.source || 'phimapi').trim().slice(0, 24),
    sourceId: String(raw.sourceId || '').trim().slice(0, 64),
    name: String(raw.name || slug).trim().slice(0, 180),
    origin_name: String(raw.origin_name || '').trim().slice(0, 180),
    poster: String(raw.poster || '').trim().slice(0, 500),
    year: String(raw.year || '').trim().slice(0, 12),
    quality: String(raw.quality || 'HD').trim().slice(0, 20),
  };
}

function sanitizeEpisode(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const url = String(raw.link_m3u8 || '').trim().slice(0, 700);
  if (!url) return null;
  return {
    name: String(raw.name || 'Full').trim().slice(0, 80),
    slug: String(raw.slug || 'full').trim().slice(0, 80),
    link_m3u8: url,
  };
}

function upsertHeartbeat(members, { memberId, nickname, isHost, status = 'playing', posSec = 0, now }) {
  const fresh = members.filter((m) => m && m.id && now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS);
  if (memberId) {
    const idx = fresh.findIndex((m) => m.id === memberId);
    const cleanStatus = ['playing', 'paused', 'buffering'].includes(status) ? status : 'playing';
    const entry = {
      id: memberId,
      name: cleanNick(nickname),
      isHost: Boolean(isHost),
      status: cleanStatus,
      posSec: Number.isFinite(Number(posSec)) ? Math.max(0, Math.round(Number(posSec))) : 0,
      lastSeen: now,
    };
    if (idx >= 0) fresh[idx] = entry;
    else if (fresh.length < 50) fresh.push(entry);
  }
  return fresh;
}

function formatPartySnapshot(row, messages, now, { sinceReactionTs = 0 } = {}) {
  const rawMembers = parseJsonField(row.members_json, []);
  const members = (Array.isArray(rawMembers) ? rawMembers : []).filter(
    (m) => m && m.id && now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS
  );
  const hostOnline = members.some((m) => Boolean(m.isHost));
  const rawQueue = parseJsonField(row.queue_json, []);
  const queue = Array.isArray(rawQueue) ? rawQueue : [];
  const rawReactions = parseJsonField(row.reactions_json, []);
  const reactions = (Array.isArray(rawReactions) ? rawReactions : []).filter(
    (r) => r && now - Number(r.createdAt || 0) <= REACTION_TTL_MS && Number(r.createdAt || 0) > sinceReactionTs
  );

  return {
    roomCode: row.room_code,
    roomTitle: String(row.room_title || '').trim(),
    visibility: row.visibility === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE',
    controlMode: row.control_mode === 'EVERYONE' ? 'EVERYONE' : 'HOST_ONLY',
    hasPasscode: Boolean(row.passcode_hash),
    hostName: row.host_name,
    hostOnline,
    hostInactive: !hostOnline && now - Number(row.state_updated_at || 0) > HOST_INACTIVE_MS,
    movie: parseJsonField(row.movie_json, {}),
    episode: parseJsonField(row.episode_json, {}),
    isPlaying: Boolean(row.is_playing),
    positionSec: Number(row.position_sec || 0),
    playbackRate: Number(row.playback_rate || 1),
    stateUpdatedAt: Number(row.state_updated_at || now),
    lastActionText: String(row.last_action_text || ''),
    lastActionAt: Number(row.last_action_at || 0),
    serverTime: now,
    members: members.map((m) => ({
      id: m.id,
      name: m.name,
      isHost: Boolean(m.isHost),
      status: m.status || 'playing',
      posSec: Number(m.posSec || 0),
    })),
    viewerCount: Math.max(1, members.length),
    queue,
    reactions,
    messages: (messages || []).map((msg) => ({
      id: Number(msg.id),
      senderName: msg.sender_name,
      isHost: Boolean(msg.sender_is_host),
      text: msg.text,
      createdAt: Number(msg.created_at),
    })),
  };
}

function runBg(promise) {
  try {
    waitUntil(promise.catch(() => {}));
  } catch {
    promise.catch(() => {});
  }
}

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const startedAt = performance.now();
  let op = 'poll';
  try {
    const url = new URL(request.url);
    op = String(url.searchParams.get('op') || 'poll').trim().toLowerCase();

    await ensureSchema();
    const sql = getSql();
    const now = Date.now();

    /* ==================== PUBLIC WATCH PARTY LOBBY ==================== */
    if (op === 'lobby') {
      const rows = await sql`
        SELECT room_code, room_title, visibility, control_mode, passcode_hash,
               host_name, movie_json, episode_json, is_playing, position_sec,
               state_updated_at, members_json, created_at
        FROM nki_watch_parties
        WHERE visibility = 'PUBLIC' AND expires_at > ${now}
        ORDER BY state_updated_at DESC
        LIMIT 30
      `;

      const rooms = [];
      for (const r of rows || []) {
        const rawMembers = parseJsonField(r.members_json, []);
        const activeMembers = (Array.isArray(rawMembers) ? rawMembers : []).filter(
          (m) => m && now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS * 2
        );
        // Show rooms with active viewers OR created/updated within the last 15 minutes
        const recentlyActive = now - Number(r.state_updated_at || r.created_at || 0) <= 15 * 60 * 1000;
        if (!activeMembers.length && !recentlyActive) continue;
        const movie = parseJsonField(r.movie_json, {});
        const episode = parseJsonField(r.episode_json, {});
        rooms.push({
          roomCode: r.room_code,
          roomTitle: String(r.room_title || '').trim() || `Phòng của ${r.host_name}`,
          hostName: r.host_name,
          controlMode: r.control_mode === 'EVERYONE' ? 'EVERYONE' : 'HOST_ONLY',
          hasPasscode: Boolean(r.passcode_hash),
          movie,
          episodeName: episode?.name || 'Full',
          isPlaying: Boolean(r.is_playing),
          viewerCount: Math.max(activeMembers.length, recentlyActive ? 1 : 0),
          activeNow: activeMembers.length > 0,
          updatedAt: Number(r.state_updated_at || r.created_at || now),
        });
      }

      rooms.sort((a, b) => (b.viewerCount - a.viewerCount) || (b.updatedAt - a.updatedAt));
      metric('party.action', 1, { op: 'lobby', outcome: 'success' });
      return new Response(JSON.stringify({ ok: true, serverTime: now, rooms }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=4, stale-while-revalidate=10',
        },
      });
    }

    /* ==================== ROOM SYNC POLL ==================== */
    const roomCode = cleanRoomCode(url.searchParams.get('room'));
    const sinceMsgId = Math.max(0, Number(url.searchParams.get('sinceMsgId') || 0));
    const sinceReactionTs = Math.max(0, Number(url.searchParams.get('sinceReactionTs') || 0));
    const memberId = String(url.searchParams.get('memberId') || '').trim().slice(0, 48);
    const nickname = cleanNick(url.searchParams.get('nickname') || 'Khách');
    const hostToken = String(url.searchParams.get('hostToken') || '').trim();
    const memberStatus = String(url.searchParams.get('status') || 'playing').trim().toLowerCase();
    const memberPos = Number(url.searchParams.get('pos') || 0);

    if (!roomCode || roomCode.length < 4) {
      return jsonResponse({ error: 'Mã phòng không hợp lệ' }, 400);
    }

    const [rows, messages] = await Promise.all([
      sql`
        SELECT room_code, room_title, visibility, control_mode, passcode_hash,
               host_token, host_name, movie_json, episode_json,
               is_playing, position_sec, playback_rate, state_updated_at,
               members_json, queue_json, reactions_json,
               last_action_text, last_action_at, expires_at
        FROM nki_watch_parties
        WHERE room_code = ${roomCode}
        LIMIT 1
      `,
      sinceMsgId > 0
        ? sql`
            SELECT id, sender_name, sender_is_host, text, created_at
            FROM nki_party_messages
            WHERE room_code = ${roomCode} AND id > ${sinceMsgId}
            ORDER BY id ASC
            LIMIT 50
          `
        : sql`
            SELECT id, sender_name, sender_is_host, text, created_at
            FROM (
              SELECT id, sender_name, sender_is_host, text, created_at
              FROM nki_party_messages
              WHERE room_code = ${roomCode}
              ORDER BY id DESC
              LIMIT 40
            ) sub
            ORDER BY id ASC
          `,
    ]);

    if (!rows.length || Number(rows[0].expires_at) < now) {
      return jsonResponse({ error: 'Phòng xem chung không tồn tại hoặc đã kết thúc' }, 404);
    }

    const row = rows[0];
    const isHost = Boolean(hostToken && hostToken === row.host_token);
    if (memberId) {
      const prevMembers = parseJsonField(row.members_json, []);
      const prevSelf = Array.isArray(prevMembers) ? prevMembers.find((m) => m?.id === memberId) : null;
      const nextMembers = upsertHeartbeat(Array.isArray(prevMembers) ? prevMembers : [], {
        memberId,
        nickname,
        isHost,
        status: memberStatus,
        posSec: memberPos,
        now,
      });
      row.members_json = nextMembers;
      // Write heartbeat in background when already present to cut poll latency in HALF!
      const shouldWrite = !prevSelf || prevSelf.status !== memberStatus || now - Number(prevSelf.lastSeen || 0) >= 6000;
      if (shouldWrite) {
        const updateP = sql`
          UPDATE nki_watch_parties
          SET members_json = ${JSON.stringify(nextMembers)}::jsonb
          WHERE room_code = ${roomCode}
        `;
        if (prevSelf) runBg(updateP);
        else await updateP;
      }
    }

    metric('party.action', 1, { op: 'poll', outcome: 'success' });
    return jsonResponse({
      ok: true,
      party: formatPartySnapshot(row, messages, now, { sinceReactionTs }),
    });
  } catch (err) {
    console.error('[api/party GET] error:', err);
    metric('party.action', 1, { op, outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Watch Party poll failed' }, 500);
  } finally {
    metric('party.duration_ms', performance.now() - startedAt, { op });
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

    /* ==================== CREATE ROOM ==================== */
    if (op === 'create') {
      // Background cleanup of expired rooms (zero wait for host!)
      runBg(
        sql`DELETE FROM nki_party_messages WHERE room_code IN (SELECT room_code FROM nki_watch_parties WHERE expires_at < ${now})`
          .then(() => sql`DELETE FROM nki_watch_parties WHERE expires_at < ${now}`)
      );

      const movie = sanitizeMovie(body.movie);
      const episode = sanitizeEpisode(body.episode);
      if (!movie?.slug || !episode?.link_m3u8) {
        return jsonResponse({ error: 'Cần chọn 1 bộ phim đang phát để tạo phòng Xem chung' }, 400);
      }

      const hostName = cleanNick(body.nickname || 'Host');
      const memberId = String(body.memberId || crypto.randomUUID().slice(0, 12)).slice(0, 48);
      const roomCode = generateRoomCode();
      const hostToken = crypto.randomBytes(16).toString('base64url');
      const roomTitle = String(body.roomTitle || '').trim().slice(0, 80) || `${movie.name}`;
      const visibility = String(body.visibility || 'PRIVATE').toUpperCase() === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE';
      const controlMode = String(body.controlMode || 'HOST_ONLY').toUpperCase() === 'EVERYONE' ? 'EVERYONE' : 'HOST_ONLY';
      const passcodeHash = hashPasscode(body.passcode || '');
      const positionSec = Math.max(0, Number(body.positionSec || 0));
      const isPlaying = Boolean(body.isPlaying);
      const members = [{ id: memberId, name: hostName, isHost: true, status: isPlaying ? 'playing' : 'paused', posSec: Math.round(positionSec), lastSeen: now }];
      const welcomeText = `🍿 ${hostName} đã tạo phòng "${roomTitle}" (${visibility === 'PUBLIC' ? '🌐 Công khai' : '🔒 Riêng tư'} • ${controlMode === 'EVERYONE' ? '🤝 Ai cũng điều khiển' : '👑 Chủ phòng điều khiển'})`;

      await sql`
        INSERT INTO nki_watch_parties (
          room_code, room_title, visibility, control_mode, passcode_hash,
          host_token, host_name, movie_json, episode_json,
          is_playing, position_sec, playback_rate, state_updated_at,
          members_json, queue_json, reactions_json,
          last_action_text, last_action_at, created_at, expires_at
        )
        VALUES (
          ${roomCode}, ${roomTitle}, ${visibility}, ${controlMode}, ${passcodeHash},
          ${hostToken}, ${hostName},
          ${JSON.stringify(movie)}::jsonb, ${JSON.stringify(episode)}::jsonb,
          ${isPlaying}, ${positionSec}, 1, ${now},
          ${JSON.stringify(members)}::jsonb, '[]'::jsonb, '[]'::jsonb,
          ${`🍿 ${hostName} đã mở phòng`}, ${now}, ${now}, ${now + ROOM_TTL_MS}
        )
      `;
      const insertedMsg = await sql`
        INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
        VALUES (${roomCode}, 'ChillFilm', TRUE, ${welcomeText}, ${now})
        RETURNING id, sender_name, sender_is_host, text, created_at
      `;

      metric('party.action', 1, { op: 'create', outcome: 'success' });
      return jsonResponse({
        ok: true,
        roomCode,
        hostToken,
        memberId,
        party: {
          roomCode,
          roomTitle,
          visibility,
          controlMode,
          hasPasscode: Boolean(passcodeHash),
          hostName,
          hostOnline: true,
          hostInactive: false,
          movie,
          episode,
          isPlaying,
          positionSec,
          playbackRate: 1,
          stateUpdatedAt: now,
          lastActionText: `🍿 ${hostName} đã mở phòng`,
          lastActionAt: now,
          serverTime: now,
          members: members.map((m) => ({ id: m.id, name: m.name, isHost: true, status: m.status, posSec: m.posSec })),
          viewerCount: 1,
          queue: [],
          reactions: [],
          messages: insertedMsg.map((m) => ({
            id: Number(m.id),
            senderName: m.sender_name,
            isHost: Boolean(m.sender_is_host),
            text: m.text,
            createdAt: Number(m.created_at),
          })),
        },
      }, 201);
    }

    const roomCode = cleanRoomCode(body.roomCode);
    if (!roomCode || roomCode.length < 4) {
      return jsonResponse({ error: 'Mã phòng không hợp lệ' }, 400);
    }

    const rows = await sql`
      SELECT room_code, room_title, visibility, control_mode, passcode_hash,
             host_token, host_name, movie_json, episode_json,
             is_playing, position_sec, playback_rate, state_updated_at,
             members_json, queue_json, reactions_json,
             last_action_text, last_action_at, expires_at
      FROM nki_watch_parties
      WHERE room_code = ${roomCode}
      LIMIT 1
    `;
    if (!rows.length || Number(rows[0].expires_at) < now) {
      return jsonResponse({ error: 'Phòng xem chung không tồn tại hoặc đã hết hạn' }, 404);
    }
    const row = rows[0];
    const isHost = Boolean(body.hostToken && String(body.hostToken).trim() === row.host_token);
    const canControl = isHost || row.control_mode === 'EVERYONE';

    /* ==================== JOIN ROOM (WITH OPTIONAL PASSCODE) ==================== */
    if (op === 'join') {
      if (row.passcode_hash && !isHost) {
        const providedPass = String(body.passcode || '').trim();
        if (!providedPass) {
          return jsonResponse({ error: 'Phòng này yêu cầu mật khẩu', needPasscode: true }, 403);
        }
        if (!verifyPasscode(providedPass, row.passcode_hash)) {
          return jsonResponse({ error: 'Sai mật khẩu phòng!', needPasscode: true }, 403);
        }
      }

      const nickname = cleanNick(body.nickname || 'Khách');
      const memberId = String(body.memberId || crypto.randomUUID().slice(0, 12)).slice(0, 48);
      const nextMembers = upsertHeartbeat(parseJsonField(row.members_json, []), {
        memberId,
        nickname,
        isHost,
        status: 'playing',
        posSec: Number(row.position_sec || 0),
        now,
      });
      row.members_json = nextMembers;

      const [, , messages] = await Promise.all([
        sql`
          UPDATE nki_watch_parties
          SET members_json = ${JSON.stringify(nextMembers)}::jsonb
          WHERE room_code = ${roomCode}
        `,
        body.silentRejoin
          ? Promise.resolve()
          : sql`
              INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
              VALUES (${roomCode}, 'ChillFilm', FALSE, ${`👋 ${nickname} đã vào phòng xem chung`}, ${now})
            `,
        sql`
          SELECT id, sender_name, sender_is_host, text, created_at
          FROM (
            SELECT id, sender_name, sender_is_host, text, created_at
            FROM nki_party_messages
            WHERE room_code = ${roomCode}
            ORDER BY id DESC
            LIMIT 40
          ) sub
          ORDER BY id ASC
        `,
      ]);
      metric('party.action', 1, { op: 'join', outcome: 'success' });
      return jsonResponse({
        ok: true,
        memberId,
        isHost,
        party: formatPartySnapshot(row, messages, now),
      });
    }

    /* ==================== SYNC CONTROL (PLAY / PAUSE / SEEK / COUNTDOWN / CHANGE MOVIE OR EPISODE) ==================== */
    if (op === 'control') {
      if (!canControl) {
        return jsonResponse({ error: 'Chỉ Chủ phòng mới có quyền điều khiển (Chủ phòng có thể bật chế độ "Ai cũng điều khiển")' }, 403);
      }
      const actorName = cleanNick(body.nickname || (isHost ? row.host_name : 'Thành viên'));
      const isCountdown = Boolean(body.countdown);
      const isPlaying = isCountdown ? true : (typeof body.isPlaying === 'boolean' ? body.isPlaying : Boolean(row.is_playing));
      const positionSec = Number.isFinite(Number(body.positionSec))
        ? Math.max(0, Number(body.positionSec))
        : Number(row.position_sec || 0);
      const playbackRate = Number.isFinite(Number(body.playbackRate))
        ? Math.max(0.5, Math.min(2.0, Number(body.playbackRate)))
        : Number(row.playback_rate || 1);
      const curMovie = parseJsonField(row.movie_json, {});
      const curEp = parseJsonField(row.episode_json, {});
      const nextMovie = sanitizeMovie(body.movie) || curMovie;
      const nextEp = sanitizeEpisode(body.episode) || curEp;
      const stateUpdatedAt = isCountdown ? now + 3200 : now;

      let actionText = String(body.actionNote || '').trim().slice(0, 120);
      if (!actionText) {
        if (isCountdown) actionText = `⏱️ ${actorName} đếm ngược 3-2-1 bắt đầu cùng lúc!`;
        else if (nextMovie.slug !== curMovie.slug) actionText = `🎬 ${actorName} đổi phim → ${nextMovie.name}`;
        else if (nextEp.link_m3u8 !== curEp.link_m3u8) actionText = `📺 ${actorName} chuyển sang ${nextEp.name}`;
      }

      await sql`
        UPDATE nki_watch_parties
        SET is_playing = ${isPlaying},
            position_sec = ${positionSec},
            playback_rate = ${playbackRate},
            movie_json = ${JSON.stringify(nextMovie)}::jsonb,
            episode_json = ${JSON.stringify(nextEp)}::jsonb,
            state_updated_at = ${stateUpdatedAt},
            last_action_text = ${actionText || row.last_action_text || ''},
            last_action_at = ${actionText ? now : Number(row.last_action_at || 0)},
            expires_at = ${now + ROOM_TTL_MS}
        WHERE room_code = ${roomCode}
      `;
      if (actionText && (isCountdown || nextMovie.slug !== curMovie.slug || nextEp.link_m3u8 !== curEp.link_m3u8)) {
        runBg(
          sql`
            INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
            VALUES (${roomCode}, 'ChillFilm', TRUE, ${actionText}, ${now})
          `
        );
      }

      metric('party.action', 1, { op: 'control', outcome: 'success' });
      return jsonResponse({
        ok: true,
        serverTime: now,
        stateUpdatedAt,
        isPlaying,
        positionSec,
        playbackRate,
        movie: nextMovie,
        episode: nextEp,
        lastActionText: actionText,
      });
    }

    /* ==================== ROOM SETTINGS & HOST TRANSFER / CLAIM ==================== */
    if (op === 'settings') {
      if (!isHost) return jsonResponse({ error: 'Chỉ Chủ phòng mới được chỉnh cài đặt phòng' }, 403);
      const nextControl = body.controlMode
        ? (String(body.controlMode).toUpperCase() === 'EVERYONE' ? 'EVERYONE' : 'HOST_ONLY')
        : row.control_mode;
      const nextVis = body.visibility
        ? (String(body.visibility).toUpperCase() === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE')
        : row.visibility;
      const nextTitle = typeof body.roomTitle === 'string' && body.roomTitle.trim()
        ? body.roomTitle.trim().slice(0, 80)
        : row.room_title;
      let nextPassHash = row.passcode_hash;
      if (typeof body.passcode === 'string') {
        nextPassHash = body.passcode.trim() ? hashPasscode(body.passcode.trim()) : '';
      }
      const noteParts = [];
      if (nextControl !== row.control_mode) {
        noteParts.push(nextControl === 'EVERYONE' ? '🤝 Bật chế độ: Ai cũng điều khiển được' : '👑 Chỉ Chủ phòng điều khiển');
      }
      if (nextVis !== row.visibility) {
        noteParts.push(nextVis === 'PUBLIC' ? '🌐 Phòng chuyển sang Công khai' : '🔒 Phòng chuyển sang Riêng tư');
      }
      const note = noteParts.join(' • ');
      await sql`
        UPDATE nki_watch_parties
        SET control_mode = ${nextControl},
            visibility = ${nextVis},
            room_title = ${nextTitle},
            passcode_hash = ${nextPassHash},
            last_action_text = ${note || row.last_action_text || ''},
            last_action_at = ${note ? now : Number(row.last_action_at || 0)}
        WHERE room_code = ${roomCode}
      `;
      if (note) {
        runBg(
          sql`INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at) VALUES (${roomCode}, 'ChillFilm', TRUE, ${note}, ${now})`
        );
      }
      metric('party.action', 1, { op: 'settings', outcome: 'success' });
      return jsonResponse({ ok: true, controlMode: nextControl, visibility: nextVis, roomTitle: nextTitle, hasPasscode: Boolean(nextPassHash) });
    }

    if (op === 'claim_host') {
      const members = parseJsonField(row.members_json, []);
      const freshMembers = (Array.isArray(members) ? members : []).filter((m) => m && now - Number(m.lastSeen || 0) <= MEMBER_STALE_MS);
      const hostOnline = freshMembers.some((m) => Boolean(m.isHost));
      if (hostOnline && !isHost) {
        return jsonResponse({ error: 'Chủ phòng vẫn đang trực tuyến' }, 403);
      }
      const memberId = String(body.memberId || '').trim().slice(0, 48);
      const nickname = cleanNick(body.nickname || 'Host');
      const newHostToken = crypto.randomBytes(16).toString('base64url');
      const updatedMembers = upsertHeartbeat(
        freshMembers.map((m) => ({ ...m, isHost: false })),
        { memberId, nickname, isHost: true, status: 'playing', now }
      );
      await sql`
        UPDATE nki_watch_parties
        SET host_token = ${newHostToken},
            host_name = ${nickname},
            members_json = ${JSON.stringify(updatedMembers)}::jsonb,
            last_action_text = ${`👑 ${nickname} đã tiếp quản quyền Chủ phòng`},
            last_action_at = ${now}
        WHERE room_code = ${roomCode}
      `;
      runBg(
        sql`INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at) VALUES (${roomCode}, 'ChillFilm', TRUE, ${`👑 ${nickname} đã trở thành Chủ phòng mới`}, ${now})`
      );
      metric('party.action', 1, { op: 'claim_host', outcome: 'success' });
      return jsonResponse({ ok: true, hostToken: newHostToken, hostName: nickname });
    }

    /* ==================== UP NEXT QUEUE (ADD / VOTE / REMOVE / PLAY NEXT) ==================== */
    if (op === 'queue_add') {
      const movie = sanitizeMovie(body.movie);
      const episode = sanitizeEpisode(body.episode);
      if (!movie?.slug || !episode?.link_m3u8) {
        return jsonResponse({ error: 'Thiếu thông tin phim hoặc tập để thêm vào hàng chờ' }, 400);
      }
      const addedBy = cleanNick(body.nickname || 'Thành viên');
      const memberId = String(body.memberId || '').trim().slice(0, 48);
      const queue = parseJsonField(row.queue_json, []);
      const list = Array.isArray(queue) ? queue.slice(0, 24) : [];
      const dup = list.find((q) => q?.movie?.slug === movie.slug && q?.episode?.link_m3u8 === episode.link_m3u8);
      if (dup) {
        return jsonResponse({ ok: true, queue: list, duplicate: true });
      }
      const item = {
        id: crypto.randomUUID().slice(0, 10),
        movie,
        episode,
        addedBy,
        addedById: memberId,
        votes: memberId ? [memberId] : [],
        addedAt: now,
      };
      list.push(item);
      await sql`
        UPDATE nki_watch_parties
        SET queue_json = ${JSON.stringify(list)}::jsonb,
            last_action_text = ${`➕ ${addedBy} thêm "${movie.name}" (${episode.name}) vào hàng chờ`},
            last_action_at = ${now}
        WHERE room_code = ${roomCode}
      `;
      runBg(
        sql`INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at) VALUES (${roomCode}, 'ChillFilm', FALSE, ${`🎬 ${addedBy} thêm "${movie.name} • ${episode.name}" vào Hàng chờ`}, ${now})`
      );
      metric('party.action', 1, { op: 'queue_add', outcome: 'success' });
      return jsonResponse({ ok: true, queue: list, item }, 201);
    }

    if (op === 'queue_vote') {
      const itemId = String(body.itemId || '').trim();
      const memberId = String(body.memberId || '').trim().slice(0, 48);
      if (!itemId || !memberId) return jsonResponse({ error: 'itemId & memberId required' }, 400);
      const queue = parseJsonField(row.queue_json, []);
      const list = (Array.isArray(queue) ? queue : []).map((it) => {
        if (!it || it.id !== itemId) return it;
        const votes = Array.isArray(it.votes) ? it.votes.filter(Boolean) : [];
        const has = votes.includes(memberId);
        return { ...it, votes: has ? votes.filter((v) => v !== memberId) : [...votes, memberId] };
      });
      // Sort highest-voted items to top (preserving order on tie)
      list.sort((a, b) => (b?.votes?.length || 0) - (a?.votes?.length || 0) || (a?.addedAt || 0) - (b?.addedAt || 0));
      await sql`
        UPDATE nki_watch_parties
        SET queue_json = ${JSON.stringify(list)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      metric('party.action', 1, { op: 'queue_vote', outcome: 'success' });
      return jsonResponse({ ok: true, queue: list });
    }

    if (op === 'queue_remove') {
      const itemId = String(body.itemId || '').trim();
      const memberId = String(body.memberId || '').trim();
      const queue = parseJsonField(row.queue_json, []);
      const list = (Array.isArray(queue) ? queue : []).filter((it) => {
        if (!it || it.id !== itemId) return true;
        return !(canControl || (memberId && it.addedById === memberId));
      });
      await sql`
        UPDATE nki_watch_parties
        SET queue_json = ${JSON.stringify(list)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      metric('party.action', 1, { op: 'queue_remove', outcome: 'success' });
      return jsonResponse({ ok: true, queue: list });
    }

    if (op === 'queue_play') {
      if (!canControl) return jsonResponse({ error: 'Chỉ Chủ phòng mới được chuyển phim tiếp theo' }, 403);
      const itemId = String(body.itemId || '').trim();
      const actorName = cleanNick(body.nickname || row.host_name);
      const queue = parseJsonField(row.queue_json, []);
      const list = Array.isArray(queue) ? queue.slice() : [];
      const idx = itemId ? list.findIndex((it) => it?.id === itemId) : 0;
      if (idx < 0 || !list[idx]) return jsonResponse({ error: 'Hàng chờ đang trống' }, 404);
      const picked = list.splice(idx, 1)[0];
      const note = `🎬 ${actorName} phát từ Hàng chờ → ${picked.movie.name} (${picked.episode.name})`;

      await sql`
        UPDATE nki_watch_parties
        SET movie_json = ${JSON.stringify(picked.movie)}::jsonb,
            episode_json = ${JSON.stringify(picked.episode)}::jsonb,
            is_playing = TRUE,
            position_sec = 0,
            playback_rate = 1,
            state_updated_at = ${now},
            queue_json = ${JSON.stringify(list)}::jsonb,
            last_action_text = ${note},
            last_action_at = ${now},
            expires_at = ${now + ROOM_TTL_MS}
        WHERE room_code = ${roomCode}
      `;
      runBg(
        sql`INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at) VALUES (${roomCode}, 'ChillFilm', TRUE, ${note}, ${now})`
      );
      metric('party.action', 1, { op: 'queue_play', outcome: 'success' });
      return jsonResponse({
        ok: true,
        movie: picked.movie,
        episode: picked.episode,
        queue: list,
        serverTime: now,
      });
    }

    /* ==================== FLOATING EMOJI REACTIONS ==================== */
    if (op === 'react') {
      const emoji = String(body.emoji || '').trim();
      if (!ALLOWED_EMOJIS.has(emoji)) return jsonResponse({ error: 'Unsupported reaction' }, 400);
      const senderName = cleanNick(body.nickname || 'Khách');
      const prev = parseJsonField(row.reactions_json, []);
      const fresh = (Array.isArray(prev) ? prev : []).filter((r) => r && now - Number(r.createdAt || 0) <= REACTION_TTL_MS);
      const reaction = {
        id: crypto.randomUUID().slice(0, 8),
        emoji,
        senderName,
        createdAt: now,
      };
      const nextReactions = [...fresh.slice(-23), reaction];
      await sql`
        UPDATE nki_watch_parties
        SET reactions_json = ${JSON.stringify(nextReactions)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      metric('party.action', 1, { op: 'react', outcome: 'success' });
      return jsonResponse({ ok: true, reaction });
    }

    /* ==================== LIVE CHAT ==================== */
    if (op === 'chat') {
      const senderName = cleanNick(body.nickname || 'Khách');
      const text = String(body.text || '').trim().slice(0, 240);
      if (!text) return jsonResponse({ error: 'Nội dung tin nhắn không được để trống' }, 400);
      const inserted = await sql`
        INSERT INTO nki_party_messages (room_code, sender_name, sender_is_host, text, created_at)
        VALUES (${roomCode}, ${senderName}, ${isHost}, ${text}, ${now})
        RETURNING id, sender_name, sender_is_host, text, created_at
      `;
      // Prune old chat messages asynchronously in background (zero wait for sender!)
      runBg(
        sql`
          DELETE FROM nki_party_messages
          WHERE room_code = ${roomCode}
            AND id NOT IN (
              SELECT id FROM nki_party_messages WHERE room_code = ${roomCode} ORDER BY id DESC LIMIT 120
            )
        `
      );
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

    /* ==================== LEAVE ROOM ==================== */
    if (op === 'leave') {
      const memberId = String(body.memberId || '').trim();
      const nextMembers = parseJsonField(row.members_json, []).filter((m) => m && m.id !== memberId);
      await sql`
        UPDATE nki_watch_parties
        SET members_json = ${JSON.stringify(nextMembers)}::jsonb
        WHERE room_code = ${roomCode}
      `;
      metric('party.action', 1, { op: 'leave', outcome: 'success' });
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
