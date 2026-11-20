package com.phyll.filmhub.data.source

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Native YouTube Movie, Series & Video Source (Keyless InnerTube Search + ANDROID/IOS 1080p Player):
 * - Searches YouTube directly inside Phyll Film Hub (no API key or login needed).
 * - Resolves direct 1080p/720p H.264 video + M4A audio streams (ad-free native ExoPlayer playback)
 *   plus progressive MP4 & HLS Live streams.
 * - Extracts Official Human YouTube Subtitles (Thai, Vietnamese, English, Chinese, Korean, Japanese)
 *   and Auto-Generated Captions in XML format3 (srv3) for Pre-Flight Thai Subtitle rendering.
 */
class YouTubeSource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        const val PROVIDER_ID = "youtube_vod"
        const val ANDROID_CLIENT_VERSION = "20.10.38"
        const val ANDROID_USER_AGENT =
            "com.google.android.youtube/$ANDROID_CLIENT_VERSION (Linux; U; Android 14) gzip"
        private const val IOS_CLIENT_VERSION = "20.10.4"
        private const val IOS_USER_AGENT =
            "com.google.ios.youtube/$IOS_CLIENT_VERSION (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val YT_URL_ID_REGEX = Regex(
            """(?:youtube\.com/(?:watch\?.*?\bv=|embed/|shorts/|live/|v/)|youtu\.be/)([A-Za-z0-9_-]{11})""",
            RegexOption.IGNORE_CASE
        )
        private val RAW_11_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

        fun isYouTubeUrl(input: String): Boolean {
            val trimmed = input.trim()
            return YT_URL_ID_REGEX.containsMatchIn(trimmed) ||
                trimmed.startsWith("yt:", ignoreCase = true)
        }

        fun extractVideoId(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.startsWith("yt:", ignoreCase = true)) {
                val candidate = trimmed.substringAfter(':').trim()
                if (RAW_11_ID_REGEX.matches(candidate)) return candidate
            }
            YT_URL_ID_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }
    }

    data class YouTubeShelfSpec(
        val id: String,
        val title: String,
        val subtitle: String,
        val query: String,
        val country: CountryCategory,
        val minDurationSec: Int = 180
    )

    private val curatedShelfSpecs = listOf(
        YouTubeShelfSpec(
            id = "yt_thai_drama",
            title = "🇹🇭 Phim Thái Lan & Series GMMTV / One31 Chính Thức (YouTube)",
            subtitle = "Phát trực tiếp 1080p không quảng cáo • Tự lấy Sub Thái gốc hoặc Sub Anh dịch AI",
            query = "GMMTV Official Full Episode Eng Sub ละครเต็มเรื่อง หนังไทย",
            country = CountryCategory.THAILAND,
            minDurationSec = 300
        ),
        YouTubeShelfSpec(
            id = "yt_full_movies",
            title = "🎬 Phim Lẻ Điện Ảnh & Hành Động Full HD Trên YouTube",
            subtitle = "Phim chiếu rạp & phim lẻ trọn bộ miễn phí trên YouTube • Khung Sub đen chống đè",
            query = "Phim lẻ hành động chiếu rạp full movie HD vietsub thuyết minh 2025",
            country = CountryCategory.YOUTUBE,
            minDurationSec = 1200
        ),
        YouTubeShelfSpec(
            id = "yt_cdrama_official",
            title = "🇨🇳 Phim Bộ & Phim Ngắn Hoa Ngữ Official (iQIYI / WeTV / Youku)",
            subtitle = "Kênh YouTube bản quyền phụ đề chuẩn • Tự động chuyển sang Sub ไทย",
            query = "Phim ngôn tình cổ trang Trung Quốc trọn bộ full HD Vietsub Engsub",
            country = CountryCategory.CHINA,
            minDurationSec = 600
        ),
        YouTubeShelfSpec(
            id = "yt_kdrama_movies",
            title = "🇰🇷 Điện Ảnh & Phim Hàn Quốc Official Trên YouTube",
            subtitle = "Phim Hàn Quốc & K-Drama chính thức trên YouTube",
            query = "Korean Full Movie Eng Sub Korean Drama Full Episode Official HD",
            country = CountryCategory.KOREA,
            minDurationSec = 600
        ),
        YouTubeShelfSpec(
            id = "yt_hollywood_free",
            title = "🇺🇸 Phim Âu Mỹ & Sci-Fi Full Movies Free on YouTube",
            subtitle = "Bom tấn hành động & viễn tưởng Âu Mỹ có sẵn phụ đề chuẩn",
            query = "Full Movie Free Hollywood Action Sci Fi Thriller English Subtitles HD",
            country = CountryCategory.USA,
            minDurationSec = 1800
        )
    )

    suspend fun fetchFeaturedHomeRow(limit: Int = 16): List<MovieItem> = coroutineScope {
        val thaiDef = async {
            searchVideos(
                query = "GMMTV Official Full Episode Eng Sub หนังไทยเต็มเรื่อง",
                limit = 8,
                defaultCountry = CountryCategory.THAILAND,
                minDurationSec = 300
            )
        }
        val movieDef = async {
            searchVideos(
                query = "Full Movie Free Action Sci Fi English Subtitles Phim lẻ chiếu rạp Full HD",
                limit = 10,
                defaultCountry = CountryCategory.YOUTUBE,
                minDurationSec = 900
            )
        }
        (thaiDef.await() + movieDef.await())
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun fetchYouTubeTabShelves(): List< Pair<YouTubeShelfSpec, List<MovieItem>> > = coroutineScope {
        curatedShelfSpecs.map { spec ->
            async {
                val items = searchVideos(
                    query = spec.query,
                    limit = 16,
                    defaultCountry = spec.country,
                    minDurationSec = spec.minDurationSec
                )
                spec to items
            }
        }.awaitAll().filter { it.second.isNotEmpty() }
    }

    suspend fun searchVideos(
        query: String,
        limit: Int = 18,
        defaultCountry: CountryCategory = CountryCategory.YOUTUBE,
        minDurationSec: Int = 0
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        // If user pasted a direct YouTube link or yt:VIDEO_ID into search box, resolve it directly!
        extractVideoId(cleanQuery)?.let { directVid ->
            val single = resolveByVideoId(directVid)
            if (single != null) return@withContext listOf(single)
        }

        try {
            val payload = JSONObject().apply {
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB")
                            .put("clientVersion", "2.20250312.01.00")
                            .put("hl", "vi")
                            .put("gl", "VN")
                    )
                )
                put("query", cleanQuery)
            }

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", WEB_USER_AGENT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val renderers = ArrayList<JSONObject>(32)
                collectVideoRenderers(root, renderers)

                val results = ArrayList<MovieItem>(limit)
                val seenIds = HashSet<String>()

                for (vr in renderers) {
                    val videoId = vr.optString("videoId").trim()
                    if (videoId.length != 11 || !seenIds.add(videoId)) continue

                    val title = extractRunsText(vr.optJSONObject("title")).trim()
                    if (title.isEmpty()) continue

                    val lengthText = vr.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
                        .ifBlank { extractRunsText(vr.optJSONObject("lengthText")) }
                    val durationSec = parseDurationToSeconds(lengthText)
                    val isLive = lengthText.isBlank() && vr.optJSONArray("badges")?.toString()
                        .orEmpty().contains("LIVE", ignoreCase = true)

                    if (!isLive && minDurationSec > 0 && durationSec in 1 until minDurationSec) {
                        continue
                    }

                    val channelName = extractRunsText(vr.optJSONObject("ownerText"))
                        .ifBlank { extractRunsText(vr.optJSONObject("longBylineText")) }
                        .ifBlank { "YouTube" }
                    val published = vr.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
                    val snippet = extractRunsText(
                        vr.optJSONArray("detailedMetadataSnippets")
                            ?.optJSONObject(0)
                            ?.optJSONObject("snippetText")
                    )

                    val poster = "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    val backdrop = "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                    val hasThaiInTitle = title.any { it in '\u0E00'..'\u0E7F' }

                    results.add(
                        MovieItem(
                            id = "yt_$videoId",
                            title = title,
                            originalTitle = title,
                            year = published.ifBlank { "YouTube" },
                            country = if (hasThaiInTitle) CountryCategory.THAILAND else defaultCountry,
                            genre = "▶️ $channelName",
                            duration = if (isLive) "LIVE" else lengthText,
                            quality = if (isLive) "YT LIVE" else "YT 1080p",
                            posterUrl = poster,
                            backdropUrl = backdrop,
                            description = buildString {
                                append("Kênh YouTube: $channelName")
                                if (lengthText.isNotBlank()) append(" • Thời lượng: $lengthText")
                                if (published.isNotBlank()) append(" • $published")
                                if (snippet.isNotBlank()) append("\n\n$snippet")
                            },
                            contentType = if (isLive) ContentType.LIVE_CHANNEL else ContentType.MOVIE,
                            providerId = PROVIDER_ID,
                            providerName = "YouTube ($channelName)",
                            hasNativeThaiSub = hasThaiInTitle,
                            detailLookupId = videoId
                        )
                    )
                    if (results.size >= limit) break
                }
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveByUrlOrId(urlOrId: String, customTitle: String = ""): MovieItem? {
        val vid = extractVideoId(urlOrId) ?: urlOrId.trim().takeIf { RAW_11_ID_REGEX.matches(it) }
            ?: return null
        val stub = MovieItem(
            id = "yt_$vid",
            title = customTitle.ifBlank { "YouTube Video ($vid)" },
            originalTitle = customTitle,
            country = CountryCategory.YOUTUBE,
            genre = "▶️ YouTube",
            quality = "YT 1080p",
            posterUrl = "https://i.ytimg.com/vi/$vid/hq720.jpg",
            backdropUrl = "https://i.ytimg.com/vi/$vid/maxresdefault.jpg",
            providerId = PROVIDER_ID,
            providerName = "YouTube",
            detailLookupId = vid
        )
        val resolved = resolveVideoDetails(stub)
        return if (resolved.servers.isNotEmpty()) resolved else null
    }

    private suspend fun resolveByVideoId(videoId: String): MovieItem? =
        resolveByUrlOrId(videoId)

    /**
     * Resolves playable 1080p/720p H.264 video + M4A audio streams, progressive MP4 fallback,
     * and YouTube Official + Auto-CC caption tracks via YouTube InnerTube ANDROID / IOS clients.
     */
    suspend fun resolveVideoDetails(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        val videoId = movie.detailLookupId?.takeIf { it.isNotBlank() }
            ?: movie.id.removePrefix("yt_").takeIf { RAW_11_ID_REGEX.matches(it) }
            ?: return@withContext movie

        val androidJson = queryInnertubePlayer(
            videoId = videoId,
            clientName = "ANDROID",
            clientVersion = ANDROID_CLIENT_VERSION,
            userAgent = ANDROID_USER_AGENT,
            extraClientFields = mapOf(
                "androidSdkVersion" to 34,
                "osName" to "Android",
                "osVersion" to "14"
            )
        )

        val playStatus = androidJson?.optJSONObject("playabilityStatus")?.optString("status")
        val playerRoot = if (playStatus == "OK" && androidJson.optJSONObject("streamingData") != null) {
            androidJson
        } else {
            queryInnertubePlayer(
                videoId = videoId,
                clientName = "IOS",
                clientVersion = IOS_CLIENT_VERSION,
                userAgent = IOS_USER_AGENT,
                extraClientFields = mapOf(
                    "deviceMake" to "Apple",
                    "deviceModel" to "iPhone16,2",
                    "osName" to "iPhone",
                    "osVersion" to "18.3.2.22D82"
                )
            ) ?: return@withContext movie
        }

        val activeUa = if (playerRoot === androidJson) ANDROID_USER_AGENT else IOS_USER_AGENT
        val headers = mapOf("User-Agent" to activeUa)

        val videoDetails = playerRoot.optJSONObject("videoDetails")
        val realTitle = videoDetails?.optString("title")?.takeIf { it.isNotBlank() } ?: movie.title
        val author = videoDetails?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube"
        val shortDesc = videoDetails?.optString("shortDescription").orEmpty()
        val lengthSec = videoDetails?.optString("lengthSeconds", "0")?.toIntOrNull() ?: 0
        val isLive = videoDetails?.optBoolean("isLiveContent", false) == true

        val streamingData = playerRoot.optJSONObject("streamingData") ?: return@withContext movie
        val hlsUrl = streamingData.optString("hlsManifestUrl").trim()

        // Extract progressive muxed formats (e.g. itag 18 = 360p MP4, itag 22 = 720p MP4)
        var bestMuxedUrl = ""
        var bestMuxedHeight = 0
        val formatsArr = streamingData.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until formatsArr.length()) {
            val f = formatsArr.optJSONObject(i) ?: continue
            val url = f.optString("url").trim()
            if (url.isEmpty()) continue
            val h = f.optInt("height", 360)
            if (h >= bestMuxedHeight) {
                bestMuxedHeight = h
                bestMuxedUrl = url
            }
        }

        // Extract adaptive H.264 (avc1) 1080p/720p video + M4A (mp4a) audio for smooth TV360 hardware decode
        var bestVideoUrl = ""
        var bestVideoScore = -1
        var bestVideoLabel = "1080p"
        var bestAudioUrl = ""
        var bestAudioBitrate = -1

        val adaptiveArr = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
        for (i in 0 until adaptiveArr.length()) {
            val af = adaptiveArr.optJSONObject(i) ?: continue
            val url = af.optString("url").trim()
            if (url.isEmpty()) continue
            val mime = af.optString("mimeType").lowercase()

            if (mime.startsWith("video/")) {
                val height = af.optInt("height", 0)
                if (height !in 360..1080) continue // Cap at 1080p so TV360 Box never chokes on 4K AV1/VP9
                val isAvc = mime.contains("avc1")
                val isMp4 = mime.contains("video/mp4")
                // Strongly prefer H.264 (avc1) 1080p -> 720p -> 480p for universal Android TV hardware decoding
                val codecBonus = when {
                    isAvc -> 5000
                    isMp4 -> 2000
                    else -> 0
                }
                val score = codecBonus + height
                if (score > bestVideoScore) {
                    bestVideoScore = score
                    bestVideoUrl = url
                    bestVideoLabel = af.optString("qualityLabel").ifBlank { "${height}p" }
                }
            } else if (mime.startsWith("audio/")) {
                val bitrate = af.optInt("bitrate", 0)
                val isM4a = mime.contains("mp4a") || mime.contains("audio/mp4")
                // Prefer AAC M4A (~128kbps itag=140) over Opus WebM for clean MergingMediaSource sync
                val effectiveScore = (if (isM4a) 1_000_000 else 0) + bitrate
                if (effectiveScore > bestAudioBitrate) {
                    bestAudioBitrate = effectiveScore
                    bestAudioUrl = url
                }
            }
        }

        // Extract YouTube Official & Auto-Generated captionTracks
        val ytSubtitles = extractYouTubeCaptions(playerRoot, videoId)
        val hasHumanThai = ytSubtitles.any { it.isThai && !it.isAutoGenerated }

        val serverGroups = ArrayList<ServerGroup>(2)

        if (isLive && hlsUrl.isNotEmpty()) {
            serverGroups.add(
                ServerGroup(
                    serverName = "YouTube Live HLS",
                    episodes = listOf(
                        Episode(
                            id = "yt_${videoId}_live",
                            name = "Trực Tiếp (LIVE)",
                            streamUrl = hlsUrl,
                            headers = headers,
                            subtitles = ytSubtitles
                        )
                    )
                )
            )
        } else {
            if (bestVideoUrl.isNotEmpty() && bestAudioUrl.isNotEmpty()) {
                serverGroups.add(
                    ServerGroup(
                        serverName = "YouTube Full HD ($bestVideoLabel)",
                        episodes = listOf(
                            Episode(
                                id = "yt_${videoId}_hd",
                                name = "Full ($bestVideoLabel)",
                                streamUrl = bestVideoUrl,
                                audioStreamUrl = bestAudioUrl,
                                fallbackStreamUrl = bestMuxedUrl.ifBlank { hlsUrl },
                                headers = headers,
                                subtitles = ytSubtitles
                            )
                        )
                    )
                )
            }
            if (bestMuxedUrl.isNotEmpty()) {
                serverGroups.add(
                    ServerGroup(
                        serverName = "YouTube Nhanh (${bestMuxedHeight}p Mượt)",
                        episodes = listOf(
                            Episode(
                                id = "yt_${videoId}_fast",
                                name = "Bản Mượt ${bestMuxedHeight}p",
                                streamUrl = bestMuxedUrl,
                                headers = headers,
                                subtitles = ytSubtitles
                            )
                        )
                    )
                )
            } else if (hlsUrl.isNotEmpty() && serverGroups.isEmpty()) {
                serverGroups.add(
                    ServerGroup(
                        serverName = "YouTube HLS",
                        episodes = listOf(
                            Episode(
                                id = "yt_${videoId}_hls",
                                name = "Phát HLS",
                                streamUrl = hlsUrl,
                                headers = headers,
                                subtitles = ytSubtitles
                            )
                        )
                    )
                )
            }
        }

        movie.copy(
            title = realTitle,
            originalTitle = realTitle,
            genre = "▶️ $author",
            duration = if (isLive) "LIVE" else formatSeconds(lengthSec).ifBlank { movie.duration },
            quality = if (isLive) "YT LIVE" else "YT $bestVideoLabel",
            description = buildString {
                append("Kênh YouTube: $author")
                if (shortDesc.isNotBlank()) {
                    append("\n\n")
                    append(shortDesc.take(900))
                }
            },
            contentType = if (isLive) ContentType.LIVE_CHANNEL else ContentType.MOVIE,
            providerName = "YouTube ($author)",
            hasNativeThaiSub = hasHumanThai || movie.hasNativeThaiSub,
            servers = serverGroups
        )
    }

    private fun queryInnertubePlayer(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        extraClientFields: Map<String, Any>
    ): JSONObject? {
        return try {
            val clientObj = JSONObject()
                .put("clientName", clientName)
                .put("clientVersion", clientVersion)
                .put("hl", "th")
                .put("gl", "TH")
            for ((k, v) in extraClientFields) {
                clientObj.put(k, v)
            }

            val body = JSONObject()
                .put("context", JSONObject().put("client", clientObj))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true)

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", userAgent)
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                JSONObject(resp.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractYouTubeCaptions(playerRoot: JSONObject, videoId: String): List<ExternalSubtitleRef> {
        val trackList = playerRoot
            .optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
            ?: return emptyList()

        data class CapCandidate(
            val ref: ExternalSubtitleRef,
            val tier: Int,
            val langRank: Int,
            val baseUrl: String
        )

        val candidates = ArrayList<CapCandidate>()
        for (i in 0 until trackList.length()) {
            val tr = trackList.optJSONObject(i) ?: continue
            val baseUrl = tr.optString("baseUrl").trim()
            if (baseUrl.isEmpty()) continue
            val rawLang = tr.optString("languageCode").lowercase().trim()
            val isAsr = tr.optString("kind").equals("asr", ignoreCase = true)
            val normLang = normalizeLang(rawLang)
            val langRank = langPriority(normLang)

            val xmlUrl = if (baseUrl.contains("fmt=")) baseUrl else "$baseUrl&fmt=srv3"
            val isThai = normLang == "th"

            // Tier 1: Human Native Thai (NO AI NEEDED!)
            // Tier 2: Human Foreign Subtitles (for high-accuracy AI Thai translation)
            // Tier 3: YouTube Auto-Generated ASR (fallback when video has no human captions)
            val tier = when {
                isThai && !isAsr -> 1
                !isAsr -> 2
                isThai && isAsr -> 3
                else -> 4
            }

            val label = when {
                isThai && !isAsr -> "ภาษาไทย Gốc (YouTube Official)"
                !isAsr -> "YouTube Official (${normLang.uppercase()} -> AI ไทย)"
                isThai && isAsr -> "ภาษาไทย Tự Động (YouTube Auto-CC)"
                else -> "YouTube Auto-CC (${normLang.uppercase()} -> AI ไทย)"
            }

            candidates.add(
                CapCandidate(
                    ref = ExternalSubtitleRef(
                        id = "ytcap_${videoId}_${rawLang}_${if (isAsr) "asr" else "human"}_$i",
                        label = label,
                        languageCode = normLang,
                        urlOrContent = xmlUrl,
                        isRawContent = false,
                        sourcePlatform = if (isAsr) "YouTube Auto-CC" else "YouTube Official",
                        isAutoGenerated = isAsr
                    ),
                    tier = tier,
                    langRank = langRank,
                    baseUrl = xmlUrl
                )
            )
        }

        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedWith(compareBy<CapCandidate> { it.tier }.thenBy { it.langRank })
        val result = sorted.map { it.ref }.toMutableList()

        // Also append YouTube's server-side Thai translation (&tlang=th) at the very end as an extra safety net
        val hasAnyThai = sorted.any { it.ref.isThai }
        val firstTranslatable = sorted.firstOrNull()
        if (!hasAnyThai && firstTranslatable != null) {
            result.add(
                ExternalSubtitleRef(
                    id = "ytcap_${videoId}_tlang_th",
                    label = "ภาษาไทย (YouTube Auto-Translate)",
                    languageCode = "th",
                    urlOrContent = "${firstTranslatable.baseUrl}&tlang=th",
                    isRawContent = false,
                    sourcePlatform = "YouTube Translate",
                    isAutoGenerated = true
                )
            )
        }

        return result
    }

    private fun collectVideoRenderers(node: Any?, out: MutableList<JSONObject>) {
        if (out.size >= 40 || node == null) return
        when (node) {
            is JSONObject -> {
                val vr = node.optJSONObject("videoRenderer")
                if (vr != null) {
                    out.add(vr)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != "videoRenderer") {
                        collectVideoRenderers(node.opt(k), out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectVideoRenderers(node.opt(i), out)
                    if (out.size >= 40) break
                }
            }
        }
    }

    private fun extractRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    private fun parseDurationToSeconds(text: String): Int {
        val parts = text.trim().split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }

    private fun formatSeconds(totalSec: Int): String {
        if (totalSec <= 0) return ""
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun normalizeLang(raw: String): String = when {
        raw.startsWith("th") -> "th"
        raw.startsWith("vi") -> "vi"
        raw.startsWith("en") -> "en"
        raw.startsWith("zh") -> "zh"
        raw.startsWith("ko") -> "ko"
        raw.startsWith("ja") -> "ja"
        raw.startsWith("id") -> "id"
        else -> raw
    }

    private fun langPriority(norm: String): Int = when (norm) {
        "th" -> 1
        "vi" -> 2
        "en" -> 3
        "zh" -> 4
        "ko" -> 5
        "ja" -> 6
        "id" -> 7
        else -> 30
    }
}
