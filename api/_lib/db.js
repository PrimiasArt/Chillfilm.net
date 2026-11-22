import crypto from 'node:crypto';
import { neon } from '@neondatabase/serverless';

let _sql = null;
let _schemaReadyPromise = null;

export function getSql() {
  if (!_sql) {
    const conn = process.env.DATABASE_URL || process.env.POSTGRES_URL;
    if (!conn) {
      throw new Error('DATABASE_URL is not configured on this Vercel environment.');
    }
    _sql = neon(conn);
  }
  return _sql;
}

export async function ensureSchema() {
  if (_schemaReadyPromise) return _schemaReadyPromise;
  _schemaReadyPromise = (async () => {
    const sql = getSql();
    await sql`
      CREATE TABLE IF NOT EXISTS nki_users (
        id TEXT PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        display_name TEXT NOT NULL,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL DEFAULT 'MEMBER',
        status TEXT NOT NULL DEFAULT 'ACTIVE',
        daily_ai_limit INT NOT NULL DEFAULT 150,
        token_version INT NOT NULL DEFAULT 1,
        created_at BIGINT NOT NULL,
        last_seen_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_invites (
        code TEXT PRIMARY KEY,
        label TEXT NOT NULL DEFAULT '',
        max_uses INT NOT NULL DEFAULT 5,
        used_count INT NOT NULL DEFAULT 0,
        daily_ai_limit INT NOT NULL DEFAULT 150,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_by TEXT NOT NULL,
        created_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_config (
        key TEXT PRIMARY KEY,
        value_encrypted TEXT NOT NULL,
        updated_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_watch_progress (
        user_id TEXT NOT NULL,
        movie_slug TEXT NOT NULL,
        episode_key TEXT NOT NULL,
        title TEXT NOT NULL,
        poster_url TEXT NOT NULL DEFAULT '',
        country_code TEXT NOT NULL DEFAULT '',
        episode_name TEXT NOT NULL DEFAULT '',
        stream_url TEXT NOT NULL DEFAULT '',
        position_ms BIGINT NOT NULL DEFAULT 0,
        duration_ms BIGINT NOT NULL DEFAULT 0,
        updated_at BIGINT NOT NULL,
        PRIMARY KEY (user_id, movie_slug, episode_key)
      )
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_watch_progress_user_updated
      ON nki_watch_progress (user_id, updated_at DESC)
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_user_prefs (
        user_id TEXT PRIMARY KEY,
        prefs_json JSONB NOT NULL DEFAULT '{}'::jsonb,
        updated_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_subtitle_pool (
        cache_key TEXT PRIMARY KEY,
        movie_slug TEXT NOT NULL,
        episode_key TEXT NOT NULL,
        lang TEXT NOT NULL,
        blob_pathname TEXT NOT NULL,
        cue_count INT NOT NULL DEFAULT 0,
        is_fully_translated BOOLEAN NOT NULL DEFAULT TRUE,
        is_ai_audio BOOLEAN NOT NULL DEFAULT FALSE,
        source_lang TEXT NOT NULL DEFAULT 'auto',
        source_platform TEXT NOT NULL DEFAULT '',
        created_by TEXT NOT NULL DEFAULT '',
        updated_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_subtitle_pool_lookup
      ON nki_subtitle_pool (movie_slug, episode_key, lang)
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_ai_usage (
        user_id TEXT NOT NULL,
        day_key TEXT NOT NULL,
        requests_count INT NOT NULL DEFAULT 0,
        cues_count INT NOT NULL DEFAULT 0,
        audio_chunks INT NOT NULL DEFAULT 0,
        PRIMARY KEY (user_id, day_key)
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_sources (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        source_type TEXT NOT NULL,
        base_url TEXT NOT NULL,
        country_hint TEXT NOT NULL DEFAULT 'global',
        enabled BOOLEAN NOT NULL DEFAULT TRUE,
        priority INT NOT NULL DEFAULT 50,
        created_by TEXT NOT NULL DEFAULT '',
        created_at BIGINT NOT NULL,
        is_builtin BOOLEAN NOT NULL DEFAULT FALSE,
        last_checked_at BIGINT NOT NULL DEFAULT 0,
        last_status TEXT NOT NULL DEFAULT 'PENDING',
        last_latency_ms INT NOT NULL DEFAULT 0,
        last_error TEXT NOT NULL DEFAULT '',
        consecutive_failures INT NOT NULL DEFAULT 0,
        items_crawled INT NOT NULL DEFAULT 0,
        total_remote INT NOT NULL DEFAULT 0
      )
    `;
    // Safe zero-downtime migration for existing nki_sources tables
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS is_builtin BOOLEAN NOT NULL DEFAULT FALSE`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS last_checked_at BIGINT NOT NULL DEFAULT 0`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS last_status TEXT NOT NULL DEFAULT 'PENDING'`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS last_latency_ms INT NOT NULL DEFAULT 0`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS last_error TEXT NOT NULL DEFAULT ''`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS consecutive_failures INT NOT NULL DEFAULT 0`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS items_crawled INT NOT NULL DEFAULT 0`;
    await sql`ALTER TABLE nki_sources ADD COLUMN IF NOT EXISTS total_remote INT NOT NULL DEFAULT 0`;

    await sql`
      CREATE TABLE IF NOT EXISTS nki_catalog_items (
        item_key TEXT PRIMARY KEY,
        source TEXT NOT NULL,
        source_id TEXT NOT NULL DEFAULT '',
        source_name TEXT NOT NULL DEFAULT '',
        slug TEXT NOT NULL,
        name TEXT NOT NULL,
        origin_name TEXT NOT NULL DEFAULT '',
        media_type TEXT NOT NULL DEFAULT 'single',
        year TEXT NOT NULL DEFAULT '',
        quality TEXT NOT NULL DEFAULT 'HD',
        episode_current TEXT NOT NULL DEFAULT '',
        poster TEXT NOT NULL DEFAULT '',
        backdrop TEXT NOT NULL DEFAULT '',
        genres_json JSONB NOT NULL DEFAULT '[]'::jsonb,
        countries_json JSONB NOT NULL DEFAULT '[]'::jsonb,
        imdb_id TEXT NOT NULL DEFAULT '',
        tmdb_score REAL NOT NULL DEFAULT 0,
        content TEXT NOT NULL DEFAULT '',
        stream_url TEXT NOT NULL DEFAULT '',
        category_tag TEXT NOT NULL DEFAULT 'latest',
        first_seen_at BIGINT NOT NULL,
        updated_at BIGINT NOT NULL,
        last_verified_at BIGINT NOT NULL,
        name_en TEXT NOT NULL DEFAULT '',
        name_vi TEXT NOT NULL DEFAULT '',
        content_en TEXT NOT NULL DEFAULT '',
        content_vi TEXT NOT NULL DEFAULT ''
      )
    `;
    await sql`ALTER TABLE nki_catalog_items ADD COLUMN IF NOT EXISTS name_en TEXT NOT NULL DEFAULT ''`;
    await sql`ALTER TABLE nki_catalog_items ADD COLUMN IF NOT EXISTS name_vi TEXT NOT NULL DEFAULT ''`;
    await sql`ALTER TABLE nki_catalog_items ADD COLUMN IF NOT EXISTS content_en TEXT NOT NULL DEFAULT ''`;
    await sql`ALTER TABLE nki_catalog_items ADD COLUMN IF NOT EXISTS content_vi TEXT NOT NULL DEFAULT ''`;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_my_list (
        user_id TEXT NOT NULL,
        item_key TEXT NOT NULL,
        source TEXT NOT NULL DEFAULT 'phimapi',
        source_id TEXT NOT NULL DEFAULT '',
        slug TEXT NOT NULL,
        name TEXT NOT NULL,
        origin_name TEXT NOT NULL DEFAULT '',
        poster TEXT NOT NULL DEFAULT '',
        year TEXT NOT NULL DEFAULT '',
        quality TEXT NOT NULL DEFAULT 'HD',
        last_known_episode TEXT NOT NULL DEFAULT '',
        added_at BIGINT NOT NULL,
        PRIMARY KEY (user_id, item_key)
      )
    `;
    await sql`ALTER TABLE nki_my_list ADD COLUMN IF NOT EXISTS last_known_episode TEXT NOT NULL DEFAULT ''`;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_movie_glossaries (
        movie_slug TEXT PRIMARY KEY,
        glossary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
        updated_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_my_list_user_added
      ON nki_my_list (user_id, added_at DESC)
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_watch_parties (
        room_code TEXT PRIMARY KEY,
        host_token TEXT NOT NULL,
        host_name TEXT NOT NULL,
        movie_json JSONB NOT NULL DEFAULT '{}'::jsonb,
        episode_json JSONB NOT NULL DEFAULT '{}'::jsonb,
        is_playing BOOLEAN NOT NULL DEFAULT FALSE,
        position_sec REAL NOT NULL DEFAULT 0,
        playback_rate REAL NOT NULL DEFAULT 1,
        state_updated_at BIGINT NOT NULL,
        members_json JSONB NOT NULL DEFAULT '[]'::jsonb,
        created_at BIGINT NOT NULL,
        expires_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_party_messages (
        id BIGSERIAL PRIMARY KEY,
        room_code TEXT NOT NULL,
        sender_name TEXT NOT NULL,
        sender_is_host BOOLEAN NOT NULL DEFAULT FALSE,
        text TEXT NOT NULL,
        created_at BIGINT NOT NULL
      )
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_party_messages_room
      ON nki_party_messages (room_code, id ASC)
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_catalog_updated
      ON nki_catalog_items (updated_at DESC)
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_catalog_source_updated
      ON nki_catalog_items (source_id, updated_at DESC)
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_catalog_tag_updated
      ON nki_catalog_items (category_tag, updated_at DESC)
    `;
    await sql`
      CREATE TABLE IF NOT EXISTS nki_crawl_runs (
        execution_id TEXT PRIMARY KEY,
        trigger_type TEXT NOT NULL DEFAULT 'SCHEDULE',
        started_at BIGINT NOT NULL,
        finished_at BIGINT NOT NULL,
        sources_checked INT NOT NULL DEFAULT 0,
        sources_healthy INT NOT NULL DEFAULT 0,
        items_discovered INT NOT NULL DEFAULT 0,
        items_new INT NOT NULL DEFAULT 0,
        items_blocked_sfw INT NOT NULL DEFAULT 0,
        total_indexed INT NOT NULL DEFAULT 0,
        summary_json JSONB NOT NULL DEFAULT '{}'::jsonb
      )
    `;
    await sql`
      CREATE INDEX IF NOT EXISTS idx_nki_crawl_runs_finished
      ON nki_crawl_runs (finished_at DESC)
    `;
  })().catch((err) => {
    _schemaReadyPromise = null;
    throw err;
  });
  return _schemaReadyPromise;
}

/**
 * Canonical cross-platform movie slug normalizer (identical across Server, Web, Android).
 * Strips Android provider prefixes like "modern_", "ophim_", "kkphim_" so both Android & Web match!
 */
export function normalizeMovieSlug(raw) {
  const s = String(raw || '')
    .trim()
    .toLowerCase()
    .replace(/^(modern|ophim|kkphim|phimapi|curated|custom)_+/i, '')
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return (s || 'unknown').slice(0, 120);
}

/**
 * Canonical cross-platform episode normalizer (identical across Server, Web, Android).
 * "Tập 01", "tap-01", "Tập 1", "EP 01" -> "tap-1" / "ep-1", "Full" -> "full".
 */
export function normalizeEpisodeKey(rawNameOrSlug) {
  const ascii = String(rawNameOrSlug || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .trim()
    .toLowerCase();

  if (!ascii || ascii === 'full' || ascii === 'full hd' || ascii === 'hd') {
    return 'full';
  }
  const tapMatch = ascii.match(/(?:tap|ep|episode)\s*[-_:]?\s*0*(\d+)([a-z]?)/);
  if (tapMatch) {
    return `tap-${tapMatch[1]}${tapMatch[2] || ''}`;
  }
  const numOnly = ascii.match(/^0*(\d+)([a-z]?)$/);
  if (numOnly) {
    return `tap-${numOnly[1]}${numOnly[2] || ''}`;
  }
  const slug = ascii
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return (slug || 'full').slice(0, 64);
}

export function normalizeLangCode(raw) {
  const s = String(raw || 'vi')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '');
  return (s || 'vi').slice(0, 20);
}

export function todayDayKey() {
  return new Date().toISOString().slice(0, 10);
}

/* ==================== AES-256-GCM ADMIN VAULT ENCRYPTION ==================== */
function deriveVaultKeyBuffer() {
  const raw = process.env.NKI_VAULT_KEY || process.env.NKI_JWT_SECRET || '';
  if (!raw) throw new Error('NKI_VAULT_KEY is not configured');
  return crypto.createHash('sha256').update(raw).digest();
}

export function encryptVaultValue(plainText) {
  const key = deriveVaultKeyBuffer();
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(String(plainText), 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${iv.toString('base64url')}.${tag.toString('base64url')}.${encrypted.toString('base64url')}`;
}

export function decryptVaultValue(token) {
  if (!token || typeof token !== 'string') return '';
  const parts = token.split('.');
  if (parts.length !== 3) return '';
  const key = deriveVaultKeyBuffer();
  const iv = Buffer.from(parts[0], 'base64url');
  const tag = Buffer.from(parts[1], 'base64url');
  const ciphertext = Buffer.from(parts[2], 'base64url');
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

export async function getVaultConfigValue(key) {
  await ensureSchema();
  const sql = getSql();
  const rows = await sql`SELECT value_encrypted FROM nki_config WHERE key = ${key} LIMIT 1`;
  if (!rows.length) return '';
  try {
    return decryptVaultValue(rows[0].value_encrypted);
  } catch {
    return '';
  }
}

export async function setVaultConfigValue(key, plainValue) {
  await ensureSchema();
  const sql = getSql();
  const clean = String(plainValue ?? '').trim();
  if (!clean) {
    await sql`DELETE FROM nki_config WHERE key = ${key}`;
    return;
  }
  const encrypted = encryptVaultValue(clean);
  const now = Date.now();
  await sql`
    INSERT INTO nki_config (key, value_encrypted, updated_at)
    VALUES (${key}, ${encrypted}, ${now})
    ON CONFLICT (key) DO UPDATE SET
      value_encrypted = EXCLUDED.value_encrypted,
      updated_at = EXCLUDED.updated_at
  `;
}

export function maskSecret(raw) {
  const s = String(raw || '').trim();
  if (!s) return '';
  if (s.length <= 8) return '••••';
  return `${s.slice(0, 4)}••••${s.slice(-4)}`;
}

/**
 * Resolves the authoritative server-side Gemini API key & defaults.
 * Priority: Admin Vault override (AES-256-GCM in Neon) > Vercel Secret `GEMINI_API_KEY`.
 * NEVER returns the raw key to clients!
 */
export async function getServerGeminiConfig() {
  const [vaultKey, defaultModel, regMode] = await Promise.all([
    getVaultConfigValue('gemini_api_key'),
    getVaultConfigValue('default_gemini_model'),
    getVaultConfigValue('registration_mode'),
  ]);
  const envKey = String(process.env.GEMINI_API_KEY || '').trim();
  const effectiveKey = vaultKey || envKey;
  return {
    apiKey: effectiveKey,
    hasVaultKey: Boolean(vaultKey),
    hasEnvKey: Boolean(envKey),
    maskedVaultKey: maskSecret(vaultKey),
    defaultModel: defaultModel || 'gemini-3-flash-preview',
    registrationMode: regMode === 'OPEN' ? 'OPEN' : 'INVITE_ONLY',
  };
}
