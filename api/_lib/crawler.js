/**
 * ChillFilm Autonomous Multi-Source Seeder, Health Checker & Periodic Auto-Crawler
 *
 * - Seeds live-verified, direct-playable (.m3u8 / .mp4), 100% VN-Safe global movie sources.
 * - Periodically probes source health/latency via Vercel Schedules (every 3h), auto-pausing
 *   offline upstreams after 3 consecutive failures and auto-recovering when back online.
 * - Crawls newly updated/hot films from all enabled multi-country sources, strictly enforces
 *   the Hard VN-Safe anti-porn & VN-banned guard (while allowing legal mainstream T18 cinema),
 *   and persists clean verified records in Neon Postgres (`nki_catalog_items` + `nki_crawl_runs`).
 */

import crypto from 'node:crypto';
import { metric } from '@vercel/functions';
import { ensureSchema, getSql } from './db.js';
import { filterSafeMovies, isBlockedContent } from './safety.js';
import {
  fetchArchiveCinema,
  fetchFromDynamicSource,
  fetchM3uCinemaList,
  fetchMacCmsSourceWithStats,
  fetchPhimApiList,
  getCustomBlockedTerms,
  listDynamicSources,
  PHIM_API,
} from './sources.js';

export const VERIFIED_BUILTIN_SOURCES = [
  {
    id: 'builtin_ffzy',
    name: 'Feifan Global Cinema',
    sourceType: 'MACCMS_JSON',
    baseUrl: 'https://api.ffzyapi.com/api.php/provide/vod',
    countryHint: 'Global (JP/KR/US/EU/TH/CN)',
    priority: 10,
  },
  {
    id: 'builtin_lzi',
    name: 'Liangzi Global VOD',
    sourceType: 'MACCMS_JSON',
    baseUrl: 'https://cj.lziapi.com/api.php/provide/vod',
    countryHint: 'Global (KR/TH/JP/US/CN)',
    priority: 15,
  },
  {
    id: 'builtin_yhzy',
    name: 'Yinghua World Cinema',
    sourceType: 'MACCMS_JSON',
    baseUrl: 'https://m3u8.apiyhzy.com/api.php/provide/vod',
    countryHint: 'Europe / UK / US / Asia',
    priority: 20,
  },
  {
    id: 'builtin_bfzy',
    name: 'Baofeng International',
    sourceType: 'MACCMS_JSON',
    baseUrl: 'https://bfzyapi.com/api.php/provide/vod',
    countryHint: 'Asia & Global',
    priority: 25,
  },
  {
    id: 'builtin_mdzy',
    name: 'Modu Anime & Drama',
    sourceType: 'MACCMS_JSON',
    baseUrl: 'https://www.mdzyapi.com/api.php/provide/vod',
    countryHint: 'Anime & Global Drama',
    priority: 30,
  },
  {
    id: 'builtin_iptv_classic',
    name: 'Live Classic Cinema',
    sourceType: 'M3U_PLAYLIST',
    baseUrl: 'https://iptv-org.github.io/iptv/categories/classic.m3u',
    countryHint: 'Global Live TV',
    priority: 40,
  },
  {
    id: 'builtin_iptv_anime',
    name: 'Live Animation TV',
    sourceType: 'M3U_PLAYLIST',
    baseUrl: 'https://iptv-org.github.io/iptv/categories/animation.m3u',
    countryHint: 'Global Live Anime',
    priority: 45,
  },
];

let _builtinSeeded = false;

export async function seedVerifiedBuiltinSources() {
  if (_builtinSeeded) return;
  try {
    await ensureSchema();
    const sql = getSql();
    const now = Date.now();
    for (const src of VERIFIED_BUILTIN_SOURCES) {
      await sql`
        INSERT INTO nki_sources (
          id, name, source_type, base_url, country_hint, enabled, priority,
          created_by, created_at, is_builtin
        )
        VALUES (
          ${src.id}, ${src.name}, ${src.sourceType}, ${src.baseUrl}, ${src.countryHint},
          TRUE, ${src.priority}, 'system-verified', ${now}, TRUE
        )
        ON CONFLICT (id) DO NOTHING
      `;
    }
    _builtinSeeded = true;
  } catch (err) {
    console.warn('[crawler] seedVerifiedBuiltinSources skipped:', err?.message || err);
  }
}

function makeItemKey(item) {
  const src = String(item.source || 'phimapi').trim().toLowerCase();
  const sid = String(item.sourceId || '').trim();
  const slug = String(item.slug || '').trim();
  return `${src}::${sid}::${slug}`.slice(0, 220);
}

function mapDbRowToMovie(r) {
  let genres = [];
  let countries = [];
  try {
    genres = Array.isArray(r.genres_json) ? r.genres_json : JSON.parse(r.genres_json || '[]');
  } catch {
    genres = [];
  }
  try {
    countries = Array.isArray(r.countries_json) ? r.countries_json : JSON.parse(r.countries_json || '[]');
  } catch {
    countries = [];
  }
  return {
    slug: String(r.slug || ''),
    source: String(r.source || 'phimapi'),
    sourceId: String(r.source_id || ''),
    sourceName: String(r.source_name || ''),
    name: String(r.name || ''),
    origin_name: String(r.origin_name || ''),
    type: String(r.media_type || 'single'),
    year: String(r.year || ''),
    quality: String(r.quality || 'HD'),
    episode_current: String(r.episode_current || ''),
    poster: String(r.poster || ''),
    backdrop: String(r.backdrop || r.poster || ''),
    genres,
    countries,
    imdbId: String(r.imdb_id || ''),
    tmdbScore: Number(r.tmdb_score || 0) || 0,
    content: String(r.content || ''),
    streamUrl: String(r.stream_url || ''),
    categoryTag: String(r.category_tag || 'latest'),
    firstSeenAt: Number(r.first_seen_at || 0),
    updatedAt: Number(r.updated_at || 0),
  };
}

async function probeAndCrawlSingleSource(source, blockedTerms) {
  const t0 = Date.now();
  try {
    if (source.sourceType === 'MACCMS_JSON') {
      // Crawl pages 1 & 2 (up to 40 newest direct-.m3u8 titles per MacCMS source)
      const [p1, p2] = await Promise.all([
        fetchMacCmsSourceWithStats(source, { page: 1, blockedTerms, timeoutMs: 8500 }),
        fetchMacCmsSourceWithStats(source, { page: 2, blockedTerms, timeoutMs: 8500 }),
      ]);
      const latencyMs = Date.now() - t0;
      const ok = p1.ok && (p1.items.length > 0 || p2.items.length > 0);
      const seen = new Set();
      const items = [];
      for (const m of [...p1.items, ...p2.items]) {
        if (!seen.has(m.slug)) {
          seen.add(m.slug);
          items.push({ ...m, categoryTag: 'global_maccms' });
        }
      }
      return {
        source,
        ok,
        latencyMs,
        items,
        blockedCount: (p1.blockedCount || 0) + (p2.blockedCount || 0),
        totalRemote: p1.totalRemote || 0,
        error: ok ? '' : (p1.error || 'No playable .m3u8 items'),
      };
    }

    if (source.sourceType === 'M3U_PLAYLIST') {
      const items = await fetchM3uCinemaList(source.baseUrl, {
        sourceId: source.id,
        sourceName: source.name,
        limit: 36,
        blockedTerms,
      });
      const latencyMs = Date.now() - t0;
      const ok = items.length > 0;
      return {
        source,
        ok,
        latencyMs,
        items: items.map((m) => ({ ...m, categoryTag: 'live' })),
        blockedCount: 0,
        totalRemote: items.length,
        error: ok ? '' : 'Empty or unreachable M3U playlist',
      };
    }

    const items = await fetchFromDynamicSource(source, { page: 1, blockedTerms });
    const latencyMs = Date.now() - t0;
    const ok = items.length > 0;
    return {
      source,
      ok,
      latencyMs,
      items: items.map((m) => ({ ...m, categoryTag: 'custom' })),
      blockedCount: 0,
      totalRemote: items.length,
      error: ok ? '' : 'Empty or unreachable endpoint',
    };
  } catch (err) {
    return {
      source,
      ok: false,
      latencyMs: Date.now() - t0,
      items: [],
      blockedCount: 0,
      totalRemote: 0,
      error: String(err?.message || 'Probe failed').slice(0, 120),
    };
  }
}

async function upsertCatalogItems(sql, items, now) {
  if (!items.length) return { discovered: 0, newCount: 0 };

  // Deduplicate by item_key
  const uniqueMap = new Map();
  for (const m of items) {
    if (!m || !m.slug || !m.name) continue;
    const key = makeItemKey(m);
    if (!uniqueMap.has(key)) {
      uniqueMap.set(key, { key, m });
    }
  }
  const deduped = [...uniqueMap.values()];
  if (!deduped.length) return { discovered: 0, newCount: 0 };

  const allKeys = deduped.map((x) => x.key);
  const existingRows = await sql`
    SELECT item_key FROM nki_catalog_items WHERE item_key = ANY(${allKeys})
  `;
  const existingSet = new Set(existingRows.map((r) => r.item_key));
  const newCount = deduped.filter((x) => !existingSet.has(x.key)).length;

  // Execute clean parallel batches of 20 upserts to stay well within Serverless timeout
  const BATCH = 20;
  for (let i = 0; i < deduped.length; i += BATCH) {
    const slice = deduped.slice(i, i + BATCH);
    await Promise.all(
      slice.map(({ key, m }, idx) => {
        // Stagger updated_at slightly so newest items keep natural upstream ranking
        const ts = now - (i + idx);
        const genresJson = JSON.stringify(Array.isArray(m.genres) ? m.genres.slice(0, 4) : []);
        const countriesJson = JSON.stringify(Array.isArray(m.countries) ? m.countries.slice(0, 3) : []);
        return sql`
          INSERT INTO nki_catalog_items (
            item_key, source, source_id, source_name, slug, name, origin_name,
            media_type, year, quality, episode_current, poster, backdrop,
            genres_json, countries_json, imdb_id, tmdb_score, content, stream_url,
            category_tag, first_seen_at, updated_at, last_verified_at
          )
          VALUES (
            ${key},
            ${String(m.source || 'phimapi')},
            ${String(m.sourceId || '')},
            ${String(m.sourceName || '')},
            ${String(m.slug)},
            ${String(m.name).slice(0, 180)},
            ${String(m.origin_name || '').slice(0, 180)},
            ${String(m.type || 'single').slice(0, 24)},
            ${String(m.year || '').slice(0, 10)},
            ${String(m.quality || 'HD').slice(0, 28)},
            ${String(m.episode_current || '').slice(0, 32)},
            ${String(m.poster || '').slice(0, 500)},
            ${String(m.backdrop || m.poster || '').slice(0, 500)},
            ${genresJson}::jsonb,
            ${countriesJson}::jsonb,
            ${String(m.imdbId || '').slice(0, 24)},
            ${Number(m.tmdbScore || 0) || 0},
            ${String(m.content || '').slice(0, 320)},
            ${String(m.streamUrl || '').slice(0, 500)},
            ${String(m.categoryTag || 'latest').slice(0, 32)},
            ${ts},
            ${ts},
            ${now}
          )
          ON CONFLICT (item_key) DO UPDATE SET
            name = EXCLUDED.name,
            origin_name = CASE WHEN EXCLUDED.origin_name <> '' THEN EXCLUDED.origin_name ELSE nki_catalog_items.origin_name END,
            quality = EXCLUDED.quality,
            episode_current = EXCLUDED.episode_current,
            poster = CASE WHEN EXCLUDED.poster <> '' THEN EXCLUDED.poster ELSE nki_catalog_items.poster END,
            backdrop = CASE WHEN EXCLUDED.backdrop <> '' THEN EXCLUDED.backdrop ELSE nki_catalog_items.backdrop END,
            genres_json = EXCLUDED.genres_json,
            countries_json = EXCLUDED.countries_json,
            tmdb_score = GREATEST(nki_catalog_items.tmdb_score, EXCLUDED.tmdb_score),
            content = CASE WHEN EXCLUDED.content <> '' THEN EXCLUDED.content ELSE nki_catalog_items.content END,
            stream_url = CASE WHEN EXCLUDED.stream_url <> '' THEN EXCLUDED.stream_url ELSE nki_catalog_items.stream_url END,
            updated_at = EXCLUDED.updated_at,
            last_verified_at = EXCLUDED.last_verified_at
        `;
      })
    );
  }

  // Keep Neon catalog lean and fast: prune beyond newest 3,000 verified items
  await sql`
    DELETE FROM nki_catalog_items
    WHERE item_key IN (
      SELECT item_key FROM nki_catalog_items
      ORDER BY updated_at DESC
      OFFSET 3000
    )
  `;

  return { discovered: deduped.length, newCount };
}

/**
 * Main entry point invoked by:
 * 1) Vercel Schedule (`api/schedules/crawl.js` every 3 hours)
 * 2) Admin Vault manual trigger (`POST /api/admin/config { action: 'runCrawlNow' }`)
 * 3) Auto-bootstrap on cold empty database (`waitUntil`)
 */
export async function runCatalogCrawlAndHealthCheck({
  triggerType = 'SCHEDULE',
  executionId = '',
  scheduledAt = null,
} = {}) {
  const startedMs = Date.now();
  const perfStart = performance.now();
  const runId = String(executionId || `${triggerType.toLowerCase()}_${startedMs}_${crypto.randomUUID().slice(0, 6)}`);

  try {
    await ensureSchema();
    await seedVerifiedBuiltinSources();
    const sql = getSql();

    // Idempotency guard (Vercel Schedules can deliver the same executionId more than once)
    const dup = await sql`SELECT execution_id FROM nki_crawl_runs WHERE execution_id = ${runId} LIMIT 1`;
    if (dup.length) {
      return { ok: true, skipped: true, reason: 'duplicate_execution', executionId: runId };
    }

    // Stale outage backfill guard for scheduled runs (> 2 hours late)
    if (triggerType === 'SCHEDULE' && scheduledAt instanceof Date) {
      const lagMs = Date.now() - scheduledAt.getTime();
      if (lagMs > 2 * 60 * 60 * 1000) {
        return { ok: true, skipped: true, reason: 'stale_scheduled_run', executionId: runId };
      }
    }

    const [blockedTerms, enabledSources] = await Promise.all([
      getCustomBlockedTerms(),
      listDynamicSources({ onlyEnabled: true, skipOffline: false }),
    ]);

    // 1. Crawl Core Built-in Feeds + ALL Enabled Dynamic Sources in parallel!
    const [
      kkP1,
      kkP2,
      kkP3,
      kkTvShows,
      archiveP1,
      archiveP2,
      sourceResults,
    ] = await Promise.all([
      fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=1`, blockedTerms, {
        source: 'phimapi',
        sourceId: 'core_kkphim',
        sourceName: 'KKPhim v3',
      }),
      fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=2`, blockedTerms, {
        source: 'phimapi',
        sourceId: 'core_kkphim',
        sourceName: 'KKPhim v3',
      }),
      fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=3`, blockedTerms, {
        source: 'phimapi',
        sourceId: 'core_kkphim',
        sourceName: 'KKPhim v3',
      }),
      fetchPhimApiList(`${PHIM_API}/v1/api/danh-sach/tv-shows?page=1&sort_field=modified.time&sort_type=desc&limit=18`, blockedTerms, {
        source: 'phimapi',
        sourceId: 'core_kkphim',
        sourceName: 'KKPhim v3',
      }),
      fetchArchiveCinema({ page: 1, rows: 24, blockedTerms }),
      fetchArchiveCinema({ page: 2, rows: 24, blockedTerms }),
      Promise.all(enabledSources.map((src) => probeAndCrawlSingleSource(src, blockedTerms))),
    ]);

    const now = Date.now();
    let totalBlockedSfw = 0;
    let healthyCount = 0;
    const sourceSummaries = [];

    // 2. Update health telemetry in nki_sources for every checked source
    await Promise.all(
      sourceResults.map(async (res) => {
        totalBlockedSfw += res.blockedCount || 0;
        const nextFailures = res.ok ? 0 : (res.source.consecutiveFailures || 0) + 1;
        const nextStatus = res.ok
          ? (res.latencyMs > 6000 ? 'DEGRADED' : 'ONLINE')
          : (nextFailures >= 3 ? 'OFFLINE' : 'DEGRADED');
        if (res.ok) healthyCount += 1;

        sourceSummaries.push({
          id: res.source.id,
          name: res.source.name,
          status: nextStatus,
          latencyMs: res.latencyMs,
          items: res.items.length,
          totalRemote: res.totalRemote,
          blockedSfw: res.blockedCount || 0,
          error: res.error,
        });

        await sql`
          UPDATE nki_sources
          SET last_checked_at = ${now},
              last_status = ${nextStatus},
              last_latency_ms = ${res.latencyMs},
              last_error = ${String(res.error || '').slice(0, 160)},
              consecutive_failures = ${nextFailures},
              items_crawled = CASE WHEN ${res.ok} THEN ${res.items.length} ELSE items_crawled END,
              total_remote = CASE WHEN ${res.totalRemote > 0} THEN ${res.totalRemote} ELSE total_remote END
          WHERE id = ${res.source.id}
        `;
      })
    );

    // 3. Collect all discovered items and run a final Hard VN-Safe anti-porn & VN-banned sweep
    const rawCombined = [
      ...kkP1.map((m) => ({ ...m, categoryTag: 'latest' })),
      ...kkP2.map((m) => ({ ...m, categoryTag: 'latest' })),
      ...kkP3.map((m) => ({ ...m, categoryTag: 'latest' })),
      ...kkTvShows.map((m) => ({ ...m, categoryTag: 'tv_shows' })),
      ...archiveP1.map((m) => ({ ...m, categoryTag: 'archive' })),
      ...archiveP2.map((m) => ({ ...m, categoryTag: 'archive' })),
      ...sourceResults.flatMap((r) => r.items),
    ];

    const finalSafeItems = [];
    for (const item of rawCombined) {
      if (isBlockedContent(item, blockedTerms)) {
        totalBlockedSfw += 1;
      } else {
        finalSafeItems.push(item);
      }
    }

    // 4. Persist all verified clean movies into Neon Postgres
    const { discovered, newCount } = await upsertCatalogItems(sql, finalSafeItems, now);

    const countRows = await sql`SELECT COUNT(*)::int AS total FROM nki_catalog_items`;
    const totalIndexed = Number(countRows?.[0]?.total || 0);
    const finishedMs = Date.now();

    const summary = {
      coreKkphim: kkP1.length + kkP2.length + kkP3.length + kkTvShows.length,
      coreArchive: archiveP1.length + archiveP2.length,
      sources: sourceSummaries,
    };

    await sql`
      INSERT INTO nki_crawl_runs (
        execution_id, trigger_type, started_at, finished_at,
        sources_checked, sources_healthy, items_discovered, items_new,
        items_blocked_sfw, total_indexed, summary_json
      )
      VALUES (
        ${runId}, ${triggerType}, ${startedMs}, ${finishedMs},
        ${enabledSources.length}, ${healthyCount}, ${discovered}, ${newCount},
        ${totalBlockedSfw}, ${totalIndexed}, ${JSON.stringify(summary)}::jsonb
      )
      ON CONFLICT (execution_id) DO NOTHING
    `;

    // Keep last 40 crawl run history rows
    await sql`
      DELETE FROM nki_crawl_runs
      WHERE execution_id IN (
        SELECT execution_id FROM nki_crawl_runs
        ORDER BY finished_at DESC
        OFFSET 40
      )
    `;

    metric('crawler.run', 1, { trigger: triggerType, outcome: 'success' });
    metric('crawler.items_new', newCount, { trigger: triggerType });
    metric('crawler.sources_healthy', healthyCount, { trigger: triggerType });
    if (totalBlockedSfw > 0) {
      metric('crawler.blocked_sfw', totalBlockedSfw, { trigger: triggerType });
    }

    return {
      ok: true,
      executionId: runId,
      triggerType,
      startedAt: startedMs,
      finishedAt: finishedMs,
      durationMs: finishedMs - startedMs,
      sourcesChecked: enabledSources.length,
      sourcesHealthy: healthyCount,
      itemsDiscovered: discovered,
      itemsNew: newCount,
      itemsBlockedSfw: totalBlockedSfw,
      totalIndexed,
      sources: sourceSummaries,
    };
  } catch (err) {
    console.error('[crawler] runCatalogCrawlAndHealthCheck failed:', err);
    metric('crawler.run', 1, { trigger: triggerType, outcome: 'error' });
    throw err;
  } finally {
    metric('crawler.duration_ms', performance.now() - perfStart, { trigger: triggerType });
  }
}

/**
 * Reads fast pre-crawled rows & health metadata directly from Neon Postgres (<30ms)
 * so the Home page loads instantaneously with fresh multi-source movies!
 */
export async function getCrawledFeedData(blockedTerms = []) {
  try {
    await ensureSchema();
    const sql = getSql();
    const [countRows, lastRunRows, recentItems] = await Promise.all([
      sql`SELECT COUNT(*)::int AS total FROM nki_catalog_items`,
      sql`
        SELECT execution_id, trigger_type, started_at, finished_at,
               sources_checked, sources_healthy, items_discovered, items_new,
               items_blocked_sfw, total_indexed
        FROM nki_crawl_runs
        ORDER BY finished_at DESC
        LIMIT 1
      `,
      sql`
        SELECT source, source_id, source_name, slug, name, origin_name, media_type,
               year, quality, episode_current, poster, backdrop, genres_json,
               countries_json, imdb_id, tmdb_score, content, stream_url,
               category_tag, first_seen_at, updated_at
        FROM nki_catalog_items
        ORDER BY updated_at DESC
        LIMIT 260
      `,
    ]);

    const totalIndexed = Number(countRows?.[0]?.total || 0);
    const mappedAll = filterSafeMovies(recentItems.map(mapDbRowToMovie), blockedTerms);

    // Group items by source_id for fast multi-source Home rows
    const bySourceId = new Map();
    for (const m of mappedAll) {
      if (!m.sourceId) continue;
      if (!bySourceId.has(m.sourceId)) bySourceId.set(m.sourceId, []);
      const bucket = bySourceId.get(m.sourceId);
      if (bucket.length < 20) bucket.push(m);
    }

    // Mix fresh multi-source items (round-robin across global sources so the row is diverse!)
    const freshMixed = [];
    const seenFresh = new Set();
    const pools = [...bySourceId.values()];
    for (let idx = 0; idx < 24 && freshMixed.length < 24; idx++) {
      for (const pool of pools) {
        const item = pool[idx];
        if (!item) continue;
        const k = `${item.source}::${item.slug}`;
        if (seenFresh.has(k)) continue;
        seenFresh.add(k);
        freshMixed.push(item);
        if (freshMixed.length >= 24) break;
      }
    }

    const lastRun = lastRunRows?.[0]
      ? {
          executionId: lastRunRows[0].execution_id,
          triggerType: lastRunRows[0].trigger_type,
          startedAt: Number(lastRunRows[0].started_at),
          finishedAt: Number(lastRunRows[0].finished_at),
          sourcesChecked: Number(lastRunRows[0].sources_checked),
          sourcesHealthy: Number(lastRunRows[0].sources_healthy),
          itemsDiscovered: Number(lastRunRows[0].items_discovered),
          itemsNew: Number(lastRunRows[0].items_new),
          itemsBlockedSfw: Number(lastRunRows[0].items_blocked_sfw),
          totalIndexed: Number(lastRunRows[0].total_indexed || totalIndexed),
        }
      : null;

    return {
      totalIndexed,
      lastRun,
      freshMixed,
      bySourceId,
    };
  } catch (err) {
    console.warn('[crawler] getCrawledFeedData fallback:', err?.message || err);
    return { totalIndexed: 0, lastRun: null, freshMixed: [], bySourceId: new Map() };
  }
}

export async function searchCrawledCatalog({ query = '', sourceId = '', category = '', page = 1, limit = 24, blockedTerms = [] } = {}) {
  try {
    await ensureSchema();
    const sql = getSql();
    const offset = (Math.max(1, page) - 1) * limit;
    let rows = [];

    if (query && query.trim()) {
      const pattern = `%${query.trim()}%`;
      rows = await sql`
        SELECT source, source_id, source_name, slug, name, origin_name, media_type,
               year, quality, episode_current, poster, backdrop, genres_json,
               countries_json, imdb_id, tmdb_score, content, stream_url,
               category_tag, first_seen_at, updated_at
        FROM nki_catalog_items
        WHERE name ILIKE ${pattern}
           OR origin_name ILIKE ${pattern}
           OR source_name ILIKE ${pattern}
        ORDER BY updated_at DESC
        LIMIT ${limit} OFFSET ${offset}
      `;
    } else if (sourceId) {
      rows = await sql`
        SELECT source, source_id, source_name, slug, name, origin_name, media_type,
               year, quality, episode_current, poster, backdrop, genres_json,
               countries_json, imdb_id, tmdb_score, content, stream_url,
               category_tag, first_seen_at, updated_at
        FROM nki_catalog_items
        WHERE source_id = ${sourceId}
        ORDER BY updated_at DESC
        LIMIT ${limit} OFFSET ${offset}
      `;
    } else if (category === 'crawled') {
      rows = await sql`
        SELECT source, source_id, source_name, slug, name, origin_name, media_type,
               year, quality, episode_current, poster, backdrop, genres_json,
               countries_json, imdb_id, tmdb_score, content, stream_url,
               category_tag, first_seen_at, updated_at
        FROM nki_catalog_items
        ORDER BY first_seen_at DESC, updated_at DESC
        LIMIT ${limit} OFFSET ${offset}
      `;
    } else {
      return [];
    }

    return filterSafeMovies(rows.map(mapDbRowToMovie), blockedTerms);
  } catch {
    return [];
  }
}

export async function listRecentCrawlRuns(limit = 8) {
  try {
    await ensureSchema();
    const sql = getSql();
    const rows = await sql`
      SELECT execution_id, trigger_type, started_at, finished_at,
             sources_checked, sources_healthy, items_discovered, items_new,
             items_blocked_sfw, total_indexed, summary_json
      FROM nki_crawl_runs
      ORDER BY finished_at DESC
      LIMIT ${limit}
    `;
    return rows.map((r) => ({
      executionId: r.execution_id,
      triggerType: r.trigger_type,
      startedAt: Number(r.started_at),
      finishedAt: Number(r.finished_at),
      durationMs: Math.max(0, Number(r.finished_at) - Number(r.started_at)),
      sourcesChecked: Number(r.sources_checked),
      sourcesHealthy: Number(r.sources_healthy),
      itemsDiscovered: Number(r.items_discovered),
      itemsNew: Number(r.items_new),
      itemsBlockedSfw: Number(r.items_blocked_sfw),
      totalIndexed: Number(r.total_indexed),
    }));
  } catch {
    return [];
  }
}
