package com.phyll.filmhub.subtitle

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.URI

/**
 * Strips injected mid-roll / pre-roll ad splice blocks from VOD HLS (.m3u8) media playlists.
 *
 * Why this is critical for subtitle synchronization:
 * Many Asian VOD CDNs (phim1280, kkphim, ophim, kvp726, etc.) splice ~30-second ad blocks
 * (such as `/v7/<hash>/segment_0001..0011.ts` or `/adjump/<date>/<hash>/000000..000010.ts`)
 * inside `#EXT-X-DISCONTINUITY` sections right in the middle of an episode, while the movie's
 * own MPEG-TS PTS timestamps before and after the ad are 100% continuous.
 *
 * Without stripping those ad blocks, every injected ad shifts all subsequent subtitles by +30.2s
 * mid-episode! Applying this sanitizer to BOTH ExoPlayer's OkHttpClient and our audio extractor
 * eliminates mid-episode subtitle jumps and removes mid-roll ads simultaneously.
 */
object HlsPlaylistSanitizer {

    private val EXTINF_REGEX = Regex("""^#EXTINF:([\d.]+)""")
    private val EXPLICIT_AD_URI_REGEX = Regex(
        """(?i)(?:/adjump/|/ad_?segments?/|/ads?/|/advert/|/preroll/|/midroll/|/v\d+/[0-9a-f]{16,}/(?:segment_\d+|\d+)\.ts)"""
    )
    private val ROOT_HASH_SEGMENT_REGEX = Regex(
        """(?i)^/[A-Za-z0-9_-]+/[0-9a-f]{16,}/.*\.(?:ts|m4s|aac)(?:\?.*)?$"""
    )

    private data class SegmentEntry(
        val metaLines: List<String>,
        val durationSec: Double,
        val uriLine: String,
        val activeKeyLine: String?
    )

    private data class DiscontinuitySection(
        val leadingDiscontinuity: Boolean,
        val segments: MutableList<SegmentEntry> = ArrayList()
    ) {
        val totalDurationSec: Double
            get() = segments.sumOf { it.durationSec }
    }

    /**
     * Sanitizes an HLS `.m3u8` playlist body. Master playlists (`#EXT-X-STREAM-INF`) and
     * clean playlists without injected ad blocks are returned untouched.
     */
    fun sanitizeMediaPlaylist(rawM3u8: String, playlistUrl: String = ""): String {
        if (!rawM3u8.contains("#EXTM3U") ||
            rawM3u8.contains("#EXT-X-STREAM-INF") ||
            !rawM3u8.contains("#EXT-X-DISCONTINUITY")
        ) {
            return rawM3u8
        }

        val playlistHost = runCatching { URI(playlistUrl).host?.lowercase().orEmpty() }.getOrDefault("")
        val playlistBaseDir = runCatching {
            val path = URI(playlistUrl).path.orEmpty()
            path.substringBeforeLast('/', "")
        }.getOrDefault("")

        val lines = rawM3u8.lines()
        val headerLines = ArrayList<String>()
        val footerLines = ArrayList<String>()
        val sections = ArrayList<DiscontinuitySection>()
        var currentSection = DiscontinuitySection(leadingDiscontinuity = false)
        sections.add(currentSection)

        var seenFirstSegmentOrDiscontinuity = false
        var pendingMeta = ArrayList<String>()
        var pendingDuration = 0.0
        var currentKeyLine: String? = null

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed == "#EXT-X-DISCONTINUITY" -> {
                    seenFirstSegmentOrDiscontinuity = true
                    pendingMeta.clear()
                    if (currentSection.segments.isNotEmpty() || currentSection.leadingDiscontinuity) {
                        currentSection = DiscontinuitySection(leadingDiscontinuity = true)
                        sections.add(currentSection)
                    } else {
                        currentSection = DiscontinuitySection(leadingDiscontinuity = true)
                        sections[sections.lastIndex] = currentSection
                    }
                }

                trimmed.startsWith("#EXT-X-ENDLIST") -> {
                    footerLines.add(trimmed)
                }

                trimmed.startsWith("#EXT-X-KEY:") -> {
                    seenFirstSegmentOrDiscontinuity = true
                    currentKeyLine = trimmed
                    pendingMeta.add(trimmed)
                }

                trimmed.startsWith("#EXTINF:") -> {
                    seenFirstSegmentOrDiscontinuity = true
                    pendingDuration = EXTINF_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
                        ?.toDoubleOrNull() ?: 3.0
                    pendingMeta.add(trimmed)
                }

                trimmed.startsWith("#") -> {
                    if (!seenFirstSegmentOrDiscontinuity && currentSection.segments.isEmpty()) {
                        headerLines.add(trimmed)
                    } else {
                        pendingMeta.add(trimmed)
                    }
                }

                else -> {
                    seenFirstSegmentOrDiscontinuity = true
                    currentSection.segments.add(
                        SegmentEntry(
                            metaLines = pendingMeta.toList(),
                            durationSec = pendingDuration,
                            uriLine = trimmed,
                            activeKeyLine = currentKeyLine
                        )
                    )
                    pendingMeta = ArrayList()
                    pendingDuration = 0.0
                }
            }
        }

        val nonEmptySections = sections.filter { it.segments.isNotEmpty() }
        if (nonEmptySections.size <= 1) return rawM3u8

        val totalPlaylistDur = nonEmptySections.sumOf { it.totalDurationSec }.coerceAtLeast(1.0)

        // Check whether the main movie segments use local relative paths (e.g. `ilxsEozG.ts` or `convertv7/x.ts`)
        val mainDurationFromLocalRelative = nonEmptySections
            .filter { sec -> sec.segments.all { !it.uriLine.startsWith("/") && !it.uriLine.startsWith("http", true) } }
            .sumOf { it.totalDurationSec }
        val mainIsLocalRelative = mainDurationFromLocalRelative > totalPlaylistDur * 0.55

        var strippedAnyAd = false
        val keptSections = ArrayList<DiscontinuitySection>(nonEmptySections.size)

        for (sec in nonEmptySections) {
            if (isInjectedAdSection(
                    section = sec,
                    totalPlaylistDur = totalPlaylistDur,
                    mainIsLocalRelative = mainIsLocalRelative,
                    playlistHost = playlistHost,
                    playlistBaseDir = playlistBaseDir
                )
            ) {
                strippedAnyAd = true
            } else {
                keptSections.add(sec)
            }
        }

        if (!strippedAnyAd || keptSections.isEmpty()) {
            return rawM3u8
        }

        // Rebuild clean HLS playlist, preserving active #EXT-X-KEY state if an ad section had #EXT-X-KEY:METHOD=NONE
        val out = StringBuilder(rawM3u8.length)
        for (h in headerLines) {
            out.append(h).append('\n')
        }

        var emittedKeyLine: String? = null
        keptSections.forEachIndexed { secIdx, sec ->
            if (secIdx > 0 && sec.leadingDiscontinuity) {
                out.append("#EXT-X-DISCONTINUITY\n")
            }
            for (seg in sec.segments) {
                val explicitKeyInMeta = seg.metaLines.firstOrNull { it.startsWith("#EXT-X-KEY:") }
                if (explicitKeyInMeta != null) {
                    emittedKeyLine = explicitKeyInMeta
                } else if (seg.activeKeyLine != emittedKeyLine) {
                    val restoreKey = seg.activeKeyLine ?: "#EXT-X-KEY:METHOD=NONE"
                    out.append(restoreKey).append('\n')
                    emittedKeyLine = seg.activeKeyLine
                }

                for (m in seg.metaLines) {
                    out.append(m).append('\n')
                }
                out.append(seg.uriLine).append('\n')
            }
        }

        for (f in footerLines) {
            out.append(f).append('\n')
        }
        return out.toString()
    }

    private fun isInjectedAdSection(
        section: DiscontinuitySection,
        totalPlaylistDur: Double,
        mainIsLocalRelative: Boolean,
        playlistHost: String,
        playlistBaseDir: String
    ): Boolean {
        val segs = section.segments
        if (segs.isEmpty()) return false

        // 1. Explicit known ad CDN URI signature -> always strip if section <= 150s
        val allMatchExplicitAd = segs.all { seg ->
            EXPLICIT_AD_URI_REGEX.containsMatchIn(seg.uriLine) ||
                ROOT_HASH_SEGMENT_REGEX.matches(seg.uriLine)
        }
        if (allMatchExplicitAd && section.totalDurationSec <= 150.0) {
            return true
        }

        // 2. Structural discontinuity ad splice check:
        //    Short discontinuity section (<= 90s and < 18% of total runtime) that escapes the
        //    main playlist's local relative directory to root `/...` or an external ad domain.
        //    (NOTE: relative subdirectories like `convertv7/BTjmeagV.ts` do NOT start with `/`
        //    or `http`, so real watermark-reencoded movie segments are 100% preserved!)
        if (section.totalDurationSec <= 90.0 && section.totalDurationSec < totalPlaylistDur * 0.18) {
            if (mainIsLocalRelative) {
                val escapesToRootOutsideBase = segs.all { seg ->
                    seg.uriLine.startsWith("/") &&
                        (playlistBaseDir.isEmpty() || !seg.uriLine.startsWith("$playlistBaseDir/"))
                }
                if (escapesToRootOutsideBase) return true

                if (playlistHost.isNotEmpty()) {
                    val escapesToDifferentHost = segs.all { seg ->
                        seg.uriLine.startsWith("http", ignoreCase = true) &&
                            runCatching { URI(seg.uriLine).host?.lowercase() }
                                .getOrNull()
                                ?.let { it != playlistHost } == true
                    }
                    if (escapesToDifferentHost) return true
                }
            }
        }

        return false
    }

    /**
     * OkHttp Interceptor that transparently cleans HLS `.m3u8` playlists on the fly for ExoPlayer
     * and audio timeline extractors.
     */
    fun createOkHttpInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val response: Response = chain.proceed(request)
        if (!response.isSuccessful) return@Interceptor response

        val urlStr = request.url.toString()
        val pathLower = request.url.encodedPath.lowercase()
        val contentTypeStr = response.header("Content-Type").orEmpty().lowercase()
        val isLikelyM3u8 = pathLower.endsWith(".m3u8") ||
            contentTypeStr.contains("mpegurl")

        if (!isLikelyM3u8) return@Interceptor response

        val body = response.body ?: return@Interceptor response
        val rawText = body.string()
        val sanitized = sanitizeMediaPlaylist(rawText, urlStr)
        val outMediaType = body.contentType() ?: "application/vnd.apple.mpegurl; charset=utf-8".toMediaType()

        response.newBuilder()
            .body(sanitized.toResponseBody(outMediaType))
            .removeHeader("Content-Length")
            .build()
    }
}
