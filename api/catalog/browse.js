import { metric } from '@vercel/functions';
import { CORS_HEADERS, jsonResponse, optionsResponse } from '../_lib/auth.js';
import { isBlockedCategoryTag } from '../_lib/safety.js';
import {
  fetchArchiveCinema,
  fetchFromDynamicSource,
  fetchM3uCinemaList,
  fetchPhimApiList,
  getCustomBlockedTerms,
  GLOBAL_COUNTRIES,
  listDynamicSources,
  PHIM_API,
} from '../_lib/sources.js';

const IPTV_MOVIES_URL = 'https://iptv-org.github.io/iptv/categories/movies.m3u';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') || '').trim();
    const country = (url.searchParams.get('country') || '').trim().toLowerCase();
    const category = (url.searchParams.get('category') || '').trim().toLowerCase();
    const sourceId = (url.searchParams.get('sourceId') || '').trim();
    const page = Math.max(1, Math.min(50, Number(url.searchParams.get('page') || 1)));

    if (isBlockedCategoryTag(category) || isBlockedCategoryTag(country)) {
      return jsonResponse({ ok: true, items: [], blocked: true, reason: 'Blocked by VN-Safe anti-porn policy' });
    }

    const [blockedTerms, dynamicSources] = await Promise.all([
      getCustomBlockedTerms(),
      listDynamicSources({ onlyEnabled: true }),
    ]);

    let items = [];

    // 1. Global Multi-Source Search across KKPhim + Internet Archive + ALL Admin dynamic sources!
    if (q) {
      const [phimHits, archiveHits, dynamicHits] = await Promise.all([
        fetchPhimApiList(`${PHIM_API}/v1/api/tim-kiem?keyword=${encodeURIComponent(q)}&page=${page}&limit=24`, blockedTerms),
        fetchArchiveCinema({ query: q, page, rows: 12, blockedTerms }),
        Promise.all(
          dynamicSources.slice(0, 6).map((src) => fetchFromDynamicSource(src, { query: q, page, blockedTerms }))
        ),
      ]);
      const seen = new Set();
      for (const m of [...phimHits, ...dynamicHits.flat(), ...archiveHits]) {
        const key = `${m.source || 'phimapi'}::${m.slug}`;
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

    // 2. Specific Admin Dynamic Source
    if (sourceId) {
      const target = dynamicSources.find((s) => s.id === sourceId);
      if (target) {
        items = await fetchFromDynamicSource(target, { page, blockedTerms });
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
    } else if (country) {
      // Any of the 36 global countries: combine KKPhim country + Internet Archive country!
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
    console.error('[api/catalog/browse] error:', err);
    metric('catalog.browse', 1, { outcome: 'error' });
    return jsonResponse({ ok: false, error: err?.message || 'Browse failed' }, 500);
  } finally {
    metric('catalog.browse.duration_ms', performance.now() - startedAt);
  }
}
