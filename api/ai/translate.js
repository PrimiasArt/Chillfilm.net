import { metric } from '@vercel/functions';
import { getServerGeminiConfig } from '../_lib/db.js';
import {
  checkAndIncrementAiQuota,
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAuth,
} from '../_lib/auth.js';
import { translateTextsServerSide } from '../_lib/gemini.js';

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
    const aiCfg = await getServerGeminiConfig();
    if (!aiCfg.apiKey) {
      metric('ai.translate', 1, { outcome: 'no_server_key' });
      return jsonResponse({ error: 'Server Gemini API Key is not configured yet by Admin' }, 503);
    }

    const quota = await checkAndIncrementAiQuota(user, { cuesDelta: rawTexts.length });
    if (!quota.allowed) {
      metric('ai.translate', 1, { outcome: 'quota_exceeded' });
      return jsonResponse(
        { error: `Daily AI translation quota exceeded (${quota.used}/${quota.limit})`, quota },
        429
      );
    }

    const preferredModel = String(body.model || aiCfg.defaultModel || 'gemini-3-flash-preview').trim();
    const { translated, modelUsed } = await translateTextsServerSide({
      texts: rawTexts,
      targetLang,
      apiKey: aiCfg.apiKey,
      preferredModel,
      movieContext: body.movieContext || null,
    });

    metric('ai.translate', 1, { outcome: 'success', model: modelUsed });
    return jsonResponse({
      ok: true,
      translated,
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
