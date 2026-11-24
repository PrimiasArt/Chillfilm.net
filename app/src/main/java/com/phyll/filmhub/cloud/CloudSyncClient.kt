package com.phyll.filmhub.cloud

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit

data class CloudSession(
    val token: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String = "MEMBER",
    val dailyAiLimit: Int = 150,
    val todayAiRequests: Int = 0,
    val serverAiReady: Boolean = false,
    val defaultModel: String = "gemini-3-flash-preview"
)

data class CloudHealthInfo(
    val ok: Boolean = false,
    val serverAiReady: Boolean = false,
    val defaultModel: String = "gemini-3-flash-preview",
    val hasAdmin: Boolean = true,
    val registrationMode: String = "INVITE_ONLY"
)

data class CloudInvite(
    val code: String,
    val label: String,
    val maxUses: Int,
    val usedCount: Int,
    val dailyAiLimit: Int,
    val active: Boolean
)

data class CloudMemberSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val status: String,
    val dailyAiLimit: Int,
    val todayRequests: Int,
    val todayAudioChunks: Int = 0,
    val watchCount: Int = 0,
    val myListCount: Int = 0,
    val lastSeenAt: Long = 0L
)

data class AdminVaultSnapshot(
    val serverAiReady: Boolean = false,
    val hasEnvKey: Boolean = false,
    val hasVaultKey: Boolean = false,
    val maskedVaultKey: String = "",
    val defaultModel: String = "gemini-3-flash-preview",
    val registrationMode: String = "INVITE_ONLY",
    val cachedSubtitlesCount: Int = 0,
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val revokedUsers: Int = 0,
    val todayAiRequests: Int = 0,
    val invites: List<CloudInvite> = emptyList(),
    val users: List<CloudMemberSummary> = emptyList()
)

data class CloudSubtitlePayload(
    val movieSlug: String,
    val episodeKey: String,
    val primaryLangCode: String,
    val secondaryLangCode: String,
    val sourceLangCode: String,
    val sourcePlatform: String,
    val isAiAudio: Boolean,
    val isFullyTranslated: Boolean,
    val completedAudioSlots: Set<Int>,
    val translatedRanges: List<Pair<Long, Long>> = emptyList(),
    val audioTranslatedUntilMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val sourceCues: List<SubtitleCue>,
    val primaryCues: List<SubtitleCue>,
    val secondaryCues: List<SubtitleCue>,
    val savedAtMs: Long
)

/* ==================== WATCH PARTY 2.0 MODELS ==================== */
class PartyPasscodeRequiredException(message: String) : Exception(message)

data class WatchPartyMovieMeta(
    val slug: String,
    val source: String = "phimapi",
    val sourceId: String = "",
    val name: String,
    val poster: String = ""
)

data class WatchPartyEpisodeMeta(
    val name: String,
    val slug: String = "",
    val linkM3u8: String
)

data class WatchPartyMember(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val status: String = "playing",
    val posSec: Int = 0
)

data class WatchPartyQueueItem(
    val id: String,
    val movie: WatchPartyMovieMeta,
    val episode: WatchPartyEpisodeMeta,
    val addedBy: String = "",
    val addedById: String = "",
    val votes: List<String> = emptyList(),
    val addedAt: Long = 0L
)

data class WatchPartyReaction(
    val id: String,
    val emoji: String,
    val senderName: String = "",
    val createdAt: Long = 0L
)

data class WatchPartyMessage(
    val id: Long,
    val senderName: String,
    val isHost: Boolean = false,
    val text: String,
    val createdAt: Long = 0L
)

data class WatchPartyLobbyRoom(
    val roomCode: String,
    val roomTitle: String,
    val hostName: String,
    val movie: WatchPartyMovieMeta,
    val episodeName: String = "",
    val viewerCount: Int = 1,
    val isPlaying: Boolean = true,
    val controlMode: String = "HOST_ONLY",
    val hasPasscode: Boolean = false,
    val queueCount: Int = 0,
    val createdAt: Long = 0L
)

data class WatchPartySnapshot(
    val roomCode: String,
    val roomTitle: String,
    val visibility: String = "PRIVATE",
    val controlMode: String = "HOST_ONLY",
    val hasPasscode: Boolean = false,
    val hostName: String = "",
    val hostOnline: Boolean = true,
    val hostInactive: Boolean = false,
    val movie: WatchPartyMovieMeta,
    val episode: WatchPartyEpisodeMeta,
    val isPlaying: Boolean = true,
    val positionSec: Double = 0.0,
    val playbackRate: Float = 1.0f,
    val stateUpdatedAt: Long = 0L,
    val lastActionText: String = "",
    val lastActionAt: Long = 0L,
    val serverTime: Long = 0L,
    val members: List<WatchPartyMember> = emptyList(),
    val viewerCount: Int = 1,
    val queue: List<WatchPartyQueueItem> = emptyList(),
    val reactions: List<WatchPartyReaction> = emptyList(),
    val messages: List<WatchPartyMessage> = emptyList(),
    val rttMs: Long = 0L
)

data class WatchPartyJoinOrCreateResult(
    val roomCode: String,
    val hostToken: String = "",
    val memberId: String = "",
    val isHost: Boolean = false,
    val party: WatchPartySnapshot
)

data class WatchPartyControlResult(
    val serverTime: Long,
    val stateUpdatedAt: Long,
    val isPlaying: Boolean,
    val positionSec: Double,
    val lastActionText: String
)

data class CloudCatalogRow(
    val id: String,
    val title: String,
    val movies: List<MovieItem>
)

data class CloudCatalogFeedResult(
    val heroAndTrending: List<MovieItem>,
    val extraSourceRows: List<CloudCatalogRow>
)

data class CloudMyListItem(
    val itemKey: String,
    val source: String = "phimapi",
    val sourceId: String = "",
    val slug: String,
    val name: String,
    val originName: String = "",
    val poster: String = "",
    val year: String = "",
    val quality: String = "HD",
    val latestEpisode: String = "",
    val hasNewEpisode: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

data class CloudSyncPullResult(
    val watchProgress: List<WatchProgress>,
    val myList: List<CloudMyListItem> = emptyList(),
    val serverTime: Long
)

/**
 * Centralized NKI Cloud Sync & AI Gateway client backed by All-in-One Vercel Functions
 * (Neon Postgres + Private Vercel Blob + Server-Side Gemini API Key Vault).
 */
object CloudSyncClient {

    const val DEFAULT_BASE_URL = "https://chillfilm.vercel.app"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val COMBINING_MARKS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val PROVIDER_PREFIX_REGEX = Regex("^(modern|ophim|kkphim|phimapi|curated|custom)_+", RegexOption.IGNORE_CASE)
    private val NON_SLUG_CHARS_REGEX = Regex("[^a-z0-9._-]+")
    private val NON_EP_CHARS_REGEX = Regex("[^a-z0-9]+")
    private val TAP_EP_REGEX = Regex("(?:tap|ep|episode)\\s*[-_:]?\\s*0*(\\d+)([a-z]?)")
    private val NUM_ONLY_REGEX = Regex("^0*(\\d+)([a-z]?)$")

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    @Volatile
    var activeToken: String = ""

    @Volatile
    var serverAiReady: Boolean = false

    @Volatile
    var defaultModel: String = "gemini-2.5-flash"

    @Volatile
    var activeLangCode: String = "en"

    @Volatile
    var activeMovieSlug: String = ""

    fun applySession(session: CloudSession?) {
        if (session == null || session.token.isBlank()) {
            activeToken = ""
            serverAiReady = false
        } else {
            activeToken = session.token.trim()
            serverAiReady = session.serverAiReady
            if (session.defaultModel.isNotBlank()) {
                defaultModel = session.defaultModel
            }
        }
    }

    fun isSignedIn(): Boolean = activeToken.isNotBlank()

    fun canUseServerAi(): Boolean = activeToken.isNotBlank() && serverAiReady

    /**
     * Canonical cross-platform movie slug normalizer (matches Vercel Backend & Web App exactly).
     */
    fun normalizeMovieSlug(raw: String): String {
        val cleaned = raw.trim()
            .lowercase()
            .replace(PROVIDER_PREFIX_REGEX, "")
            .replace(NON_SLUG_CHARS_REGEX, "-")
            .trim('-')
        return cleaned.ifEmpty { "unknown" }.take(120)
    }

    /**
     * Canonical cross-platform episode normalizer (matches Vercel Backend & Web App exactly).
     */
    fun normalizeEpisodeKey(rawNameOrSlug: String): String {
        val decomposed = Normalizer.normalize(rawNameOrSlug, Normalizer.Form.NFD)
        val ascii = COMBINING_MARKS_REGEX.replace(decomposed, "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .trim()
            .lowercase()
        if (ascii.isEmpty() || ascii == "full" || ascii == "full hd" || ascii == "hd") {
            return "full"
        }
        TAP_EP_REGEX.find(ascii)?.let { m ->
            return "tap-${m.groupValues[1]}${m.groupValues[2]}"
        }
        NUM_ONLY_REGEX.find(ascii)?.let { m ->
            return "tap-${m.groupValues[1]}${m.groupValues[2]}"
        }
        return ascii.replace(NON_EP_CHARS_REGEX, "-").trim('-').ifEmpty { "full" }.take(64)
    }

    suspend fun fetchHealth(): CloudHealthInfo = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/health").get().build()
            client.newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val ready = obj.optBoolean("serverAiReady", false)
                if (resp.isSuccessful && obj.optBoolean("ok", false)) {
                    serverAiReady = ready
                    val defModel = obj.optString("defaultModel", defaultModel)
                    if (defModel.isNotBlank()) defaultModel = defModel
                }
                CloudHealthInfo(
                    ok = resp.isSuccessful && obj.optBoolean("ok", false),
                    serverAiReady = ready,
                    defaultModel = obj.optString("defaultModel", "gemini-3-flash-preview"),
                    hasAdmin = obj.optBoolean("hasAdmin", true),
                    registrationMode = obj.optString("registrationMode", "INVITE_ONLY")
                )
            }
        }.getOrDefault(CloudHealthInfo())
    }

    suspend fun login(username: String, password: String): Result<CloudSession> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("username", username.trim())
                .put("password", password)
            val obj = postJson("/api/auth/login", payload, requireAuth = false)
            parseSessionResponse(obj)
        }
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String = "",
        inviteCode: String = ""
    ): Result<CloudSession> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("displayName", displayName.trim())
                .put("inviteCode", inviteCode.trim().uppercase())
            val obj = postJson("/api/auth/register", payload, requireAuth = false)
            parseSessionResponse(obj)
        }
    }

    suspend fun refreshMe(): Result<CloudSession> = withContext(Dispatchers.IO) {
        runCatching {
            val token = activeToken
            if (token.isBlank()) error("Not signed in")
            val obj = getJson("/api/auth/me")
            val u = obj.optJSONObject("user") ?: error("Invalid profile response")
            val usage = u.optJSONObject("todayUsage")
            CloudSession(
                token = token,
                userId = u.optString("id"),
                username = u.optString("username"),
                displayName = u.optString("displayName", u.optString("username")),
                role = u.optString("role", "MEMBER"),
                dailyAiLimit = u.optInt("dailyAiLimit", 150),
                todayAiRequests = usage?.optInt("requests", 0) ?: 0,
                serverAiReady = obj.optBoolean("serverAiReady", false),
                defaultModel = obj.optString("defaultModel", "gemini-3-flash-preview")
            ).also { applySession(it) }
        }
    }

    suspend fun updateProfile(
        displayName: String? = null,
        currentPassword: String? = null,
        newPassword: String? = null,
        revokeOtherSessions: Boolean = false
    ): Result<CloudSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (activeToken.isBlank()) error("Bạn chưa đăng nhập NKI Cloud")
            val payload = JSONObject()
            if (!displayName.isNullOrBlank()) payload.put("displayName", displayName.trim())
            if (!newPassword.isNullOrBlank()) {
                payload.put("currentPassword", currentPassword.orEmpty())
                payload.put("newPassword", newPassword)
            }
            if (revokeOtherSessions) payload.put("revokeOtherSessions", true)
            val obj = postJson("/api/auth/profile", payload, requireAuth = true)
            val u = obj.optJSONObject("user") ?: error("Invalid profile response")
            val usage = u.optJSONObject("todayUsage")
            val nextToken = obj.optString("token", activeToken).ifBlank { activeToken }
            CloudSession(
                token = nextToken,
                userId = u.optString("id"),
                username = u.optString("username"),
                displayName = u.optString("displayName", u.optString("username")),
                role = u.optString("role", "MEMBER"),
                dailyAiLimit = u.optInt("dailyAiLimit", 150),
                todayAiRequests = usage?.optInt("requests", 0) ?: 0,
                serverAiReady = obj.optBoolean("serverAiReady", serverAiReady),
                defaultModel = obj.optString("defaultModel", "gemini-2.5-flash")
            ).also { applySession(it) }
        }
    }

    suspend fun fetchAdminConfig(): Result<AdminVaultSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = getJson("/api/admin/config")
            val cfg = obj.optJSONObject("config") ?: JSONObject()
            val invArr = obj.optJSONArray("invites") ?: JSONArray()
            val invites = ArrayList<CloudInvite>(invArr.length())
            for (i in 0 until invArr.length()) {
                val item = invArr.optJSONObject(i) ?: continue
                invites.add(
                    CloudInvite(
                        code = item.optString("code"),
                        label = item.optString("label"),
                        maxUses = item.optInt("maxUses", 5),
                        usedCount = item.optInt("usedCount", 0),
                        dailyAiLimit = item.optInt("dailyAiLimit", 150),
                        active = item.optBoolean("active", true)
                    )
                )
            }
            val usrArr = obj.optJSONArray("users") ?: JSONArray()
            val users = ArrayList<CloudMemberSummary>(usrArr.length())
            for (i in 0 until usrArr.length()) {
                val u = usrArr.optJSONObject(i) ?: continue
                val usage = u.optJSONObject("todayUsage")
                users.add(
                    CloudMemberSummary(
                        id = u.optString("id"),
                        username = u.optString("username"),
                        displayName = u.optString("displayName"),
                        role = u.optString("role", "MEMBER"),
                        status = u.optString("status", "ACTIVE"),
                        dailyAiLimit = u.optInt("dailyAiLimit", 150),
                        todayRequests = usage?.optInt("requests", 0) ?: 0,
                        todayAudioChunks = usage?.optInt("audioChunks", 0) ?: 0,
                        watchCount = u.optInt("watchCount", 0),
                        myListCount = u.optInt("myListCount", 0),
                        lastSeenAt = u.optLong("lastSeenAt", 0L)
                    )
                )
            }
            val stats = obj.optJSONObject("accountStats")
            AdminVaultSnapshot(
                serverAiReady = cfg.optBoolean("serverAiReady", false),
                hasEnvKey = cfg.optBoolean("hasEnvKey", false),
                hasVaultKey = cfg.optBoolean("hasVaultKey", false),
                maskedVaultKey = cfg.optString("maskedVaultKey", ""),
                defaultModel = cfg.optString("defaultModel", "gemini-3-flash-preview"),
                registrationMode = cfg.optString("registrationMode", "INVITE_ONLY"),
                cachedSubtitlesCount = cfg.optInt("cachedSubtitlesCount", 0),
                totalUsers = stats?.optInt("totalUsers", users.size) ?: users.size,
                activeUsers = stats?.optInt("activeUsers", users.count { it.status == "ACTIVE" }) ?: users.count { it.status == "ACTIVE" },
                revokedUsers = stats?.optInt("revokedUsers", users.count { it.status != "ACTIVE" }) ?: users.count { it.status != "ACTIVE" },
                todayAiRequests = stats?.optInt("todayAiRequests", 0) ?: 0,
                invites = invites,
                users = users
            )
        }
    }

    suspend fun adminUpdateConfig(
        geminiApiKey: String? = null,
        defaultModel: String? = null,
        registrationMode: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "updateConfig")
            if (!geminiApiKey.isNullOrBlank()) body.put("geminiApiKey", geminiApiKey.trim())
            if (!defaultModel.isNullOrBlank()) body.put("defaultModel", defaultModel.trim())
            if (!registrationMode.isNullOrBlank()) body.put("registrationMode", registrationMode.trim())
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminCreateInvite(
        label: String = "NKI Member",
        maxUses: Int = 5,
        dailyAiLimit: Int = 150
    ): Result<CloudInvite> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "createInvite")
                .put("label", label)
                .put("maxUses", maxUses)
                .put("dailyAiLimit", dailyAiLimit)
            val res = postJson("/api/admin/config", body, requireAuth = true)
            val inv = res.optJSONObject("invite") ?: error("Missing invite in response")
            CloudInvite(
                code = inv.optString("code"),
                label = inv.optString("label"),
                maxUses = inv.optInt("maxUses", maxUses),
                usedCount = inv.optInt("usedCount", 0),
                dailyAiLimit = inv.optInt("dailyAiLimit", dailyAiLimit),
                active = inv.optBoolean("active", true)
            )
        }
    }

    suspend fun adminRevokeInvite(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "revokeInvite").put("code", code.trim().uppercase())
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminUpdateUserStatus(userId: String, nextStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "updateUser")
                .put("userId", userId)
                .put("status", nextStatus)
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminCreateUser(
        username: String,
        displayName: String,
        password: String,
        role: String = "MEMBER",
        dailyAiLimit: Int = 150
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "createUser")
                .put("username", username.trim().lowercase())
                .put("displayName", displayName.trim().ifBlank { username.trim() })
                .put("password", password)
                .put("role", if (role == "ADMIN") "ADMIN" else "MEMBER")
                .put("dailyAiLimit", dailyAiLimit.coerceIn(0, 10000))
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminUpdateUserDetails(
        userId: String,
        displayName: String? = null,
        role: String? = null,
        dailyAiLimit: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "updateUser")
                .put("userId", userId)
            if (!displayName.isNullOrBlank()) body.put("displayName", displayName.trim())
            if (role == "ADMIN" || role == "MEMBER") body.put("role", role)
            if (dailyAiLimit != null) body.put("dailyAiLimit", dailyAiLimit.coerceIn(0, 10000))
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminResetUserPassword(userId: String, newPassword: String = ""): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "resetUserPassword")
                .put("userId", userId)
            if (newPassword.isNotBlank()) body.put("newPassword", newPassword.trim())
            val res = postJson("/api/admin/config", body, requireAuth = true)
            res.optString("tempPassword")
        }
    }

    suspend fun adminResetUserQuota(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "resetUserQuota")
                .put("userId", userId)
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminForceLogoutUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "forceLogoutUser")
                .put("userId", userId)
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    suspend fun adminDeleteUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "deleteUser")
                .put("userId", userId)
            postJson("/api/admin/config", body, requireAuth = true)
            Unit
        }
    }

    /**
     * Fetches LIVE generative models from Google Gemini API via /api/ai/models
     * using the centralized server GEMINI_API_KEY (no local API key needed on phone/TV!).
     */
    suspend fun fetchLiveModels(personalApiKey: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = personalApiKey.trim()
            val obj = if (cleanKey.length >= 15) {
                postJson("/api/ai/models", JSONObject().put("apiKey", cleanKey), requireAuth = isSignedIn())
            } else {
                if (!isSignedIn()) error("Sign in to NKI Cloud to load live models")
                getJson("/api/ai/models")
            }
            val arr = obj.optJSONArray("models") ?: JSONArray()
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val id = m.optString("id").trim()
                if (id.isNotEmpty()) list.add(id)
            }
            val serverDef = obj.optString("defaultModel", "").trim()
            if (serverDef.isNotEmpty()) defaultModel = serverDef
            list
        }
    }

    /**
     * Fetches the auto-updating multi-source live movie feed (KKPhim v3 + NguonC hot signal +
     * Internet Archive World Cinema + ALL Admin dynamic sources, 100% VN-Safe filtered!).
     */
    suspend fun fetchLiveCatalogFeedFull(): Result<CloudCatalogFeedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/catalog/feed").get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Catalog feed HTTP ${resp.code}")
                val root = JSONObject(raw)
                val out = LinkedHashMap<String, MovieItem>()
                parseCloudMovieArray(root.optJSONArray("heroMovies")).forEach { out[it.id] = it }
                val rows = root.optJSONArray("rows")
                val extraRows = ArrayList<CloudCatalogRow>()
                if (rows != null) {
                    for (rIdx in 0 until rows.length()) {
                        val rowObj = rows.optJSONObject(rIdx) ?: continue
                        val rowId = rowObj.optString("id")
                        val rowMovies = parseCloudMovieArray(rowObj.optJSONArray("movies"))
                        if (rIdx == 0) {
                            rowMovies.forEach { if (!out.containsKey(it.id)) out[it.id] = it }
                        } else if (rowId.startsWith("src_") || rowId in setOf("archive", "an-do", "phap")) {
                            val title = rowObj.optString("customTitle").ifBlank { rowId }
                            if (rowMovies.isNotEmpty()) {
                                extraRows.add(CloudCatalogRow(id = rowId, title = title, movies = rowMovies))
                            }
                        }
                    }
                }
                CloudCatalogFeedResult(
                    heroAndTrending = out.values.toList(),
                    extraSourceRows = extraRows
                )
            }
        }
    }

    suspend fun fetchLiveHeroAndTrending(): Result<List<MovieItem>> =
        fetchLiveCatalogFeedFull().map { it.heroAndTrending }

    /**
     * Searches across ALL server-side global sources (KKPhim + Internet Archive + Admin dynamic sources)
     * through the Hard VN-Safe anti-porn filter.
     */
    suspend fun searchGlobalCatalog(query: String): Result<List<MovieItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = query.trim()
            if (q.isEmpty()) return@runCatching emptyList()
            val req = Request.Builder().url("$baseUrl/api/catalog/browse?q=${enc(q)}").get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Browse HTTP ${resp.code}")
                val root = JSONObject(raw)
                parseCloudMovieArray(root.optJSONArray("items"))
            }
        }
    }

    /**
     * Resolves playable streams & subtitles for multi-source cloud items (Internet Archive, MacCMS,
     * custom PhimAPI sources) via `/api/catalog/detail`.
     */
    suspend fun resolveCloudDetail(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        runCatching {
            val source = movie.providerId.removePrefix("cloud_").ifBlank { "phimapi" }
            val parts = (movie.detailLookupId ?: movie.id).split("|", limit = 2)
            val slug = parts[0].removePrefix("modern_")
            val srcId = if (parts.size > 1) parts[1] else ""
            val url = buildString {
                append("$baseUrl/api/catalog/detail?source=${enc(source)}&slug=${enc(slug)}")
                if (srcId.isNotBlank()) append("&sourceId=${enc(srcId)}")
            }
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@runCatching movie
                val d = JSONObject(raw).optJSONObject("detail") ?: return@runCatching movie
                val srvArr = d.optJSONArray("servers") ?: JSONArray()
                val servers = ArrayList<ServerGroup>()
                for (i in 0 until srvArr.length()) {
                    val sObj = srvArr.optJSONObject(i) ?: continue
                    val epArr = sObj.optJSONArray("episodes") ?: continue
                    val eps = ArrayList<Episode>()
                    for (j in 0 until epArr.length()) {
                        val eObj = epArr.optJSONObject(j) ?: continue
                        val link = eObj.optString("link_m3u8").trim()
                        if (link.isEmpty()) continue
                        val subsArr = eObj.optJSONArray("subtitles")
                        val extSubs = ArrayList<ExternalSubtitleRef>()
                        if (subsArr != null) {
                            for (k in 0 until subsArr.length()) {
                                val subObj = subsArr.optJSONObject(k) ?: continue
                                val subUrl = subObj.optString("url").trim()
                                if (subUrl.isNotEmpty()) {
                                    extSubs.add(
                                        ExternalSubtitleRef(
                                            id = subObj.optString("id", "cloud_sub_$k"),
                                            label = subObj.optString("label", "Subtitle"),
                                            languageCode = subObj.optString("langCode", "en"),
                                            urlOrContent = subUrl,
                                            sourcePlatform = movie.providerName
                                        )
                                    )
                                }
                            }
                        }
                        eps.add(
                            Episode(
                                id = "${slug}_${i}_$j",
                                name = eObj.optString("name", "Full"),
                                streamUrl = link,
                                subtitles = extSubs
                            )
                        )
                    }
                    if (eps.isNotEmpty()) {
                        servers.add(ServerGroup(sObj.optString("server_name", movie.providerName), eps))
                    }
                }
                val prefLang = activeLangCode.lowercase()
                val rawName = d.optString("name").trim()
                val nameVi = d.optString("nameVi").trim()
                val nameEn = d.optString("nameEn").trim()
                val localizedTitle = when {
                    prefLang == "vi" && nameVi.isNotEmpty() -> nameVi
                    prefLang != "zh" && nameEn.isNotEmpty() -> nameEn
                    rawName.isNotEmpty() -> rawName
                    else -> movie.title
                }
                val contentVi = d.optString("contentVi").trim()
                val contentEn = d.optString("contentEn").trim()
                val localizedDesc = when {
                    prefLang == "vi" && contentVi.isNotEmpty() -> contentVi
                    prefLang != "zh" && contentEn.isNotEmpty() -> contentEn
                    else -> d.optString("content").trim()
                }.ifBlank { movie.description }

                movie.copy(
                    title = localizedTitle,
                    originalTitle = d.optString("origin_name").ifBlank { movie.originalTitle },
                    description = localizedDesc,
                    duration = d.optString("time").ifBlank { movie.duration },
                    imdbId = d.optString("imdbId").ifBlank { movie.imdbId },
                    servers = servers
                )
            }
        }.getOrDefault(movie)
    }

    private fun parseCloudMovieArray(arr: JSONArray?): List<MovieItem> {
        if (arr == null || arr.length() == 0) return emptyList()
        val list = ArrayList<MovieItem>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val slug = m.optString("slug").trim()
            val rawName = m.optString("name").trim()
            val nameVi = m.optString("nameVi").trim()
            val nameEn = m.optString("nameEn").trim()
            val prefLang = activeLangCode.lowercase()
            val name = when {
                prefLang == "vi" && nameVi.isNotEmpty() -> nameVi
                prefLang != "zh" && nameEn.isNotEmpty() -> nameEn
                else -> rawName
            }.ifBlank { rawName }
            if (slug.isEmpty() || name.isEmpty()) continue
            val src = m.optString("source", "phimapi").trim().lowercase()
            val srcId = m.optString("sourceId", "").trim()
            val srcName = m.optString("sourceName", "NKI Live Cinema").trim()
            val streamUrl = m.optString("streamUrl", "").trim()

            val genresArr = m.optJSONArray("genres")
            val genres = buildList {
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) {
                        genresArr.optString(g).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.joinToString(" • ").ifBlank { srcName }
            val countriesArr = m.optJSONArray("countries")
            val firstCountry = countriesArr?.optString(0).orEmpty()
            val countryCat = CountryCategory.fromSlugOrName(firstCountry)
            val epCur = m.optString("episode_current").trim()
            val quality = listOfNotNull(
                m.optString("quality", "FHD").trim().takeIf { it.isNotBlank() },
                epCur.takeIf { it.isNotBlank() && !it.equals("Full", true) }
            ).joinToString(" • ").ifBlank { "FHD" }
            val rawType = m.optString("type").lowercase()
            val contentType = when (rawType) {
                "live" -> ContentType.LIVE_CHANNEL
                "single" -> ContentType.MOVIE
                else -> ContentType.SERIES
            }
            val contentVi = m.optString("contentVi").trim()
            val contentEn = m.optString("contentEn").trim()
            val rawDesc = when {
                prefLang == "vi" && contentVi.isNotEmpty() -> contentVi
                prefLang != "zh" && contentEn.isNotEmpty() -> contentEn
                else -> m.optString("content").trim()
            }
            val desc = rawDesc.ifBlank {
                "${m.optString("origin_name").trim().ifBlank { rawName }} (${m.optString("year", "2026")}) • $genres"
            }
            val directServers = if (src == "direct" && streamUrl.isNotEmpty()) {
                listOf(ServerGroup(srcName, listOf(Episode(id = "${slug}_live", name = "Live", streamUrl = streamUrl))))
            } else {
                emptyList()
            }
            val providerId = if (src == "phimapi") "modern_vod" else "cloud_$src"
            val itemId = if (src == "phimapi") "modern_$slug" else "${providerId}_${srcId}_$slug"
            val lookup = if (srcId.isNotEmpty() && src != "phimapi") "$slug|$srcId" else slug

            list.add(
                MovieItem(
                    id = itemId,
                    title = name,
                    originalTitle = m.optString("origin_name").trim(),
                    year = m.optString("year").trim(),
                    country = countryCat,
                    genre = genres,
                    duration = m.optString("time").trim(),
                    quality = quality,
                    posterUrl = m.optString("poster").trim(),
                    backdropUrl = m.optString("backdrop").trim().ifBlank { m.optString("poster").trim() },
                    description = desc,
                    contentType = contentType,
                    providerId = providerId,
                    providerName = srcName,
                    hasCloudSub = m.optBoolean("hasCloudSub", false),
                    imdbId = m.optString("imdbId").trim(),
                    servers = directServers,
                    detailLookupId = lookup
                )
            )
        }
        return list
    }

    /**
     * #9 Natural-language AI Vibe & Mood Search powered by /api/catalog/ai-search.
     */
    suspend fun searchAiVibe(query: String): Pair<String, List<MovieItem>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext "" to emptyList()
        runCatching<Pair<String, List<MovieItem>>> {
            val req = Request.Builder()
                .url("$baseUrl/api/catalog/ai-search?q=${enc(clean)}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use "" to emptyList()
                val obj = JSONObject(resp.body?.string().orEmpty().ifBlank { "{}" })
                val summary = obj.optString("vibeSummary", "")
                val items = parseCloudMovieArray(obj.optJSONArray("items"))
                summary to items
            }
        }.getOrDefault("" to emptyList())
    }

    suspend fun pullState(sinceMs: Long = 0L): Result<CloudSyncPullResult> = withContext(Dispatchers.IO) {
        runCatching {
            val path = if (sinceMs > 0L) "/api/sync/state?since=$sinceMs" else "/api/sync/state"
            val obj = getJson(path)
            val arr = obj.optJSONArray("watchProgress") ?: JSONArray()
            val list = ArrayList<WatchProgress>(arr.length())
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val slug = normalizeMovieSlug(r.optString("movieSlug"))
                if (slug == "unknown") continue
                val epKey = normalizeEpisodeKey(r.optString("episodeKey", r.optString("episodeName")))
                list.add(
                    WatchProgress(
                        movieId = "modern_$slug",
                        title = r.optString("title", slug),
                        posterUrl = r.optString("posterUrl", ""),
                        countryCode = r.optString("countryCode", "global"),
                        streamUrl = r.optString("streamUrl", ""),
                        episodeId = epKey,
                        episodeName = r.optString("episodeName", epKey),
                        positionMs = r.optLong("positionMs", 0L),
                        durationMs = r.optLong("durationMs", 0L),
                        updatedAt = r.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            val myArr = obj.optJSONArray("myList") ?: JSONArray()
            val myItems = ArrayList<CloudMyListItem>(myArr.length())
            for (i in 0 until myArr.length()) {
                val r = myArr.optJSONObject(i) ?: continue
                val slug = r.optString("slug").trim()
                if (slug.isEmpty()) continue
                val src = r.optString("source", "phimapi").trim().lowercase()
                val srcId = r.optString("sourceId", "").trim()
                myItems.add(
                    CloudMyListItem(
                        itemKey = r.optString("itemKey", "$src::$srcId::$slug"),
                        source = src,
                        sourceId = srcId,
                        slug = slug,
                        name = r.optString("name", slug),
                        originName = r.optString("origin_name", ""),
                        poster = r.optString("poster", ""),
                        year = r.optString("year", ""),
                        quality = r.optString("quality", "HD"),
                        latestEpisode = r.optString("latestEpisode", ""),
                        hasNewEpisode = r.optBoolean("hasNewEpisode", false),
                        addedAt = r.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            CloudSyncPullResult(
                watchProgress = list,
                myList = myItems,
                serverTime = obj.optLong("serverTime", System.currentTimeMillis())
            )
        }
    }

    suspend fun pushMyListUpdates(
        added: List<CloudMyListItem> = emptyList(),
        removedKeys: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn() || (added.isEmpty() && removedKeys.isEmpty())) return@withContext false
        runCatching {
            val body = JSONObject()
            if (added.isNotEmpty()) {
                val arr = JSONArray()
                for (it in added.take(100)) {
                    arr.put(
                        JSONObject()
                            .put("itemKey", it.itemKey)
                            .put("source", it.source)
                            .put("sourceId", it.sourceId)
                            .put("slug", it.slug)
                            .put("name", it.name)
                            .put("origin_name", it.originName)
                            .put("poster", it.poster)
                            .put("year", it.year)
                            .put("quality", it.quality)
                            .put("addedAt", it.addedAt)
                    )
                }
                body.put("myList", arr)
            }
            if (removedKeys.isNotEmpty()) {
                val remArr = JSONArray()
                removedKeys.take(100).forEach { remArr.put(it) }
                body.put("myListRemoved", remArr)
            }
            postJson("/api/sync/state", body, requireAuth = true)
            true
        }.getOrDefault(false)
    }

    suspend fun pushWatchProgress(items: List<WatchProgress>): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn() || items.isEmpty()) return@withContext false
        runCatching {
            val arr = JSONArray()
            for (item in items.take(80)) {
                val slug = normalizeMovieSlug(item.movieId)
                if (slug == "unknown" || slug == "direct" || slug == "local") continue
                val epKey = normalizeEpisodeKey(item.episodeName.ifBlank { item.episodeId })
                arr.put(
                    JSONObject()
                        .put("movieSlug", slug)
                        .put("episodeKey", epKey)
                        .put("title", item.title)
                        .put("posterUrl", item.posterUrl)
                        .put("countryCode", item.countryCode)
                        .put("episodeName", item.episodeName)
                        .put("streamUrl", item.streamUrl)
                        .put("positionMs", item.positionMs)
                        .put("durationMs", item.durationMs)
                        .put("updatedAt", item.updatedAt)
                )
            }
            if (arr.length() == 0) return@runCatching false
            postJson("/api/sync/state", JSONObject().put("watchProgress", arr), requireAuth = true)
            true
        }.getOrDefault(false)
    }

    suspend fun removeWatchProgress(movieIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn() || movieIds.isEmpty()) return@withContext false
        runCatching {
            val arr = JSONArray()
            movieIds.take(100).forEach { id ->
                val slug = normalizeMovieSlug(id)
                if (slug.isNotBlank() && slug != "unknown") arr.put(slug)
            }
            if (arr.length() == 0) return@runCatching false
            postJson("/api/sync/state", JSONObject().put("watchProgressRemoved", arr), requireAuth = true)
            true
        }.getOrDefault(false)
    }

    suspend fun fetchCloudSubtitle(
        rawMovieSlug: String,
        rawEpisodeNameOrId: String,
        langCode: String
    ): CloudSubtitlePayload? = withContext(Dispatchers.IO) {
        if (!isSignedIn()) return@withContext null
        val slug = normalizeMovieSlug(rawMovieSlug)
        if (slug == "unknown" || slug == "direct" || slug == "local") return@withContext null
        val epKey = normalizeEpisodeKey(rawEpisodeNameOrId)
        val lang = langCode.trim().lowercase().ifBlank { "vi" }

        runCatching {
            val q = "/api/sync/subtitles?movieSlug=${enc(slug)}&episodeKey=${enc(epKey)}&lang=${enc(lang)}"
            val obj = getJson(q)
            if (!obj.optBoolean("hit", false)) return@runCatching null
            val b = obj.optJSONObject("bundle") ?: return@runCatching null
            val primary = decodeCues(b.optJSONArray("primaryCues"))
            if (primary.size < 3) return@runCatching null
            val source = decodeCues(b.optJSONArray("sourceCues")).ifEmpty { primary }
            val secondary = decodeCues(b.optJSONArray("secondaryCues"))
            val slots = HashSet<Int>()
            (b.optJSONArray("completedAudioSlots") ?: b.optJSONArray("translatedWindows"))?.let { sArr ->
                for (i in 0 until sArr.length()) slots.add(sArr.optInt(i))
            }
            val ranges = decodeTimeRanges(b.optJSONArray("translatedRanges"))
            val isAudio = b.optBoolean("isAiAudio", b.optBoolean("audioMode", false))
            val maxRangeEnd = ranges.lastOrNull()?.second ?: 0L
            val maxCueEnd = primary.lastOrNull()?.endMs ?: 0L
            val audioUntil = maxOf(
                b.optLong("audioTranslatedUntilMs", 0L),
                maxRangeEnd,
                if (isAudio) maxCueEnd else 0L
            )
            val resolvedPrimaryLang = b.optString("primaryLangCode", lang)
            if (hasWrongScriptForLang(primary, resolvedPrimaryLang)) return@runCatching null
            CloudSubtitlePayload(
                movieSlug = slug,
                episodeKey = epKey,
                primaryLangCode = resolvedPrimaryLang,
                secondaryLangCode = b.optString("secondaryLangCode", ""),
                sourceLangCode = b.optString("sourceLangCode", "auto"),
                sourcePlatform = b.optString("sourcePlatform", "NKI Cloud"),
                isAiAudio = isAudio,
                isFullyTranslated = b.optBoolean("isFullyTranslated", true),
                completedAudioSlots = slots,
                translatedRanges = ranges,
                audioTranslatedUntilMs = audioUntil,
                totalDurationMs = b.optLong("totalDurationMs", 0L),
                sourceCues = source,
                primaryCues = primary,
                secondaryCues = secondary,
                savedAtMs = b.optLong("savedAtMs", System.currentTimeMillis())
            )
        }.getOrNull()
    }

    suspend fun uploadCloudSubtitle(
        rawMovieSlug: String,
        rawEpisodeNameOrId: String,
        primaryLangCode: String,
        secondaryLangCode: String,
        sourceLangCode: String,
        sourcePlatform: String,
        isAiAudio: Boolean,
        isFullyTranslated: Boolean,
        completedAudioSlots: Set<Int>,
        sourceCues: List<SubtitleCue>,
        primaryCues: List<SubtitleCue>,
        secondaryCues: List<SubtitleCue>,
        translatedRanges: List<Pair<Long, Long>> = emptyList(),
        audioTranslatedUntilMs: Long = 0L,
        totalDurationMs: Long = 0L,
        forceOverwrite: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn() || primaryCues.size < 3) return@withContext false
        if (hasWrongScriptForLang(primaryCues, primaryLangCode)) return@withContext false
        val slug = normalizeMovieSlug(rawMovieSlug)
        if (slug == "unknown" || slug == "direct" || slug == "local") return@withContext false
        val epKey = normalizeEpisodeKey(rawEpisodeNameOrId)
        val mergedRanges = mergeTimeRanges(translatedRanges)

        runCatching {
            val payload = JSONObject()
                .put("movieSlug", slug)
                .put("episodeKey", epKey)
                .put("primaryLangCode", primaryLangCode.trim().lowercase())
                .put("secondaryLangCode", secondaryLangCode.trim().lowercase())
                .put("sourceLangCode", sourceLangCode)
                .put("sourcePlatform", sourcePlatform)
                .put("isAiAudio", isAiAudio)
                .put("audioMode", isAiAudio)
                .put("isFullyTranslated", isFullyTranslated)
                .put("completedAudioSlots", JSONArray().apply { completedAudioSlots.sorted().forEach { put(it) } })
                .put("translatedWindows", JSONArray().apply { completedAudioSlots.sorted().forEach { put(it) } })
                .put("translatedRanges", encodeTimeRanges(mergedRanges))
                .put("audioTranslatedUntilMs", audioTranslatedUntilMs.coerceAtLeast(0L))
                .put("totalDurationMs", totalDurationMs.coerceAtLeast(0L))
                .put("forceOverwrite", forceOverwrite)
                .put("sourceCues", encodeCues(sourceCues))
                .put("primaryCues", encodeCues(primaryCues))
                .put("secondaryCues", encodeCues(secondaryCues))
            putJson("/api/sync/subtitles", payload)
            true
        }.getOrDefault(false)
    }

    /**
     * Checks whether a subtitle bundle is predominantly in a forbidden foreign script for `targetLang`
     * (e.g. Chinese Hanzi accidentally saved under Vietnamese `vi`), mirroring Web & Server guards!
     */
    fun hasWrongScriptForLang(cues: List<SubtitleCue>, targetLang: String): Boolean {
        if (cues.isEmpty()) return false
        val key = targetLang.trim().lowercase()
        var nonEmpty = 0
        var badCues = 0
        var totalForbidden = 0
        var totalThai = 0
        var totalLetters = 0
        for (c in cues) {
            val txt = c.text
            if (txt.isBlank()) continue
            var letters = 0
            var han = 0
            var kana = 0
            var hangul = 0
            var thai = 0
            for (ch in txt) {
                if (Character.isLetter(ch)) {
                    letters++
                    val code = ch.code
                    when {
                        code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF -> han++
                        code in 0x3040..0x30FF -> kana++
                        code in 0xAC00..0xD7AF -> hangul++
                        code in 0x0E00..0x0E7F -> thai++
                    }
                }
            }
            if (letters == 0) continue
            val forbidden = when (key) {
                "zh", "zh-tw", "zh-cn" -> kana + hangul + thai
                "ja" -> hangul + thai
                "ko" -> (if (hangul == 0) han + kana else kana) + thai
                "th" -> han + kana + hangul
                else -> han + kana + hangul + thai
            }
            nonEmpty++
            totalForbidden += forbidden
            totalThai += thai
            totalLetters += letters
            if (forbidden >= 2 && forbidden.toFloat() / letters.toFloat() >= 0.30f) {
                badCues++
            }
        }
        if (nonEmpty == 0 || totalLetters == 0) return false
        if (key == "th" && totalLetters >= 20 && totalThai.toFloat() / totalLetters.toFloat() < 0.20f) {
            return true
        }
        if (nonEmpty <= 5) {
            return badCues >= 1 && totalForbidden >= 4 && totalForbidden.toFloat() / totalLetters.toFloat() >= 0.22f
        }
        return badCues.toFloat() / nonEmpty.toFloat() >= 0.18f ||
            totalForbidden.toFloat() / totalLetters.toFloat() >= 0.20f
    }

    fun mergeTimeRanges(ranges: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges
            .mapNotNull { (s, e) ->
                val cs = s.coerceAtLeast(0L)
                val ce = e.coerceAtLeast(0L)
                if (ce > cs) cs to ce else null
            }
            .sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Long, Long>>(sorted.size)
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd + 2_500L) {
                if (e > curEnd) curEnd = e
            } else {
                out.add(curStart to curEnd)
                curStart = s
                curEnd = e
            }
        }
        out.add(curStart to curEnd)
        return out.take(400)
    }

    private fun encodeTimeRanges(ranges: List<Pair<Long, Long>>): JSONArray {
        val arr = JSONArray()
        for ((s, e) in ranges) {
            arr.put(JSONArray().put(s).put(e))
        }
        return arr
    }

    fun decodeTimeRanges(arr: JSONArray?): List<Pair<Long, Long>> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<Pair<Long, Long>>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            when (item) {
                is JSONArray -> if (item.length() >= 2) {
                    val s = item.optLong(0, 0L)
                    val e = item.optLong(1, 0L)
                    if (e > s) out.add(s to e)
                }
                is JSONObject -> {
                    val s = item.optLong("startMs", item.optLong("s", 0L))
                    val e = item.optLong("endMs", item.optLong("e", 0L))
                    if (e > s) out.add(s to e)
                }
            }
        }
        return mergeTimeRanges(out)
    }

    /* ==================== WATCH PARTY 2.0 API (BACKED BY /api/party v18) ==================== */
    private fun parsePartyMovie(obj: JSONObject?): WatchPartyMovieMeta {
        return WatchPartyMovieMeta(
            slug = obj?.optString("slug").orEmpty(),
            source = obj?.optString("source", "phimapi").orEmpty().ifBlank { "phimapi" },
            sourceId = obj?.optString("sourceId").orEmpty(),
            name = obj?.optString("name", obj.optString("title", "Phim")).orEmpty().ifBlank { "Phim" },
            poster = obj?.optString("poster").orEmpty()
        )
    }

    private fun parsePartyEpisode(obj: JSONObject?): WatchPartyEpisodeMeta {
        return WatchPartyEpisodeMeta(
            name = obj?.optString("name", "Full").orEmpty().ifBlank { "Full" },
            slug = obj?.optString("slug").orEmpty(),
            linkM3u8 = obj?.optString("link_m3u8", obj.optString("linkM3u8", "")).orEmpty()
        )
    }

    private fun parsePartyQueue(arr: JSONArray?): List<WatchPartyQueueItem> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<WatchPartyQueueItem>(arr.length())
        for (i in 0 until arr.length()) {
            val q = arr.optJSONObject(i) ?: continue
            val votes = ArrayList<String>()
            q.optJSONArray("votes")?.let { vArr ->
                for (j in 0 until vArr.length()) vArr.optString(j).takeIf { it.isNotBlank() }?.let { votes.add(it) }
            }
            out.add(
                WatchPartyQueueItem(
                    id = q.optString("id"),
                    movie = parsePartyMovie(q.optJSONObject("movie")),
                    episode = parsePartyEpisode(q.optJSONObject("episode")),
                    addedBy = q.optString("addedBy"),
                    addedById = q.optString("addedById"),
                    votes = votes,
                    addedAt = q.optLong("addedAt", 0L)
                )
            )
        }
        return out
    }

    private fun parsePartySnapshot(p: JSONObject, rttMs: Long = 0L): WatchPartySnapshot {
        val members = ArrayList<WatchPartyMember>()
        p.optJSONArray("members")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                members.add(
                    WatchPartyMember(
                        id = m.optString("id"),
                        name = m.optString("name", "Khách"),
                        isHost = m.optBoolean("isHost", false),
                        status = m.optString("status", "playing"),
                        posSec = m.optInt("posSec", 0)
                    )
                )
            }
        }
        val reactions = ArrayList<WatchPartyReaction>()
        p.optJSONArray("reactions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                reactions.add(
                    WatchPartyReaction(
                        id = r.optString("id"),
                        emoji = r.optString("emoji"),
                        senderName = r.optString("senderName"),
                        createdAt = r.optLong("createdAt", 0L)
                    )
                )
            }
        }
        val messages = ArrayList<WatchPartyMessage>()
        p.optJSONArray("messages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                messages.add(
                    WatchPartyMessage(
                        id = m.optLong("id", 0L),
                        senderName = m.optString("senderName", "Khách"),
                        isHost = m.optBoolean("isHost", false),
                        text = m.optString("text"),
                        createdAt = m.optLong("createdAt", 0L)
                    )
                )
            }
        }
        return WatchPartySnapshot(
            roomCode = p.optString("roomCode"),
            roomTitle = p.optString("roomTitle"),
            visibility = p.optString("visibility", "PRIVATE"),
            controlMode = p.optString("controlMode", "HOST_ONLY"),
            hasPasscode = p.optBoolean("hasPasscode", false),
            hostName = p.optString("hostName", "Host"),
            hostOnline = p.optBoolean("hostOnline", true),
            hostInactive = p.optBoolean("hostInactive", false),
            movie = parsePartyMovie(p.optJSONObject("movie")),
            episode = parsePartyEpisode(p.optJSONObject("episode")),
            isPlaying = p.optBoolean("isPlaying", true),
            positionSec = p.optDouble("positionSec", 0.0),
            playbackRate = p.optDouble("playbackRate", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            stateUpdatedAt = p.optLong("stateUpdatedAt", 0L),
            lastActionText = p.optString("lastActionText"),
            lastActionAt = p.optLong("lastActionAt", 0L),
            serverTime = p.optLong("serverTime", System.currentTimeMillis()),
            members = members,
            viewerCount = p.optInt("viewerCount", members.size.coerceAtLeast(1)),
            queue = parsePartyQueue(p.optJSONArray("queue")),
            reactions = reactions,
            messages = messages,
            rttMs = rttMs
        )
    }

    suspend fun partyListLobby(): Result<List<WatchPartyLobbyRoom>> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = getJson("/api/party?op=lobby&_=${System.currentTimeMillis()}")
            val arr = obj.optJSONArray("rooms") ?: return@runCatching emptyList()
            val out = ArrayList<WatchPartyLobbyRoom>(arr.length())
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                out.add(
                    WatchPartyLobbyRoom(
                        roomCode = r.optString("roomCode"),
                        roomTitle = r.optString("roomTitle"),
                        hostName = r.optString("hostName", "Host"),
                        movie = parsePartyMovie(r.optJSONObject("movie")),
                        episodeName = r.optString("episodeName"),
                        viewerCount = r.optInt("viewerCount", 1),
                        isPlaying = r.optBoolean("isPlaying", true),
                        controlMode = r.optString("controlMode", "HOST_ONLY"),
                        hasPasscode = r.optBoolean("hasPasscode", false),
                        queueCount = r.optInt("queueCount", 0),
                        createdAt = r.optLong("createdAt", 0L)
                    )
                )
            }
            out
        }
    }

    suspend fun partyCreate(
        nickname: String,
        roomTitle: String,
        visibility: String,
        controlMode: String,
        passcode: String,
        movie: WatchPartyMovieMeta,
        episode: WatchPartyEpisodeMeta,
        positionSec: Double = 0.0,
        isPlaying: Boolean = true
    ): Result<WatchPartyJoinOrCreateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = System.currentTimeMillis()
            val body = JSONObject()
                .put("action", "create")
                .put("nickname", nickname)
                .put("roomTitle", roomTitle)
                .put("visibility", visibility)
                .put("controlMode", controlMode)
                .put("passcode", passcode)
                .put("movie", JSONObject().put("slug", movie.slug).put("source", movie.source).put("sourceId", movie.sourceId).put("name", movie.name).put("poster", movie.poster))
                .put("episode", JSONObject().put("name", episode.name).put("slug", episode.slug).put("link_m3u8", episode.linkM3u8))
                .put("positionSec", positionSec)
                .put("isPlaying", isPlaying)
            val res = postJson("/api/party", body, requireAuth = false)
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(0L)
            val pObj = res.optJSONObject("party") ?: error("Missing party snapshot")
            WatchPartyJoinOrCreateResult(
                roomCode = res.optString("roomCode"),
                hostToken = res.optString("hostToken"),
                memberId = res.optString("memberId"),
                isHost = true,
                party = parsePartySnapshot(pObj, rtt)
            )
        }
    }

    suspend fun partyJoin(
        roomCode: String,
        nickname: String,
        memberId: String = "",
        passcode: String = "",
        hostToken: String = ""
    ): Result<WatchPartyJoinOrCreateResult> = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = System.currentTimeMillis()
            val body = JSONObject()
                .put("action", "join")
                .put("roomCode", roomCode.trim().uppercase())
                .put("nickname", nickname)
                .put("memberId", memberId)
                .put("passcode", passcode)
                .put("hostToken", hostToken)
            val builder = Request.Builder()
                .url("$baseUrl/api/party")
                .post(body.toString().toRequestBody(JSON_MEDIA))
            client.newCall(builder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
                if (!resp.isSuccessful) {
                    if (resp.code == 403 && obj.optBoolean("needPasscode", false)) {
                        throw PartyPasscodeRequiredException(obj.optString("error", "Phòng này yêu cầu mật khẩu"))
                    }
                    error(obj.optString("error", "HTTP ${resp.code}"))
                }
                val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(0L)
                val pObj = obj.optJSONObject("party") ?: error("Missing party snapshot")
                WatchPartyJoinOrCreateResult(
                    roomCode = pObj.optString("roomCode", roomCode.uppercase()),
                    hostToken = if (obj.optBoolean("isHost", false)) hostToken else "",
                    memberId = obj.optString("memberId", memberId),
                    isHost = obj.optBoolean("isHost", false),
                    party = parsePartySnapshot(pObj, rtt)
                )
            }
        }
    }

    suspend fun partyPoll(
        roomCode: String,
        memberId: String,
        nickname: String,
        hostToken: String = "",
        sinceMsgId: Long = 0L,
        sinceReactionTs: Long = 0L,
        status: String = "playing",
        posSec: Int = 0
    ): Result<WatchPartySnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val t0 = System.currentTimeMillis()
            val q = buildString {
                append("/api/party?room=").append(enc(roomCode))
                append("&memberId=").append(enc(memberId))
                append("&nickname=").append(enc(nickname))
                append("&sinceMsgId=").append(sinceMsgId)
                append("&sinceReactionTs=").append(sinceReactionTs)
                append("&status=").append(enc(status))
                append("&pos=").append(posSec)
                if (hostToken.isNotBlank()) append("&hostToken=").append(enc(hostToken))
            }
            val obj = getJson(q)
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(0L)
            val pObj = obj.optJSONObject("party") ?: error("Missing party snapshot")
            parsePartySnapshot(pObj, rtt)
        }
    }

    suspend fun partyControl(
        roomCode: String,
        hostToken: String = "",
        memberId: String = "",
        nickname: String = "",
        isPlaying: Boolean? = null,
        positionSec: Double? = null,
        playbackRate: Float? = null,
        countdown: Boolean = false,
        movie: WatchPartyMovieMeta? = null,
        episode: WatchPartyEpisodeMeta? = null
    ): Result<WatchPartyControlResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "control")
                .put("roomCode", roomCode)
                .put("hostToken", hostToken)
                .put("memberId", memberId)
                .put("nickname", nickname)
                .put("countdown", countdown)
            if (isPlaying != null) body.put("isPlaying", isPlaying)
            if (positionSec != null) body.put("positionSec", positionSec)
            if (playbackRate != null) body.put("playbackRate", playbackRate.toDouble())
            if (movie != null) {
                body.put("movie", JSONObject().put("slug", movie.slug).put("source", movie.source).put("sourceId", movie.sourceId).put("name", movie.name).put("poster", movie.poster))
            }
            if (episode != null) {
                body.put("episode", JSONObject().put("name", episode.name).put("slug", episode.slug).put("link_m3u8", episode.linkM3u8))
            }
            val res = postJson("/api/party", body, requireAuth = false)
            WatchPartyControlResult(
                serverTime = res.optLong("serverTime", System.currentTimeMillis()),
                stateUpdatedAt = res.optLong("stateUpdatedAt", System.currentTimeMillis()),
                isPlaying = res.optBoolean("isPlaying", true),
                positionSec = res.optDouble("positionSec", positionSec ?: 0.0),
                lastActionText = res.optString("lastActionText")
            )
        }
    }

    suspend fun partyUpdateSettings(
        roomCode: String,
        hostToken: String,
        controlMode: String? = null,
        visibility: String? = null,
        roomTitle: String? = null,
        passcode: String? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "settings").put("roomCode", roomCode).put("hostToken", hostToken)
            if (controlMode != null) body.put("controlMode", controlMode)
            if (visibility != null) body.put("visibility", visibility)
            if (roomTitle != null) body.put("roomTitle", roomTitle)
            if (passcode != null) body.put("passcode", passcode)
            postJson("/api/party", body, requireAuth = false)
        }
    }

    suspend fun partyClaimHost(roomCode: String, memberId: String, nickname: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "claim_host").put("roomCode", roomCode).put("memberId", memberId).put("nickname", nickname)
            val res = postJson("/api/party", body, requireAuth = false)
            res.optString("hostToken").ifBlank { error("Missing hostToken") }
        }
    }

    suspend fun partyQueueAdd(
        roomCode: String,
        memberId: String,
        nickname: String,
        movie: WatchPartyMovieMeta,
        episode: WatchPartyEpisodeMeta
    ): Result<List<WatchPartyQueueItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "queue_add")
                .put("roomCode", roomCode)
                .put("memberId", memberId)
                .put("nickname", nickname)
                .put("movie", JSONObject().put("slug", movie.slug).put("source", movie.source).put("sourceId", movie.sourceId).put("name", movie.name).put("poster", movie.poster))
                .put("episode", JSONObject().put("name", episode.name).put("slug", episode.slug).put("link_m3u8", episode.linkM3u8))
            val res = postJson("/api/party", body, requireAuth = false)
            parsePartyQueue(res.optJSONArray("queue"))
        }
    }

    suspend fun partyQueueVote(roomCode: String, memberId: String, itemId: String): Result<List<WatchPartyQueueItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "queue_vote").put("roomCode", roomCode).put("memberId", memberId).put("itemId", itemId)
            val res = postJson("/api/party", body, requireAuth = false)
            parsePartyQueue(res.optJSONArray("queue"))
        }
    }

    suspend fun partyQueueRemove(roomCode: String, hostToken: String, memberId: String, itemId: String): Result<List<WatchPartyQueueItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "queue_remove").put("roomCode", roomCode).put("hostToken", hostToken).put("memberId", memberId).put("itemId", itemId)
            val res = postJson("/api/party", body, requireAuth = false)
            parsePartyQueue(res.optJSONArray("queue"))
        }
    }

    suspend fun partyQueuePlay(roomCode: String, hostToken: String, memberId: String, nickname: String, itemId: String = ""): Result<Pair<WatchPartyMovieMeta, WatchPartyEpisodeMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("action", "queue_play")
                .put("roomCode", roomCode)
                .put("hostToken", hostToken)
                .put("memberId", memberId)
                .put("nickname", nickname)
                .put("itemId", itemId)
            val res = postJson("/api/party", body, requireAuth = false)
            parsePartyMovie(res.optJSONObject("movie")) to parsePartyEpisode(res.optJSONObject("episode"))
        }
    }

    suspend fun partyReact(roomCode: String, memberId: String, nickname: String, emoji: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "react").put("roomCode", roomCode).put("memberId", memberId).put("nickname", nickname).put("emoji", emoji)
            postJson("/api/party", body, requireAuth = false)
            true
        }
    }

    suspend fun partySendChat(roomCode: String, nickname: String, text: String, hostToken: String = ""): Result<WatchPartyMessage?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "chat").put("roomCode", roomCode).put("nickname", nickname).put("text", text).put("hostToken", hostToken)
            val res = postJson("/api/party", body, requireAuth = false)
            val m = res.optJSONObject("message") ?: return@runCatching null
            WatchPartyMessage(
                id = m.optLong("id", 0L),
                senderName = m.optString("senderName", nickname),
                isHost = m.optBoolean("isHost", false),
                text = m.optString("text", text),
                createdAt = m.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    suspend fun partyLeave(roomCode: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("action", "leave").put("roomCode", roomCode).put("memberId", memberId)
            postJson("/api/party", body, requireAuth = false)
            true
        }.getOrDefault(false)
    }

    suspend fun partyQuickSearch(keyword: String): List<MovieItem> = withContext(Dispatchers.IO) {
        val q = keyword.trim()
        if (q.length < 2) return@withContext emptyList()
        runCatching {
            val obj = getJson("/api/catalog?op=browse&keyword=${enc(q)}&limit=10&fast=1")
            val arr = obj.optJSONArray("items") ?: return@runCatching emptyList()
            val out = ArrayList<MovieItem>(arr.length())
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val src = it.optString("source", "phimapi").ifBlank { "phimapi" }
                val srcId = it.optString("sourceId")
                val slug = it.optString("slug")
                if (slug.isBlank()) continue
                val providerId = if (src == "phimapi") "modern_vod" else "cloud_$src"
                out.add(
                    MovieItem(
                        id = if (src == "phimapi") "modern_$slug" else "${providerId}_${srcId}_$slug",
                        title = it.optString("nameVi").ifBlank { it.optString("name", slug) },
                        originalTitle = it.optString("origin_name"),
                        year = it.optString("year"),
                        quality = it.optString("quality", "HD"),
                        posterUrl = it.optString("poster"),
                        backdropUrl = it.optString("poster"),
                        providerId = providerId,
                        detailLookupId = if (srcId.isNotBlank() && src != "phimapi") "$slug|$srcId" else slug
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    /**
     * Blocking IO call (used inside SubtitleTranslator's IO coroutine) to translate a batch via
     * the centralized server-side Gemini API Key on Vercel (zero API key on Android!).
     */
    fun translateTextsViaCloudSync(
        texts: List<String>,
        targetLang: String,
        modelId: String
    ): List<String>? {
        if (!canUseServerAi() || texts.isEmpty()) return null
        return runCatching {
            val arr = JSONArray()
            texts.forEach { arr.put(it) }
            val payload = JSONObject()
                .put("texts", arr)
                .put("targetLang", targetLang)
                .put("model", modelId.ifBlank { defaultModel })
            val slugCtx = activeMovieSlug.trim()
            if (slugCtx.isNotEmpty()) {
                payload.put("movieContext", JSONObject().put("slug", slugCtx))
            }
            val res = postJson("/api/ai/translate", payload, requireAuth = true)
            val outArr = res.optJSONArray("translated") ?: return@runCatching null
            if (outArr.length() != texts.size) return@runCatching null
            List(texts.size) { idx -> outArr.optString(idx, texts[idx]).ifBlank { texts[idx] } }
        }.getOrNull()
    }

    /**
     * #4 Cinema AI Subtitle Polisher (text-only batch via /api/ai/translate mode="polish"):
     * Unifies character names/glossary, pronouns & honorifics across up to 80 lines per call.
     */
    fun polishSubtitleTextsViaCloudSync(
        texts: List<String>,
        targetLang: String,
        modelId: String,
        movieTitle: String = "",
        movieGenres: List<String> = emptyList()
    ): List<String>? {
        if (!canUseServerAi() || texts.isEmpty()) return null
        return runCatching {
            val arr = JSONArray()
            texts.forEach { arr.put(it) }
            val payload = JSONObject()
                .put("mode", "polish")
                .put("texts", arr)
                .put("targetLang", targetLang)
                .put("model", modelId.ifBlank { defaultModel })
            val ctx = JSONObject()
            val slugCtx = activeMovieSlug.trim()
            if (slugCtx.isNotEmpty()) ctx.put("slug", slugCtx)
            if (movieTitle.isNotBlank()) ctx.put("title", movieTitle.take(100))
            if (movieGenres.isNotEmpty()) {
                val gArr = JSONArray()
                movieGenres.take(4).forEach { gArr.put(it) }
                ctx.put("genres", gArr)
            }
            if (ctx.length() > 0) payload.put("movieContext", ctx)
            val res = postJson("/api/ai/translate", payload, requireAuth = true)
            val outArr = res.optJSONArray("translated") ?: return@runCatching null
            if (outArr.length() != texts.size) return@runCatching null
            List(texts.size) { idx -> outArr.optString(idx, texts[idx]).ifBlank { texts[idx] } }
        }.getOrNull()
    }

    /**
     * Blocking IO call (used inside GeminiAudioSubtitleGenerator's IO coroutine) to transcribe
     * and translate a movie audio chunk via server-side Gemini Multimodal on Vercel!
     */
    fun transcribeAudioViaCloudSync(
        audioBase64: String,
        mimeType: String,
        chunkStartOffsetMs: Long,
        chunkDurationMs: Long,
        targetLang: String,
        modelId: String,
        preferTurboFlash: Boolean,
        previousLines: List<String> = emptyList(),
        retrySensitive: Boolean = false
    ): List<SubtitleCue>? {
        if (!canUseServerAi() || audioBase64.isBlank()) return null
        return runCatching {
            val payload = JSONObject()
                .put("audioBase64", audioBase64)
                .put("mimeType", mimeType)
                .put("chunkStartOffsetMs", chunkStartOffsetMs)
                .put("chunkDurationMs", chunkDurationMs)
                .put("targetLang", targetLang)
                .put("model", modelId.ifBlank { defaultModel })
                .put("preferTurboFlash", preferTurboFlash)
                .put("retrySensitive", retrySensitive)
            if (previousLines.isNotEmpty()) {
                val prevArr = JSONArray()
                previousLines.takeLast(5).forEach { prevArr.put(it.take(120)) }
                payload.put("previousLines", prevArr)
            }
            val slugCtx = activeMovieSlug.trim()
            if (slugCtx.isNotEmpty()) {
                payload.put("movieContext", JSONObject().put("slug", slugCtx))
            }
            val res = postJson("/api/ai/audio-sub", payload, requireAuth = true)
            val cuesArr = res.optJSONArray("cues") ?: return@runCatching emptyList()
            decodeCues(cuesArr)
        }.getOrNull()
    }

    private fun parseSessionResponse(obj: JSONObject): CloudSession {
        val token = obj.optString("token").trim()
        val u = obj.optJSONObject("user") ?: error("Missing user in response")
        if (token.isBlank()) error("Missing session token")
        val usage = u.optJSONObject("todayUsage")
        return CloudSession(
            token = token,
            userId = u.optString("id"),
            username = u.optString("username"),
            displayName = u.optString("displayName", u.optString("username")),
            role = u.optString("role", "MEMBER"),
            dailyAiLimit = u.optInt("dailyAiLimit", 150),
            todayAiRequests = usage?.optInt("requests", 0) ?: 0,
            serverAiReady = obj.optBoolean("serverAiReady", false),
            defaultModel = obj.optString("defaultModel", "gemini-3-flash-preview")
        ).also { applySession(it) }
    }

    private fun getJson(path: String): JSONObject {
        val builder = Request.Builder().url("$baseUrl$path").get()
        if (activeToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $activeToken")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                error(obj.optString("error", "HTTP ${resp.code}"))
            }
            return obj
        }
    }

    private fun postJson(path: String, body: JSONObject, requireAuth: Boolean): JSONObject {
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody(JSON_MEDIA))
        if (requireAuth && activeToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $activeToken")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                error(obj.optString("error", "HTTP ${resp.code}"))
            }
            return obj
        }
    }

    private fun putJson(path: String, body: JSONObject): JSONObject {
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .put(body.toString().toRequestBody(JSON_MEDIA))
        if (activeToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $activeToken")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) {
                error(obj.optString("error", "HTTP ${resp.code}"))
            }
            return obj
        }
    }

    private fun encodeCues(cues: List<SubtitleCue>): JSONArray {
        val arr = JSONArray()
        for (c in cues) {
            arr.put(JSONArray().put(c.id).put(c.startMs).put(c.endMs).put(c.text))
        }
        return arr
    }

    private fun decodeCues(arr: JSONArray?): List<SubtitleCue> {
        if (arr == null || arr.length() == 0) return emptyList()
        val list = ArrayList<SubtitleCue>(arr.length())
        for (i in 0 until arr.length()) {
            val elem = arr.opt(i)
            when (elem) {
                is JSONArray -> if (elem.length() >= 4) {
                    list.add(
                        SubtitleCue(
                            id = elem.optInt(0, i + 1),
                            startMs = elem.optLong(1, 0L),
                            endMs = elem.optLong(2, 0L),
                            text = elem.optString(3, "")
                        )
                    )
                }
                is JSONObject -> {
                    val txt = elem.optString("text", elem.optString("t", "")).trim()
                    if (txt.isNotEmpty()) {
                        list.add(
                            SubtitleCue(
                                id = elem.optInt("id", i + 1),
                                startMs = elem.optLong("startMs", 0L),
                                endMs = elem.optLong("endMs", 0L),
                                text = txt
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
