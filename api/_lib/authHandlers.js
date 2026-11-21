import crypto from 'node:crypto';
import { metric } from '@vercel/functions';
import { ensureSchema, getServerGeminiConfig, getSql, setVaultConfigValue, todayDayKey } from './db.js';
import {
  hashPassword,
  jsonResponse,
  readJsonBody,
  requireAuth,
  sanitizeUserPublic,
  signSessionToken,
  verifyPassword,
} from './auth.js';

export async function handleAuthLogin(request) {
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

export async function handleAuthRegister(request) {
  const startedAt = performance.now();
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const rawUsername = String(body.username || '').trim().toLowerCase();
    const password = String(body.password || '');
    const displayName = String(body.displayName || rawUsername).trim().slice(0, 48);
    const inviteCode = String(body.inviteCode || '').trim().toUpperCase();

    if (!/^[a-z0-9._@-]{3,40}$/.test(rawUsername)) {
      return jsonResponse({ error: 'Username must be 3-40 characters (letters, numbers, . _ @ -)' }, 400);
    }
    if (password.length < 6 || password.length > 128) {
      return jsonResponse({ error: 'Password must be at least 6 characters' }, 400);
    }

    await ensureSchema();
    const sql = getSql();

    const existingUser = await sql`SELECT id FROM nki_users WHERE username = ${rawUsername} LIMIT 1`;
    if (existingUser.length) {
      metric('auth.register', 1, { outcome: 'duplicate' });
      return jsonResponse({ error: 'Username already exists' }, 409);
    }

    const adminCountRows = await sql`SELECT COUNT(*)::int AS total FROM nki_users WHERE role = 'ADMIN'`;
    const isBootstrapAdmin = Number(adminCountRows?.[0]?.total || 0) === 0;
    const aiCfg = await getServerGeminiConfig();

    let role = 'MEMBER';
    let dailyAiLimit = 150;

    if (isBootstrapAdmin) {
      role = 'ADMIN';
      dailyAiLimit = 0;
      await setVaultConfigValue('registration_mode', 'INVITE_ONLY');
    } else if (aiCfg.registrationMode === 'INVITE_ONLY') {
      if (!inviteCode) {
        metric('auth.register', 1, { outcome: 'invite_required' });
        return jsonResponse({ error: 'Invite code is required by the administrator' }, 403);
      }
      const invites = await sql`
        SELECT code, max_uses, used_count, daily_ai_limit, active
        FROM nki_invites
        WHERE code = ${inviteCode}
        LIMIT 1
      `;
      if (!invites.length || !invites[0].active) {
        metric('auth.register', 1, { outcome: 'invalid_invite' });
        return jsonResponse({ error: 'Invalid or revoked invite code' }, 403);
      }
      const inv = invites[0];
      if (Number(inv.max_uses) > 0 && Number(inv.used_count) >= Number(inv.max_uses)) {
        metric('auth.register', 1, { outcome: 'invite_exhausted' });
        return jsonResponse({ error: 'Invite code has reached its maximum uses' }, 403);
      }
      dailyAiLimit = Number(inv.daily_ai_limit ?? 150);
      await sql`UPDATE nki_invites SET used_count = used_count + 1 WHERE code = ${inviteCode}`;
    }

    const now = Date.now();
    const id = crypto.randomUUID();
    const passwordHash = hashPassword(password);

    await sql`
      INSERT INTO nki_users (
        id, username, display_name, password_hash, role, status, daily_ai_limit, token_version, created_at, last_seen_at
      )
      VALUES (
        ${id}, ${rawUsername}, ${displayName || rawUsername}, ${passwordHash}, ${role}, 'ACTIVE', ${dailyAiLimit}, 1, ${now}, ${now}
      )
    `;

    const userRow = {
      id,
      username: rawUsername,
      display_name: displayName || rawUsername,
      role,
      status: 'ACTIVE',
      daily_ai_limit: dailyAiLimit,
      token_version: 1,
      created_at: now,
      last_seen_at: now,
    };
    const token = signSessionToken(userRow);
    metric('auth.register', 1, { outcome: 'success', role });
    return jsonResponse({
      ok: true,
      token,
      user: sanitizeUserPublic(userRow),
      serverAiReady: Boolean(aiCfg.apiKey),
      defaultModel: aiCfg.defaultModel,
    }, 201);
  } catch (err) {
    console.error('[api/auth/register] error:', err);
    metric('auth.register', 1, { outcome: 'error' });
    return jsonResponse({ error: err?.message || 'Registration failed' }, 500);
  } finally {
    metric('auth.register.duration_ms', performance.now() - startedAt);
  }
}

export async function handleAuthMe(request) {
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
