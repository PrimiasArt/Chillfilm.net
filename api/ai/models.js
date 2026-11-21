import { metric } from '@vercel/functions';
import { getServerGeminiConfig } from '../_lib/db.js';
import { jsonResponse, optionsResponse, readJsonBody, requireAuth } from '../_lib/auth.js';
import { fetchLiveGeminiModels } from '../_lib/gemini.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  const startedAt = performance.now();
  try {
    const auth = await requireAuth(request);
    if (auth.errorResponse) return auth.errorResponse;

    const aiCfg = await getServerGeminiConfig();
    if (!aiCfg.apiKey) {
      metric('ai.models', 1, { outcome: 'no_server_key' });
      return jsonResponse({ error: 'Server Gemini API Key is not configured yet by Admin' }, 503);
    }

    const models = await fetchLiveGeminiModels(aiCfg.apiKey);
    metric('ai.models', 1, { outcome: 'success' });
    return jsonResponse({
      ok: true,
      defaultModel: aiCfg.defaultModel,
      count: models.length,
      models,
    });
  } catch (err) {
    console.error('[api/ai/models GET] error:', err);
    metric('ai.models', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Failed to load live Gemini models' }, 502);
  } finally {
    metric('ai.models.duration_ms', performance.now() - startedAt);
  }
}

export async function POST(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    // Optional: allows signed-out user with their own personal API key to list models cleanly
    const customKey = typeof body.apiKey === 'string' ? body.apiKey.trim() : '';
    let keyToUse = customKey;
    let defaultModel = 'gemini-3-flash-preview';

    if (!keyToUse) {
      const auth = await requireAuth(request, body);
      if (auth.errorResponse) return auth.errorResponse;
      const aiCfg = await getServerGeminiConfig();
      keyToUse = aiCfg.apiKey;
      defaultModel = aiCfg.defaultModel;
    }

    if (!keyToUse) {
      return jsonResponse({ error: 'No Gemini API Key available to load models' }, 400);
    }

    const models = await fetchLiveGeminiModels(keyToUse);
    metric('ai.models', 1, { outcome: 'success' });
    return jsonResponse({
      ok: true,
      defaultModel,
      count: models.length,
      models,
    });
  } catch (err) {
    console.error('[api/ai/models POST] error:', err);
    metric('ai.models', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Failed to load live Gemini models' }, 502);
  } finally {
    metric('ai.models.duration_ms', performance.now() - startedAt);
  }
}
