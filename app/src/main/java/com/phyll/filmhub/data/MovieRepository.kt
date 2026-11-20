package com.phyll.filmhub.data

import com.phyll.filmhub.data.source.CustomVodApiSource
import com.phyll.filmhub.data.source.ModernVodSource
import com.phyll.filmhub.data.source.YouTubeSource
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import com.phyll.filmhub.model.VodProviderConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

data class CatalogSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val movies: List<MovieItem>
)

class MovieRepository {

    private val modernSource = ModernVodSource()
    private val youtubeSource = YouTubeSource()
    private val customSource = CustomVodApiSource()

    private val movieLookupCache = ConcurrentHashMap<String, MovieItem>()

    init {
        modernSource.getVerifiedBlockbusters().forEach { movieLookupCache[it.id] = it }
    }

    suspend fun loadHomeSections(
        selectedCountry: CountryCategory,
        customProviders: List<VodProviderConfig>
    ): List<CatalogSection> = coroutineScope {
        val sections = ArrayList<CatalogSection>()
        val blockbusters = modernSource.getVerifiedBlockbusters()

        // 1. User-added custom VOD APIs / M3U playlists
        val enabledCustom = customProviders.filter { it.enabled }
        if (enabledCustom.isNotEmpty()) {
            val customResults = enabledCustom.map { provider ->
                async {
                    val items = customSource.fetchCatalog(provider, selectedCountry)
                    cacheAll(items)
                    if (items.isNotEmpty()) {
                        CatalogSection(
                            id = "custom_${provider.id}_${selectedCountry.code}",
                            title = "🎬 ${provider.name}",
                            subtitle = provider.type.displayName,
                            movies = items
                        )
                    } else null
                }
            }.awaitAll().filterNotNull()
            sections.addAll(customResults)
        }

        when (selectedCountry) {
            CountryCategory.ALL -> {
                sections.add(
                    CatalogSection(
                        id = "verified_blockbusters",
                        title = "🔥 Bom Tấn Chọn Lọc — Đã Xác Thực 100% Có Sub Thái Thật",
                        subtitle = "Tự động tải full phụ đề .SRT thực trước khi phát • Khung nền đen chống đè Sub gốc",
                        movies = blockbusters
                    )
                )

                val asyncSections = listOf(
                    async {
                        val movies = modernSource.fetchNewMoviesList("phim-le", 18)
                        cacheAll(movies)
                        CatalogSection(
                            id = "new_feature_films",
                            title = "🎬 Phim Lẻ Chiếu Rạp Mới Cập Nhật (2025–2026)",
                            subtitle = "Phim điện ảnh mới nhất đa quốc gia • Quét Sub Thái gốc trên OpenSubtitles/YIFY trước khi dịch AI",
                            movies = movies
                        )
                    },
                    async {
                        val ytFeatured = youtubeSource.fetchFeaturedHomeRow(16)
                        cacheAll(ytFeatured)
                        CatalogSection(
                            id = "youtube_featured_home",
                            title = "▶️ Phim Full & Series Chính Thức Trên YouTube (1080p • Không Quảng Cáo)",
                            subtitle = "Phát trực tiếp luồng gốc YouTube 1080p • Tự lấy Sub Thái gốc YouTube/OpenSubtitles trước khi dịch AI",
                            movies = ytFeatured
                        )
                    },
                    async {
                        val th = modernSource.fetchByCountry(CountryCategory.THAILAND, 18)
                        cacheAll(th)
                        CatalogSection(
                            id = "modern_th",
                            title = "🇹🇭 Phim Thái Lan Mới Nhất",
                            subtitle = "Phim bộ & phim lẻ Thái Lan mới cập nhật",
                            movies = th
                        )
                    },
                    async {
                        val kr = modernSource.fetchByCountry(CountryCategory.KOREA, 18)
                        cacheAll(kr)
                        CatalogSection(
                            id = "modern_kr",
                            title = "🇰🇷 Phim Hàn Quốc Hot Mới Cập Nhật",
                            subtitle = "K-Drama & Điện ảnh Hàn Quốc mới nhất",
                            movies = kr
                        )
                    },
                    async {
                        val us = modernSource.fetchByCountry(CountryCategory.USA, 18)
                        cacheAll(us)
                        CatalogSection(
                            id = "modern_us",
                            title = "🇺🇸 Phim Âu Mỹ & Hollywood Mới",
                            subtitle = "Bom tấn hành động, viễn tưởng Âu Mỹ (Nguồn OpenSubtitles phong phú nhất)",
                            movies = us
                        )
                    },
                    async {
                        val cn = modernSource.fetchByCountry(CountryCategory.CHINA, 18)
                        cacheAll(cn)
                        CatalogSection(
                            id = "modern_cn",
                            title = "🇨🇳 Phim Trung Quốc Mới Cập Nhật",
                            subtitle = "Cổ trang, tiên hiệp, ngôn tình & hành động Hoa Ngữ mới",
                            movies = cn
                        )
                    },
                    async {
                        val uk = modernSource.fetchByCountry(CountryCategory.UK, 14)
                        cacheAll(uk)
                        CatalogSection(
                            id = "modern_uk",
                            title = "🇬🇧 Điện Ảnh & Phim Bộ Anh Quốc Mới",
                            subtitle = "Trinh thám, hình sự & tâm lý Anh Quốc",
                            movies = uk
                        )
                    },
                    async {
                        val jp = modernSource.fetchByCountry(CountryCategory.JAPAN, 16)
                        cacheAll(jp)
                        CatalogSection(
                            id = "modern_jp",
                            title = "🇯🇵 Phim Nhật Bản & Anime Mới",
                            subtitle = "Điện ảnh Nhật Bản & Hoạt hình Anime mới nhất",
                            movies = jp
                        )
                    }
                ).awaitAll()

                sections.addAll(asyncSections.filter { it.movies.isNotEmpty() })
            }

            CountryCategory.YOUTUBE -> {
                val ytShelves = youtubeSource.fetchYouTubeTabShelves()
                for ((spec, items) in ytShelves) {
                    cacheAll(items)
                    sections.add(
                        CatalogSection(
                            id = spec.id,
                            title = spec.title,
                            subtitle = spec.subtitle,
                            movies = items
                        )
                    )
                }
            }

            else -> {
                val matchingHits = blockbusters.filter { it.country == selectedCountry }
                if (matchingHits.isNotEmpty()) {
                    sections.add(
                        CatalogSection(
                            id = "hits_${selectedCountry.code}",
                            title = "🔥 Bom Tấn Nổi Bật — ${selectedCountry.flagEmoji} ${selectedCountry.displayName}",
                            subtitle = "Đã xác thực có sẵn file Sub .SRT thực trên OpenSubtitles & YIFY",
                            movies = matchingHits
                        )
                    )
                }

                val list = modernSource.fetchByCountry(selectedCountry, 24)
                cacheAll(list)
                if (list.isNotEmpty()) {
                    sections.add(
                        CatalogSection(
                            id = "cat_${selectedCountry.code}",
                            title = "${selectedCountry.flagEmoji} Danh Sách ${selectedCountry.displayName} Mới Cập Nhật",
                            subtitle = "Tự quét Sub Thái gốc trên OpenSubtitles/YIFY trước khi chuyển qua dịch AI",
                            movies = list
                        )
                    )
                }
            }
        }

        sections
    }

    suspend fun searchAll(
        keyword: String,
        customProviders: List<VodProviderConfig>
    ): List<MovieItem> = coroutineScope {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@coroutineScope emptyList()

        // Direct YouTube link pasted into Search
        if (YouTubeSource.isYouTubeUrl(kw)) {
            val ytDirect = youtubeSource.resolveByUrlOrId(kw)
            if (ytDirect != null) {
                cacheAll(listOf(ytDirect))
                return@coroutineScope listOf(ytDirect)
            }
        }

        val modernDeferred = async { modernSource.searchMovies(kw, 20) }
        val youtubeDeferred = async { youtubeSource.searchVideos(kw, 16) }
        val customDeferreds = customProviders.filter { it.enabled }.map { p ->
            async { customSource.searchProvider(p, kw) }
        }

        val modernResults = modernDeferred.await()
        val youtubeResults = youtubeDeferred.await()
        val customResults = customDeferreds.awaitAll().flatten()

        val combined = (modernResults + youtubeResults + customResults).distinctBy { it.id }
        cacheAll(combined)
        combined
    }

    suspend fun resolveMovieDetail(
        movie: MovieItem,
        customProviders: List<VodProviderConfig>
    ): MovieItem {
        if (movie.servers.isNotEmpty()) {
            movieLookupCache[movie.id] = movie
            return movie
        }
        val resolved = when (movie.providerId) {
            "modern_vod" -> modernSource.resolveMovieDetails(movie)
            YouTubeSource.PROVIDER_ID -> youtubeSource.resolveVideoDetails(movie)
            else -> {
                val provider = customProviders.firstOrNull { it.id == movie.providerId }
                if (provider != null) customSource.resolveDetails(provider, movie) else movie
            }
        }
        movieLookupCache[resolved.id] = resolved
        return resolved
    }

    suspend fun resolveDirectOrYouTubeStream(title: String, streamUrl: String): MovieItem {
        if (YouTubeSource.isYouTubeUrl(streamUrl)) {
            val ytResolved = youtubeSource.resolveByUrlOrId(streamUrl, title)
            if (ytResolved != null) {
                movieLookupCache[ytResolved.id] = ytResolved
                return ytResolved
            }
        }
        return registerAdHocStream(title, streamUrl)
    }

    fun getCachedMovie(movieId: String): MovieItem? = movieLookupCache[movieId]

    fun registerAdHocStream(title: String, streamUrl: String): MovieItem {
        val item = MovieItem(
            id = "direct_${streamUrl.hashCode()}",
            title = title.ifBlank { "Luồng Phát Trực Tiếp" },
            originalTitle = title,
            country = CountryCategory.GLOBAL,
            genre = "Luồng trực tiếp / USB",
            quality = "Direct",
            description = "Phát trực tiếp từ đường dẫn URL, USB hoặc Điện thoại gửi sang TV360 Box.",
            contentType = ContentType.MOVIE,
            providerId = "direct",
            providerName = "Direct Stream",
            servers = listOf(
                ServerGroup(
                    serverName = "Nguồn Trực Tiếp",
                    episodes = listOf(
                        Episode(
                            id = "direct_ep1",
                            name = "Phát Ngay",
                            streamUrl = streamUrl
                        )
                    )
                )
            )
        )
        movieLookupCache[item.id] = item
        return item
    }

    private fun cacheAll(items: List<MovieItem>) {
        items.forEach { movieLookupCache[it.id] = it }
    }
}
