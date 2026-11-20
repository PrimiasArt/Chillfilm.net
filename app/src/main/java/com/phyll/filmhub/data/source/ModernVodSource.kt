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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Primary Modern Movie & Series Catalog (2024–2026) across Thailand, South Korea,
 * China, USA/Hollywood, UK, and Japan with direct 1080p HLS (.m3u8) streams
 * and original English/international titles for automatic OpenSubtitles .SRT matching.
 */
class ModernVodSource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val BASE_API = "https://phimapi.com"
        private const val DEFAULT_CDN = "https://phimimg.com"
    }

    /**
     * Verified 2024–2025 Blockbusters across Thailand, Korea, and Hollywood
     * with confirmed IMDb IDs & full-length OpenSubtitles .SRT tracks (Thai + Vi + En).
     */
    fun getVerifiedBlockbusters(): List<MovieItem> = listOf(
        MovieItem(
            id = "modern_gia-tai-cua-ngoai",
            title = "Gia Tài Của Ngoại",
            originalTitle = "How to Make Millions Before Grandma Dies",
            year = "2024",
            country = CountryCategory.THAILAND,
            genre = "Gia đình • Tâm lý • Điện ảnh Thái Lan",
            duration = "125 phút",
            quality = "FHD • Sub ไทย Gốc",
            posterUrl = "https://phimimg.com/upload/vod/20240912-1/9e13b635ca60b62f004f6e3de4ed5336.jpg",
            backdropUrl = "https://phimimg.com/upload/vod/20240912-1/64bf86cb22534c7bfae9a710e0ace8e1.jpg",
            description = "Bom tấn điện ảnh Thái Lan lấy nước mắt hàng triệu khán giả châu Á. Tự động nạp full phụ đề Tiếng Thái gốc từ OpenSubtitles trước khi chiếu.",
            contentType = ContentType.MOVIE,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = true,
            imdbId = "tt31392609",
            detailLookupId = "gia-tai-cua-ngoai"
        ),
        MovieItem(
            id = "modern_deadpool-va-wolverine",
            title = "Deadpool Và Wolverine",
            originalTitle = "Deadpool & Wolverine",
            year = "2024",
            country = CountryCategory.USA,
            genre = "Hành động • Siêu anh hùng • Hài hước",
            duration = "128 phút",
            quality = "FHD • Sub ไทย Gốc",
            posterUrl = "https://phimimg.com/upload/vod/20250821-1/45b6b9aad03ae0aa2aceb5d73419831a.jpg",
            backdropUrl = "https://phimimg.com/upload/vod/20250821-1/1ec414f82adc729512410edd1b083996.jpg",
            description = "Bom tấn Marvel kết hợp bộ đôi Deadpool & Wolverine khuynh đảo đa vũ trụ. Có sẵn phụ đề Tiếng Thái chuẩn rạp + Tiếng Việt & Tiếng Anh.",
            contentType = ContentType.MOVIE,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = true,
            imdbId = "tt6263850",
            detailLookupId = "deadpool-va-wolverine"
        ),
        MovieItem(
            id = "modern_nu-hoang-nuoc-mat",
            title = "Nữ Hoàng Nước Mắt (Queen of Tears)",
            originalTitle = "Queen Of Tears",
            year = "2024",
            country = CountryCategory.KOREA,
            genre = "Tình cảm • Lãng mạn • Hàn Quốc",
            duration = "16 Tập",
            quality = "FHD • Sub ไทย Gốc",
            posterUrl = "https://phimimg.com/uploads/movies/20260723/nu-hoang-nuoc-mat-poster.webp",
            backdropUrl = "https://phimimg.com/uploads/movies/20260723/nu-hoang-nuoc-mat-thumb.webp",
            description = "Siêu phẩm truyền hình Hàn Quốc của Kim Soo-hyun và Kim Ji-won. Tự động tải phụ đề Tiếng Thái chuẩn theo từng tập trước khi phát.",
            contentType = ContentType.SERIES,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = true,
            imdbId = "tt27668559",
            detailLookupId = "nu-hoang-nuoc-mat"
        ),
        MovieItem(
            id = "modern_hanh-tinh-cat-phan-hai",
            title = "Hành Tinh Cát: Phần Hai (Dune 2)",
            originalTitle = "Dune: Part Two",
            year = "2024",
            country = CountryCategory.USA,
            genre = "Khoa học viễn tưởng • Sử thi • Hành động",
            duration = "166 phút",
            quality = "FHD • AI Sub ไทย",
            posterUrl = "https://phimimg.com/upload/vod/20240303-1/e77a2a1a09e4600337f82cc28c21b5ee.jpg",
            backdropUrl = "https://phimimg.com/upload/vod/20240303-1/f73ca2f939961f46e199680ca0e4dec5.jpg",
            description = "Hành trình báo thù vĩ đại của Paul Atreides cùng người Fremen trên hành tinh cát Arrakis. Tự động tải .SRT thực và AI dịch Tiếng Thái trước khi chiếu.",
            contentType = ContentType.MOVIE,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = false,
            imdbId = "tt15239678",
            detailLookupId = "hanh-tinh-cat-phan-hai"
        ),
        MovieItem(
            id = "modern_quat-mo-trung-ma",
            title = "Quật Mộ Trùng Ma (Exhuma)",
            originalTitle = "Exhuma",
            year = "2024",
            country = CountryCategory.KOREA,
            genre = "Kinh dị • Tâm linh • Bí ẩn Hàn Quốc",
            duration = "134 phút",
            quality = "FHD • Sub ไทย",
            posterUrl = "https://phimimg.com/upload/vod/20250530-1/759df554cc21bf9d6805966dc3fe2b67.jpg",
            backdropUrl = "https://phimimg.com/upload/vod/20250530-1/fdf11774cff47f0ffc9c2dbe2e02d0ca.jpg",
            description = "Bom tấn tâm linh huyền bí ăn khách nhất phòng vé Hàn Quốc với sự góp mặt của Choi Min-sik, Kim Go-eun và Lee Do-hyun.",
            contentType = ContentType.MOVIE,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = true,
            imdbId = "tt27802490",
            detailLookupId = "quat-mo-trung-ma"
        ),
        MovieItem(
            id = "modern_venom-keo-cuoi",
            title = "Venom: Kèo Cuối",
            originalTitle = "Venom: The Last Dance",
            year = "2024",
            country = CountryCategory.USA,
            genre = "Hành động • Viễn tưởng",
            duration = "109 phút",
            quality = "FHD • Sub ไทย",
            posterUrl = "https://phimimg.com/upload/vod/20250420-1/585fe9d01af1aa30a1051920e7e5bb02.jpg",
            backdropUrl = "https://phimimg.com/upload/vod/20250420-1/831e394435dfd36bedad54d79d1884b5.jpg",
            description = "Cuộc chạy trốn sinh tử cuối cùng của Eddie Brock và Symbiote Venom trước sự truy lùng của cả hai thế giới.",
            contentType = ContentType.MOVIE,
            providerId = "modern_vod",
            providerName = "Phyll Modern Cinema",
            hasNativeThaiSub = true,
            imdbId = "tt16366836",
            detailLookupId = "venom-keo-cuoi"
        )
    )

    suspend fun fetchByCountry(country: CountryCategory, limit: Int = 20): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val endpoint = when (country) {
                CountryCategory.THAILAND -> "$BASE_API/v1/api/quoc-gia/thai-lan?page=1&limit=$limit"
                CountryCategory.KOREA -> "$BASE_API/v1/api/quoc-gia/han-quoc?page=1&limit=$limit"
                CountryCategory.CHINA -> "$BASE_API/v1/api/quoc-gia/trung-quoc?page=1&limit=$limit"
                CountryCategory.USA -> "$BASE_API/v1/api/quoc-gia/au-my?page=1&limit=$limit"
                CountryCategory.UK -> "$BASE_API/v1/api/quoc-gia/anh?page=1&limit=$limit"
                CountryCategory.JAPAN -> "$BASE_API/v1/api/quoc-gia/nhat-ban?page=1&limit=$limit"
                CountryCategory.GLOBAL -> "$BASE_API/v1/api/danh-sach/phim-le?page=1&limit=$limit"
                CountryCategory.LIVE_TV -> "$BASE_API/v1/api/danh-sach/phim-bo?page=1&limit=$limit"
                CountryCategory.ALL, CountryCategory.YOUTUBE -> "$BASE_API/v1/api/danh-sach/phim-le?page=1&limit=$limit"
            }
            val body = httpGet(endpoint) ?: return@withContext emptyList()
            parseV1ListResponse(body, country)
        }

    suspend fun fetchNewMoviesList(typeSlug: String, limit: Int = 20): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_API/v1/api/danh-sach/$typeSlug?page=1&limit=$limit"
            val body = httpGet(url) ?: return@withContext emptyList()
            parseV1ListResponse(body, CountryCategory.GLOBAL)
        }

    suspend fun searchMovies(keyword: String, limit: Int = 24): List<MovieItem> =
        withContext(Dispatchers.IO) {
            val clean = keyword.trim()
            if (clean.isEmpty()) return@withContext emptyList()
            val enc = URLEncoder.encode(clean, "UTF-8")
            val url = "$BASE_API/v1/api/tim-kiem?keyword=$enc&limit=$limit"
            val body = httpGet(url) ?: return@withContext emptyList()
            parseV1ListResponse(body, CountryCategory.GLOBAL)
        }

    suspend fun resolveMovieDetails(movie: MovieItem): MovieItem = withContext(Dispatchers.IO) {
        if (movie.servers.isNotEmpty()) return@withContext movie
        val slug = movie.detailLookupId ?: movie.id.removePrefix("modern_")
        val url = "$BASE_API/phim/$slug"
        try {
            val body = httpGet(url) ?: return@withContext movie
            val root = JSONObject(body)
            val movieObj = root.optJSONObject("movie")
            val episodesArr = root.optJSONArray("episodes") ?: JSONArray()

            val servers = ArrayList<ServerGroup>()
            for (i in 0 until episodesArr.length()) {
                val sObj = episodesArr.optJSONObject(i) ?: continue
                val serverName = sObj.optString("server_name", "Server ${i + 1}")
                val dataArr = sObj.optJSONArray("server_data") ?: continue
                val eps = ArrayList<Episode>()
                for (j in 0 until dataArr.length()) {
                    val eObj = dataArr.optJSONObject(j) ?: continue
                    val m3u8 = eObj.optString("link_m3u8").trim()
                    if (m3u8.isNotEmpty()) {
                        eps.add(
                            Episode(
                                id = "${slug}_${i}_$j",
                                name = eObj.optString("name", "Tập ${j + 1}"),
                                streamUrl = m3u8
                            )
                        )
                    }
                }
                if (eps.isNotEmpty()) {
                    servers.add(ServerGroup(serverName = serverName, episodes = eps))
                }
            }

            val rawType = movieObj?.optString("type").orEmpty().lowercase()
            val resolvedType = if (rawType == "single") {
                ContentType.MOVIE
            } else if (rawType.isNotEmpty()) {
                ContentType.SERIES
            } else {
                movie.contentType
            }

            val imdbFromApi = movieObj?.optJSONObject("imdb")?.optString("id")
                ?.takeIf { it.startsWith("tt") }
                ?: movie.imdbId

            val originFromApi = movieObj?.optString("origin_name")
                ?.takeIf { it.isNotBlank() }
                ?: movie.originalTitle

            val desc = movieObj?.optString("content")
                ?.replace(Regex("<[^>]*>"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: movie.description

            val duration = movieObj?.optString("time")?.takeIf { it.isNotBlank() } ?: movie.duration

            movie.copy(
                originalTitle = originFromApi,
                description = desc,
                duration = duration,
                contentType = resolvedType,
                imdbId = imdbFromApi,
                servers = servers
            )
        } catch (_: Exception) {
            movie
        }
    }

    private fun parseV1ListResponse(
        jsonStr: String,
        fallbackCountry: CountryCategory
    ): List<MovieItem> {
        return try {
            val root = JSONObject(jsonStr)
            val dataObj = root.optJSONObject("data")
            val cdnDomain = dataObj?.optString("APP_DOMAIN_CDN_IMAGE")
                ?.ifBlank { DEFAULT_CDN }
                .orEmpty()
                .trimEnd('/')

            val itemsArr = dataObj?.optJSONArray("items")
                ?: root.optJSONArray("items")
                ?: return emptyList()

            val list = ArrayList<MovieItem>(itemsArr.length())
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                val slug = item.optString("slug").trim()
                val name = item.optString("name").trim()
                if (slug.isEmpty() || name.isEmpty()) continue

                val originName = item.optString("origin_name").trim()
                val year = item.optString("year").trim()
                val quality = item.optString("quality", "FHD").trim()
                val epCurrent = item.optString("episode_current").trim()
                val rawType = item.optString("type").lowercase()
                val contentType = if (rawType == "single") ContentType.MOVIE else ContentType.SERIES

                val rawPoster = item.optString("poster_url").ifBlank { item.optString("thumb_url") }
                val rawThumb = item.optString("thumb_url").ifBlank { rawPoster }
                val poster = normalizeImageUrl(rawPoster, cdnDomain)
                val backdrop = normalizeImageUrl(rawThumb, cdnDomain)

                val countryArr = item.optJSONArray("country")
                val resolvedCountry = if (countryArr != null && countryArr.length() > 0) {
                    val cObj = countryArr.optJSONObject(0)
                    CountryCategory.fromSlugOrName(
                        cObj?.optString("slug").orEmpty() + " " + cObj?.optString("name").orEmpty()
                    )
                } else if (fallbackCountry != CountryCategory.ALL &&
                    fallbackCountry != CountryCategory.LIVE_TV
                ) {
                    fallbackCountry
                } else {
                    CountryCategory.GLOBAL
                }

                val categoryArr = item.optJSONArray("category")
                val genres = buildList {
                    if (categoryArr != null) {
                        for (c in 0 until minOf(3, categoryArr.length())) {
                            categoryArr.optJSONObject(c)?.optString("name")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { add(it) }
                        }
                    }
                }.joinToString(" • ").ifBlank { "Điện ảnh ${resolvedCountry.displayName}" }

                val imdbId = item.optJSONObject("imdb")?.optString("id")
                    ?.takeIf { it.startsWith("tt") }
                    .orEmpty()

                val badgeQuality = listOfNotNull(
                    quality.takeIf { it.isNotBlank() },
                    epCurrent.takeIf { it.isNotBlank() && !it.equals("Full", true) }
                ).joinToString(" • ").ifBlank { "FHD" }

                list.add(
                    MovieItem(
                        id = "modern_$slug",
                        title = name,
                        originalTitle = originName,
                        year = year,
                        country = resolvedCountry,
                        genre = genres,
                        duration = item.optString("time"),
                        quality = badgeQuality,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        description = "$name ($originName - $year). Tự động tải phụ đề .SRT thực từ OpenSubtitles và dịch AI sang Tiếng Thái trước khi chiếu.",
                        contentType = contentType,
                        providerId = "modern_vod",
                        providerName = "Kho Phim Mới",
                        hasNativeThaiSub = resolvedCountry == CountryCategory.THAILAND,
                        imdbId = imdbId,
                        detailLookupId = slug
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeImageUrl(raw: String, cdnDomain: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        val base = cdnDomain.ifBlank { DEFAULT_CDN }.trimEnd('/')
        return "$base/${clean.trimStart('/')}"
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 PhyllFilmHub/2.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
