import zlib from 'node:zlib';
import { get, put } from '@vercel/blob';
import { metric } from '@vercel/functions';
import {
  ensureSchema,
  getSql,
  normalizeEpisodeKey,
  normalizeLangCode,
  normalizeMovieSlug,
} from '../_lib/db.js';
import {
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAuth,
} from '../_lib/auth.js';
import { hasWrongScriptForTarget } from '../_lib/gemini.js';

export async function OPTIONS() {
  return optionsResponse();
}

function makeSubPoolKey(movieSlug, episodeKey, lang) {
  return `${movieSlug}|${episodeKey}|${lang}`;
}

function makeBlobPathname(movieSlug, episodeKey, lang) {
  return `subtitles/${movieSlug}/${episodeKey}/${lang}.json.gz`;
}

function normalizeCues(raw) {
  if (!Array.isArray(raw)) return [];
  const out = [];
  for (let i = 0; i < raw.length; i++) {
    const item = raw[i];
    if (Array.isArray(item) && item.length >= 4) {
      out.push({
        id: Number(item[0]) || i + 1,
        startMs: Math.max(0, Math.round(Number(item[1]) || 0)),
        endMs: Math.max(0, Math.round(Number(item[2]) || 0)),
        text: String(item[3] ?? '').trim(),
      });
    } else if (item && typeof item === 'object') {
      const text = String(item.text ?? item.t ?? '').trim();
      if (!text) continue;
      out.push({
        id: Number(item.id) || i + 1,
        startMs: Math.max(0, Math.round(Number(item.startMs ?? item.s ?? 0))),
        endMs: Math.max(0, Math.round(Number(item.endMs ?? item.e ?? 0))),
        text,
      });
    }
  }
  return out;
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const auth = await requireAuth(request);
    if (auth.errorResponse) return auth.errorResponse;

    const url = new URL(request.url);
    const movieSlug = normalizeMovieSlug(url.searchParams.get('movieSlug') || url.searchParams.get('movieId'));
    const episodeKey = normalizeEpisodeKey(url.searchParams.get('episodeKey') || url.searchParams.get('episodeName'));
    const lang = normalizeLangCode(url.searchParams.get('lang') || 'vi');

    if (!movieSlug || movieSlug === 'unknown') {
      return jsonResponse({ ok: true, hit: false });
    }

    await ensureSchema();
    const sql = getSql();
    const cacheKey = makeSubPoolKey(movieSlug, episodeKey, lang);
    const rows = await sql`
      SELECT blob_pathname, cue_count, is_fully_translated, is_ai_audio, source_lang, source_platform, updated_at
      FROM nki_subtitle_pool
      WHERE cache_key = ${cacheKey}
      LIMIT 1
    `;
    if (!rows.length) {
      metric('sync.subtitles', 1, { action: 'get', outcome: 'miss' });
      return jsonResponse({ ok: true, hit: false, cacheKey });
    }

    const row = rows[0];
    const blobResult = await get(row.blob_pathname, { access: 'private' });
    if (!blobResult || blobResult.statusCode !== 200 || !blobResult.stream) {
      metric('sync.subtitles', 1, { action: 'get', outcome: 'blob_missing' });
      return jsonResponse({ ok: true, hit: false, cacheKey });
    }

    const buf = Buffer.from(await new Response(blobResult.stream).arrayBuffer());
    const jsonText = zlib.gunzipSync(buf).toString('utf8');
    const bundle = JSON.parse(jsonText);

    // Self-heal poisoned Cloud cache (e.g. Chinese Hanzi accidentally saved under lang='vi')
    if (Array.isArray(bundle?.primaryCues) && hasWrongScriptForTarget(bundle.primaryCues, lang)) {
      metric('sync.subtitles', 1, { action: 'get', outcome: 'rejected_wrong_script' });
      return jsonResponse({ ok: true, hit: false, cacheKey, rejectedWrongScript: true });
    }

    metric('sync.subtitles', 1, { action: 'get', outcome: 'hit' });
    return jsonResponse({
      ok: true,
      hit: true,
      cacheKey,
      movieSlug,
      episodeKey,
      lang,
      updatedAt: Number(row.updated_at || 0),
      bundle,
    });
  } catch (err) {
    console.error('[api/sync/subtitles GET] error:', err);
    metric('sync.subtitles', 1, { action: 'get', outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Subtitle cloud fetch failed' }, 500);
  } finally {
    metric('sync.subtitles.duration_ms', performance.now() - startedAt, { action: 'get' });
  }
}

async function handleSaveSubtitle(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const auth = await requireAuth(request, body);
    if (auth.errorResponse) return auth.errorResponse;
    const { user } = auth;

    const movieSlug = normalizeMovieSlug(body.movieSlug || body.movieId);
    const episodeKey = normalizeEpisodeKey(body.episodeKey || body.episodeName);
    const lang = normalizeLangCode(body.primaryLangCode || body.lang || 'vi');

    if (!movieSlug || movieSlug === 'unknown' || movieSlug === 'direct' || movieSlug === 'local') {
      return jsonResponse({ ok: false, skipped: 'Non-catalog local stream' }, 200);
    }

    const primaryCues = normalizeCues(body.primaryCues);
    if (primaryCues.length < 3) {
      return jsonResponse({ error: 'At least 3 translated cues are required' }, 400);
    }
    if (hasWrongScriptForTarget(primaryCues, lang)) {
      metric('sync.subtitles', 1, { action: 'put', outcome: 'rejected_wrong_script' });
      return jsonResponse({ ok: false, skipped: `Subtitle text does not match target language (${lang})` }, 200);
    }
    const sourceCues = normalizeCues(body.sourceCues);
    const secondaryCues = normalizeCues(body.secondaryCues);
    const isFullyTranslated = Boolean(body.isFullyTranslated);
    const isAiAudio = Boolean(body.isAiAudio);
    const forceOverwrite = Boolean(body.forceOverwrite);

    await ensureSchema();
    const sql = getSql();
    const cacheKey = makeSubPoolKey(movieSlug, episodeKey, lang);

    // Protect complete community subtitles from being overwritten by a smaller/incomplete session
    // (unless explicitly force-overwriting or replacing a previously poisoned wrong-script blob!)
    const existing = await sql`
      SELECT blob_pathname, cue_count, is_fully_translated
      FROM nki_subtitle_pool
      WHERE cache_key = ${cacheKey}
      LIMIT 1
    `;
    if (existing.length && !forceOverwrite) {
      let existingPoisoned = false;
      try {
        const oldBlob = await get(existing[0].blob_pathname, { access: 'private' });
        if (oldBlob?.statusCode === 200 && oldBlob.stream) {
          const oldBuf = Buffer.from(await new Response(oldBlob.stream).arrayBuffer());
          const oldBundle = JSON.parse(zlib.gunzipSync(oldBuf).toString('utf8'));
          if (hasWrongScriptForTarget(oldBundle?.primaryCues, lang)) {
            existingPoisoned = true;
          }
        }
      } catch {
        // ignore check failure
      }
      if (!existingPoisoned) {
        const exFull = Boolean(existing[0].is_fully_translated);
        const exCount = Number(existing[0].cue_count || 0);
        if (exFull && !isFullyTranslated) {
          return jsonResponse({ ok: true, skipped: 'Cloud already has a complete subtitle', cacheKey });
        }
        if (exFull && isFullyTranslated && primaryCues.length < exCount * 0.8) {
          return jsonResponse({ ok: true, skipped: 'Cloud already has a richer complete subtitle', cacheKey });
        }
      }
    }

    const now = Date.now();
    const bundle = {
      v: 1,
      movieSlug,
      episodeKey,
      primaryLangCode: lang,
      secondaryLangCode: normalizeLangCode(body.secondaryLangCode || ''),
      sourceLangCode: String(body.sourceLangCode || 'auto').slice(0, 20),
      sourcePlatform: String(body.sourcePlatform || body.trackLabel || 'NKI Cloud').slice(0, 80),
      isAiAudio,
      isFullyTranslated,
      completedAudioSlots: Array.isArray(body.completedAudioSlots) ? body.completedAudioSlots.slice(0, 500) : [],
      sourceCues: sourceCues.length ? sourceCues : primaryCues,
      primaryCues,
      secondaryCues,
      savedAtMs: now,
    };

    const gzBuffer = zlib.gzipSync(Buffer.from(JSON.stringify(bundle), 'utf8'), { level: 6 });
    const pathname = makeBlobPathname(movieSlug, episodeKey, lang);

    await put(pathname, gzBuffer, {
      access: 'private',
      addRandomSuffix: false,
      allowOverwrite: true,
      contentType: 'application/gzip',
    });

    await sql`
      INSERT INTO nki_subtitle_pool (
        cache_key, movie_slug, episode_key, lang, blob_pathname, cue_count,
        is_fully_translated, is_ai_audio, source_lang, source_platform, created_by, updated_at
      )
      VALUES (
        ${cacheKey}, ${movieSlug}, ${episodeKey}, ${lang}, ${pathname}, ${primaryCues.length},
        ${isFullyTranslated}, ${isAiAudio}, ${bundle.sourceLangCode}, ${bundle.sourcePlatform}, ${user.username}, ${now}
      )
      ON CONFLICT (cache_key) DO UPDATE SET
        blob_pathname = EXCLUDED.blob_pathname,
        cue_count = EXCLUDED.cue_count,
        is_fully_translated = EXCLUDED.is_fully_translated,
        is_ai_audio = EXCLUDED.is_ai_audio,
        source_lang = EXCLUDED.source_lang,
        source_platform = EXCLUDED.source_platform,
        created_by = EXCLUDED.created_by,
        updated_at = EXCLUDED.updated_at
    `;

    metric('sync.subtitles', 1, { action: 'put', outcome: 'success' });
    return jsonResponse({
      ok: true,
      cacheKey,
      cueCount: primaryCues.length,
      compressedBytes: gzBuffer.length,
      updatedAt: now,
    });
  } catch (err) {
    console.error('[api/sync/subtitles PUT] error:', err);
    metric('sync.subtitles', 1, { action: 'put', outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Subtitle cloud save failed' }, 500);
  } finally {
    metric('sync.subtitles.duration_ms', performance.now() - startedAt, { action: 'put' });
  }
}

export async function PUT(request) {
  return handleSaveSubtitle(request);
}

export async function POST(request) {
  return handleSaveSubtitle(request);
}
