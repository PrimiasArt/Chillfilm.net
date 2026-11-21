import { getServerGeminiConfig, getSql, todayDayKey } from '../_lib/db.js';
import { jsonResponse, optionsResponse, requireAuth, sanitizeUserPublic } from '../_lib/auth.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  try {
    const auth = await requireAuth(request);
    if (auth.errorResponse) return auth.errorResponse;
    const { user } = auth;

    const sql = getSql();
    const day = todayDayKey();
    const [usageRows, aiCfg] = await Promise.all([
      sql`SELECT requests_count, cues_count, audio_chunks FROM nki_ai_usage WHERE user_id = ${user.id} AND day_key = ${day} LIMIT 1`,
      getServerGeminiConfig(),
    ]);

    return jsonResponse({
      ok: true,
      user: sanitizeUserPublic(user, usageRows[0] || { requests_count: 0, cues_count: 0, audio_chunks: 0 }),
      serverAiReady: Boolean(aiCfg.apiKey),
      defaultModel: aiCfg.defaultModel,
    });
  } catch (err) {
    console.error('[api/auth/me] error:', err);
    return jsonResponse({ error: err?.message || 'Failed to load profile' }, 500);
  }
}
