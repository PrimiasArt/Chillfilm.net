package com.phyll.filmhub.data

import com.phyll.filmhub.cloud.CloudSyncClient
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
    private val resolvedDetailCache = ConcurrentHashMap<String, Pair<Long, MovieItem>>()
    private val sectionsCache = ConcurrentHashMap<String, Pair<Long, List<CatalogSection>>>()

    companion object {
        private const val DETAIL_CACHE_TTL_MS = 5 * 60_000L
        private const val SECTIONS_CACHE_TTL_MS = 3 * 60_000L
    }

    init {
        modernSource.getVerifiedBlockbusters().forEach { movieLookupCache[it.id] = it }
    }

    suspend fun loadHomeSections(
        selectedCountry: CountryCategory,
        customProviders: List<VodProviderConfig>,
        forceRefresh: Boolean = false
    ): List<CatalogSection> = coroutineScope {
        val cacheKey = "${selectedCountry.code}::${customProviders.count { it.enabled }}"
        if (!forceRefresh) {
            val hit = sectionsCache[cacheKey]
            if (hit != null && System.currentTimeMillis() - hit.first < SECTIONS_CACHE_TTL_MS && hit.second.isNotEmpty()) {
                return@coroutineScope hit.second
            }
        }
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
                val cloudFeedDeferred = async {
                    CloudSyncClient.fetchLiveCatalogFeedFull().getOrNull()
                }
                val asyncSections = listOf(
                    async {
                        val cloudFeed = cloudFeedDeferred.await()
                        val liveHot = cloudFeed?.heroAndTrending?.takeIf { it.isNotEmpty() }
                            ?: modernSource.fetchLatestUpdatedFeed(24).ifEmpty { blockbusters }
                        val safeHot = ContentSafetyFilter.filterSafe(liveHot)
                        cacheAll(safeHot)
                        CatalogSection(
                            id = "live_hot_trending",
                            title = "🔥 Trending & Newly Updated (Live)",
                            subtitle = "Auto-updated from global clean VOD crawlers • Multi-language AI Subtitles",
                            movies = safeHot
                        )
                    },
                    async {
                        val movies = ContentSafetyFilter.filterSafe(modernSource.fetchNewMoviesList("phim-le", 18))
                        cacheAll(movies)
                        CatalogSection(
                            id = "new_feature_films",
                            title = "🎬 New Movies (Latest Updates)",
                            subtitle = "Newest global cinema releases • Auto-synced AI Subtitles",
                            movies = movies
                        )
                    },
                    async {
                        val series = ContentSafetyFilter.filterSafe(modernSource.fetchNewMoviesList("phim-bo", 18))
                        cacheAll(series)
                        CatalogSection(
                            id = "new_tv_series",
                            title = "📺 New TV Series (Latest Episodes)",
                            subtitle = "Ongoing & newly completed series updated daily",
                            movies = series
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

                // Append global extra rows from Vercel Cloud Aggregator (India, Europe, Internet Archive & Admin dynamic sources!)
                val extraRows = cloudFeedDeferred.await()?.extraSourceRows.orEmpty()
                for (row in extraRows) {
                    val safeRow = ContentSafetyFilter.filterSafe(row.movies)
                    if (safeRow.isNotEmpty()) {
                        cacheAll(safeRow)
                        sections.add(
                            CatalogSection(
                                id = "cloud_${row.id}",
                                title = row.title,
                                subtitle = "Global Clean Multi-Source • AI Subtitles Ready",
                                movies = safeRow
                            )
                        )
                    }
                }
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

        if (sections.isNotEmpty()) {
            sectionsCache[cacheKey] = System.currentTimeMillis() to sections
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

        val cloudSearchDeferred = async { CloudSyncClient.searchGlobalCatalog(kw).getOrDefault(emptyList()) }
        val modernDeferred = async { modernSource.searchMovies(kw, 20) }
        val youtubeDeferred = async { youtubeSource.searchVideos(kw, 16) }
        val customDeferreds = customProviders.filter { it.enabled }.map { p ->
            async { customSource.searchProvider(p, kw) }
        }

        val cloudResults = cloudSearchDeferred.await()
        val modernResults = modernDeferred.await()
        val youtubeResults = youtubeDeferred.await()
        val customResults = customDeferreds.awaitAll().flatten()

        val combined = ContentSafetyFilter.filterSafe(
            (cloudResults + modernResults + youtubeResults + customResults).distinctBy { it.id }
        )
        cacheAll(combined)
        combined
    }

    suspend fun resolveMovieDetail(
        movie: MovieItem,
        customProviders: List<VodProviderConfig>
    ): MovieItem {
        if (movie.servers.isNotEmpty()) {
            movieLookupCache[movie.id] = movie
            resolvedDetailCache[movie.id] = System.currentTimeMillis() to movie
            return movie
        }
        val cached = resolvedDetailCache[movie.id]
        if (cached != null && System.currentTimeMillis() - cached.first < DETAIL_CACHE_TTL_MS && cached.second.servers.isNotEmpty()) {
            return cached.second
        }
        val resolved = when {
            movie.providerId == "modern_vod" -> modernSource.resolveMovieDetails(movie)
            movie.providerId == YouTubeSource.PROVIDER_ID -> youtubeSource.resolveVideoDetails(movie)
            movie.providerId.startsWith("cloud_") -> CloudSyncClient.resolveCloudDetail(movie)
            else -> {
                val provider = customProviders.firstOrNull { it.id == movie.providerId }
                if (provider != null) customSource.resolveDetails(provider, movie) else movie
            }
        }
        movieLookupCache[resolved.id] = resolved
        if (resolved.servers.isNotEmpty()) {
            resolvedDetailCache[movie.id] = System.currentTimeMillis() to resolved
            resolvedDetailCache[resolved.id] = System.currentTimeMillis() to resolved
        }
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
