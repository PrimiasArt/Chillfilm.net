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

function normalizeRanges(raw) {
  if (!Array.isArray(raw)) return [];
  const pairs = [];
  for (const r of raw) {
    if (Array.isArray(r) && r.length >= 2) {
      const s = Math.max(0, Math.round(Number(r[0]) || 0));
      const e = Math.max(0, Math.round(Number(r[1]) || 0));
      if (e > s) pairs.push([s, e]);
    } else if (r && typeof r === 'object') {
      const s = Math.max(0, Math.round(Number(r.startMs ?? r.s ?? 0) || 0));
      const e = Math.max(0, Math.round(Number(r.endMs ?? r.e ?? 0) || 0));
      if (e > s) pairs.push([s, e]);
    }
  }
  if (!pairs.length) return [];
  pairs.sort((a, b) => a[0] - b[0]);
  const merged = [pairs[0]];
  for (let i = 1; i < pairs.length; i++) {
    const prev = merged[merged.length - 1];
    if (pairs[i][0] <= prev[1] + 2500) {
      prev[1] = Math.max(prev[1], pairs[i][1]);
    } else {
      merged.push(pairs[i]);
    }
  }
  return merged.slice(0, 400);
}

function normalizeCueTextKey(text) {
  return String(text || '').trim().replace(/\s+/g, ' ').toLowerCase();
}

function scoreSubtitleCueQualityServer(text, durMs = 2500) {
  const s = String(text || '').trim();
  if (!s) return -100;
  let score = Math.min(s.length, 95) * 0.6;
  if (s.length > 140) score -= (s.length - 140) * 0.5;
  if (/[.?!…]$/.test(s)) score += 6;
  if (/[,:;?!]/.test(s)) score += 3;
  if (/[àáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]/i.test(s)) score += 5;
  if (durMs >= 900 && durMs <= 5500) score += 4;
  return score;
}

function cueTextSimilarityServer(a, b) {
  const na = normalizeCueTextKey(a);
  const nb = normalizeCueTextKey(b);
  if (!na || !nb) return 0;
  if (na === nb) return 1;
  const pa = na.replace(/[^\p{L}\p{N}]+/gu, '');
  const pb = nb.replace(/[^\p{L}\p{N}]+/gu, '');
  if (!pa || !pb) return 0;
  if (pa === pb) return 1;
  if (pa.length >= 6 && pb.length >= 6 && (pa.includes(pb) || pb.includes(pa))) {
    return Math.max(0.75, Math.min(pa.length, pb.length) / Math.max(pa.length, pb.length));
  }
  const tok = (s) => {
    const clean = s.replace(/[^\p{L}\p{N}\s]+/gu, ' ').replace(/\s+/g, ' ').trim();
    if (!clean.includes(' ') && clean.length >= 4) {
      const g = [];
      for (let i = 0; i < clean.length - 1; i++) g.push(clean.slice(i, i + 2));
      return g;
    }
    return clean.split(' ').filter(Boolean);
  };
  const setA = new Set(tok(na));
  const setB = new Set(tok(nb));
  let inter = 0;
  for (const w of setA) if (setB.has(w)) inter += 1;
  const union = setA.size + setB.size - inter;
  const jaccard = union > 0 ? inter / union : 0;
  const minSize = Math.min(setA.size, setB.size);
  const overlapCoeff = minSize >= 3 ? inter / minSize : 0;
  return Math.max(jaccard, overlapCoeff * 0.92);
}

function cueOverlapRatioServer(s1, e1, s2, e2) {
  const ov = Math.min(e1, e2) - Math.max(s1, s2);
  if (ov <= 0) return 0;
  return ov / Math.max(400, Math.min(e1 - s1, e2 - s2));
}

function mergeCuesMonotonic(a, b, isAudioMode = false) {
  // Pass 1: O(N) exact (startMs + text) dedup BEFORE sorting — instantly collapses 2M bloated duplicates in <30ms!
  const seenExact = new Set();
  const unique = [];
  for (const list of [a, b]) {
    if (!Array.isArray(list)) continue;
    for (let i = 0; i < list.length; i++) {
      const c = list[i];
      if (!c || !c.text || !Number.isFinite(c.startMs)) continue;
      const text = String(c.text).trim();
      if (!text) continue;
      const startMs = Math.max(0, Math.round(c.startMs));
      const endMs = Math.max(Math.round(c.endMs || startMs + 2500), startMs + 450);
      const normKey = normalizeCueTextKey(text);
      const exactKey = `${startMs}|${normKey}`;
      if (seenExact.has(exactKey)) continue;
      seenExact.add(exactKey);
      unique.push({ startMs, endMs, text, normKey });
    }
  }
  unique.sort((x, y) => (x.startMs - y.startMs) || (x.normKey < y.normKey ? -1 : x.normKey > y.normKey ? 1 : 0));

  // Pass 2: Sliding 2600ms fuzzy & AI same-timestamp quality-winner dedup!
  const out = [];
  const outNorm = [];
  for (let i = 0; i < unique.length; i++) {
    const c = unique[i];
    let isDup = false;
    for (let j = out.length - 1; j >= 0 && (c.startMs - out[j].startMs) <= 2600; j--) {
      const prev = out[j];
      const ovRatio = cueOverlapRatioServer(prev.startMs, prev.endMs, c.startMs, c.endMs);
      const sim = cueTextSimilarityServer(prev.text, c.text);
      const exactNear = outNorm[j] === c.normKey && (c.startMs - prev.startMs) <= 2200;
      const fuzzyNear = (ovRatio >= 0.20 || (c.startMs - prev.startMs) <= 2000) && sim >= 0.48;
      const audioSameSlot = isAudioMode && (
        (ovRatio >= 0.55 && Math.abs(c.startMs - prev.startMs) <= 1300) ||
        (ovRatio >= 0.35 && sim >= 0.30)
      );
      if (exactNear || fuzzyNear || audioSameSlot) {
        const prevScore = scoreSubtitleCueQualityServer(prev.text, prev.endMs - prev.startMs);
        const curScore = scoreSubtitleCueQualityServer(c.text, c.endMs - c.startMs);
        if (c.endMs > prev.endMs) prev.endMs = c.endMs;
        if (curScore > prevScore) {
          prev.text = c.text;
          outNorm[j] = c.normKey;
        }
        isDup = true;
        break;
      }
    }
    if (isDup) continue;
    out.push({ id: out.length + 1, startMs: c.startMs, endMs: c.endMs, text: c.text });
    outNorm.push(c.normKey);
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

    // Normalize audio progress metadata so Web & Android resume from exact translated frontier!
    const audioMode = Boolean(bundle.audioMode || bundle.isAiAudio || row.is_ai_audio);
    // Self-heal bloated & near-duplicate cues on read so old corrupted Cloud blobs return clean cues immediately!
    const cleanPrimary = mergeCuesMonotonic(normalizeCues(bundle.primaryCues), [], audioMode);
    const cleanSource = bundle.sourceCues?.length
      ? mergeCuesMonotonic(normalizeCues(bundle.sourceCues), [], audioMode)
      : cleanPrimary;
    const translatedWindows = Array.isArray(bundle.translatedWindows)
      ? bundle.translatedWindows
      : Array.isArray(bundle.completedAudioSlots)
        ? bundle.completedAudioSlots
        : [];
    const translatedRanges = normalizeRanges(bundle.translatedRanges);
    const normalizedBundle = {
      ...bundle,
      primaryCues: cleanPrimary,
      sourceCues: cleanSource,
      isAiAudio: audioMode,
      audioMode,
      completedAudioSlots: translatedWindows,
      translatedWindows,
      translatedRanges,
      audioTranslatedUntilMs: Number(bundle.audioTranslatedUntilMs || 0),
      totalDurationMs: Number(bundle.totalDurationMs || 0),
    };

    metric('sync.subtitles', 1, { action: 'get', outcome: 'hit' });
    return jsonResponse({
      ok: true,
      hit: true,
      cacheKey,
      movieSlug,
      episodeKey,
      lang,
      updatedAt: Number(row.updated_at || 0),
      bundle: normalizedBundle,
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

    let isFullyTranslated = Boolean(body.isFullyTranslated);
    let isAiAudio = Boolean(body.isAiAudio || body.audioMode);
    let primaryCues = mergeCuesMonotonic(normalizeCues(body.primaryCues), [], isAiAudio);
    if (primaryCues.length < 3) {
      return jsonResponse({ error: 'At least 3 translated cues are required' }, 400);
    }
    if (hasWrongScriptForTarget(primaryCues, lang)) {
      metric('sync.subtitles', 1, { action: 'put', outcome: 'rejected_wrong_script' });
      return jsonResponse({ ok: false, skipped: `Subtitle text does not match target language (${lang})` }, 200);
    }
    let sourceCues = mergeCuesMonotonic(normalizeCues(body.sourceCues), [], isAiAudio);
    const secondaryCues = mergeCuesMonotonic(normalizeCues(body.secondaryCues), [], isAiAudio);
    let completedAudioSlots = Array.isArray(body.completedAudioSlots)
      ? body.completedAudioSlots.slice(0, 500)
      : Array.isArray(body.translatedWindows)
        ? body.translatedWindows.slice(0, 500)
        : [];
    let translatedRanges = normalizeRanges(body.translatedRanges);
    let audioTranslatedUntilMs = Math.max(0, Math.round(Number(body.audioTranslatedUntilMs || 0)));
    let totalDurationMs = Math.max(0, Math.round(Number(body.totalDurationMs || 0)));
    const forceOverwrite = Boolean(body.forceOverwrite);

    await ensureSchema();
    const sql = getSql();
    const cacheKey = makeSubPoolKey(movieSlug, episodeKey, lang);

    // Protect & monotonically merge existing Cloud subtitles so a partial/older client
    // NEVER shrinks a 20-minute translated Cloud subtitle back to 5 minutes!
    const existing = await sql`
      SELECT blob_pathname, cue_count, is_fully_translated, is_ai_audio
      FROM nki_subtitle_pool
      WHERE cache_key = ${cacheKey}
      LIMIT 1
    `;
    if (existing.length && !forceOverwrite) {
      let oldBundle = null;
      let existingPoisoned = false;
      try {
        const oldBlob = await get(existing[0].blob_pathname, { access: 'private' });
        if (oldBlob?.statusCode === 200 && oldBlob.stream) {
          const oldBuf = Buffer.from(await new Response(oldBlob.stream).arrayBuffer());
          oldBundle = JSON.parse(zlib.gunzipSync(oldBuf).toString('utf8'));
          if (hasWrongScriptForTarget(oldBundle?.primaryCues, lang)) {
            existingPoisoned = true;
          }
        }
      } catch {
        // ignore check failure
      }
      if (!existingPoisoned) {
        const exFull = Boolean(existing[0].is_fully_translated);
        const oldIsAudio = Boolean(isAiAudio || oldBundle?.isAiAudio || oldBundle?.audioMode);
        const dedupedOldPrimary = oldBundle ? mergeCuesMonotonic(normalizeCues(oldBundle.primaryCues), [], oldIsAudio) : [];
        const oldWasBloated = Boolean(oldBundle?.primaryCues && oldBundle.primaryCues.length > dedupedOldPrimary.length);
        const exCount = dedupedOldPrimary.length || Number(existing[0].cue_count || 0);
        if (!oldWasBloated && exFull && !isFullyTranslated) {
          return jsonResponse({ ok: true, skipped: 'Cloud already has a complete subtitle', cacheKey });
        }
        if (!oldWasBloated && exFull && isFullyTranslated && primaryCues.length < exCount * 0.8) {
          return jsonResponse({ ok: true, skipped: 'Cloud already has a richer complete subtitle', cacheKey });
        }
        // Monotonic merge for partial/incremental AI Audio subtitles
        if (oldBundle && oldIsAudio) {
          if (dedupedOldPrimary.length) {
            primaryCues = mergeCuesMonotonic(dedupedOldPrimary, primaryCues, true);
            sourceCues = sourceCues.length ? mergeCuesMonotonic(normalizeCues(oldBundle.sourceCues), sourceCues, true) : primaryCues;
          }
          const oldSlots = Array.isArray(oldBundle.translatedWindows)
            ? oldBundle.translatedWindows
            : Array.isArray(oldBundle.completedAudioSlots)
              ? oldBundle.completedAudioSlots
              : [];
          completedAudioSlots = [...new Set([...oldSlots, ...completedAudioSlots])].slice(0, 500);
          translatedRanges = normalizeRanges([...(oldBundle.translatedRanges || []), ...translatedRanges]);
          audioTranslatedUntilMs = Math.max(audioTranslatedUntilMs, Math.round(Number(oldBundle.audioTranslatedUntilMs || 0)));
          totalDurationMs = Math.max(totalDurationMs, Math.round(Number(oldBundle.totalDurationMs || 0)));
          isAiAudio = Boolean(isAiAudio || oldBundle.isAiAudio || oldBundle.audioMode);
          isFullyTranslated = Boolean(isFullyTranslated || oldBundle.isFullyTranslated);
        }
      }
    }

    const now = Date.now();
    const bundle = {
      v: 2,
      movieSlug,
      episodeKey,
      primaryLangCode: lang,
      secondaryLangCode: normalizeLangCode(body.secondaryLangCode || ''),
      sourceLangCode: String(body.sourceLangCode || 'auto').slice(0, 20),
      sourcePlatform: String(body.sourcePlatform || body.trackLabel || 'NKI Cloud').slice(0, 80),
      isAiAudio,
      audioMode: isAiAudio,
      isFullyTranslated,
      completedAudioSlots,
      translatedWindows: completedAudioSlots,
      translatedRanges,
      audioTranslatedUntilMs,
      totalDurationMs,
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
