import { metric } from '@vercel/functions';
import { ensureSchema, getServerGeminiConfig, getSql, normalizeMovieSlug } from '../_lib/db.js';
import {
  checkAndIncrementAiQuota,
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAuth,
} from '../_lib/auth.js';
import { polishSubtitleTextsServerSide, translateTextsServerSide } from '../_lib/gemini.js';

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

    const rawTexts = Array.isArray(body.texts)
      ? body.texts
      : Array.isArray(body.cues)
        ? body.cues.map((c) => (typeof c === 'string' ? c : c?.text ?? ''))
        : [];
    if (!rawTexts.length) {
      return jsonResponse({ error: 'texts or cues array is required' }, 400);
    }
    if (rawTexts.length > 80) {
      return jsonResponse({ error: 'Maximum 80 lines per batch' }, 400);
    }

    const targetLang = String(body.targetLang || 'vi').trim();
    let movieContext = body.movieContext || null;
    const needGlossaryFetch = Boolean(movieContext && movieContext.slug && !movieContext.glossary);

    // Parallelize Server AI Config + Daily Quota + Saved Glossary lookup (saves ~400-600ms per batch!)
    const [aiCfg, quota, glossaryRows] = await Promise.all([
      getServerGeminiConfig(),
      checkAndIncrementAiQuota(user, { cuesDelta: rawTexts.length }),
      needGlossaryFetch
        ? getSql()`SELECT glossary_json FROM nki_movie_glossaries WHERE movie_slug = ${normalizeMovieSlug(movieContext.slug)} LIMIT 1`.catch(() => [])
        : Promise.resolve([]),
    ]);
    if (!aiCfg.apiKey) {
      metric('ai.translate', 1, { outcome: 'no_server_key' });
      return jsonResponse({ error: 'Server Gemini API Key is not configured yet by Admin' }, 503);
    }
    if (!quota.allowed) {
      metric('ai.translate', 1, { outcome: 'quota_exceeded' });
      return jsonResponse(
        { error: `Daily AI translation quota exceeded (${quota.used}/${quota.limit})`, quota },
        429
      );
    }
    if (needGlossaryFetch && glossaryRows?.[0]?.glossary_json) {
      movieContext = { ...movieContext, glossary: glossaryRows[0].glossary_json };
    }

    const mode = String(body.mode || 'translate').trim().toLowerCase();
    const isPolish = mode === 'polish';
    const preferredModel = String(body.model || aiCfg.defaultModel || 'gemini-2.5-flash').trim();
    const { translated, modelUsed } = isPolish
      ? await polishSubtitleTextsServerSide({
          texts: rawTexts,
          targetLang,
          apiKey: aiCfg.apiKey,
          preferredModel,
          movieContext,
        })
      : await translateTextsServerSide({
          texts: rawTexts,
          targetLang,
          apiKey: aiCfg.apiKey,
          preferredModel,
          movieContext,
        });

    metric('ai.translate', 1, { outcome: 'success', mode: isPolish ? 'polish' : 'translate', model: modelUsed });
    return jsonResponse({
      ok: true,
      translated,
      polished: isPolish,
      modelUsed,
      quota,
    });
  } catch (err) {
    console.error('[api/ai/translate] error:', err);
    metric('ai.translate', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'AI translation failed' }, 502);
  } finally {
    metric('ai.translate.duration_ms', performance.now() - startedAt);
  }
}
