package com.phyll.filmhub.subtitle

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Multi-Platform Subtitle Aggregator:
 * Automatically searches major global subtitle platforms IN PARALLEL:
 *   1. OpenSubtitles.org Official REST API (VLSub / .gz .srt)
 *   2. YTS / YIFY Subtitles Global Cinema Repository (.zip .srt)
 *   3. OpenSubtitles v3 Global Index (UTF-8 .srt)
 *   (+ YouTube Official Human TimedText tracks attached by YouTubeSource)
 *
 * Strictly prioritizes NATIVE HUMAN THAI ("th" / "tha") across all platforms FIRST
 * so the app only switches to AI Thai translation when no major platform has a native Thai subtitle.
 */
class OnlineSubtitleFetcher(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val imdbResolutionCache = ConcurrentHashMap<String, String>()
    companion object {
        private val SEASON_REGEX = Regex(
            """(?i)(?:season|phần|phan|ss)\s*(\d{1,2})"""
        )
        private val PAREN_SEASON_STRIP_REGEX = Regex(
            """(?i)\s*[\(\[-–:]?\s*(?:season|phần|phan|ss)\s*\d{1,2}\s*[\)\]]?"""
        )
        private val YT_NOISE_BRACKETS_REGEX = Regex(
            """(?i)\[[^\]]*(?:sub|dub|hd|4k|1080|720|full|ep|tập|\d+/\d+|official|ost|trailer| thuyết minh| lồng tiếng)[^\]]*\]|\([^\)]*(?:sub|dub|hd|4k|1080|720|full movie|official|thuyết minh|lồng tiếng)[^\)]*\)"""
        )
        private val YT_TRAILING_NOISE_REGEX = Regex(
            """(?i)\s*(?:\||-)\s*(?:ep\.?\s*\d+|episode\s*\d+|tập\s*\d+|full\s*movie.*|official.*|vietsub.*|eng\s*sub.*|thuyết\s*minh.*)$"""
        )
        private val YT_EPISODE_IN_TITLE_REGEX = Regex(
            """(?i)(?:\bep\.?\s*|episode\s*|tập\s*)(\d{1,3})\b"""
        )
        private val EPISODE_NUM_REGEX = Regex("""(\d{1,4})""")
        private val YTS_ROW_REGEX = Regex(
            """(?is)<tr[^>]*>.*?<span class="sub-lang">\s*([^<]+?)\s*</span>.*?href="(/subtitles/([^"]+))""""
        )
        private val TOKEN_SPLIT_REGEX = Regex("""[^a-z0-9\p{L}]+""")
        private val STOP_ARTICLES = setOf("the", "a", "an")

        /**
         * Strict title + media-type + release-year scorer that prevents false-positive IMDb matches
         * (e.g., preventing a 2026 Chinese TV series "Dear Song" or "Dream" from matching an unrelated
         * 2023 feature film "A Song for My Dear" or a 2009 show).
         * Returns -1 when the candidate must be rejected.
         */
        fun scoreImdbCandidate(
            query: String,
            candidateLabel: String,
            qTypeRaw: String,
            targetYear: Int?,
            itemYear: Int,
            expectSeries: Boolean,
            season: Int = 1,
            rankIndex: Int = 0
        ): Int {
            val qType = qTypeRaw.lowercase().trim()
            if (qType.contains("video game") ||
                qType.contains("podcast") ||
                qType.contains("music")
            ) {
                return -1
            }

            val isTvSeries = (qType.contains("tv") && !qType.contains("tv movie")) ||
                qType.contains("series") ||
                qType.contains("mini")
            val isExplicitMovie = qType.contains("feature") ||
                qType == "movie" ||
                qType.contains("tv movie") ||
                qType.contains("short") ||
                qType == "video"

            // A multi-episode TV series must NEVER match a feature film
            if (expectSeries && (isExplicitMovie || (qType.isNotEmpty() && !isTvSeries))) {
                return -1
            }

            val normQuery = normalizeTitleForComparison(query)
            val normLabel = normalizeTitleForComparison(candidateLabel)
            if (normQuery.isEmpty() || normLabel.isEmpty()) return -1

            val qTokens = tokenizeTitle(normQuery)
            val lTokens = tokenizeTitle(normLabel)
            if (qTokens.isEmpty() || lTokens.isEmpty()) return -1

            val exactMatch = normQuery == normLabel
            val interSize = qTokens.intersect(lTokens).size
            val unionSize = qTokens.union(lTokens).size.coerceAtLeast(1)
            val jaccard = interSize.toDouble() / unionSize.toDouble()
            val colonPrefixMatch = normQuery.length >= 6 &&
                candidateLabel.lowercase().trim().startsWith("${query.lowercase().trim()}:")

            if (!exactMatch && jaccard < 0.65 && !colonPrefixMatch) {
                return -1
            }

            // Strict year validation so brand-new 2025/2026 dramas with generic English names ("Dream", "Hamlet")
            // never pull subtitles from unrelated older movies/shows
            if (targetYear != null && targetYear > 1900 && itemYear > 1900) {
                if (!expectSeries) {
                    if (abs(itemYear - targetYear) >= 3) return -1
                } else {
                    if (itemYear > targetYear + 1) return -1
                    if (season <= 1 && abs(itemYear - targetYear) >= 3) return -1
                    if (season > 1 && itemYear < targetYear - season - 6) return -1
                }
            }

            var score = 40 - (rankIndex * 5)
            score += when {
                exactMatch -> 95
                colonPrefixMatch -> 65
                else -> (jaccard * 75.0).toInt()
            }

            if (targetYear != null && itemYear > 1900) {
                val diff = abs(itemYear - targetYear)
                score += when {
                    diff == 0 -> 50
                    diff == 1 -> 28
                    diff == 2 -> 10
                    else -> 0
                }
            }

            if (expectSeries && isTvSeries) score += 25
            if (!expectSeries && !isTvSeries) score += 20
            if (!expectSeries && isTvSeries) score -= 45

            return if (score >= 115) score else -1
        }

        private fun normalizeTitleForComparison(raw: String): String {
            return raw.lowercase()
                .replace("&", " and ")
                .replace(TOKEN_SPLIT_REGEX, " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        private fun tokenizeTitle(normalized: String): Set<String> {
            val rawTokens = normalized.split(' ').filter { it.isNotEmpty() }
            if (rawTokens.size <= 2) return rawTokens.toSet()
            val filtered = rawTokens.filterNot { it in STOP_ARTICLES }
            return (if (filtered.isNotEmpty()) filtered else rawTokens).toSet()
        }
    }

    suspend fun discoverSubtitles(
        movie: MovieItem,
        episode: Episode
    ): List<ExternalSubtitleRef> = withContext(Dispatchers.IO) {
        val season = extractSeasonNumber(movie.originalTitle, movie.title)
        val epFromName = extractEpisodeNumber(episode.name)
        val epFromTitle = YT_EPISODE_IN_TITLE_REGEX.find(movie.title)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val epNum = if (epFromName > 1) epFromName else (epFromTitle ?: epFromName)

        val isSeries = movie.contentType == ContentType.SERIES ||
            (movie.servers.firstOrNull()?.episodes?.size ?: 0) > 1 ||
            epNum > 1 || season > 1

        // 1. Resolve IMDb tt-ID (use explicit movie.imdbId if present, else search by cleaned originalTitle / title)
        val imdbId = movie.imdbId.takeIf { it.startsWith("tt") }
            ?: resolveImdbId(
                rawOriginalTitle = movie.originalTitle,
                fallbackTitle = movie.title,
                yearStr = movie.year,
                expectSeries = isSeries,
                season = season
            )
            ?: return@withContext emptyList()

        fetchFromAllMajorPlatforms(
            imdbId = imdbId,
            isSeries = isSeries,
            season = season,
            episode = epNum
        )
    }

    suspend fun discoverByCustomQuery(
        customQuery: String,
        isSeries: Boolean,
        season: Int = 1,
        episode: Int = 1
    ): List<ExternalSubtitleRef> = withContext(Dispatchers.IO) {
        val clean = customQuery.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val imdbId = if (clean.startsWith("tt") && clean.drop(2).all { it.isDigit() }) {
            clean
        } else {
            resolveImdbId(
                rawOriginalTitle = clean,
                fallbackTitle = clean,
                yearStr = "",
                expectSeries = isSeries,
                season = season
            )
        } ?: return@withContext emptyList()

        fetchFromAllMajorPlatforms(
            imdbId = imdbId,
            isSeries = isSeries,
            season = season.coerceAtLeast(1),
            episode = episode.coerceAtLeast(1)
        )
    }

    fun extractSeasonNumber(originalTitle: String, localTitle: String): Int {
        val m1 = SEASON_REGEX.find(originalTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (m1 != null && m1 in 1..30) return m1
        val m2 = SEASON_REGEX.find(localTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (m2 != null && m2 in 1..30) return m2
        return 1
    }

    fun extractEpisodeNumber(episodeName: String): Int {
        val lower = episodeName.lowercase()
        if (lower.contains("full") || lower.contains("1080p") || lower.contains("720p") || lower.contains("360p")) {
            return 1
        }
        val num = EPISODE_NUM_REGEX.find(episodeName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (num != null && num in 1..2000) num else 1
    }

    fun cleanTitleForImdbLookup(raw: String): String {
        var s = raw
            .replace(YT_NOISE_BRACKETS_REGEX, " ")
            .replace(PAREN_SEASON_STRIP_REGEX, " ")
            .replace(YT_TRAILING_NOISE_REGEX, " ")
        // Strip common VOD/YouTube suffixes
        s = s.replace(
            Regex("""(?i)\b(full\s*movie|full\s*hd|vietsub|eng\s*sub|thai\s*sub|thuyết\s*minh|lồng\s*tiếng|1080p|720p|4k)\b"""),
            " "
        )
        return s.replace(Regex("""\s+"""), " ").trim(' ', '|', '-', '–', ':')
    }

    /**
     * Queries OpenSubtitles.org REST, YIFY Subtitles, and OpenSubtitles v3 IN PARALLEL,
     * putting ALL Native Human Thai tracks across every platform at the very front!
     */
    private suspend fun fetchFromAllMajorPlatforms(
        imdbId: String,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): List<ExternalSubtitleRef> = coroutineScope {
        val osRestThaiDeferred = async {
            withTimeoutOrNull(4_800L) {
                fetchOpenSubtitlesOrgRest(imdbId, isSeries, season, episode, subLangId = "tha")
            } ?: emptyList()
        }
        val osRestBackupDeferred = async {
            withTimeoutOrNull(4_800L) {
                fetchOpenSubtitlesOrgRest(imdbId, isSeries, season, episode, subLangId = "vie,eng")
            } ?: emptyList()
        }
        val osV3Deferred = async {
            withTimeoutOrNull(4_800L) {
                fetchOpenSubtitlesV3ByImdb(imdbId, isSeries, season, episode)
            } ?: emptyList()
        }
        val yifyDeferred = async {
            if (!isSeries) {
                withTimeoutOrNull(4_800L) { fetchYifySubtitlesByImdb(imdbId) } ?: emptyList()
            } else {
                emptyList()
            }
        }

        val osRestThai = osRestThaiDeferred.await()
        val yifySubs = yifyDeferred.await()
        val osV3Subs = osV3Deferred.await()
        val osRestBackup = osRestBackupDeferred.await()

        val allMerged = ArrayList<ExternalSubtitleRef>(24)
        val seenUrls = HashSet<String>()

        fun appendUnique(list: List<ExternalSubtitleRef>) {
            for (item in list) {
                if (item.urlOrContent.isNotBlank() && seenUrls.add(item.urlOrContent)) {
                    allMerged.add(item)
                }
            }
        }

        // 1. NATIVE HUMAN THAI FIRST across ALL major subtitle platforms!
        appendUnique(osRestThai.filter { it.isThai })
        appendUnique(yifySubs.filter { it.isThai })
        appendUnique(osV3Subs.filter { it.isThai })

        // 2. NON-THAI HUMAN SUBTITLES (for AI translation fallback ONLY if Native Thai fails/absent)
        val nonThaiCandidates = (osV3Subs + yifySubs + osRestBackup).filter { !it.isThai }
        val sortedNonThai = nonThaiCandidates.sortedBy { mapLanguagePriority(it.languageCode).second }
        appendUnique(sortedNonThai)

        allMerged.take(20)
    }

    /**
     * Platform 1: Official OpenSubtitles.org REST API (VLSub protocol).
     * Note: Path parameters MUST be sorted alphabetically by key name:
     *   Series: /search/episode-{e}/imdbid-{num}/season-{s}/sublanguageid-{lang}
     *   Movie:  /search/imdbid-{num}/sublanguageid-{lang}
     */
    private fun fetchOpenSubtitlesOrgRest(
        imdbId: String,
        isSeries: Boolean,
        season: Int,
        episode: Int,
        subLangId: String
    ): List<ExternalSubtitleRef> {
        return try {
            val numericImdb = imdbId.removePrefix("tt").trimStart('0').ifEmpty { "0" }
            val endpoint = if (isSeries) {
                "https://rest.opensubtitles.org/search/episode-$episode/imdbid-$numericImdb/season-$season/sublanguageid-$subLangId"
            } else {
                "https://rest.opensubtitles.org/search/imdbid-$numericImdb/sublanguageid-$subLangId"
            }

            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "TemporaryUserAgent")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty().trim()
                if (!body.startsWith("[")) return emptyList()
                val arr = JSONArray(body)
                if (arr.length() == 0) return emptyList()

                data class RestItem(
                    val id: String,
                    val normLang: String,
                    val rank: Int,
                    val downloads: Int,
                    val dlUrl: String
                )

                val parsed = ArrayList<RestItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val fmt = obj.optString("SubFormat").lowercase()
                    if (fmt !in listOf("srt", "vtt", "ass", "ssa", "sub")) continue
                    val dlUrl = obj.optString("SubDownloadLink").trim()
                    if (dlUrl.isEmpty()) continue
                    val rawLang = obj.optString("SubLanguageID").lowercase().trim()
                    val (norm, rank) = mapLanguagePriority(rawLang)
                    val downloads = obj.optString("SubDownloadsCnt", "0").toIntOrNull() ?: 0
                    parsed.add(
                        RestItem(
                            id = obj.optString("IDSubtitleFile", "${numericImdb}_$i"),
                            normLang = norm,
                            rank = rank,
                            downloads = downloads,
                            dlUrl = dlUrl
                        )
                    )
                }

                val perLang = HashMap<String, Int>()
                parsed
                    .sortedWith(compareBy<RestItem> { it.rank }.thenByDescending { it.downloads })
                    .filter { item ->
                        val count = perLang.getOrDefault(item.normLang, 0)
                        val limit = if (item.normLang == "th") 3 else 2
                        if (count < limit) {
                            perLang[item.normLang] = count + 1
                            true
                        } else false
                    }
                    .mapIndexed { idx, item ->
                        ExternalSubtitleRef(
                            id = "osrest_${imdbId}_${item.normLang}_${idx + 1}",
                            label = buildPlatformDisplayLabel("OpenSubtitles.org", item.normLang, idx + 1),
                            languageCode = item.normLang,
                            urlOrContent = item.dlUrl,
                            isRawContent = false,
                            sourcePlatform = "OpenSubtitles.org",
                            isAutoGenerated = false
                        )
                    }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Platform 2: YTS / YIFY Subtitles Global Cinema Repository (yts-subs.com -> subtitles.yts-subs.com/{slug}.zip).
     */
    private fun fetchYifySubtitlesByImdb(imdbId: String): List<ExternalSubtitleRef> {
        return try {
            val req = Request.Builder()
                .url("https://yts-subs.com/movie-imdb/$imdbId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val html = resp.body?.string().orEmpty()
                if (html.isEmpty()) return emptyList()

                data class YifyRow(val normLang: String, val rank: Int, val slug: String)
                val rows = ArrayList<YifyRow>()

                for (m in YTS_ROW_REGEX.findAll(html)) {
                    val langName = m.groupValues[1].trim().lowercase()
                    val slug = m.groupValues[3].trim()
                    if (slug.isEmpty()) continue
                    val iso = mapYifyLanguageName(langName) ?: continue
                    val (norm, rank) = mapLanguagePriority(iso)
                    rows.add(YifyRow(normLang = norm, rank = rank, slug = slug))
                }

                if (rows.isEmpty()) return emptyList()

                val perLang = HashMap<String, Int>()
                rows.sortedBy { it.rank }
                    .filter { r ->
                        val c = perLang.getOrDefault(r.normLang, 0)
                        val maxKeep = if (r.normLang == "th") 3 else 2
                        if (c < maxKeep) {
                            perLang[r.normLang] = c + 1
                            true
                        } else false
                    }
                    .mapIndexed { idx, r ->
                        ExternalSubtitleRef(
                            id = "yify_${imdbId}_${r.normLang}_${idx + 1}",
                            label = buildPlatformDisplayLabel("YIFY Subtitles", r.normLang, idx + 1),
                            languageCode = r.normLang,
                            urlOrContent = "https://subtitles.yts-subs.com/subtitles/${r.slug}.zip",
                            isRawContent = false,
                            sourcePlatform = "YIFY Subtitles",
                            isAutoGenerated = false
                        )
                    }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapYifyLanguageName(langName: String): String? = when {
        langName.contains("thai") -> "th"
        langName.contains("vietnamese") -> "vi"
        langName == "english" -> "en"
        langName.contains("chinese") -> "zh"
        langName.contains("korean") -> "ko"
        langName.contains("japanese") -> "ja"
        langName.contains("indonesian") || langName.contains("malay") -> "id"
        else -> null
    }

    /**
     * Platform 3: OpenSubtitles v3 Stremio Global Index (UTF-8 .srt for Movies & TV Series).
     */
    private fun fetchOpenSubtitlesV3ByImdb(
        imdbId: String,
        isSeries: Boolean,
        season: Int,
        episode: Int
    ): List<ExternalSubtitleRef> {
        // NEVER fall back to `/subtitles/movie/` when `isSeries == true` — loading a 2-hour movie .SRT
        // onto Episode 1..100 of a TV series causes impossible-to-sync wrong subtitles!
        val urlsToTry = if (isSeries) {
            buildList {
                add("https://opensubtitles-v3.strem.io/subtitles/series/$imdbId:$season:$episode.json")
                if (season > 1) {
                    add("https://opensubtitles-v3.strem.io/subtitles/series/$imdbId:1:$episode.json")
                }
            }
        } else {
            listOf(
                "https://opensubtitles-v3.strem.io/subtitles/movie/$imdbId.json",
                "https://opensubtitles-v3.strem.io/subtitles/series/$imdbId:1:1.json"
            )
        }

        for (endpoint in urlsToTry) {
            val refs = queryStremioOpenSubtitlesEndpoint(endpoint, imdbId)
            if (refs.isNotEmpty()) return refs
        }
        return emptyList()
    }

    private fun queryStremioOpenSubtitlesEndpoint(
        endpoint: String,
        imdbId: String
    ): List<ExternalSubtitleRef> {
        return try {
            val req = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "Mozilla/5.0 PhyllFilmHub/2.1")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val subsArr = JSONObject(resp.body?.string().orEmpty())
                    .optJSONArray("subtitles") ?: return emptyList()
                if (subsArr.length() == 0) return emptyList()

                data class RawSub(val id: String, val normLang: String, val url: String, val rank: Int)

                val candidates = ArrayList<RawSub>()
                for (i in 0 until subsArr.length()) {
                    val s = subsArr.optJSONObject(i) ?: continue
                    val subUrl = s.optString("url").trim()
                    val rawLang = s.optString("lang").lowercase().trim()
                    if (subUrl.isEmpty() || rawLang.isEmpty()) continue
                    val (norm, rank) = mapLanguagePriority(rawLang)
                    if (rank <= 50) {
                        candidates.add(
                            RawSub(
                                id = s.optString("id", "${imdbId}_$i"),
                                normLang = norm,
                                url = subUrl,
                                rank = rank
                            )
                        )
                    }
                }

                if (candidates.isEmpty()) return emptyList()

                val perLangCounts = HashMap<String, Int>()
                val selected = candidates
                    .sortedBy { it.rank }
                    .filter { c ->
                        val count = perLangCounts.getOrDefault(c.normLang, 0)
                        val maxForLang = if (c.normLang in listOf("th", "vi", "en")) 3 else 1
                        if (count < maxForLang) {
                            perLangCounts[c.normLang] = count + 1
                            true
                        } else {
                            false
                        }
                    }
                    .take(12)

                val langCounters = HashMap<String, Int>()
                selected.map { c ->
                    val n = (langCounters.getOrDefault(c.normLang, 0) + 1).also {
                        langCounters[c.normLang] = it
                    }
                    ExternalSubtitleRef(
                        id = "osv3_${imdbId}_${c.normLang}_$n",
                        label = buildPlatformDisplayLabel("OpenSubtitles v3", c.normLang, n),
                        languageCode = c.normLang,
                        urlOrContent = c.url,
                        isRawContent = false,
                        sourcePlatform = "OpenSubtitles v3",
                        isAutoGenerated = false
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveImdbId(
        rawOriginalTitle: String,
        fallbackTitle: String,
        yearStr: String,
        expectSeries: Boolean,
        season: Int = 1
    ): String? {
        val cacheKey = "${rawOriginalTitle.trim().lowercase()}|${fallbackTitle.trim().lowercase()}|${yearStr.trim()}|$expectSeries|$season"
        imdbResolutionCache[cacheKey]?.let { cached ->
            return if (cached == "NONE") null else cached
        }

        val targetYear = yearStr.trim().take(4).toIntOrNull()
        val candidateQueries = buildCandidateQueries(rawOriginalTitle, fallbackTitle)

        for (query in candidateQueries) {
            val found = queryImdbSuggestion(query, targetYear, expectSeries, season)
            if (found != null) {
                if (imdbResolutionCache.size > 120) imdbResolutionCache.clear()
                imdbResolutionCache[cacheKey] = found
                return found
            }
        }
        if (imdbResolutionCache.size > 120) imdbResolutionCache.clear()
        imdbResolutionCache[cacheKey] = "NONE"
        return null
    }

    private fun buildCandidateQueries(originalTitle: String, fallbackTitle: String): List<String> {
        val set = LinkedHashSet<String>()
        for (raw in listOf(originalTitle, fallbackTitle)) {
            val cleaned = cleanTitleForImdbLookup(raw)
            val parts = cleaned.split('/', '|').map { it.trim() }.filter { it.length >= 2 }
            for (p in parts) {
                set.add(p)
                // Also try ASCII/Latin portion if title mixes Latin + Thai/CJK scripts (common on YouTube)
                val latinOnly = p.replace(Regex("""[^\x20-\x7E]+"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (latinOnly.length >= 3) {
                    set.add(latinOnly)
                }
            }
            if (cleaned.length >= 2) set.add(cleaned)
        }
        return set.take(6)
    }

    private fun queryImdbSuggestion(
        query: String,
        targetYear: Int?,
        expectSeries: Boolean,
        season: Int = 1
    ): String? {
        return try {
            val cleaned = query.trim()
            if (cleaned.length < 2) return null
            val firstChar = cleaned.firstOrNull { it.isLetterOrDigit() && it.code < 128 }
                ?.lowercaseChar() ?: 'x'
            val encoded = URLEncoder.encode(cleaned, "UTF-8")
            val url = "https://v3.sg.media-imdb.com/suggestion/$firstChar/$encoded.json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 PhyllFilmHub/2.3")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("d") ?: return null
                var bestId: String? = null
                var bestScore = -1

                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (!id.startsWith("tt")) continue

                    val label = item.optString("l")
                    val qType = item.optString("q")
                    val itemYear = item.optInt("y", 0)

                    val score = scoreImdbCandidate(
                        query = cleaned,
                        candidateLabel = label,
                        qTypeRaw = qType,
                        targetYear = targetYear,
                        itemYear = itemYear,
                        expectSeries = expectSeries,
                        season = season,
                        rankIndex = i
                    )

                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
                bestId
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapLanguagePriority(rawIso: String): Pair<String, Int> = when (rawIso.lowercase()) {
        "tha", "th" -> "th" to 1
        "vie", "vi" -> "vi" to 2
        "eng", "en" -> "en" to 3
        "zho", "zht", "chi", "zh", "zh-cn", "zh-tw", "zh-hans", "zh-hant" -> "zh" to 4
        "kor", "ko" -> "ko" to 5
        "jpn", "ja" -> "ja" to 6
        "ind", "id", "may", "ms" -> "id" to 7
        "fre", "fra", "fr" -> "fr" to 8
        "spa", "spl", "es" -> "es" to 9
        else -> rawIso.lowercase() to 60
    }

    private fun buildPlatformDisplayLabel(platform: String, normLang: String, index: Int): String {
        val suffix = if (index > 1) " #$index" else ""
        return when (normLang) {
            "th" -> "ภาษาไทย Gốc ($platform$suffix)"
            "vi" -> "Tiếng Việt ($platform$suffix -> AI ไทย)"
            "en" -> "English ($platform$suffix -> AI ไทย)"
            "zh" -> "Tiếng Trung ($platform$suffix -> AI ไทย)"
            "ko" -> "Tiếng Hàn ($platform$suffix -> AI ไทย)"
            "ja" -> "Tiếng Nhật ($platform$suffix -> AI ไทย)"
            "id" -> "Indonesian ($platform$suffix -> AI ไทย)"
            else -> "${normLang.uppercase()} ($platform$suffix -> AI ไทย)"
        }
    }
}
