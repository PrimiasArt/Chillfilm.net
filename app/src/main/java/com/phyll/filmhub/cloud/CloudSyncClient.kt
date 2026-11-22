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
