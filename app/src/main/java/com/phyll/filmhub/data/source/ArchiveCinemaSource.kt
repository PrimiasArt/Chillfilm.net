package com.phyll.filmhub.data.source

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fetches legal, free, direct-streaming international feature films from Internet Archive
 * across USA, UK, China/HK, South Korea, and Thailand, and automatically extracts playable
 * H.264 MP4 streams + attached .SRT / .VTT subtitles from item metadata.
 */
class ArchiveCinemaSource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    private val blockedKeywords = listOf(
        "sex", "adult", "erotic", "porn", "xxx", "nude", "capcut", "tiktok", "template"
    )

    suspend fun fetchMoviesByCountry(
        country: CountryCategory,
        rows: Int = 18,
        page: Int = 1
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val query = buildCountryQuery(country) ?: return@withContext emptyList()
        executeSearchQuery(query = query, defaultCountry = country, rows = rows, page = page)
    }

    suspend fun searchMovies(keyword: String, rows: Int = 20): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val safeKw = keyword.trim().replace("\"", "")
            if (safeKw.isEmpty()) return@withContext emptyList()
            val q = "collection:(feature_films OR sci_fi_thriller_films OR comedy_films OR Film_Noir OR moviesandfilms) " +
                "AND mediatype:movies AND (title:($safeKw) OR description:($safeKw) OR subject:($safeKw)) " +
                "-subject:(adult OR erotica)"
            executeSearchQuery(query = q, defaultCountry = CountryCategory.GLOBAL, rows = rows, page = 1)
        }

    private fun buildCountryQuery(country: CountryCategory): String? {
        val safetyFilter = "-subject:(adult OR erotica) -title:(sex OR capcut OR template)"
        return when (country) {
            CountryCategory.USA, CountryCategory.ALL ->
                "collection:(feature_films OR sci_fi_thriller_films OR Film_Noir) AND mediatype:movies $safetyFilter"
            CountryCategory.UK ->
                "collection:feature_films AND mediatype:movies AND (subject:British OR subject:England OR subject:London OR subject:UK) $safetyFilter"
            CountryCategory.CHINA ->
                "mediatype:movies AND collection:(feature_films OR moviesandfilms) AND (subject:China OR subject:Chinese OR subject:\"Kung Fu\" OR subject:\"Hong Kong\" OR subject:\"Martial Arts\") $safetyFilter"
            CountryCategory.KOREA ->
                "mediatype:movies AND collection:(feature_films OR moviesandfilms) AND (subject:Korea OR subject:Korean OR title:Korea) $safetyFilter"
            CountryCategory.THAILAND ->
                "mediatype:movies AND collection:(feature_films OR moviesandfilms) AND (subject:Thailand OR subject:Thai OR subject:Bangkok OR language:tha) $safetyFilter"
            CountryCategory.JAPAN ->
                "mediatype:movies AND collection:(feature_films OR moviesandfilms) AND (subject:Japan OR subject:Japanese OR subject:Anime) $safetyFilter"
            CountryCategory.GLOBAL ->
                "collection:(sci_fi_thriller_films OR comedy_films) AND mediatype:movies $safetyFilter"
            CountryCategory.LIVE_TV, CountryCategory.YOUTUBE -> null
        }
    }

    private fun executeSearchQuery(
        query: String,
        defaultCountry: CountryCategory,
        rows: Int,
        page: Int
    ): List<MovieItem> {
        return try {
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$encodedQ" +
                "&fl[]=identifier&fl[]=title&fl[]=description&fl[]=year&fl[]=subject" +
                "&sort[]=downloads+desc&rows=$rows&page=$page&output=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "PhyllFilmHub/1.0 (Android TV)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                val docs = JSONObject(body)
                    .optJSONObject("response")
                    ?.optJSONArray("docs") ?: return emptyList()

                val items = ArrayList<MovieItem>(docs.length())
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    val identifier = doc.optString("identifier").trim()
                    val title = doc.optString("title").trim()
                    if (identifier.isEmpty() || title.isEmpty()) continue
                    if (identifier.startsWith("youtube-", ignoreCase = true)) continue

                    val lowerCheck = "$identifier $title".lowercase()
                    if (blockedKeywords.any { lowerCheck.contains(it) }) continue

                    val year = doc.optString("year").take(4)
                    val rawDesc = doc.optString("description")
                        .replace(Regex("<[^>]*>"), "")
                        .trim()
                        .take(420)

                    val resolvedCountry = if (defaultCountry == CountryCategory.ALL) {
                        CountryCategory.USA
                    } else {
                        defaultCountry
                    }

                    val thumbUrl = "https://archive.org/services/img/$identifier"
                    items.add(
                        MovieItem(
                            id = "archive_$identifier",
                            title = title,
                            originalTitle = identifier.replace('_', ' ').replace('-', ' '),
                            year = year,
                            country = resolvedCountry,
                            genre = "Điện ảnh ${resolvedCountry.displayName}",
                            quality = "VOD • AI ไทย",
                            posterUrl = thumbUrl,
                            backdropUrl = thumbUrl,
                            description = rawDesc.ifBlank {
                                "Phim điện ảnh quốc tế từ kho lưu trữ Internet Archive. Tự động nạp phụ đề và AI dịch sang Tiếng Thái."
                            },
                            contentType = ContentType.MOVIE,
                            providerId = "archive",
                            providerName = "Archive Cinema",
                            hasNativeThaiSub = resolvedCountry == CountryCategory.THAILAND,
                            detailLookupId = identifier
                        )
                    )
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolves playable .mp4 stream URL(s) and any attached .srt / .vtt subtitles
     * from https://archive.org/metadata/{identifier}.
     */
    suspend fun resolveMovieDetails(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        if (movie.servers.isNotEmpty()) return@withContext movie
        val identifier = movie.detailLookupId ?: movie.id.removePrefix("archive_")
        try {
            val url = "https://archive.org/metadata/$identifier"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "PhyllFilmHub/1.0 (Android TV)")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext movie
                val root = JSONObject(resp.body?.string().orEmpty())
                val server = root.optString("server").ifBlank { "archive.org" }
                val dir = root.optString("dir")
                if (dir.isBlank()) return@withContext movie
                val files = root.optJSONArray("files") ?: return@withContext movie

                val mp4Files = mutableListOf<Pair<String, Long>>()
                val subtitleRefs = mutableListOf<ExternalSubtitleRef>()

                for (i in 0 until files.length()) {
                    val f = files.optJSONObject(i) ?: continue
                    val name = f.optString("name")
                    val lower = name.lowercase()
                    val encodedPath = name.split('/').joinToString("/") { seg ->
                        URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                    }
                    val fullUrl = "https://$server$dir/$encodedPath"

                    when {
                        lower.endsWith(".mp4") && !lower.contains("ia_thumb") -> {
                            val size = f.optString("size").toLongOrNull() ?: 0L
                            mp4Files.add(fullUrl to size)
                        }
                        lower.endsWith(".srt") || lower.endsWith(".vtt") -> {
                            val langGuess = when {
                                lower.contains(".th.") || lower.contains("thai") -> "th"
                                lower.contains(".vi.") || lower.contains("viet") -> "vi"
                                lower.contains(".zh.") || lower.contains("chi") -> "zh"
                                lower.contains(".ko.") || lower.contains("kor") -> "ko"
                                else -> "en"
                            }
                            val label = when (langGuess) {
                                "th" -> "ภาษาไทย ($name)"
                                "vi" -> "Tiếng Việt ($name)"
                                else -> " Phụ đề gốc ($name -> AI ไทย)"
                            }
                            subtitleRefs.add(
                                ExternalSubtitleRef(
                                    id = "ia_sub_${subtitleRefs.size}_$identifier",
                                    label = label.trim(),
                                    languageCode = langGuess,
                                    urlOrContent = fullUrl,
                                    isRawContent = false
                                )
                            )
                        }
                    }
                }

                if (mp4Files.isEmpty()) return@withContext movie

                // Sort MP4 candidates so highest quality reasonable stream is primary,
                // while keeping lighter encode as backup server for smooth playback
                val sortedMp4 = mp4Files.sortedByDescending { it.second }
                val episodes = sortedMp4.take(3).mapIndexed { index, (streamUrl, sizeBytes) ->
                    val mb = if (sizeBytes > 0) " (${sizeBytes / (1024 * 1024)} MB)" else ""
                    val qualityTag = if (index == 0) "Bản Chất Lượng Cao$mb" else "Bản Nhẹ Mượt$mb"
                    Episode(
                        id = "${identifier}_ep_$index",
                        name = qualityTag,
                        streamUrl = streamUrl,
                        subtitles = subtitleRefs
                    )
                }

                movie.copy(
                    hasNativeThaiSub = movie.hasNativeThaiSub || subtitleRefs.any { it.isThai },
                    servers = listOf(
                        ServerGroup(
                            serverName = "Archive Direct CDN",
                            episodes = episodes
                        )
                    )
                )
            }
        } catch (_: Exception) {
            movie
        }
    }
}
