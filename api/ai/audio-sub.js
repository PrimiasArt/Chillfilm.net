import { metric } from '@vercel/functions';
import { ensureSchema, getServerGeminiConfig, getSql, normalizeMovieSlug } from '../_lib/db.js';
import {
  checkAndIncrementAiQuota,
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAuth,
} from '../_lib/auth.js';
import { transcribeAudioChunkServerSide } from '../_lib/gemini.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function POST(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const auth = await requireAuth(request, body);
    if (auth.errorResponse) return auth.errorResponse;
    const { user } = auth;

    const audioBase64 = String(body.audioBase64 || '').trim();
    if (!audioBase64 || audioBase64.length < 100) {
      return jsonResponse({ error: 'audioBase64 payload is required' }, 400);
    }
    if (audioBase64.length > 5_500_000) {
      return jsonResponse({ error: 'Audio chunk too large (max ~4MB base64)' }, 413);
    }

    let movieContext = body.movieContext || null;
    const needGlossaryFetch = Boolean(movieContext && movieContext.slug && !movieContext.glossary);

    // Parallelize Server AI Config + Daily Quota + Saved Glossary lookup (saves ~400-600ms per audio chunk!)
    const [aiCfg, quota, glossaryRows] = await Promise.all([
      getServerGeminiConfig(),
      checkAndIncrementAiQuota(user, { audioDelta: 1 }),
      needGlossaryFetch
        ? getSql()`SELECT glossary_json FROM nki_movie_glossaries WHERE movie_slug = ${normalizeMovieSlug(movieContext.slug)} LIMIT 1`.catch(() => [])
        : Promise.resolve([]),
    ]);
    if (!aiCfg.apiKey) {
      metric('ai.audio_sub', 1, { outcome: 'no_server_key' });
      return jsonResponse({ error: 'Server Gemini API Key is not configured yet by Admin' }, 503);
    }
    if (!quota.allowed) {
      metric('ai.audio_sub', 1, { outcome: 'quota_exceeded' });
      return jsonResponse(
        { error: `Daily AI audio quota exceeded (${quota.used}/${quota.limit})`, quota },
        429
      );
    }
    if (needGlossaryFetch && glossaryRows?.[0]?.glossary_json) {
      movieContext = { ...movieContext, glossary: glossaryRows[0].glossary_json };
    }

    const preferredModel = String(body.model || aiCfg.defaultModel || 'gemini-2.5-flash').trim();
    const previousLines = Array.isArray(body.previousLines)
      ? body.previousLines.map((l) => String(l || '').trim().slice(0, 120)).filter(Boolean).slice(-5)
      : [];
    const { cues, modelUsed } = await transcribeAudioChunkServerSide({
      audioBase64,
      mimeType: String(body.mimeType || 'audio/aac'),
      chunkStartOffsetMs: Number(body.chunkStartOffsetMs || 0),
      chunkDurationMs: Number(body.chunkDurationMs || 60000),
      targetLang: String(body.targetLang || 'vi'),
      apiKey: aiCfg.apiKey,
      preferredModel,
      preferTurboFlash: Boolean(body.preferTurboFlash),
      retrySensitive: Boolean(body.retrySensitive),
      movieContext,
      previousLines,
    });

    metric('ai.audio_sub', 1, { outcome: 'success', model: modelUsed });
    return jsonResponse({
      ok: true,
      cues,
      modelUsed,
      quota,
    });
  } catch (err) {
    console.error('[api/ai/audio-sub] error:', err);
    metric('ai.audio_sub', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'AI audio subtitle generation failed' }, 502);
  } finally {
    metric('ai.audio_sub.duration_ms', performance.now() - startedAt);
  }
}
