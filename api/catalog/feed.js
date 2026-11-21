import { metric } from '@vercel/functions';
import { normalizeMovieSlug } from '../_lib/db.js';
import { CORS_HEADERS, optionsResponse } from '../_lib/auth.js';
import { filterSafeMovies, isBlockedContent } from '../_lib/safety.js';
import {
  fetchArchiveCinema,
  fetchFromDynamicSource,
  fetchJsonSafe,
  fetchPhimApiList,
  getCustomBlockedTerms,
  GLOBAL_COUNTRIES,
  listDynamicSources,
  normalizeImage,
  PHIM_API,
  stripHtml,
} from '../_lib/sources.js';

const NGUONC_API = 'https://phim.nguonc.com/api/films/phim-moi-cap-nhat?page=1';

export async function OPTIONS() {
  return optionsResponse();
}

async function enrichHeroMovie(movie, blockedTerms) {
  if (movie.source && movie.source !== 'phimapi') return movie;
  const detail = await fetchJsonSafe(`${PHIM_API}/phim/${encodeURIComponent(movie.slug)}`, 6000);
  const m = detail?.movie;
  if (!m) return movie;
  if (isBlockedContent(m, blockedTerms)) return null;
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
  };
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    const forceRefresh = url.searchParams.get('refresh') === '1';

    const [blockedTerms, dynamicSources] = await Promise.all([
      getCustomBlockedTerms(),
      listDynamicSources({ onlyEnabled: true }),
    ]);

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
        dynamicSources.slice(0, 6).map(async (src) => {
          const movies = await fetchFromDynamicSource(src, { page: 1, blockedTerms });
          return {
            id: `src_${src.id}`,
            customTitle: `🌐 ${src.name}`,
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

    // Enrich Top 8 candidates then take first 6 clean verified Hero slides
    const enrichedHeroes = await Promise.all(rankedTrending.slice(0, 8).map((m) => enrichHeroMovie(m, blockedTerms)));
    const heroMovies = enrichedHeroes.filter(Boolean).slice(0, 6);

    const rows = [
      { id: 'hot', isTopTen: true, movies: rankedTrending.slice(0, 20) },
      ...dynamicRows.filter((r) => r.movies.length > 0),
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
          ...dynamicSources.map((s) => s.name),
        ],
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
    console.error('[api/catalog/feed] error:', err);
    metric('catalog.feed', 1, { outcome: 'error' });
    return new Response(JSON.stringify({ ok: false, error: err?.message || 'Catalog feed failed' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json; charset=utf-8', ...CORS_HEADERS },
    });
  } finally {
    metric('catalog.feed.duration_ms', performance.now() - startedAt);
  }
}
