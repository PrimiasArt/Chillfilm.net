import crypto from 'node:crypto';
import { ensureSchema, getSql, todayDayKey } from './db.js';

const TOKEN_TTL_MS = 90 * 24 * 60 * 60 * 1000; // 90 days persistent login across Phone/Web/TV

export const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Cache-Control': 'no-store, max-age=0',
};

export function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...CORS_HEADERS,
    },
  });
}

export function optionsResponse() {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

export async function readJsonBody(request) {
  try {
    const text = await request.text();
    if (!text || !text.trim()) return {};
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export function hashPassword(password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(String(password), salt, 64).toString('hex');
  return `scrypt$${salt}$${derived}`;
}

export function verifyPassword(password, storedHash) {
  const parts = String(storedHash || '').split('$');
  if (parts.length !== 3 || parts[0] !== 'scrypt') return false;
  const [, salt, hex] = parts;
  const derived = crypto.scryptSync(String(password), salt, 64);
  const expected = Buffer.from(hex, 'hex');
  if (derived.length !== expected.length) return false;
  return crypto.timingSafeEqual(derived, expected);
}

function getJwtSecret() {
  const s = process.env.NKI_JWT_SECRET;
  if (!s) throw new Error('NKI_JWT_SECRET is not configured');
  return s;
}

export function signSessionToken(user) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const now = Date.now();
  const payloadObj = {
    sub: user.id,
    u: user.username,
    r: user.role,
    v: Number(user.token_version || 1),
    iat: now,
    exp: now + TOKEN_TTL_MS,
  };
  const payload = Buffer.from(JSON.stringify(payloadObj)).toString('base64url');
  const sig = crypto
    .createHmac('sha256', getJwtSecret())
    .update(`${header}.${payload}`)
    .digest('base64url');
  return `${header}.${payload}.${sig}`;
}

export function verifySessionTokenRaw(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [header, payload, sig] = parts;
  const expected = crypto
    .createHmac('sha256', getJwtSecret())
    .update(`${header}.${payload}`)
    .digest('base64url');
  const sigBuf = Buffer.from(sig);
  const expBuf = Buffer.from(expected);
  if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
    return null;
  }
  try {
    const parsed = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (!parsed || !parsed.sub || Date.now() > Number(parsed.exp || 0)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function extractBearerToken(request, bodyFallback = null) {
  const authHeader = request.headers.get('authorization') || request.headers.get('Authorization') || '';
  if (authHeader.toLowerCase().startsWith('bearer ')) {
    return authHeader.slice(7).trim();
  }
  if (bodyFallback && typeof bodyFallback.token === 'string' && bodyFallback.token.trim()) {
    return bodyFallback.token.trim();
  }
  try {
    const url = new URL(request.url);
    const q = url.searchParams.get('token');
    if (q && q.trim()) return q.trim();
  } catch {
    // ignore
  }
  return '';
}

/**
 * Authenticates the request against JWT signature AND live Neon DB user row
 * (checks ACTIVE status + token_version so Admin revocation takes effect instantaneously).
 */
export async function requireAuth(request, bodyFallback = null) {
  const token = extractBearerToken(request, bodyFallback);
  if (!token) {
    return { errorResponse: jsonResponse({ error: 'Authentication required' }, 401) };
  }
  const claims = verifySessionTokenRaw(token);
  if (!claims) {
    return { errorResponse: jsonResponse({ error: 'Invalid or expired session token' }, 401) };
  }
  await ensureSchema();
  const sql = getSql();
  const rows = await sql`
    SELECT id, username, display_name, role, status, daily_ai_limit, token_version, created_at, last_seen_at
    FROM nki_users
    WHERE id = ${claims.sub}
    LIMIT 1
  `;
  if (!rows.length) {
    return { errorResponse: jsonResponse({ error: 'Account not found' }, 401) };
  }
  const user = rows[0];
  if (user.status !== 'ACTIVE' || Number(user.token_version) !== Number(claims.v)) {
    return { errorResponse: jsonResponse({ error: 'Session revoked by administrator' }, 401) };
  }
  return { user };
}

export async function requireAdmin(request, bodyFallback = null) {
  const auth = await requireAuth(request, bodyFallback);
  if (auth.errorResponse) return auth;
  if (auth.user.role !== 'ADMIN') {
    return { errorResponse: jsonResponse({ error: 'Admin privileges required' }, 403) };
  }
  return auth;
}

export function sanitizeUserPublic(user, usage = null) {
  return {
    id: user.id,
    username: user.username,
    displayName: user.display_name,
    role: user.role,
    status: user.status,
    dailyAiLimit: Number(user.daily_ai_limit ?? 150),
    createdAt: Number(user.created_at || 0),
    lastSeenAt: Number(user.last_seen_at || 0),
    todayUsage: usage
      ? {
          requests: Number(usage.requests_count || 0),
          cues: Number(usage.cues_count || 0),
          audioChunks: Number(usage.audio_chunks || 0),
        }
      : undefined,
  };
}

export async function checkAndIncrementAiQuota(user, { cuesDelta = 0, audioDelta = 0 } = {}) {
  const sql = getSql();
  const day = todayDayKey();
  const limit = Number(user.daily_ai_limit ?? 150);
  const isUnlimited = user.role === 'ADMIN' || limit <= 0;

  const existing = await sql`
    SELECT requests_count, cues_count, audio_chunks
    FROM nki_ai_usage
    WHERE user_id = ${user.id} AND day_key = ${day}
    LIMIT 1
  `;
  const used = existing.length ? Number(existing[0].requests_count || 0) : 0;
  if (!isUnlimited && used >= limit) {
    return { allowed: false, used, limit };
  }

  const nextUsed = used + 1;
  await sql`
    INSERT INTO nki_ai_usage (user_id, day_key, requests_count, cues_count, audio_chunks)
    VALUES (${user.id}, ${day}, 1, ${Math.max(0, cuesDelta)}, ${Math.max(0, audioDelta)})
    ON CONFLICT (user_id, day_key) DO UPDATE SET
      requests_count = nki_ai_usage.requests_count + 1,
      cues_count = nki_ai_usage.cues_count + ${Math.max(0, cuesDelta)},
      audio_chunks = nki_ai_usage.audio_chunks + ${Math.max(0, audioDelta)}
  `;
  return { allowed: true, used: nextUsed, limit: isUnlimited ? 0 : limit };
}

export function generateInviteCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const pick = (n) => {
    const bytes = crypto.randomBytes(n);
    let out = '';
    for (let i = 0; i < n; i++) out += alphabet[bytes[i] % alphabet.length];
    return out;
  };
  return `NKI-${pick(4)}-${pick(4)}`;
}
