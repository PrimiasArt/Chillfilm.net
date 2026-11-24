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
  hashPassword,
  jsonResponse,
  optionsResponse,
  readJsonBody,
  requireAdmin,
  sanitizeUserPublic,
} from '../_lib/auth.js';
import { isSafeSourceUrl } from '../_lib/safety.js';
import { getCustomBlockedTerms, invalidateDynamicSourcesCache, listDynamicSources, setCustomBlockedTerms } from '../_lib/sources.js';
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

    const [
      aiCfg,
      invites,
      users,
      usageToday,
      watchCounts,
      myListCounts,
      poolStats,
      catalogStats,
      sources,
      customBlockedTerms,
      crawlRuns,
    ] = await Promise.all([
      getServerGeminiConfig({ bypassCache: true }),
      sql`SELECT code, label, max_uses, used_count, daily_ai_limit, active, created_at FROM nki_invites ORDER BY created_at DESC LIMIT 80`,
      sql`SELECT id, username, display_name, role, status, daily_ai_limit, token_version, created_at, last_seen_at FROM nki_users ORDER BY CASE WHEN role = 'ADMIN' THEN 0 ELSE 1 END, created_at ASC LIMIT 300`,
      sql`SELECT user_id, requests_count, cues_count, audio_chunks FROM nki_ai_usage WHERE day_key = ${day}`,
      sql`SELECT user_id, COUNT(*)::int AS cnt FROM nki_watch_progress GROUP BY user_id`.catch(() => []),
      sql`SELECT user_id, COUNT(*)::int AS cnt FROM nki_my_list GROUP BY user_id`.catch(() => []),
      sql`SELECT COUNT(*)::int AS total_subs FROM nki_subtitle_pool`,
      sql`SELECT COUNT(*)::int AS total_items FROM nki_catalog_items`,
      listDynamicSources({ onlyEnabled: false, bypassCache: true }),
      getCustomBlockedTerms(),
      listRecentCrawlRuns(8),
    ]);

    const usageMap = new Map(usageToday.map((u) => [u.user_id, u]));
    const watchMap = new Map((watchCounts || []).map((r) => [r.user_id, Number(r.cnt || 0)]));
    const listMap = new Map((myListCounts || []).map((r) => [r.user_id, Number(r.cnt || 0)]));
    const healthyCount = sources.filter((s) => s.enabled && s.lastStatus === 'ONLINE').length;

    const enrichedUsers = users.map((u) => ({
      ...sanitizeUserPublic(u, usageMap.get(u.id) || { requests_count: 0, cues_count: 0, audio_chunks: 0 }),
      watchCount: watchMap.get(u.id) || 0,
      myListCount: listMap.get(u.id) || 0,
    }));

    let todayAiRequests = 0;
    let todayAudioChunks = 0;
    let todayCues = 0;
    for (const u of usageToday) {
      todayAiRequests += Number(u.requests_count || 0);
      todayAudioChunks += Number(u.audio_chunks || 0);
      todayCues += Number(u.cues_count || 0);
    }

    const accountStats = {
      totalUsers: enrichedUsers.length,
      activeUsers: enrichedUsers.filter((u) => u.status === 'ACTIVE').length,
      revokedUsers: enrichedUsers.filter((u) => u.status !== 'ACTIVE').length,
      adminCount: enrichedUsers.filter((u) => u.role === 'ADMIN').length,
      todayAiRequests,
      todayAudioChunks,
      todayCues,
    };

    return jsonResponse({
      ok: true,
      accountStats,
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
      users: enrichedUsers,
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

    if (action === 'deleteInvite') {
      const code = String(body.code || '').trim().toUpperCase();
      if (!code) return jsonResponse({ error: 'Invite code required' }, 400);
      await sql`DELETE FROM nki_invites WHERE code = ${code}`;
      return jsonResponse({ ok: true, code });
    }

    if (action === 'createUser') {
      const username = String(body.username || '').trim().toLowerCase();
      const displayName = String(body.displayName || username).trim().slice(0, 50) || username;
      const password = String(body.password || '');
      const role = body.role === 'ADMIN' ? 'ADMIN' : 'MEMBER';
      const dailyAiLimit = Math.max(0, Math.min(10000, Math.round(Number(body.dailyAiLimit ?? (role === 'ADMIN' ? 0 : 150)))));

      if (!/^[a-z0-9_.-]{3,32}$/.test(username)) {
        return jsonResponse({ error: 'Tên đăng nhập phải từ 3–32 ký tự (chữ thường, số, _, ., -)' }, 400);
      }
      if (password.length < 6 || password.length > 128) {
        return jsonResponse({ error: 'Mật khẩu phải từ 6 ký tự trở lên' }, 400);
      }
      const existing = await sql`SELECT id FROM nki_users WHERE username = ${username} LIMIT 1`;
      if (existing.length) {
        return jsonResponse({ error: `Tài khoản @${username} đã tồn tại` }, 409);
      }
      const id = `usr_${crypto.randomUUID().replace(/-/g, '').slice(0, 16)}`;
      const now = Date.now();
      const pwHash = hashPassword(password);
      await sql`
        INSERT INTO nki_users (id, username, display_name, password_hash, role, status, daily_ai_limit, token_version, created_at, last_seen_at)
        VALUES (${id}, ${username}, ${displayName}, ${pwHash}, ${role}, 'ACTIVE', ${dailyAiLimit}, 1, ${now}, ${now})
      `;
      return jsonResponse({
        ok: true,
        user: sanitizeUserPublic(
          { id, username, display_name: displayName, role, status: 'ACTIVE', daily_ai_limit: dailyAiLimit, created_at: now, last_seen_at: now },
          { requests_count: 0, cues_count: 0, audio_chunks: 0 }
        ),
      }, 201);
    }

    if (action === 'updateUser') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      const targetRows = await sql`SELECT id, username, role, status FROM nki_users WHERE id = ${targetUserId} LIMIT 1`;
      if (!targetRows.length) return jsonResponse({ error: 'Không tìm thấy tài khoản' }, 404);
      const target = targetRows[0];

      if (targetUserId === adminUser.id && body.status === 'REVOKED') {
        return jsonResponse({ error: 'Không thể tự khóa tài khoản Admin đang đăng nhập' }, 400);
      }
      if (targetUserId === adminUser.id && body.role === 'MEMBER') {
        return jsonResponse({ error: 'Không thể tự hạ quyền Admin của chính mình' }, 400);
      }
      if (target.role === 'ADMIN' && (body.role === 'MEMBER' || body.status === 'REVOKED')) {
        const admins = await sql`SELECT COUNT(*)::int AS cnt FROM nki_users WHERE role = 'ADMIN' AND status = 'ACTIVE'`;
        if (Number(admins?.[0]?.cnt || 0) <= 1) {
          return jsonResponse({ error: 'Phải giữ lại ít nhất 1 tài khoản ADMIN đang hoạt động' }, 400);
        }
      }

      if (typeof body.displayName === 'string' && body.displayName.trim()) {
        const cleanName = body.displayName.trim().slice(0, 50);
        await sql`UPDATE nki_users SET display_name = ${cleanName} WHERE id = ${targetUserId}`;
      }
      if (body.role === 'ADMIN' || body.role === 'MEMBER') {
        await sql`
          UPDATE nki_users
          SET role = ${body.role}, token_version = token_version + 1
          WHERE id = ${targetUserId}
        `;
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

    if (action === 'resetUserPassword') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      const targetRows = await sql`SELECT id, username FROM nki_users WHERE id = ${targetUserId} LIMIT 1`;
      if (!targetRows.length) return jsonResponse({ error: 'Không tìm thấy tài khoản' }, 404);

      const provided = typeof body.newPassword === 'string' ? body.newPassword.trim() : '';
      let nextPassword = provided;
      if (!nextPassword) {
        const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
        const bytes = crypto.randomBytes(10);
        nextPassword = Array.from(bytes, (b) => chars[b % chars.length]).join('');
      }
      if (nextPassword.length < 6 || nextPassword.length > 128) {
        return jsonResponse({ error: 'Mật khẩu mới phải từ 6 ký tự trở lên' }, 400);
      }
      const pwHash = hashPassword(nextPassword);
      await sql`
        UPDATE nki_users
        SET password_hash = ${pwHash}, token_version = token_version + 1
        WHERE id = ${targetUserId}
      `;
      return jsonResponse({
        ok: true,
        userId: targetUserId,
        username: targetRows[0].username,
        tempPassword: nextPassword,
      });
    }

    if (action === 'forceLogoutUser') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      if (targetUserId === adminUser.id) {
        return jsonResponse({ error: 'Dùng nút Đăng xuất trong hồ sơ để thoát phiên của chính bạn' }, 400);
      }
      await sql`UPDATE nki_users SET token_version = token_version + 1 WHERE id = ${targetUserId}`;
      return jsonResponse({ ok: true, userId: targetUserId });
    }

    if (action === 'resetUserQuota') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      const day = todayDayKey();
      await sql`DELETE FROM nki_ai_usage WHERE user_id = ${targetUserId} AND day_key = ${day}`;
      return jsonResponse({ ok: true, userId: targetUserId, dayKey: day });
    }

    if (action === 'deleteUser') {
      const targetUserId = String(body.userId || '').trim();
      if (!targetUserId) return jsonResponse({ error: 'userId required' }, 400);
      if (targetUserId === adminUser.id) {
        return jsonResponse({ error: 'Không thể tự xóa tài khoản Admin đang đăng nhập' }, 400);
      }
      const targetRows = await sql`SELECT id, username, role FROM nki_users WHERE id = ${targetUserId} LIMIT 1`;
      if (!targetRows.length) return jsonResponse({ error: 'Không tìm thấy tài khoản' }, 404);
      if (targetRows[0].role === 'ADMIN') {
        return jsonResponse({ error: 'Hãy hạ quyền về MEMBER trước khi xóa tài khoản ADMIN' }, 400);
      }
      await Promise.all([
        sql`DELETE FROM nki_ai_usage WHERE user_id = ${targetUserId}`.catch(() => {}),
        sql`DELETE FROM nki_watch_progress WHERE user_id = ${targetUserId}`.catch(() => {}),
        sql`DELETE FROM nki_user_prefs WHERE user_id = ${targetUserId}`.catch(() => {}),
        sql`DELETE FROM nki_my_list WHERE user_id = ${targetUserId}`.catch(() => {}),
      ]);
      await sql`DELETE FROM nki_users WHERE id = ${targetUserId}`;
      return jsonResponse({ ok: true, deletedUserId: targetUserId, username: targetRows[0].username });
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
      invalidateDynamicSourcesCache();
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
      invalidateDynamicSourcesCache();
      return jsonResponse({ ok: true, id, enabled });
    }

    if (action === 'deleteSource') {
      const id = String(body.id || '').trim();
      if (!id) return jsonResponse({ error: 'Source id required' }, 400);
      await sql`DELETE FROM nki_sources WHERE id = ${id}`;
      invalidateDynamicSourcesCache();
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
