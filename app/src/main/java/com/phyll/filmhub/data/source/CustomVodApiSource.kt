package com.phyll.filmhub.data.source

import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.ServerGroup
import com.phyll.filmhub.model.VodProviderConfig
import com.phyll.filmhub.model.VodProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Universal Open VOD Connector supporting:
 * 1. Standard JSON VOD CMS REST APIs (OPhim / KKPhim / PhimAPI compatible JSON schema)
 * 2. Custom M3U / M3U8 VOD & Live Playlists
 * 3. Simple Custom JSON Movie Catalogs
 */
class CustomVodApiSource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build(),
    private val m3uSource: IptvCountrySource = IptvCountrySource(httpClient)
) {

    suspend fun fetchCatalog(
        provider: VodProviderConfig,
        countryFilter: CountryCategory
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        if (!provider.enabled) return@withContext emptyList()
        when (provider.type) {
            VodProviderType.M3U_PLAYLIST -> {
                m3uSource.fetchAndParseM3u(
                    playlistUrl = provider.baseUrl,
                    country = provider.defaultCountry,
                    providerId = provider.id,
                    providerName = provider.name,
                    isLiveDefault = false,
                    limit = 100
                )
            }
            VodProviderType.CUSTOM_JSON -> fetchSimpleCustomJson(provider)
            VodProviderType.STANDARD_CMS_JSON -> fetchStandardCmsCatalog(provider, countryFilter)
        }
    }

    suspend fun searchProvider(
        provider: VodProviderConfig,
        keyword: String
    ): List<MovieItem> = withContext(Dispatchers.IO) {
        if (!provider.enabled || keyword.isBlank()) return@withContext emptyList()
        when (provider.type) {
            VodProviderType.STANDARD_CMS_JSON -> searchStandardCms(provider, keyword)
            else -> fetchCatalog(provider, CountryCategory.ALL).filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.originalTitle.contains(keyword, ignoreCase = true)
            }
        }
    }

    suspend fun resolveDetails(
        provider: VodProviderConfig,
        movie: MovieItem
    ): MovieItem = withContext(Dispatchers.IO) {
        if (movie.servers.isNotEmpty() || provider.type != VodProviderType.STANDARD_CMS_JSON) {
            return@withContext movie
        }
        val slug = movie.detailLookupId ?: return@withContext movie
        val base = provider.baseUrl.trimEnd('/')
        val detailUrl = "$base/phim/$slug"
        try {
            val body = httpGet(detailUrl) ?: return@withContext movie
            val root = JSONObject(body)
            val movieObj = root.optJSONObject("movie") ?: root.optJSONObject("data")?.optJSONObject("item")
            val episodesArr = root.optJSONArray("episodes")
                ?: movieObj?.optJSONArray("episodes")
                ?: JSONArray()

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

            val updatedDesc = movieObj?.optString("content")
                ?.replace(Regex("<[^>]*>"), "")
                ?.trim()
                ?.ifBlank { movie.description }
                ?: movie.description

            movie.copy(
                description = updatedDesc,
                servers = servers
            )
        } catch (_: Exception) {
            movie
        }
    }

    private fun fetchStandardCmsCatalog(
        provider: VodProviderConfig,
        country: CountryCategory
    ): List<MovieItem> {
        val base = provider.baseUrl.trimEnd('/')
        val countrySlug = when (country) {
            CountryCategory.THAILAND -> "thai-lan"
            CountryCategory.KOREA -> "han-quoc"
            CountryCategory.CHINA -> "trung-quoc"
            CountryCategory.USA -> "au-my"
            CountryCategory.UK -> "anh"
            else -> null
        }

        val candidateUrls = if (countrySlug != null) {
            listOf(
                "$base/v1/api/quoc-gia/$countrySlug?page=1",
                "$base/danh-sach/phim-moi-cap-nhat?page=1"
            )
        } else {
            listOf(
                "$base/danh-sach/phim-moi-cap-nhat?page=1",
                "$base/v1/api/danh-sach/phim-moi-cap-nhat?page=1"
            )
        }

        for (url in candidateUrls) {
            val body = httpGet(url) ?: continue
            val parsed = parseStandardCmsItems(body, provider, country)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun searchStandardCms(provider: VodProviderConfig, keyword: String): List<MovieItem> {
        val base = provider.baseUrl.trimEnd('/')
        val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "$base/v1/api/tim-kiem?keyword=$enc&limit=24"
        val body = httpGet(url) ?: return emptyList()
        return parseStandardCmsItems(body, provider, CountryCategory.GLOBAL)
    }

    private fun parseStandardCmsItems(
        jsonStr: String,
        provider: VodProviderConfig,
        fallbackCountry: CountryCategory
    ): List<MovieItem> {
        return try {
            val root = JSONObject(jsonStr)
            val dataObj = root.optJSONObject("data")
            val cdnDomain = dataObj?.optString("APP_DOMAIN_CDN_IMAGE")
                ?.ifBlank { root.optString("pathImage") }
                .orEmpty()
                .trimEnd('/')

            val itemsArr = root.optJSONArray("items")
                ?: dataObj?.optJSONArray("items")
                ?: return emptyList()

            val list = ArrayList<MovieItem>(itemsArr.length())
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                val slug = item.optString("slug").trim()
                val name = item.optString("name").trim()
                if (slug.isEmpty() || name.isEmpty()) continue

                val originName = item.optString("origin_name")
                val year = item.optString("year")
                val quality = item.optString("quality", "HD")
                val lang = item.optString("lang")
                val rawPoster = item.optString("poster_url").ifBlank { item.optString("thumb_url") }
                val poster = resolveImageUrl(rawPoster, cdnDomain)
                val rawThumb = item.optString("thumb_url").ifBlank { rawPoster }
                val backdrop = resolveImageUrl(rawThumb, cdnDomain)

                val countryArr = item.optJSONArray("country")
                val resolvedCountry = if (countryArr != null && countryArr.length() > 0) {
                    val cObj = countryArr.optJSONObject(0)
                    CountryCategory.fromSlugOrName(
                        cObj?.optString("slug").orEmpty() + " " + cObj?.optString("name").orEmpty()
                    )
                } else if (fallbackCountry != CountryCategory.ALL) {
                    fallbackCountry
                } else {
                    provider.defaultCountry
                }

                list.add(
                    MovieItem(
                        id = "${provider.id}_$slug",
                        title = name,
                        originalTitle = originName,
                        year = year,
                        country = resolvedCountry,
                        genre = lang.ifBlank { "VOD ${resolvedCountry.displayName}" },
                        quality = quality,
                        posterUrl = poster,
                        backdropUrl = backdrop,
                        description = "$name ($originName) - Nguồn: ${provider.name}",
                        contentType = ContentType.SERIES,
                        providerId = provider.id,
                        providerName = provider.name,
                        hasNativeThaiSub = lang.contains("Thái", ignoreCase = true) ||
                            lang.contains("Thai", ignoreCase = true),
                        detailLookupId = slug
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchSimpleCustomJson(provider: VodProviderConfig): List<MovieItem> {
        val body = httpGet(provider.baseUrl) ?: return emptyList()
        return try {
            val arr = if (body.trimStart().startsWith("[")) {
                JSONArray(body)
            } else {
                JSONObject(body).optJSONArray("movies") ?: return emptyList()
            }
            val result = ArrayList<MovieItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val streamUrl = obj.optString("streamUrl").ifBlank { obj.optString("url") }.trim()
                if (title.isEmpty() || streamUrl.isEmpty()) continue

                val subsArr = obj.optJSONArray("subtitles")
                val subs = ArrayList<ExternalSubtitleRef>()
                if (subsArr != null) {
                    for (k in 0 until subsArr.length()) {
                        val s = subsArr.optJSONObject(k) ?: continue
                        val sUrl = s.optString("url").trim()
                        val sLang = s.optString("lang", "th")
                        if (sUrl.isNotEmpty()) {
                            subs.add(
                                ExternalSubtitleRef(
                                    id = "csub_${i}_$k",
                                    label = s.optString("label", sLang.uppercase()),
                                    languageCode = sLang,
                                    urlOrContent = sUrl
                                )
                            )
                        }
                    }
                }

                val cCat = CountryCategory.fromSlugOrName(
                    obj.optString("country", provider.defaultCountry.code)
                )
                result.add(
                    MovieItem(
                        id = "${provider.id}_item_$i",
                        title = title,
                        originalTitle = obj.optString("originalTitle"),
                        year = obj.optString("year"),
                        country = cCat,
                        genre = obj.optString("genre", "Phim lẻ"),
                        quality = obj.optString("quality", "HD"),
                        posterUrl = obj.optString("poster"),
                        backdropUrl = obj.optString("backdrop", obj.optString("poster")),
                        description = obj.optString("description", title),
                        contentType = ContentType.MOVIE,
                        providerId = provider.id,
                        providerName = provider.name,
                        hasNativeThaiSub = subs.any { it.isThai },
                        servers = listOf(
                            ServerGroup(
                                serverName = provider.name,
                                episodes = listOf(
                                    Episode(
                                        id = "${provider.id}_ep_$i",
                                        name = "Full",
                                        streamUrl = streamUrl,
                                        subtitles = subs
                                    )
                                )
                            )
                        )
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveImageUrl(raw: String, cdnDomain: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return if (cdnDomain.isNotEmpty()) {
            "$cdnDomain/${clean.trimStart('/')}"
        } else {
            clean
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 PhyllFilmHub/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
