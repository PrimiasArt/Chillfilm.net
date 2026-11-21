import crypto from 'node:crypto';
import {
  ensureSchema,
  getServerGeminiConfig,
  getSql,
  setVaultConfigValue,
  todayDayKey,
} from '../_lib/db.js';
import {
  generateInviteCode,
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAdmin,
  sanitizeUserPublic,
} from '../_lib/auth.js';
import { isSafeSourceUrl } from '../_lib/safety.js';
import { getCustomBlockedTerms, listDynamicSources, setCustomBlockedTerms } from '../_lib/sources.js';
import {
  listRecentCrawlRuns,
  runCatalogCrawlAndHealthCheck,
  seedVerifiedBuiltinSources,
} from '../_lib/crawler.js';

export async function OPTIONS() {
  return optionsResponse();
}

export async function GET(request) {
  try {
    const auth = await requireAdmin(request);
    if (auth.errorResponse) return auth.errorResponse;

    await ensureSchema();
    await seedVerifiedBuiltinSources();
    const sql = getSql();
    const day = todayDayKey();

    const [aiCfg, invites, users, usageToday, poolStats, catalogStats, sources, customBlockedTerms, crawlRuns] = await Promise.all([
      getServerGeminiConfig(),
      sql`SELECT code, label, max_uses, used_count, daily_ai_limit, active, created_at FROM nki_invites ORDER BY created_at DESC LIMIT 50`,
      sql`SELECT id, username, display_name, role, status, daily_ai_limit, token_version, created_at, last_seen_at FROM nki_users ORDER BY created_at ASC LIMIT 100`,
      sql`SELECT user_id, requests_count, cues_count, audio_chunks FROM nki_ai_usage WHERE day_key = ${day}`,
      sql`SELECT COUNT(*)::int AS total_subs FROM nki_subtitle_pool`,
      sql`SELECT COUNT(*)::int AS total_items FROM nki_catalog_items`,
      listDynamicSources({ onlyEnabled: false }),
      getCustomBlockedTerms(),
      listRecentCrawlRuns(8),
    ]);

    const usageMap = new Map(usageToday.map((u) => [u.user_id, u]));
    const healthyCount = sources.filter((s) => s.enabled && s.lastStatus === 'ONLINE').length;

    return jsonResponse({
      ok: true,
      config: {
        serverAiReady: Boolean(aiCfg.apiKey),
        hasEnvKey: aiCfg.hasEnvKey,
        hasVaultKey: aiCfg.hasVaultKey,
        maskedVaultKey: aiCfg.maskedVaultKey,
        defaultModel: aiCfg.defaultModel,
        registrationMode: aiCfg.registrationMode,
        cachedSubtitlesCount: Number(poolStats?.[0]?.total_subs || 0),
        customBlockedTerms,
      },
      crawlerStats: {
        schedule: 'Every 3h (0 */3 * * * VN)',
        totalIndexed: Number(catalogStats?.[0]?.total_items || 0),
        healthySources: healthyCount,
        totalSources: sources.length,
        lastRun: crawlRuns[0] || null,
      },
      crawlRuns,
      sources,
      invites: invites.map((i) => ({
        code: i.code,
        label: i.label,
        maxUses: Number(i.max_uses),
        usedCount: Number(i.used_count),
        dailyAiLimit: Number(i.daily_ai_limit),
        active: Boolean(i.active),
        createdAt: Number(i.created_at),
      })),
      users: users.map((u) =>
        sanitizeUserPublic(u, usageMap.get(u.id) || { requests_count: 0, cues_count: 0, audio_chunks: 0 })
      ),
    });
  } catch (err) {
    console.error('[api/admin/config GET] error:', err);
    return jsonResponse({ error: err?.message || 'Admin query failed' }, 500);
  }
}

export async function POST(request) {
  try {
    const body = await readJsonBody(request);
    if (!body) return jsonResponse({ error: 'Invalid JSON payload' }, 400);

    const auth = await requireAdmin(request, body);
    if (auth.errorResponse) return auth.errorResponse;
    const { user: adminUser } = auth;

    await ensureSchema();
    const sql = getSql();
    const action = String(body.action || 'updateConfig');

    if (action === 'updateConfig') {
      if (typeof body.geminiApiKey === 'string') {
        const trimmed = body.geminiApiKey.trim();
        if (trimmed === '__CLEAR__') {
          await setVaultConfigValue('gemini_api_key', '');
        } else if (trimmed.length >= 15) {
          await setVaultConfigValue('gemini_api_key', trimmed);
        }
      }
      if (typeof body.defaultModel === 'string' && body.defaultModel.trim()) {
        await setVaultConfigValue('default_gemini_model', body.defaultModel.trim().replace(/^models\//, ''));
      }
      if (body.registrationMode === 'OPEN' || body.registrationMode === 'INVITE_ONLY') {
        await setVaultConfigValue('registration_mode', body.registrationMode);
      }
      const nextCfg = await getServerGeminiConfig();
      return jsonResponse({
        ok: true,
        config: {
          serverAiReady: Boolean(nextCfg.apiKey),
          hasEnvKey: nextCfg.hasEnvKey,
          hasVaultKey: nextCfg.hasVaultKey,
          maskedVaultKey: nextCfg.maskedVaultKey,
          defaultModel: nextCfg.defaultModel,
          registrationMode: nextCfg.registrationMode,
        },
      });
    }

    if (action === 'createInvite') {
      const code = generateInviteCode();
      const label = String(body.label || 'Member Invite').trim().slice(0, 60);
      const maxUses = Math.max(1, Math.min(500, Number(body.maxUses || 5)));
      const dailyAiLimit = Math.max(0, Math.min(5000, Number(body.dailyAiLimit ?? 150)));
      const now = Date.now();
      await sql`
        INSERT INTO nki_invites (code, label, max_uses, used_count, daily_ai_limit, active, created_by, created_at)
        VALUES (${code}, ${label}, ${maxUses}, 0, ${dailyAiLimit}, TRUE, ${adminUser.id}, ${now})
      `;
      return jsonResponse({
        ok: true,
        invite: { code, label, maxUses, usedCount: 0, dailyAiLimit, active: true, createdAt: now },
      }, 201);
    }

    if (action === 'revokeInvite') {
      const code = String(body.code || '').trim().toUpperCase();
      if (!code) return jsonResponse({ error: 'Invite code required' }, 400);
      await sql`UPDATE nki_invites SET active = FALSE WHERE code = ${code}`;
      return jsonResponse({ ok: true, code });
    }

    if (action === 'updateUser') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      if (targetUserId === adminUser.id && body.status === 'REVOKED') {
        return jsonResponse({ error: 'Cannot revoke your own admin account' }, 400);
      }
      if (body.status === 'ACTIVE' || body.status === 'REVOKED') {
        // Bumping token_version immediately invalidates all active tokens across phones/web/TV!
        await sql`
          UPDATE nki_users
          SET status = ${body.status}, token_version = token_version + 1
          WHERE id = ${targetUserId}
        `;
      }
      if (typeof body.dailyAiLimit === 'number' && Number.isFinite(body.dailyAiLimit)) {
        const clamped = Math.max(0, Math.min(10000, Math.round(body.dailyAiLimit)));
        await sql`UPDATE nki_users SET daily_ai_limit = ${clamped} WHERE id = ${targetUserId}`;
      }
      return jsonResponse({ ok: true, userId: targetUserId });
    }

    if (action === 'addSource') {
      const name = String(body.name || '').trim().slice(0, 60);
      const sourceType = String(body.sourceType || 'PHIMAPI_JSON').trim().toUpperCase();
      const baseUrl = String(body.baseUrl || '').trim();
      const countryHint = String(body.countryHint || 'global').trim().slice(0, 32);
      const allowedTypes = new Set(['PHIMAPI_JSON', 'MACCMS_JSON', 'M3U_PLAYLIST', 'CUSTOM_JSON']);
      if (!name || !baseUrl) {
        return jsonResponse({ error: 'Source name and URL are required' }, 400);
      }
      if (!allowedTypes.has(sourceType)) {
        return jsonResponse({ error: 'Unsupported sourceType' }, 400);
      }
      if (!isSafeSourceUrl(baseUrl)) {
        return jsonResponse({ error: 'Source URL blocked by VN-Safe anti-porn / SSRF guard' }, 403);
      }
      const id = `src_${crypto.randomUUID().slice(0, 8)}`;
      const now = Date.now();
      await sql`
        INSERT INTO nki_sources (id, name, source_type, base_url, country_hint, enabled, priority, created_by, created_at)
        VALUES (${id}, ${name}, ${sourceType}, ${baseUrl}, ${countryHint}, TRUE, 50, ${adminUser.username}, ${now})
      `;
      return jsonResponse({
        ok: true,
        source: { id, name, sourceType, baseUrl, countryHint, enabled: true, priority: 50, createdAt: now },
      }, 201);
    }

    if (action === 'toggleSource') {
      const id = String(body.id || '').trim();
      if (!id) return jsonResponse({ error: 'Source id required' }, 400);
      const enabled = Boolean(body.enabled);
      await sql`UPDATE nki_sources SET enabled = ${enabled} WHERE id = ${id}`;
      return jsonResponse({ ok: true, id, enabled });
    }

    if (action === 'deleteSource') {
      const id = String(body.id || '').trim();
      if (!id) return jsonResponse({ error: 'Source id required' }, 400);
      await sql`DELETE FROM nki_sources WHERE id = ${id}`;
      return jsonResponse({ ok: true, id });
    }

    if (action === 'updateBlockedTerms') {
      const rawTerms = Array.isArray(body.terms)
        ? body.terms
        : String(body.termsText || '').split(/[,\n]+/);
      const saved = await setCustomBlockedTerms(rawTerms);
      return jsonResponse({ ok: true, customBlockedTerms: saved });
    }

    if (action === 'runCrawlNow') {
      const report = await runCatalogCrawlAndHealthCheck({
        triggerType: 'ADMIN_MANUAL',
      });
      return jsonResponse({ ok: true, report });
    }

    return jsonResponse({ error: `Unknown action: ${action}` }, 400);
  } catch (err) {
    console.error('[api/admin/config POST] error:', err);
    return jsonResponse({ error: err?.message || 'Admin update failed' }, 500);
  }
}
