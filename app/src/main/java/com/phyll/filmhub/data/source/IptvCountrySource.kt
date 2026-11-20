package com.phyll.filmhub.data.source

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches live public international Movie & Broadcast TV streams by country
 * (Thailand, South Korea, China, USA, UK) using open M3U playlists.
 */
class IptvCountrySource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val COUNTRY_M3U_URLS = mapOf(
            CountryCategory.THAILAND to "https://iptv-org.github.io/iptv/countries/th.m3u",
            CountryCategory.KOREA to "https://iptv-org.github.io/iptv/countries/kr.m3u",
            CountryCategory.CHINA to "https://iptv-org.github.io/iptv/countries/cn.m3u",
            CountryCategory.UK to "https://iptv-org.github.io/iptv/countries/uk.m3u",
            CountryCategory.USA to "https://iptv-org.github.io/iptv/categories/movies.m3u",
            CountryCategory.LIVE_TV to "https://iptv-org.github.io/iptv/categories/movies.m3u"
        )

        private val ATTR_LOGO_REGEX = Regex("""tvg-logo="([^"]*)"""")
        private val ATTR_GROUP_REGEX = Regex("""group-title="([^"]*)"""")
    }

    suspend fun fetchChannelsForCountry(
        country: CountryCategory,
        limit: Int = 24
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        val url = COUNTRY_M3U_URLS[country] ?: return@withContext emptyList()
        fetchAndParseM3u(
            playlistUrl = url,
            country = if (country == CountryCategory.LIVE_TV) CountryCategory.GLOBAL else country,
            providerId = "iptv_${country.code}",
            providerName = "Live TV ${country.displayName}",
            isLiveDefault = true,
            limit = limit
        )
    }

    suspend fun fetchAndParseM3u(
        playlistUrl: String,
        country: CountryCategory,
        providerId: String,
        providerName: String,
        isLiveDefault: Boolean = false,
        limit: Int = 80
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", "Mozilla/5.0 PhyllFilmHub/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val text = resp.body?.string().orEmpty()
                parseM3uContent(
                    content = text,
                    defaultCountry = country,
                    providerId = providerId,
                    providerName = providerName,
                    isLiveDefault = isLiveDefault,
                    limit = limit
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseM3uContent(
        content: String,
        defaultCountry: CountryCategory,
        providerId: String,
        providerName: String,
        isLiveDefault: Boolean = false,
        limit: Int = 80
    ): List<MovieItem> {
        val lines = content.replace("\r\n", "\n").split('\n')
        val result = ArrayList<MovieItem>()

        var pendingTitle = ""
        var pendingLogo = ""
        var pendingGroup = ""
        val pendingHeaders = LinkedHashMap<String, String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pendingTitle = line.substringAfterLast(',').trim()
                    pendingLogo = ATTR_LOGO_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    pendingGroup = ATTR_GROUP_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    pendingHeaders.clear()
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    when {
                        opt.startsWith("http-user-agent=", ignoreCase = true) -> {
                            pendingHeaders["User-Agent"] = opt.substringAfter('=').trim()
                        }
                        opt.startsWith("http-referrer=", ignoreCase = true) ||
                            opt.startsWith("http-referer=", ignoreCase = true) -> {
                            pendingHeaders["Referer"] = opt.substringAfter('=').trim()
                        }
                    }
                }
                !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://")) -> {
                    if (pendingTitle.isNotEmpty()) {
                        val lowerGroup = pendingGroup.lowercase()
                        if (!lowerGroup.contains("xxx") && !lowerGroup.contains("adult")) {
                            val isVodFile = line.endsWith(".mp4", ignoreCase = true) ||
                                line.endsWith(".mkv", ignoreCase = true)
                            val cType = if (!isVodFile && isLiveDefault) {
                                ContentType.LIVE_CHANNEL
                            } else {
                                ContentType.MOVIE
                            }
                            val idSlug = "${providerId}_${result.size}_${pendingTitle.hashCode()}"
                            val headersCopy = pendingHeaders.toMap()

                            result.add(
                                MovieItem(
                                    id = idSlug,
                                    title = pendingTitle,
                                    originalTitle = pendingGroup.ifBlank { providerName },
                                    country = defaultCountry,
                                    genre = pendingGroup.ifBlank { "Truyền hình & Phim ${defaultCountry.displayName}" },
                                    quality = if (cType == ContentType.LIVE_CHANNEL) "LIVE" else "HD",
                                    posterUrl = pendingLogo,
                                    backdropUrl = pendingLogo,
                                    description = "Kênh phát trực tuyến ${defaultCountry.displayName} ($providerName). Hỗ trợ phụ đề nhúng HLS & dịch tự động sang Tiếng Thái.",
                                    contentType = cType,
                                    providerId = providerId,
                                    providerName = providerName,
                                    hasNativeThaiSub = defaultCountry == CountryCategory.THAILAND,
                                    servers = listOf(
                                        ServerGroup(
                                            serverName = providerName,
                                            episodes = listOf(
                                                Episode(
                                                    id = "${idSlug}_live",
                                                    name = if (cType == ContentType.LIVE_CHANNEL) "Phát Trực Tiếp" else "Phát Ngay",
                                                    streamUrl = line,
                                                    headers = headersCopy
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    }
                    pendingTitle = ""
                    pendingLogo = ""
                    pendingGroup = ""
                    pendingHeaders.clear()
                    if (result.size >= limit) break
                }
            }
        }
        return result
    }
}
