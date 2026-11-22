import { metric } from '@vercel/functions';
import {
  ensureSchema,
  getSql,
  normalizeEpisodeKey,
  normalizeMovieSlug,
} from '../_lib/db.js';
import {
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAuth,
} from '../_lib/auth.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const auth = await requireAuth(request);
    if (auth.errorResponse) return auth.errorResponse;
    const { user } = auth;

    const url = new URL(request.url);
    const since = Math.max(0, Number(url.searchParams.get('since') || 0));

    await ensureSchema();
    const sql = getSql();

    const [progressRows, prefsRows, myListRows] = await Promise.all([
      since > 0
        ? sql`
            SELECT movie_slug, episode_key, title, poster_url, country_code, episode_name, stream_url, position_ms, duration_ms, updated_at
            FROM nki_watch_progress
            WHERE user_id = ${user.id} AND updated_at > ${since}
            ORDER BY updated_at DESC
            LIMIT 250
          `
        : sql`
            SELECT movie_slug, episode_key, title, poster_url, country_code, episode_name, stream_url, position_ms, duration_ms, updated_at
            FROM nki_watch_progress
            WHERE user_id = ${user.id}
            ORDER BY updated_at DESC
            LIMIT 250
          `,
      sql`SELECT prefs_json, updated_at FROM nki_user_prefs WHERE user_id = ${user.id} LIMIT 1`,
      sql`
        SELECT item_key, source, source_id, slug, name, origin_name, poster, year, quality, last_known_episode, added_at
        FROM nki_my_list
        WHERE user_id = ${user.id}
        ORDER BY added_at DESC
        LIMIT 200
      `,
    ]);

    // Episode Release Tracker (#10): cross-check My List items against latest crawled episode counts!
    const latestEpBySlug = new Map();
    const mySlugs = myListRows.map((r) => r.slug).filter(Boolean);
    if (mySlugs.length) {
      try {
        const catRows = await sql`
          SELECT slug, episode_current
          FROM nki_catalog_items
          WHERE slug = ANY(${mySlugs}) AND episode_current <> ''
          ORDER BY updated_at DESC
        `;
        for (const cr of catRows || []) {
          if (cr.slug && cr.episode_current && !latestEpBySlug.has(cr.slug)) {
            latestEpBySlug.set(cr.slug, String(cr.episode_current));
          }
        }
      } catch { /* non-fatal */ }
    }
    const extractEpNum = (str) => {
      const m = String(str || '').match(/(?:tập|tap|ep|episode|第)?\s*(\d{1,4})/i);
      return m ? Number(m[1]) : 0;
    };

    metric('sync.state', 1, { direction: 'pull', outcome: 'success' });
    return jsonResponse({
      ok: true,
      serverTime: Date.now(),
      watchProgress: progressRows.map((r) => ({
        movieSlug: r.movie_slug,
        episodeKey: r.episode_key,
        title: r.title,
        posterUrl: r.poster_url,
        countryCode: r.country_code,
        episodeName: r.episode_name,
        streamUrl: r.stream_url,
        positionMs: Number(r.position_ms || 0),
        durationMs: Number(r.duration_ms || 0),
        updatedAt: Number(r.updated_at || 0),
      })),
      myList: myListRows.map((r) => {
        const latestEpisode = latestEpBySlug.get(r.slug) || '';
        const knownEp = String(r.last_known_episode || r.quality || '');
        const newNum = extractEpNum(latestEpisode);
        const oldNum = extractEpNum(knownEp);
        const hasNewEpisode = Boolean(latestEpisode && newNum > 0 && ((oldNum > 0 && newNum > oldNum) || (!oldNum && /tập|ep|第/i.test(latestEpisode) && latestEpisode !== knownEp)));
        return {
          itemKey: r.item_key,
          source: r.source,
          sourceId: r.source_id,
          slug: r.slug,
          name: r.name,
          origin_name: r.origin_name,
          poster: r.poster,
          year: r.year,
          quality: r.quality,
          lastKnownEpisode: knownEp,
          latestEpisode,
          hasNewEpisode,
          addedAt: Number(r.added_at || 0),
        };
      }),
      prefs: prefsRows.length ? prefsRows[0].prefs_json : null,
      prefsUpdatedAt: prefsRows.length ? Number(prefsRows[0].updated_at || 0) : 0,
    });
  } catch (err) {
    console.error('[api/sync/state GET] error:', err);
    metric('sync.state', 1, { direction: 'pull', outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Sync pull failed' }, 500);
  } finally {
    metric('sync.state.duration_ms', performance.now() - startedAt, { direction: 'pull' });
  }
}

export async function POST(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const auth = await requireAuth(request, body);
    if (auth.errorResponse) return auth.errorResponse;
    const { user } = auth;

    await ensureSchema();
    const sql = getSql();
    const now = Date.now();
    let upsertedCount = 0;

    if (Array.isArray(body.watchProgress) && body.watchProgress.length > 0) {
      const items = body.watchProgress.slice(0, 120);
      for (const raw of items) {
        if (!raw) continue;
        const movieSlug = normalizeMovieSlug(raw.movieSlug || raw.movieId || raw.slug);
        if (!movieSlug || movieSlug === 'unknown') continue;
        const episodeName = String(raw.episodeName || raw.epName || 'Full').trim().slice(0, 80);
        const episodeKey = normalizeEpisodeKey(raw.episodeKey || episodeName || raw.episodeId);
        const title = String(raw.title || raw.name || movieSlug).trim().slice(0, 180);
        const posterUrl = String(raw.posterUrl || raw.poster || '').trim().slice(0, 500);
        const countryCode = String(raw.countryCode || '').trim().slice(0, 20);
        const streamUrl = String(raw.streamUrl || '').trim().slice(0, 700);
        const positionMs = Math.max(0, Math.round(Number(raw.positionMs ?? (Number(raw.pos || 0) * 1000))));
        const durationMs = Math.max(0, Math.round(Number(raw.durationMs ?? (Number(raw.dur || 0) * 1000))));
        const updatedAt = Math.min(now + 60_000, Math.max(1, Math.round(Number(raw.updatedAt || now))));

        await sql`
          INSERT INTO nki_watch_progress (
            user_id, movie_slug, episode_key, title, poster_url, country_code, episode_name, stream_url, position_ms, duration_ms, updated_at
          )
          VALUES (
            ${user.id}, ${movieSlug}, ${episodeKey}, ${title}, ${posterUrl}, ${countryCode}, ${episodeName}, ${streamUrl}, ${positionMs}, ${durationMs}, ${updatedAt}
          )
          ON CONFLICT (user_id, movie_slug, episode_key) DO UPDATE SET
            title = EXCLUDED.title,
            poster_url = CASE WHEN EXCLUDED.poster_url <> '' THEN EXCLUDED.poster_url ELSE nki_watch_progress.poster_url END,
            country_code = CASE WHEN EXCLUDED.country_code <> '' THEN EXCLUDED.country_code ELSE nki_watch_progress.country_code END,
            episode_name = EXCLUDED.episode_name,
            stream_url = CASE WHEN EXCLUDED.stream_url <> '' THEN EXCLUDED.stream_url ELSE nki_watch_progress.stream_url END,
            position_ms = EXCLUDED.position_ms,
            duration_ms = GREATEST(EXCLUDED.duration_ms, nki_watch_progress.duration_ms),
            updated_at = EXCLUDED.updated_at
          WHERE EXCLUDED.updated_at >= nki_watch_progress.updated_at
        `;
        upsertedCount++;
      }
    }

    if (body.prefs && typeof body.prefs === 'object') {
      const prefsUpdatedAt = Math.min(now + 60_000, Math.max(1, Math.round(Number(body.prefsUpdatedAt || now))));
      const prefsJson = JSON.stringify(body.prefs);
      await sql`
        INSERT INTO nki_user_prefs (user_id, prefs_json, updated_at)
        VALUES (${user.id}, ${prefsJson}::jsonb, ${prefsUpdatedAt})
        ON CONFLICT (user_id) DO UPDATE SET
          prefs_json = EXCLUDED.prefs_json,
          updated_at = EXCLUDED.updated_at
        WHERE EXCLUDED.updated_at >= nki_user_prefs.updated_at
      `;
    }

    if (Array.isArray(body.myList) && body.myList.length > 0) {
      for (const raw of body.myList.slice(0, 120)) {
        if (!raw || !raw.slug) continue;
        const source = String(raw.source || 'phimapi').trim().slice(0, 24);
        const sourceId = String(raw.sourceId || '').trim().slice(0, 64);
        const slug = String(raw.slug).trim().slice(0, 140);
        const itemKey = String(raw.itemKey || `${source}::${sourceId}::${slug}`).slice(0, 220);
        const name = String(raw.name || slug).trim().slice(0, 180);
        const originName = String(raw.origin_name || '').trim().slice(0, 180);
        const poster = String(raw.poster || '').trim().slice(0, 500);
        const year = String(raw.year || '').trim().slice(0, 10);
        const quality = String(raw.quality || 'HD').trim().slice(0, 24);
        const lastKnownEpisode = String(raw.lastKnownEpisode || raw.episode_current || raw.quality || '').trim().slice(0, 48);
        const addedAt = Math.min(now + 60_000, Math.max(1, Math.round(Number(raw.addedAt || now))));
        await sql`
          INSERT INTO nki_my_list (user_id, item_key, source, source_id, slug, name, origin_name, poster, year, quality, last_known_episode, added_at)
          VALUES (${user.id}, ${itemKey}, ${source}, ${sourceId}, ${slug}, ${name}, ${originName}, ${poster}, ${year}, ${quality}, ${lastKnownEpisode}, ${addedAt})
          ON CONFLICT (user_id, item_key) DO UPDATE SET
            name = EXCLUDED.name,
            poster = CASE WHEN EXCLUDED.poster <> '' THEN EXCLUDED.poster ELSE nki_my_list.poster END,
            last_known_episode = CASE WHEN EXCLUDED.last_known_episode <> '' THEN EXCLUDED.last_known_episode ELSE nki_my_list.last_known_episode END,
            added_at = GREATEST(nki_my_list.added_at, EXCLUDED.added_at)
        `;
      }
    }

    if (Array.isArray(body.myListRemoved) && body.myListRemoved.length > 0) {
      const removeKeys = body.myListRemoved.map((k) => String(k || '').trim()).filter(Boolean).slice(0, 100);
      if (removeKeys.length) {
        await sql`DELETE FROM nki_my_list WHERE user_id = ${user.id} AND item_key = ANY(${removeKeys})`;
      }
    }

    if (Array.isArray(body.watchProgressRemoved) && body.watchProgressRemoved.length > 0) {
      const removedSlugs = body.watchProgressRemoved
        .map((s) => normalizeMovieSlug(s))
        .filter((s) => s && s !== 'unknown')
        .slice(0, 100);
      if (removedSlugs.length) {
        await sql`DELETE FROM nki_watch_progress WHERE user_id = ${user.id} AND movie_slug = ANY(${removedSlugs})`;
        metric('sync.state.removed_history', removedSlugs.length, { outcome: 'success' });
      }
    }

    await sql`UPDATE nki_users SET last_seen_at = ${now} WHERE id = ${user.id}`;
    metric('sync.state', 1, { direction: 'push', outcome: 'success' });
    return jsonResponse({
      ok: true,
      upsertedCount,
      serverTime: now,
    });
  } catch (err) {
    console.error('[api/sync/state POST] error:', err);
    metric('sync.state', 1, { direction: 'push', outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Sync push failed' }, 500);
  } finally {
    metric('sync.state.duration_ms', performance.now() - startedAt, { direction: 'push' });
  }
}
