import { metric } from '@vercel/functions';
import { normalizeMovieSlug } from '../_lib/db.js';
import { CORS_HEADERS, optionsResponse } from '../_lib/auth.js';

const PHIM_API = 'https://phimapi.com';
const PHIM_CDN = 'https://phimimg.com';
const NGUONC_API = 'https://phim.nguonc.com/api/films/phim-moi-cap-nhat?page=1';

export async function OPTIONS() {
  return optionsResponse();
}

function normalizeImage(url, cdn = PHIM_CDN) {
  const s = String(url || '').trim();
  if (!s) return '';
  if (s.startsWith('http://') || s.startsWith('https://')) return s;
  return `${cdn.replace(/\/+$/, '')}/${s.replace(/^\/+/, '')}`;
}

function stripHtml(raw) {
  return String(raw || '')
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function mapPhimItem(m, cdnDomain = PHIM_CDN) {
  if (!m || !m.slug || !m.name) return null;
  const categories = Array.isArray(m.category)
    ? m.category.map((c) => c?.name).filter(Boolean).slice(0, 3)
    : [];
  const countries = Array.isArray(m.country)
    ? m.country.map((c) => c?.name).filter(Boolean).slice(0, 2)
    : [];
  const voteAvg = Number(m.tmdb?.vote_average || m.imdb?.vote_average || 0);
  const yearNum = Number(m.year || 0);
  const modifiedIso = m.modified?.time || m.modified || '';
  return {
    slug: String(m.slug).trim(),
    name: String(m.name).trim(),
    origin_name: String(m.origin_name || '').trim(),
    type: String(m.type || '').trim(),
    year: yearNum ? String(yearNum) : '',
    quality: String(m.quality || 'FHD').trim(),
    lang: String(m.lang || '').trim(),
    time: String(m.time || '').trim(),
    episode_current: String(m.episode_current || '').trim(),
    poster: normalizeImage(m.poster_url || m.thumb_url, cdnDomain),
    backdrop: normalizeImage(m.thumb_url || m.poster_url, cdnDomain),
    genres: categories,
    countries,
    imdbId: String(m.imdb?.id || '').startsWith('tt') ? String(m.imdb.id) : '',
    tmdbScore: Number.isFinite(voteAvg) && voteAvg > 0 ? Math.round(voteAvg * 10) / 10 : 0,
    modifiedAt: modifiedIso,
    content: '',
  };
}

async function fetchJsonSafe(url, timeoutMs = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'ChillFilm-NKI/3.2 (+https://chillfilm.vercel.app)' },
    });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function fetchPhimFeed(url) {
  const json = await fetchJsonSafe(url, 8500);
  if (!json) return [];
  const cdn = json?.data?.APP_DOMAIN_CDN_IMAGE || PHIM_CDN;
  const raw = json?.items || json?.data?.items || [];
  return raw.map((item) => mapPhimItem(item, cdn)).filter(Boolean);
}

async function enrichHeroMovie(movie) {
  const detail = await fetchJsonSafe(`${PHIM_API}/phim/${encodeURIComponent(movie.slug)}`, 6000);
  const m = detail?.movie;
  if (!m) return movie;
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
      nguonCRaw,
    ] = await Promise.all([
      fetchPhimFeed(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=1`),
      fetchPhimFeed(`${PHIM_API}/danh-sach/phim-moi-cap-nhat-v3?page=2`),
      fetchPhimFeed(`${PHIM_API}/v1/api/danh-sach/phim-le?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/danh-sach/phim-bo?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/danh-sach/hoat-hinh?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/quoc-gia/han-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/quoc-gia/au-my?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/quoc-gia/trung-quoc?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/quoc-gia/thai-lan?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchPhimFeed(`${PHIM_API}/v1/api/quoc-gia/nhat-ban?page=1&sort_field=modified.time&sort_type=desc&limit=24`),
      fetchJsonSafe(NGUONC_API, 5000),
    ]);

    // Track trending titles appearing across multiple Vietnamese VOD crawlers (KKPhim + NguonC)
    const nguonCTrendingSlugs = new Set();
    const nguonCTrendingNames = new Set();
    if (Array.isArray(nguonCRaw?.items)) {
      for (const item of nguonCRaw.items) {
        if (item?.slug) nguonCTrendingSlugs.add(normalizeMovieSlug(item.slug));
        if (item?.name) nguonCTrendingNames.add(String(item.name).trim().toLowerCase());
      }
    }

    // Deduplicate newest releases across page 1 + page 2
    const seenHot = new Set();
    const hotCombined = [];
    for (const m of [...latestP1, ...latestP2, ...phimLe.slice(0, 8), ...phimBo.slice(0, 8)]) {
      if (!m || seenHot.has(m.slug)) continue;
      seenHot.add(m.slug);
      hotCombined.push(m);
    }

    // Score hero & top-10 candidates: fresh 2025-2026 releases + multi-crawler hot overlap + TMDB rating + widescreen backdrop
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
        score += 24; // Multi-source hot signal!
      }
      return score;
    };

    const rankedTrending = hotCombined
      .map((m, idx) => ({ m, s: scoreHot(m, idx) }))
      .sort((a, b) => b.s - a.s)
      .map((x) => x.m);

    // Enrich Top 6 Hero Billboard movies in parallel with real synopses, genres & high-res backdrops!
    const heroCandidates = rankedTrending.slice(0, 6);
    const heroMovies = await Promise.all(heroCandidates.map((m) => enrichHeroMovie(m)));

    const rows = [
      { id: 'hot', isTopTen: true, movies: rankedTrending.slice(0, 20) },
      { id: 'phim-le', isTopTen: false, movies: phimLe.slice(0, 20) },
      { id: 'phim-bo', isTopTen: false, movies: phimBo.slice(0, 20) },
      { id: 'han-quoc', isTopTen: false, movies: hanQuoc.slice(0, 20) },
      { id: 'au-my', isTopTen: false, movies: auMy.slice(0, 20) },
      { id: 'hoat-hinh', isTopTen: false, movies: hoatHinh.slice(0, 20) },
      { id: 'trung-quoc', isTopTen: false, movies: trungQuoc.slice(0, 20) },
      { id: 'thai-lan', isTopTen: false, movies: thaiLan.slice(0, 20) },
      { id: 'nhat-ban', isTopTen: false, movies: nhatBan.slice(0, 20) },
    ].filter((r) => r.movies.length > 0);

    metric('catalog.feed', 1, { outcome: rows.length ? 'success' : 'empty' });

    return new Response(
      JSON.stringify({
        ok: true,
        updatedAt: Date.now(),
        sources: ['KKPhim/PhimAPI v3', 'NguonC Trending Signal'],
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
