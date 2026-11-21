import { metric } from '@vercel/functions';
import { ensureSchema, getServerGeminiConfig, getSql, todayDayKey } from '../_lib/db.js';
import {
  jsonResponse,
  optionsResponse,
  readJsonBody,
  sanitizeUserPublic,
  signSessionToken,
  verifyPassword,
} from '../_lib/auth.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function POST(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const username = String(body.username || '').trim().toLowerCase();
    const password = String(body.password || '');
    if (!username || !password) {
      return jsonResponse({ error: 'Username and password are required' }, 400);
    }

    await ensureSchema();
    const sql = getSql();
    const rows = await sql`
      SELECT id, username, display_name, password_hash, role, status, daily_ai_limit, token_version, created_at, last_seen_at
      FROM nki_users
      WHERE username = ${username}
      LIMIT 1
    `;
    if (!rows.length || !verifyPassword(password, rows[0].password_hash)) {
      metric('auth.login', 1, { outcome: 'invalid_credentials' });
      return jsonResponse({ error: 'Invalid username or password' }, 401);
    }

    const user = rows[0];
    if (user.status !== 'ACTIVE') {
      metric('auth.login', 1, { outcome: 'revoked' });
      return jsonResponse({ error: 'Account has been disabled by the administrator' }, 403);
    }

    const now = Date.now();
    const day = todayDayKey();
    const [usageRows, aiCfg] = await Promise.all([
      sql`SELECT requests_count, cues_count, audio_chunks FROM nki_ai_usage WHERE user_id = ${user.id} AND day_key = ${day} LIMIT 1`,
      getServerGeminiConfig(),
      sql`UPDATE nki_users SET last_seen_at = ${now} WHERE id = ${user.id}`,
    ]);

    const token = signSessionToken(user);
    metric('auth.login', 1, { outcome: 'success', role: user.role });
    return jsonResponse({
      ok: true,
      token,
      user: sanitizeUserPublic({ ...user, last_seen_at: now }, usageRows[0] || { requests_count: 0, cues_count: 0, audio_chunks: 0 }),
      serverAiReady: Boolean(aiCfg.apiKey),
      defaultModel: aiCfg.defaultModel,
    });
  } catch (err) {
    console.error('[api/auth/login] error:', err);
    metric('auth.login', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Login failed' }, 500);
  } finally {
    metric('auth.login.duration_ms', performance.now() - startedAt);
  }
}
