import { CORS_HEADERS, jsonResponse, optionsResponse } from '../_lib/auth.js';
import { isBlockedContent } from '../_lib/safety.js';
import {
  fetchJsonSafe,
  getCustomBlockedTerms,
  listDynamicSources,
  normalizeImage,
  PHIM_API,
  resolveArchiveDetail,
  resolveMacCmsDetail,
  stripHtml,
} from '../_lib/sources.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  try {
    const url = new URL(request.url);
    const source = (url.searchParams.get('source') || 'phimapi').trim().toLowerCase();
    const slug = (url.searchParams.get('slug') || '').trim();
    const sourceId = (url.searchParams.get('sourceId') || '').trim();

    if (!slug) {
      return jsonResponse({ error: 'slug is required' }, 400);
    }

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
      const sources = await listDynamicSources({ onlyEnabled: false });
      const target = sources.find((s) => s.id === sourceId);
      if (!target) return jsonResponse({ error: 'Custom source not found' }, 404);

      if (source === 'maccms') {
        const detail = await resolveMacCmsDetail(target, slug, blockedTerms);
        if (!detail) return jsonResponse({ error: 'Movie not found in MacCMS source' }, 404);
        if (detail.blocked) return jsonResponse({ error: 'Content blocked by VN-Safe anti-porn policy' }, 403);
        return jsonResponse({ ok: true, detail });
      }

      // phimapi_custom
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

    // Default: KKPhim / PhimAPI
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
          poster: normalizeImage(m.poster_url || m.thumb_url),
          backdrop: normalizeImage(m.thumb_url || m.poster_url),
          servers,
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
    console.error('[api/catalog/detail] error:', err);
    return jsonResponse({ error: err?.message || 'Detail fetch failed' }, 500);
  }
}
