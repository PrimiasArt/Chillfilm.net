import { metric, waitUntil } from '@vercel/functions';
import { ensureSchema, getSql, normalizeMovieSlug } from './db.js';
import { CORS_HEADERS, jsonResponse } from './auth.js';
import { filterSafeMovies, isBlockedCategoryTag, isBlockedContent } from './safety.js';
import {
  findSimilarMovies,
  getCrawledFeedData,
  runCatalogCrawlAndHealthCheck,
  searchCrawledCatalog,
  seedVerifiedBuiltinSources,
} from './crawler.js';
import {
  fetchArchiveCinema,
  fetchFromDynamicSource,
  fetchJsonSafe,
  fetchM3uCinemaList,
  fetchPhimApiList,
  getCustomBlockedTerms,
  GLOBAL_COUNTRIES,
  listDynamicSources,
  normalizeImage,
  PHIM_API,
  resolveArchiveDetail,
  resolveMacCmsDetail,
  stripHtml,
} from './sources.js';

const NGUONC_API = 'https://phim.nguonc.com/api/films/phim-moi-cap-nhat?page=1';
const IPTV_MOVIES_URL = 'https://iptv-org.github.io/iptv/categories/movies.m3u';
let _bootCrawlTriggered = false;

const GENRE_MAP_PHIMAPI = {
  action: { slug: 'hanh-dong', sqlTerm: 'Action' },
  romance: { slug: 'tinh-cam', sqlTerm: 'Romance' },
  'sci-fi': { slug: 'vien-tuong', sqlTerm: 'Sci-Fi' },
  horror: { slug: 'kinh-di', sqlTerm: 'Horror' },
  comedy: { slug: 'hai-huoc', sqlTerm: 'Comedy' },
  drama: { slug: 'chinh-kich', sqlTerm: 'Drama' },
  thriller: { slug: 'tam-ly', sqlTerm: 'Thriller' },
  crime: { slug: 'hinh-su', sqlTerm: 'Crime' },
  historical: { slug: 'co-trang', sqlTerm: 'Historical' },
  documentary: { slug: 'tai-lieu', sqlTerm: 'Documentary' },
  anime: { slug: 'hoat-hinh', sqlTerm: 'Anime' },
};

async function verifyStreamAlive(streamUrl, timeoutMs = 2400) {
  if (!streamUrl || !streamUrl.startsWith('http')) return false;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(streamUrl, {
      method: 'GET',
      signal: ctrl.signal,
      headers: { 'User-Agent': 'ChillFilm-NKI/3.5' },
    });
    const ok = res.ok;
    try { res.body?.cancel?.(); } catch { /* ignore */ }
    return ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

async function enrichHeroMovie(movie, blockedTerms) {
  if (movie.source && movie.source !== 'phimapi') return movie;
  const detail = await fetchJsonSafe(`${PHIM_API}/phim/${encodeURIComponent(movie.slug)}`, 6000);
  const m = detail?.movie;
  if (!m) return movie;
  if (isBlockedContent(m, blockedTerms)) return null;
  const firstStream = (detail?.episodes || [])
    .flatMap((s) => s?.server_data || [])
    .find((e) => e?.link_m3u8)?.link_m3u8 || '';
  if (!firstStream) return null;
  const streamAlive = await verifyStreamAlive(firstStream, 2200);
  const desc = stripHtml(m.content || '').slice(0, 260);
  const genres = Array.isArray(m.category)
    ? m.category.map((c) => c?.name).filter(Boolean).slice(0, 3)
    : movie.genres;
  const countries = Array.isArray(m.country)
    ? m.country.map((c) => c?.name).filter(Boolean).slice(0, 2)
    : movie.countries;
  const imdbId = String(m.imdb?.id || '').startsWith('tt') ? String(m.imdb.id) : movie.imdbId;
  const voteAvg = Number(m.tmdb?.vote_average || movie.tmdbScore || 0);
  return {
    ...movie,
    origin_name: String(m.origin_name || movie.origin_name || '').trim(),
    backdrop: normalizeImage(m.thumb_url || m.poster_url || movie.backdrop),
    poster: normalizeImage(m.poster_url || m.thumb_url || movie.poster),
    quality: String(m.quality || movie.quality || 'FHD').trim(),
    time: String(m.time || movie.time || '').trim(),
    episode_current: String(m.episode_current || movie.episode_current || '').trim(),
    genres: genres.length ? genres : movie.genres,
    countries: countries.length ? countries : movie.countries,
    imdbId,
    tmdbScore: Number.isFinite(voteAvg) && voteAvg > 0 ? Math.round(voteAvg * 10) / 10 : movie.tmdbScore,
    content: desc,
    streamVerified: streamAlive,
  };
}

export async function handleCatalogFeed(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    const forceRefresh = url.searchParams.get('refresh') === '1';

    await seedVerifiedBuiltinSources();
    const [blockedTerms, allEnabledSources, crawled] = await Promise.all([
      getCustomBlockedTerms(),
      listDynamicSources({ onlyEnabled: true, skipOffline: false }),
      getCrawledFeedData([]),
    ]);
    const healthyDynamicSources = allEnabledSources.filter(
      (s) => s.consecutiveFailures < 3 && s.lastStatus !== 'OFFLINE'
    );

    // On first cold boot (or stale > 4 hours since last auto-crawl), trigger background crawl via waitUntil
    const lastCrawlAgeMs = crawled.lastRun ? Date.now() - crawled.lastRun.finishedAt : Infinity;
    if ((crawled.totalIndexed === 0 || lastCrawlAgeMs > 4 * 60 * 60 * 1000) && !_bootCrawlTriggered) {
      _bootCrawlTriggered = true;
      try {
        waitUntil(
          runCatalogCrawlAndHealthCheck({ triggerType: 'AUTO_BOOT' }).catch((e) => {
            console.warn('[catalog/feed] background auto-crawl error:', e?.message || e);
            _bootCrawlTriggered = false;
          })
        );
      } catch {
        _bootCrawlTriggered = false;
      }
    }

    // Re-filter crawled slices with any latest Admin custom blocklist terms
    const safeFreshCrawled = filterSafeMovies(crawled.freshMixed, blockedTerms);

    const [
      latestP1,
      latestP2,
      phimLe,
      phimBo,
      hoatHinh,
      hanQuoc,
      auMy,
      trungQuoc,
      thaiLan,
      nhatBan,
      anDo,
      chauAu,
      archiveGlobal,
      nguonCRaw,
      dynamicRows,
    ] = await Promise.all([
      fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=1`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=2`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/danh-sach/phim-le?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/danh-sach/phim-bo?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/danh-sach/hoat-hinh?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/han-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/au-my?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/trung-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/thai-lan?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/nhat-ban?page=1&sort_field=modified.time&sort_type=desc&limit=24`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/an-do?page=1&sort_field=modified.time&sort_type=desc&limit=18`, blockedTerms),
      fetchPhimApiList(`${PHIM_API}/v1/api/quoc-gia/phap?page=1&sort_field=modified.time&sort_type=desc&limit=18`, blockedTerms),
      fetchArchiveCinema({ rows: 18, page: 1, blockedTerms }),
      fetchJsonSafe(NGUONC_API, 5000),
      Promise.all(
        healthyDynamicSources.slice(0, 7).map(async (src) => {
          // Prefer instant pre-crawled Neon Postgres rows (<30ms); live-fetch fallback only on first boot!
          const cachedFromDb = filterSafeMovies(crawled.bySourceId.get(src.id) || [], blockedTerms);
          const movies = cachedFromDb.length >= 6
            ? cachedFromDb
            : await fetchFromDynamicSource(src, { page: 1, blockedTerms });
          const icon = src.sourceType === 'M3U_PLAYLIST' ? '📡' : '🌐';
          return {
            id: `src_${src.id}`,
            customTitle: `${icon} ${src.name}`,
            isTopTen: false,
            movies: movies.slice(0, 20),
          };
        })
      ),
    ]);

    const nguonCTrendingSlugs = new Set();
    const nguonCTrendingNames = new Set();
    if (Array.isArray(nguonCRaw?.items)) {
      for (const item of filterSafeMovies(nguonCRaw.items, blockedTerms)) {
        if (item?.slug) nguonCTrendingSlugs.add(normalizeMovieSlug(item.slug));
        if (item?.name) nguonCTrendingNames.add(String(item.name).trim().toLowerCase());
      }
    }

    const seenHot = new Set();
    const hotCombined = [];
    for (const m of [...latestP1, ...latestP2, ...phimLe.slice(0, 8), ...phimBo.slice(0, 8)]) {
      if (!m || seenHot.has(m.slug)) continue;
      seenHot.add(m.slug);
      hotCombined.push(m);
    }

    const currentYear = new Date().getUTCFullYear();
    const scoreHot = (m, idx) => {
      let score = Math.max(0, 40 - idx);
      const y = Number(m.year || 0);
      if (y >= currentYear) score += 26;
      else if (y >= currentYear - 1) score += 16;
      if (m.tmdbScore >= 7.0) score += 20;
      else if (m.tmdbScore >= 5.5) score += 10;
      if (m.imdbId) score += 6;
      if (m.backdrop && m.backdrop !== m.poster) score += 12;
      if (
        nguonCTrendingSlugs.has(normalizeMovieSlug(m.slug)) ||
        nguonCTrendingNames.has(m.name.toLowerCase())
      ) {
        score += 24;
      }
      return score;
    };

    const rankedTrending = hotCombined
      .map((m, idx) => ({ m, s: scoreHot(m, idx) }))
      .sort((a, b) => b.s - a.s)
      .map((x) => x.m);

    const enrichedHeroes = await Promise.all(rankedTrending.slice(0, 9).map((m) => enrichHeroMovie(m, blockedTerms)));
    const validHeroes = enrichedHeroes.filter(Boolean);
    const verifiedHeroes = validHeroes.filter((m) => m.streamVerified);
    const heroMovies = (verifiedHeroes.length >= 3 ? verifiedHeroes : validHeroes).slice(0, 6);

    const rows = [
      { id: 'hot', isTopTen: true, movies: rankedTrending.slice(0, 20) },
      ...(safeFreshCrawled.length >= 6
        ? [{ id: 'crawled', customTitle: '🤖 Auto-Crawled Global Releases', isTopTen: false, movies: safeFreshCrawled.slice(0, 20) }]
        : []),
      { id: 'phim-le', isTopTen: false, movies: phimLe.slice(0, 20) },
      { id: 'phim-bo', isTopTen: false, movies: phimBo.slice(0, 20) },
      { id: 'han-quoc', isTopTen: false, movies: hanQuoc.slice(0, 20) },
      { id: 'au-my', isTopTen: false, movies: auMy.slice(0, 20) },
      { id: 'hoat-hinh', isTopTen: false, movies: hoatHinh.slice(0, 20) },
      { id: 'trung-quoc', isTopTen: false, movies: trungQuoc.slice(0, 20) },
      { id: 'thai-lan', isTopTen: false, movies: thaiLan.slice(0, 20) },
      { id: 'nhat-ban', isTopTen: false, movies: nhatBan.slice(0, 20) },
      { id: 'an-do', customTitle: '🇮🇳 India & Bollywood', isTopTen: false, movies: anDo.slice(0, 18) },
      { id: 'phap', customTitle: '🇫🇷 European Cinema (France & EU)', isTopTen: false, movies: chauAu.slice(0, 18) },
      ...dynamicRows.filter((r) => r.movies.length > 0),
      { id: 'archive', customTitle: '🏛 World Public Cinema (Internet Archive)', isTopTen: false, movies: archiveGlobal.slice(0, 18) },
    ].filter((r) => r.movies.length > 0);

    metric('catalog.feed', 1, { outcome: rows.length ? 'success' : 'empty' });
    return new Response(
      JSON.stringify({
        ok: true,
        updatedAt: Date.now(),
        safeFilter: 'VN_SFW_HARD_GUARD',
        sources: [
          'KKPhim v3 (36 Countries)',
          'NguonC Trending Signal',
          'Internet Archive World Cinema',
          ...healthyDynamicSources.map((s) => s.name),
        ],
        crawlerStatus: {
          schedule: 'Every 3h (VN)',
          totalIndexed: crawled.totalIndexed,
          healthySources: healthyDynamicSources.length,
          totalSources: allEnabledSources.length,
          lastRun: crawled.lastRun,
        },
        countries: GLOBAL_COUNTRIES,
        heroMovies,
        rows,
      }),
      {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': forceRefresh
            ? 'no-store, max-age=0'
            : 'public, s-maxage=300, stale-while-revalidate=600',
        },
      }
    );
  } catch (err) {
    console.error('[catalog/feed] error:', err);
    metric('catalog.feed', 1, { outcome: 'error' });
    return jsonResponse({ ok: false, error: err?.message || 'Catalog feed failed' }, 500);
  } finally {
    metric('catalog.feed.duration_ms', performance.now() - startedAt);
  }
}

export async function handleCatalogBrowse(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') || '').trim();
    const country = (url.searchParams.get('country') || '').trim().toLowerCase();
    const category = (url.searchParams.get('category') || '').trim().toLowerCase();
    const genre = (url.searchParams.get('genre') || '').trim().toLowerCase();
    const year = (url.searchParams.get('year') || '').trim().toLowerCase();
    const minRating = Math.max(0, Math.min(10, Number(url.searchParams.get('minRating') || 0)));
    const sourceId = (url.searchParams.get('sourceId') || '').trim();
    const page = Math.max(1, Math.min(50, Number(url.searchParams.get('page') || 1)));

    if (isBlockedCategoryTag(category) || isBlockedCategoryTag(country) || isBlockedCategoryTag(genre)) {
      return jsonResponse({ ok: true, items: [], blocked: true, reason: 'Blocked by VN-Safe anti-porn policy' });
    }

    await seedVerifiedBuiltinSources();
    const [blockedTerms, dynamicSources] = await Promise.all([
      getCustomBlockedTerms(),
      listDynamicSources({ onlyEnabled: true, skipOffline: true }),
    ]);

    let items = [];

    if (q) {
      const [dbHits, phimHits, archiveHits, dynamicHits] = await Promise.all([
        searchCrawledCatalog({ query: q, page, limit: 24, blockedTerms }),
        fetchPhimApiList(`${PHIM_API}/v1/api/tim-kiem?keyword=${encodeURIComponent(q)}&page=${page}&limit=24`, blockedTerms),
        fetchArchiveCinema({ query: q, page, rows: 12, blockedTerms }),
        Promise.all(
          dynamicSources.slice(0, 5).map((src) => fetchFromDynamicSource(src, { query: q, page, blockedTerms }))
        ),
      ]);
      const seen = new Set();
      for (const m of [...phimHits, ...dbHits, ...dynamicHits.flat(), ...archiveHits]) {
        const key = `${m.source || 'phimapi'}::${m.sourceId || ''}::${m.slug}`;
        if (!seen.has(key)) {
          seen.add(key);
          items.push(m);
        }
      }
      metric('catalog.browse', 1, { mode: 'search', outcome: 'success' });
      return new Response(JSON.stringify({ ok: true, mode: 'search', query: q, count: items.length, items }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=120, stale-while-revalidate=300',
        },
      });
    }

    if (sourceId) {
      const dbItems = await searchCrawledCatalog({ sourceId, page, limit: 24, blockedTerms });
      if (dbItems.length) {
        items = dbItems;
      } else {
        const target = dynamicSources.find((s) => s.id === sourceId);
        if (target) {
          items = await fetchFromDynamicSource(target, { page, blockedTerms });
        }
      }
    } else if (category === 'crawled') {
      items = await searchCrawledCatalog({ category: 'crawled', page, limit: 28, blockedTerms });
      if (!items.length) {
        items = await fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=${page}`, blockedTerms);
      }
    } else if (category === 'archive') {
      items = await fetchArchiveCinema({ country, page, rows: 24, blockedTerms });
    } else if (category === 'iptv') {
      items = await fetchM3uCinemaList(IPTV_MOVIES_URL, {
        sourceId: 'iptv_movies',
        sourceName: 'Live Cinema Channels',
        limit: 36,
        blockedTerms,
      });
    } else if (genre || year || minRating > 0) {
      // Advanced Multi-Filter Mode (Genre + Country + Year + Min TMDB Score across KKPhim + Neon Index)
      const gInfo = GENRE_MAP_PHIMAPI[genre];
      const countryInfo = GLOBAL_COUNTRIES.find((c) => c.slug === country);
      const qsParts = [`page=${page}`, 'sort_field=modified.time', 'sort_type=desc', 'limit=36'];
      if (country) qsParts.push(`country=${encodeURIComponent(country)}`);
      if (year && /^\d{4}$/.test(year)) qsParts.push(`year=${encodeURIComponent(year)}`);

      const phimUrl = gInfo
        ? (gInfo.slug === 'hoat-hinh'
          ? `${PHIM_API}/v1/api/danh-sach/hoat-hinh?${qsParts.join('&')}`
          : `${PHIM_API}/v1/api/the-loai/${gInfo.slug}?${qsParts.join('&')}`)
        : country
          ? `${PHIM_API}/v1/api/quoc-gia/${encodeURIComponent(country)}?${qsParts.join('&')}`
          : `${PHIM_API}/v1/api/danh-sach/phim-le?${qsParts.join('&')}`;

      const [phimFiltered, dbFiltered] = await Promise.all([
        fetchPhimApiList(phimUrl, blockedTerms),
        searchCrawledCatalog({
          country: countryInfo ? countryInfo.name.split(' ')[0] : '',
          genre: gInfo ? gInfo.sqlTerm : genre,
          year,
          minRating,
          page,
          limit: 28,
          blockedTerms,
        }),
      ]);

      const merged = [];
      const seen = new Set();
      for (const m of [...phimFiltered, ...dbFiltered]) {
        const yNum = Number(m.year || 0);
        if (year === 'classic' && (!yNum || yNum >= 2015)) continue;
        if (year === '2020s' && (!yNum || yNum < 2020 || yNum > 2023)) continue;
        if (/^\d{4}$/.test(year) && String(m.year || '') !== year) continue;
        if (minRating > 0 && Number(m.tmdbScore || 0) < minRating) continue;
        const key = `${m.source || 'phimapi'}::${m.sourceId || ''}::${m.slug}`;
        if (!seen.has(key)) {
          seen.add(key);
          merged.push(m);
        }
      }
      items = merged;
    } else if (country) {
      const [phimCountry, archCountry] = await Promise.all([
        fetchPhimApiList(
          `${PHIM_API}/v1/api/quoc-gia/${encodeURIComponent(country)}?page=${page}&sort_field=modified.time&sort_type=desc&limit=24`,
          blockedTerms
        ),
        page === 1 ? fetchArchiveCinema({ country, page: 1, rows: 8, blockedTerms }) : Promise.resolve([]),
      ]);
      items = [...phimCountry, ...archCountry];
    } else if (category) {
      const safeCat = ['phim-le', 'phim-bo', 'hoat-hinh', 'tv-shows'].includes(category) ? category : 'phim-le';
      items = await fetchPhimApiList(
        `${PHIM_API}/v1/api/danh-sach/${safeCat}?page=${page}&sort_field=modified.time&sort_type=desc&limit=24`,
        blockedTerms
      );
    } else {
      items = await fetchPhimApiList(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=${page}`, blockedTerms);
    }

    metric('catalog.browse', 1, { mode: country ? 'country' : category || 'latest', outcome: 'success' });
    return new Response(
      JSON.stringify({
        ok: true,
        count: items.length,
        page,
        countries: GLOBAL_COUNTRIES,
        items,
      }),
      {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=600',
        },
      }
    );
  } catch (err) {
    console.error('[catalog/browse] error:', err);
    metric('catalog.browse', 1, { outcome: 'error' });
    return jsonResponse({ ok: false, error: err?.message || 'Browse failed' }, 500);
  } finally {
    metric('catalog.browse.duration_ms', performance.now() - startedAt);
  }
}

export async function handleCatalogDetail(request) {
  try {
    const url = new URL(request.url);
    const source = (url.searchParams.get('source') || 'phimapi').trim().toLowerCase();
    const slug = (url.searchParams.get('slug') || '').trim();
    const sourceId = (url.searchParams.get('sourceId') || '').trim();

    if (!slug) return jsonResponse({ error: 'slug is required' }, 400);

    const blockedTerms = await getCustomBlockedTerms();
    if (isBlockedContent({ slug, name: slug }, blockedTerms)) {
      return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
    }

    if (source === 'archive') {
      const detail = await resolveArchiveDetail(slug, blockedTerms);
      if (!detail) return jsonResponse({ error: 'Archive movie not found' }, 404);
      if (detail.blocked) return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
      return new Response(JSON.stringify({ ok: true, detail }), {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=600, stale-while-revalidate=1200',
        },
      });
    }

    if (source === 'maccms' || source === 'phimapi_custom') {
      await seedVerifiedBuiltinSources();
      const sources = await listDynamicSources({ onlyEnabled: false });
      const target = sources.find((s) => s.id === sourceId);
      if (!target) return jsonResponse({ error: 'Custom source not found' }, 404);

      if (source === 'maccms') {
        const detail = await resolveMacCmsDetail(target, slug, blockedTerms);
        if (!detail) return jsonResponse({ error: 'Movie not found in MacCMS source' }, 404);
        if (detail.blocked) return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
        // Hydrate cached bilingual translation + similar movies from Neon Postgres
        let cachedMeta = null;
        try {
          await ensureSchema();
          const sql = getSql();
          const key = `maccms::${target.id}::${slug}`;
          const rows = await sql`SELECT name_en, name_vi, content_en, content_vi, genres_json, countries_json FROM nki_catalog_items WHERE item_key = ${key} LIMIT 1`;
          cachedMeta = rows?.[0] || null;
        } catch { /* non-fatal */ }
        const similar = await findSimilarMovies({
          slug,
          genres: Array.isArray(cachedMeta?.genres_json) ? cachedMeta.genres_json : [],
          countries: Array.isArray(cachedMeta?.countries_json) ? cachedMeta.countries_json : [],
          limit: 10,
          blockedTerms,
        });
        return jsonResponse({
          ok: true,
          detail: {
            ...detail,
            nameEn: cachedMeta?.name_en || '',
            nameVi: cachedMeta?.name_vi || '',
            contentEn: cachedMeta?.content_en || '',
            contentVi: cachedMeta?.content_vi || '',
            similar,
          },
        });
      }

      const base = target.baseUrl.replace(/\/+$/, '');
      const json = await fetchJsonSafe(`${base}/phim/${encodeURIComponent(slug)}`, 8500);
      const m = json?.movie;
      if (!m) return jsonResponse({ error: 'Movie not found' }, 404);
      if (isBlockedContent(m, blockedTerms)) {
        return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
      }
      const servers = (json.episodes || [])
        .map((s) => ({
          server_name: s.server_name || target.name,
          episodes: (s.server_data || []).filter((e) => e.link_m3u8),
        }))
        .filter((s) => s.episodes.length);
      return jsonResponse({
        ok: true,
        detail: {
          slug: m.slug || slug,
          source: 'phimapi_custom',
          sourceId: target.id,
          name: m.name || slug,
          origin_name: m.origin_name || '',
          year: m.year ? String(m.year) : '',
          quality: m.quality || 'FHD',
          time: m.time || '',
          content: stripHtml(m.content || ''),
          imdbId: String(m.imdb?.id || '').startsWith('tt') ? m.imdb.id : '',
          type: m.type || 'single',
          poster: normalizeImage(m.poster_url || m.thumb_url, base),
          backdrop: normalizeImage(m.thumb_url || m.poster_url, base),
          servers,
        },
      });
    }

    const json = await fetchJsonSafe(`${PHIM_API}/phim/${encodeURIComponent(slug)}`, 8500);
    const m = json?.movie;
    if (!m) return jsonResponse({ error: 'Movie not found' }, 404);
    if (isBlockedContent(m, blockedTerms)) {
      return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
    }
    const servers = (json.episodes || [])
      .map((s) => ({
        server_name: s.server_name || 'Server',
        episodes: (s.server_data || []).filter((e) => e.link_m3u8),
      }))
      .filter((s) => s.episodes.length);

    const genres = Array.isArray(m.category) ? m.category.map((c) => c?.name).filter(Boolean) : [];
    const countries = Array.isArray(m.country) ? m.country.map((c) => c?.name).filter(Boolean) : [];
    const similar = await findSimilarMovies({
      slug: m.slug || slug,
      genres,
      countries,
      limit: 10,
      blockedTerms,
    });

    return new Response(
      JSON.stringify({
        ok: true,
        detail: {
          slug: m.slug || slug,
          source: 'phimapi',
          name: m.name || slug,
          origin_name: m.origin_name || '',
          year: m.year ? String(m.year) : '',
          quality: m.quality || 'FHD',
          time: m.time || '',
          content: stripHtml(m.content || ''),
          imdbId: String(m.imdb?.id || '').startsWith('tt') ? m.imdb.id : '',
          type: m.type || 'single',
          genres,
          countries,
          poster: normalizeImage(m.poster_url || m.thumb_url),
          backdrop: normalizeImage(m.thumb_url || m.poster_url),
          servers,
          similar,
        },
      }),
      {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=300, stale-while-revalidate=600',
        },
      }
    );
  } catch (err) {
    console.error('[catalog/detail] error:', err);
    return jsonResponse({ error: err?.message || 'Detail fetch failed' }, 500);
  }
}

/* ==================== SUBTITLE AUTO-DISCOVERY (CINEMETA IMDB + OPENSUBTITLES V3) & CORS PROXY ==================== */
const OS3_LANG_MAP = {
  vie: { code: 'vi', native: 'Tiếng Việt' },
  eng: { code: 'en', native: 'English' },
  tha: { code: 'th', native: 'ภาษาไทย' },
  chi: { code: 'zh', native: '简体中文' },
  zho: { code: 'zh', native: '简体中文' },
  zht: { code: 'zh-TW', native: '繁體中文' },
  kor: { code: 'ko', native: '한국어' },
  jpn: { code: 'ja', native: '日本語' },
  fre: { code: 'fr', native: 'Français' },
  fra: { code: 'fr', native: 'Français' },
  spa: { code: 'es', native: 'Español' },
  ger: { code: 'de', native: 'Deutsch' },
  deu: { code: 'de', native: 'Deutsch' },
  ind: { code: 'id', native: 'Bahasa Indonesia' },
  rus: { code: 'ru', native: 'Русский' },
  por: { code: 'pt', native: 'Português' },
  ita: { code: 'it', native: 'Italiano' },
  ara: { code: 'ar', native: 'العربية' },
  hin: { code: 'hi', native: 'हिन्दी' },
  may: { code: 'ms', native: 'Bahasa Melayu' },
  msa: { code: 'ms', native: 'Bahasa Melayu' },
};

function cleanTitleForImdbLookup(raw) {
  return String(raw || '')
    .split('/')[0]
    .replace(/\((?:season|phần|phan|tập|tap|part|vol)\s*\d+[^)]*\)/gi, '')
    .replace(/\b(?:season|phần|phan|part)\s*\d+\b/gi, '')
    .replace(/\(\d{4}\)/g, '')
    .replace(/[$@#^*]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

async function resolveImdbViaCinemeta({ title, originTitle, year, isSeries }) {
  const queries = [...new Set([
    cleanTitleForImdbLookup(originTitle),
    cleanTitleForImdbLookup(title),
  ].filter((q) => q && q.length >= 2))];
  if (!queries.length) return '';

  const yearNum = Number(String(year || '').match(/\d{4}/)?.[0] || 0);
  const kinds = isSeries ? ['series', 'movie'] : ['movie', 'series'];

  for (const q of queries) {
    for (const kind of kinds) {
      const endpoint = `https://v3-cinemeta.strem.io/catalog/${kind}/top/search=${encodeURIComponent(q)}.json`;
      const data = await fetchJsonSafe(endpoint, 4200);
      const metas = Array.isArray(data?.metas) ? data.metas : [];
      if (!metas.length) continue;

      const qNorm = q.toLowerCase().replace(/[^a-z0-9\u00c0-\u024f\u4e00-\u9fff]+/g, ' ').trim();
      const qWords = new Set(qNorm.split(' ').filter((w) => w.length >= 2));

      let best = null;
      let bestScore = -1;
      for (const m of metas.slice(0, 8)) {
        const id = String(m?.id || '').trim();
        if (!id.startsWith('tt')) continue;
        const mName = String(m?.name || '').toLowerCase().replace(/[^a-z0-9\u00c0-\u024f\u4e00-\u9fff]+/g, ' ').trim();
        let score = 0;
        if (mName === qNorm) score += 100;
        else if (mName.includes(qNorm) || qNorm.includes(mName)) score += 60;
        else if (qWords.size) {
          let overlap = 0;
          for (const w of qWords) if (mName.includes(w)) overlap++;
          score += Math.round((overlap / qWords.size) * 45);
        }
        const mYear = Number(String(m?.releaseInfo || '').match(/\d{4}/)?.[0] || 0);
        if (yearNum && mYear) {
          const diff = Math.abs(mYear - yearNum);
          if (diff === 0) score += 28;
          else if (diff === 1) score += 16;
          else if (diff > 5) score -= 20;
        }
        if (score > bestScore) {
          bestScore = score;
          best = id;
        }
      }
      if (best && bestScore >= 40) return best;
    }
  }
  return '';
}

async function queryOpenSubtitlesTracks(imdbId, isSeries, season, episode) {
  if (!imdbId || !imdbId.startsWith('tt')) return [];
  const endpoints = isSeries
    ? [
        `https://opensubtitles-v3.strem.io/subtitles/series/${imdbId}:${season}:${episode}.json`,
        ...(season !== 1 || episode !== 1 ? [`https://opensubtitles-v3.strem.io/subtitles/series/${imdbId}:1:1.json`] : []),
        `https://opensubtitles-v3.strem.io/subtitles/movie/${imdbId}.json`,
      ]
    : [
        `https://opensubtitles-v3.strem.io/subtitles/movie/${imdbId}.json`,
        `https://opensubtitles-v3.strem.io/subtitles/series/${imdbId}:${season}:${episode}.json`,
      ];

  for (const ep of endpoints) {
    const data = await fetchJsonSafe(ep, 4800);
    const list = Array.isArray(data?.subtitles) ? data.subtitles : [];
    if (list.length > 0) return list;
  }
  return [];
}

export async function handleCatalogSubtitles(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    let imdbId = String(url.searchParams.get('imdbId') || '').trim();
    const title = String(url.searchParams.get('title') || '').trim();
    const originTitle = String(url.searchParams.get('originTitle') || '').trim();
    const year = String(url.searchParams.get('year') || '').trim();
    const type = String(url.searchParams.get('type') || 'movie').trim().toLowerCase();
    const isSeries = type === 'series' || type === 'tv';
    const season = Math.max(1, Math.min(99, Number(url.searchParams.get('season') || 1) || 1));
    const episode = Math.max(1, Math.min(2000, Number(url.searchParams.get('episode') || 1) || 1));

    let lookupMode = 'direct_imdb';
    if (!imdbId || !/^tt\d{5,}$/.test(imdbId)) {
      lookupMode = 'cinemeta';
      imdbId = await resolveImdbViaCinemeta({ title, originTitle, year, isSeries });
    }

    const rawList = imdbId ? await queryOpenSubtitlesTracks(imdbId, isSeries, season, episode) : [];
    const seenUrls = new Set();
    const tracks = [];
    for (let idx = 0; idx < rawList.length; idx++) {
      const s = rawList[idx];
      if (!s || !s.url || !s.lang || seenUrls.has(s.url)) continue;
      seenUrls.add(s.url);
      const rawLang = String(s.lang).trim().toLowerCase();
      const mapped = OS3_LANG_MAP[rawLang] || { code: rawLang.slice(0, 2) || 'en', native: rawLang.toUpperCase() };
      const isUtf8 = String(s.url).toLowerCase().includes('utf8');
      tracks.push({
        id: String(s.id || `osv3_${idx}`),
        langCode: mapped.code,
        rawLang,
        label: `${mapped.native} (OpenSubtitles #${tracks.length + 1})`,
        url: s.url,
        proxyUrl: `/api/catalog/subproxy?url=${encodeURIComponent(s.url)}`,
        priority: (mapped.code === 'vi' ? 30 : mapped.code === 'en' ? 20 : 5) + (isUtf8 ? 5 : 0),
      });
    }

    tracks.sort((a, b) => b.priority - a.priority);
    const trimmed = tracks.slice(0, 30).map(({ priority, ...rest }) => rest);

    metric('catalog.subtitles', 1, { outcome: trimmed.length ? 'hit' : 'miss', lookup: lookupMode });
    return new Response(
      JSON.stringify({
        ok: true,
        imdbId: imdbId || '',
        resolvedViaCinemeta: lookupMode === 'cinemeta' && Boolean(imdbId),
        tracks: trimmed,
      }),
      {
        status: 200,
        headers: {
          'Content-Type': 'application/json; charset=utf-8',
          ...CORS_HEADERS,
          'Cache-Control': 'public, s-maxage=900, stale-while-revalidate=3600',
        },
      }
    );
  } catch (err) {
    console.error('[catalog/subtitles] error:', err);
    metric('catalog.subtitles', 1, { outcome: 'error', lookup: 'unknown' });
    return jsonResponse({ ok: false, tracks: [], error: err?.message || 'Subtitle lookup failed' }, 500);
  } finally {
    metric('catalog.subtitles.duration_ms', performance.now() - startedAt);
  }
}

function isAllowedSubProxyHost(hostname) {
  const h = String(hostname || '').toLowerCase();
  if (!h) return false;
  return (
    h === 'opensubtitles-v3.strem.io' ||
    h.endsWith('.strem.io') ||
    h === 'dl.opensubtitles.org' ||
    h.endsWith('.opensubtitles.org') ||
    h === 'archive.org' ||
    h.endsWith('.archive.org')
  );
}

export async function handleCatalogSubProxy(request) {
  try {
    const url = new URL(request.url);
    const targetRaw = String(url.searchParams.get('url') || '').trim();
    if (!targetRaw) return jsonResponse({ error: 'url is required' }, 400);

    let parsed;
    try {
      parsed = new URL(targetRaw);
    } catch {
      return jsonResponse({ error: 'Invalid subtitle URL' }, 400);
    }
    if (parsed.protocol !== 'https:' || !isAllowedSubProxyHost(parsed.hostname)) {
      return jsonResponse({ error: 'Subtitle host not allowed' }, 403);
    }

    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), 8500);
    let upstream;
    try {
      upstream = await fetch(parsed.href, {
        signal: ctrl.signal,
        headers: {
          'User-Agent': 'ChillFilm-NKI/3.6',
          Accept: 'text/plain, text/vtt, application/x-subrip, */*',
        },
      });
    } finally {
      clearTimeout(timer);
    }

    if (!upstream.ok) {
      return jsonResponse({ error: `Upstream subtitle HTTP ${upstream.status}` }, 502);
    }

    const buf = await upstream.arrayBuffer();
    if (buf.byteLength > 2_500_000) {
      return jsonResponse({ error: 'Subtitle file too large' }, 413);
    }

    let text = new TextDecoder('utf-8', { fatal: false }).decode(buf);
    // Fallback to Windows-1252/Latin1 if raw UTF-8 decode produced excessive replacement characters
    const replacementCount = (text.match(/\uFFFD/g) || []).length;
    if (replacementCount > 12) {
      try {
        text = new TextDecoder('windows-1252').decode(buf);
      } catch {
        // Keep UTF-8 text
      }
    }
    text = text.replace(/^\uFEFF/, '');

    return new Response(text, {
      status: 200,
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, OPTIONS',
        'Cache-Control': 'public, s-maxage=3600, stale-while-revalidate=86400',
      },
    });
  } catch (err) {
    console.error('[catalog/subproxy] error:', err);
    return jsonResponse({ error: err?.message || 'Subtitle proxy failed' }, 502);
  }
}
