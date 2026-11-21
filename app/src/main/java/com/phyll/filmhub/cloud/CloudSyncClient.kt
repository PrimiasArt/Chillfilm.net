package com.phyll.filmhub.cloud

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
    val todayRequests: Int
)

data class AdminVaultSnapshot(
    val serverAiReady: Boolean = false,
    val hasEnvKey: Boolean = false,
    val hasVaultKey: Boolean = false,
    val maskedVaultKey: String = "",
    val defaultModel: String = "gemini-3-flash-preview",
    val registrationMode: String = "INVITE_ONLY",
    val cachedSubtitlesCount: Int = 0,
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
    val sourceCues: List<SubtitleCue>,
    val primaryCues: List<SubtitleCue>,
    val secondaryCues: List<SubtitleCue>,
    val savedAtMs: Long
)

data class CloudSyncPullResult(
    val watchProgress: List<WatchProgress>,
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
    var defaultModel: String = "gemini-3-flash-preview"

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
                        todayRequests = usage?.optInt("requests", 0) ?: 0
                    )
                )
            }
            AdminVaultSnapshot(
                serverAiReady = cfg.optBoolean("serverAiReady", false),
                hasEnvKey = cfg.optBoolean("hasEnvKey", false),
                hasVaultKey = cfg.optBoolean("hasVaultKey", false),
                maskedVaultKey = cfg.optString("maskedVaultKey", ""),
                defaultModel = cfg.optString("defaultModel", "gemini-3-flash-preview"),
                registrationMode = cfg.optString("registrationMode", "INVITE_ONLY"),
                cachedSubtitlesCount = cfg.optInt("cachedSubtitlesCount", 0),
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
            CloudSyncPullResult(
                watchProgress = list,
                serverTime = obj.optLong("serverTime", System.currentTimeMillis())
            )
        }
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
            b.optJSONArray("completedAudioSlots")?.let { sArr ->
                for (i in 0 until sArr.length()) slots.add(sArr.optInt(i))
            }
            CloudSubtitlePayload(
                movieSlug = slug,
                episodeKey = epKey,
                primaryLangCode = b.optString("primaryLangCode", lang),
                secondaryLangCode = b.optString("secondaryLangCode", ""),
                sourceLangCode = b.optString("sourceLangCode", "auto"),
                sourcePlatform = b.optString("sourcePlatform", "NKI Cloud"),
                isAiAudio = b.optBoolean("isAiAudio", false),
                isFullyTranslated = b.optBoolean("isFullyTranslated", true),
                completedAudioSlots = slots,
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
        secondaryCues: List<SubtitleCue>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSignedIn() || primaryCues.size < 3) return@withContext false
        val slug = normalizeMovieSlug(rawMovieSlug)
        if (slug == "unknown" || slug == "direct" || slug == "local") return@withContext false
        val epKey = normalizeEpisodeKey(rawEpisodeNameOrId)

        runCatching {
            val payload = JSONObject()
                .put("movieSlug", slug)
                .put("episodeKey", epKey)
                .put("primaryLangCode", primaryLangCode.trim().lowercase())
                .put("secondaryLangCode", secondaryLangCode.trim().lowercase())
                .put("sourceLangCode", sourceLangCode)
                .put("sourcePlatform", sourcePlatform)
                .put("isAiAudio", isAiAudio)
                .put("isFullyTranslated", isFullyTranslated)
                .put("completedAudioSlots", JSONArray().apply { completedAudioSlots.sorted().forEach { put(it) } })
                .put("sourceCues", encodeCues(sourceCues))
                .put("primaryCues", encodeCues(primaryCues))
                .put("secondaryCues", encodeCues(secondaryCues))
            putJson("/api/sync/subtitles", payload)
            true
        }.getOrDefault(false)
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
        preferTurboFlash: Boolean
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
