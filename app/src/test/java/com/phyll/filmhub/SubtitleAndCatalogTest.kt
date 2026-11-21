package com.phyll.filmhub

import com.phyll.filmhub.companion.CompanionPlayerTelemetry
import com.phyll.filmhub.data.source.IptvCountrySource
import com.phyll.filmhub.data.source.YouTubeSource
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.SubtitleTrackOption
import com.phyll.filmhub.subtitle.GeminiAudioSubtitleGenerator
import com.phyll.filmhub.subtitle.HlsPlaylistSanitizer
import com.phyll.filmhub.subtitle.OnlineSubtitleFetcher
import com.phyll.filmhub.subtitle.SubtitleAutoSyncEngine
import com.phyll.filmhub.subtitle.SubtitleParser
import com.phyll.filmhub.subtitle.SubtitleTranslator
import com.phyll.filmhub.subtitle.ThaiSubtitleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubtitleAndCatalogTest {

    @Test
    fun parseThaiSrtAndLookupWithSyncOffset() {
        val srt = """
            1
            00:00:02,000 --> 00:00:05,000
            สวัสดีทุกคน ยินดีต้อนรับ

            2
            00:00:06,500 --> 00:00:10,000
            <i>คืนนี้เราจะดูหนังด้วยกัน</i>
        """.trimIndent()

        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals("สวัสดีทุกคน ยินดีต้อนรับ", cues[0].text)
        assertEquals("คืนนี้เราจะดูหนังด้วยกัน", cues[1].text)
        assertTrue(SubtitleTranslator.containsThaiScript(cues[0].text))

        // At 3.0s -> cue 1
        assertEquals("สวัสดีทุกคน ยินดีต้อนรับ", SubtitleParser.findActiveText(cues, 3_000L))
        // At 5.5s -> gap
        assertNull(SubtitleParser.findActiveText(cues, 5_500L))
        // If user applies +1000ms delay (sub appears 1s later), at playerPos=5500ms effectivePos=4500ms -> cue 1!
        val effectiveWithDelay = 5_500L - 1_000L
        assertEquals("สวัสดีทุกคน ยินดีต้อนรับ", SubtitleParser.findActiveText(cues, effectiveWithDelay))
    }

    @Test
    fun parseWebVttAndAssFormats() {
        val vtt = """
            WEBVTT

            00:01.000 --> 00:04.500 line:85% align:center
            ผู้กล้าแห่งดวงดาว
        """.trimIndent()

        val vttCues = SubtitleParser.parse(vtt)
        assertEquals(1, vttCues.size)
        assertEquals(1_000L, vttCues[0].startMs)
        assertEquals(4_500L, vttCues[0].endMs)
        assertEquals("ผู้กล้าแห่งดวงดาว", vttCues[0].text)

        val ass = """
            [Script Info]
            Title: Thai Cinema ASS

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:10.00,0:00:14.20,Default,,0,0,0,,{\pos(320,50)\fad(150,150)}บรรทัดแรก\Nบรรทัดที่สอง
        """.trimIndent()

        val assCues = SubtitleParser.parse(ass)
        assertEquals(1, assCues.size)
        assertEquals("บรรทัดแรก\nบรรทัดที่สอง", assCues[0].text)
    }

    @Test
    fun decodeLegacyWindows874ThaiSubtitleBytes() {
        val win874 = runCatching { Charset.forName("windows-874") }.getOrNull() ?: return
        val originalThai = "ภาษาไทยชัดเจนไม่เพี้ยน"
        val legacyBytes = originalThai.toByteArray(win874)

        val decoded = SubtitleParser.decodeBytesSmart(legacyBytes)
        assertEquals(originalThai, decoded)
    }

    @Test
    fun thaiSubtitleManagerPrioritizesThaiTrackFirst() {
        val manager = ThaiSubtitleManager(cacheDir = null)
        val tracks = listOf(
            SubtitleTrackOption("1", "English", "en", isEmbedded = true, isThai = false),
            SubtitleTrackOption("2", "Tiếng Việt", "vi", isEmbedded = false, isThai = false),
            SubtitleTrackOption("3", "ภาษาไทย", "th", isEmbedded = true, isThai = true)
        )
        val chosen = manager.chooseBestDefaultTrack(tracks)
        assertNotNull(chosen)
        assertEquals("3", chosen?.id)
        manager.release()
    }

    @Test
    fun parseM3uWithVlcHeadersAndCountryMetadata() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="https://img.test/thai.png" group-title="Movies",Thai Action Channel
            #EXTVLCOPT:http-user-agent=CustomTVAgent/2.0
            #EXTVLCOPT:http-referrer=https://tv.example.th/
            https://stream.example.th/live/index.m3u8
        """.trimIndent()

        val source = IptvCountrySource()
        val parsed = source.parseM3uContent(
            content = sampleM3u,
            defaultCountry = CountryCategory.THAILAND,
            providerId = "iptv_th",
            providerName = "Live TV Thái Lan",
            isLiveDefault = true
        )

        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("Thai Action Channel", item.title)
        assertEquals(CountryCategory.THAILAND, item.country)
        val ep = item.firstPlayableEpisode
        assertNotNull(ep)
        assertEquals("CustomTVAgent/2.0", ep?.headers?.get("User-Agent"))
        assertEquals("https://tv.example.th/", ep?.headers?.get("Referer"))
    }

    @Test
    fun v2AntiOverlapSubtitleFrameDefaultsAndSeasonEpisodeExtraction() {
        val defaultStyle = SubtitleStyleConfig()
        assertEquals(SubtitleEdgeStyle.BACKGROUND_BOX, defaultStyle.edgeStyle)
        assertFalse(defaultStyle.hardsubMaskEnabled)
        assertTrue(defaultStyle.boxOpacity in 0.70f..0.90f)
        assertEquals("th", defaultStyle.preferredPrimaryLang)
        assertTrue(defaultStyle.autoTranslateToThai)

        val fetcher = OnlineSubtitleFetcher()
        assertEquals(2, fetcher.extractSeasonNumber("Squid Game (Season 2)", "Trò Chơi Con Mực Phần 2"))
        assertEquals(3, fetcher.extractSeasonNumber("Sweet Home", "Thế Giới Ma Quái (Phần 3)"))
        assertEquals(1, fetcher.extractSeasonNumber("How to Make Millions Before Grandma Dies", "Gia Tài Của Ngoại"))
        assertEquals(1, fetcher.extractEpisodeNumber("Full HD"))
        assertEquals(8, fetcher.extractEpisodeNumber("Tập 08"))

        // Unwrap mid-sentence SRT line breaks while keeping multi-speaker dialogue turns
        val wrappedClause = "I don't think we can\nmake it before midnight."
        assertEquals(
            "I don't think we can make it before midnight.",
            SubtitleTranslator.normalizeDialogueForTranslation(wrappedClause)
        )
        val multiSpeaker = "- Are you ready?\n- Born ready."
        assertEquals(
            "- Are you ready?\n- Born ready.",
            SubtitleTranslator.normalizeDialogueForTranslation(multiSpeaker)
        )
    }

    @Test
    fun parseYouTubeTimedTextAndCompressedOpenSubtitlesAndYifyArchives() {
        // 1. YouTube URL extraction
        assertEquals("73_1biulkYk", YouTubeSource.extractVideoId("https://www.youtube.com/watch?v=73_1biulkYk&t=15s"))
        assertEquals("jgTlsXMrgmc", YouTubeSource.extractVideoId("https://youtu.be/jgTlsXMrgmc"))
        assertEquals("aqz-KE-bpKQ", YouTubeSource.extractVideoId("https://www.youtube.com/shorts/aqz-KE-bpKQ"))
        assertEquals("aqz-KE-bpKQ", YouTubeSource.extractVideoId("yt:aqz-KE-bpKQ"))

        // 2. YouTube TimedText format 3 XML (including nested <s> ASR segments & entities)
        val ytXml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <timedtext format="3">
            <body>
            <p t="1367" d="2084">I told you, you&#39;re not welcome here.</p>
            <p t="3500" d="4000"><s ac="252">สวัสดี</s><s t="400" ac="252">ครับทุกคน</s></p>
            <p t="5200" d="2000">ประโยคถัดไป</p>
            </body>
            </timedtext>
        """.trimIndent()
        val ytCues = SubtitleParser.parse(ytXml)
        assertEquals(3, ytCues.size)
        assertEquals("I told you, you're not welcome here.", ytCues[0].text)
        assertEquals("สวัสดีครับทุกคน", ytCues[1].text)
        // Rolling overlap clamped cleanly before cue 3 starts at 5200ms
        assertTrue(ytCues[1].endMs < ytCues[2].startMs)

        // 3. GZIP (.gz from OpenSubtitles.org REST)
        val rawSrt = "1\n00:00:01,000 --> 00:00:03,000\nซับไทยแท้จาก OpenSubtitles.org\n"
        val gzOut = ByteArrayOutputStream()
        GZIPOutputStream(gzOut).use { it.write(rawSrt.toByteArray(Charsets.UTF_8)) }
        val gzCues = SubtitleParser.parseBytes(gzOut.toByteArray())
        assertEquals(1, gzCues.size)
        assertEquals("ซับไทยแท้จาก OpenSubtitles.org", gzCues[0].text)

        // 4. ZIP (.zip from YTS / YIFY Subtitles)
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("YIFY Subtitles".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("movie.th.srt"))
            zos.write("1\n00:00:05,000 --> 00:00:08,000\nซับไทยแท้จาก YIFY\n".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val zipCues = SubtitleParser.parseBytes(zipOut.toByteArray())
        assertEquals(1, zipCues.size)
        assertEquals("ซับไทยแท้จาก YIFY", zipCues[0].text)
    }

    @Test
    fun geminiAudioBase64AndMpegTsDemuxerExtractsPureAdtsAac() {
        // 1. RFC 4648 pure-Kotlin Base64 encoder verification
        assertEquals("", GeminiAudioSubtitleGenerator.encodeBase64(ByteArray(0)))
        assertEquals("Zg==", GeminiAudioSubtitleGenerator.encodeBase64("f".toByteArray()))
        assertEquals("Zm8=", GeminiAudioSubtitleGenerator.encodeBase64("fo".toByteArray()))
        assertEquals("Zm9v", GeminiAudioSubtitleGenerator.encodeBase64("foo".toByteArray()))
        assertEquals("Zm9vYmFy", GeminiAudioSubtitleGenerator.encodeBase64("foobar".toByteArray()))

        // 2. Default Gemini model & auto audio subtitle config verification
        val cfg = SubtitleStyleConfig()
        assertEquals("gemini-3-flash-preview", cfg.geminiModelId)
        assertTrue(cfg.autoGenerateAudioSubWhenMissing)

        // 3. Build a synthetic 3-packet MPEG-TS stream (with 8-byte fake CDN wrapper prefix):
        // Packet 0: Video PES (PID 0x100, stream_id 0xE0) -> must be ignored
        // Packet 1: Audio PES start (PID 0x101, PUSI=1, stream_id 0xC0, ADTS AAC 0xFF 0xF1...)
        // Packet 2: Audio continuation (PID 0x101, PUSI=0, raw AAC continuation bytes)
        val tsStream = ByteArrayOutputStream()
        tsStream.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) // fake PNG header

        // Packet 0: Video PID 0x0100
        val pkt0 = ByteArray(188) { 0xAA.toByte() }
        pkt0[0] = 0x47
        pkt0[1] = 0x41 // PUSI=1, PID high=1 -> PID 0x100
        pkt0[2] = 0x00
        pkt0[3] = 0x10 // AFC=1 (payload only)
        pkt0[4] = 0x00; pkt0[5] = 0x00; pkt0[6] = 0x01; pkt0[7] = 0xE0.toByte() // Video PES
        pkt0[12] = 0x00
        tsStream.write(pkt0)

        // Packet 1: Audio PID 0x0101 (PUSI=1, Audio PES 0xC0, pesHeaderDataLen=3)
        val pkt1 = ByteArray(188) { 0x55.toByte() }
        pkt1[0] = 0x47
        pkt1[1] = 0x41 // PUSI=1, PID 0x101
        pkt1[2] = 0x01
        pkt1[3] = 0x10 // AFC=1
        pkt1[4] = 0x00; pkt1[5] = 0x00; pkt1[6] = 0x01; pkt1[7] = 0xC0.toByte() // Audio PES!
        pkt1[8] = 0x00; pkt1[9] = 0x00; pkt1[10] = 0x80.toByte(); pkt1[11] = 0x80.toByte()
        pkt1[12] = 0x03 // 3 PTS header bytes (13, 14, 15) -> Elementary stream starts at index 16!
        pkt1[16] = 0xFF.toByte()
        pkt1[17] = 0xF1.toByte() // ADTS AAC syncword
        pkt1[18] = 0x50
        tsStream.write(pkt1)

        // Packet 2: Audio PID 0x0101 continuation (PUSI=0 -> payload starts at index 4)
        val pkt2 = ByteArray(188) { 0x77.toByte() }
        pkt2[0] = 0x47
        pkt2[1] = 0x01 // PUSI=0, PID 0x101
        pkt2[2] = 0x01
        pkt2[3] = 0x10 // AFC=1
        tsStream.write(pkt2)

        val extractedOut = ByteArrayOutputStream()
        val mime = GeminiAudioSubtitleGenerator.extractElementaryAudioFromMpegTs(
            tsStream.toByteArray(),
            extractedOut
        )
        val aacBytes = extractedOut.toByteArray()

        assertEquals("audio/aac", mime)
        // Packet 1 contributes (188 - 16) = 172 bytes; Packet 2 contributes (188 - 4) = 184 bytes -> 356 bytes total
        assertEquals(356, aacBytes.size)
        assertEquals(0xFF.toByte(), aacBytes[0])
        assertEquals(0xF1.toByte(), aacBytes[1])
        assertEquals(0x50.toByte(), aacBytes[2])
        assertEquals(0x77.toByte(), aacBytes.last())
    }

    @Test
    fun subtitleAutoSyncEngineRecoversStudioIntroOffsetAndRejectsOutliers() {
        // 1. Trimmed median rejects wild outlier (-32.0s) when 4 anchor lines agree on +14.5s
        val medianPair = SubtitleAutoSyncEngine.computeTrimmedMedianOffsetMs(
            listOf(14_400L, 14_600L, -32_000L, 14_500L, 14_500L)
        )
        assertNotNull(medianPair)
        assertEquals(14_500L, medianPair!!.first)
        assertEquals(4, medianPair.second)

        // 2. Acoustic speech-envelope cross-correlation recovers a +12.0s CDN studio intro shift!
        val cues = listOf(
            SubtitleCue(1, 5_000L, 7_500L, "ประโยคที่ 1"),
            SubtitleCue(2, 9_200L, 12_800L, "ประโยคที่ 2"),
            SubtitleCue(3, 15_000L, 16_800L, "ประโยคที่ 3"),
            SubtitleCue(4, 21_500L, 26_000L, "ประโยคที่ 4"),
            SubtitleCue(5, 28_000L, 30_500L, "ประโยคที่ 5")
        )
        // Simulate 48s audio envelope starting at 0ms where every spoken line occurs +12,000ms LATER than SRT
        val trueIntroShiftMs = 12_000L
        val env = FloatArray(480) { binIdx ->
            val movieMs = binIdx * 100L
            val origMs = movieMs - trueIntroShiftMs
            val isSpeaking = cues.any { origMs in it.startMs..it.endMs }
            if (isSpeaking) 120f else 12f
        }

        val corr = SubtitleAutoSyncEngine.correlateEnvelopeWithCues(
            audioStartMs = 0L,
            audioEnvelope = env,
            cues = cues
        )
        assertNotNull(corr)
        assertTrue(kotlin.math.abs(corr!!.first - trueIntroShiftMs) <= 200L)
    }

    @Test
    fun hlsPlaylistSanitizerStripsMidRollAdsWhileKeepingRealConvertV7Segments() {
        val rawM3u8 = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:4.000,
            seg_0001.ts
            #EXTINF:4.000,
            seg_0002.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:4.000,
            convertv7/real_movie_watermark_01.ts
            #EXTINF:4.000,
            convertv7/real_movie_watermark_02.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:4.000,
            S2WROjET.ts
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:3.000,
            /v7/d61a03b1dabe537cc05ffe5c9c280f56/segment_0001.ts
            #EXTINF:3.000,
            /v7/d61a03b1dabe537cc05ffe5c9c280f56/segment_0002.ts
            #EXTINF:3.000,
            /adjump/202610/9988776655443322/000001.ts
            #EXT-X-DISCONTINUITY
            #EXTINF:4.000,
            THaLQ0gg.ts
            #EXTINF:4.000,
            seg_0005.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val cleaned = HlsPlaylistSanitizer.sanitizeMediaPlaylist(
            rawM3u8 = rawM3u8,
            playlistUrl = "https://s6.kkphimplayer6.com/20260401/abc12345/2000kb/hls/index.m3u8"
        )

        // All real movie segments (including relative convertv7/ re-encoded segments) MUST remain intact
        assertTrue(cleaned.contains("seg_0001.ts"))
        assertTrue(cleaned.contains("convertv7/real_movie_watermark_01.ts"))
        assertTrue(cleaned.contains("S2WROjET.ts"))
        assertTrue(cleaned.contains("THaLQ0gg.ts"))
        assertTrue(cleaned.contains("#EXT-X-ENDLIST"))

        // Injected mid-roll ad segments MUST be completely stripped so subtitles never jump +30.2s mid-episode
        assertFalse(cleaned.contains("d61a03b1dabe537cc05ffe5c9c280f56"))
        assertFalse(cleaned.contains("/adjump/"))
    }

    @Test
    fun strictImdbScorerRejectsFalseMovieMatchesForSeries() {
        // 1. 2026 Chinese TV Series "Dear Song" must NEVER match 2023 feature film "A Song for My Dear"
        assertEquals(
            -1,
            OnlineSubtitleFetcher.scoreImdbCandidate(
                query = "Dear Song",
                candidateLabel = "A Song for My Dear",
                qTypeRaw = "feature",
                targetYear = 2026,
                itemYear = 2023,
                expectSeries = true
            )
        )

        // 2. 2026 TV Series "Dream" must NEVER match 2023 Korean feature film "Dream" or 2009 TV series "Dream"
        assertEquals(
            -1,
            OnlineSubtitleFetcher.scoreImdbCandidate(
                query = "Dream",
                candidateLabel = "Dream",
                qTypeRaw = "feature",
                targetYear = 2026,
                itemYear = 2023,
                expectSeries = true
            )
        )
        assertEquals(
            -1,
            OnlineSubtitleFetcher.scoreImdbCandidate(
                query = "Dream",
                candidateLabel = "Dream",
                qTypeRaw = "TV series",
                targetYear = 2026,
                itemYear = 2009,
                expectSeries = true
            )
        )

        // 3. Real 2024 TV Series "Queen of Tears" matches cleanly
        val qotScore = OnlineSubtitleFetcher.scoreImdbCandidate(
            query = "Queen of Tears",
            candidateLabel = "Queen of Tears",
            qTypeRaw = "TV series",
            targetYear = 2024,
            itemYear = 2024,
            expectSeries = true
        )
        assertTrue(qotScore >= 115)

        // 4. Real 2024 Feature Film "Dune: Part Two" matches cleanly
        val duneScore = OnlineSubtitleFetcher.scoreImdbCandidate(
            query = "Dune: Part Two",
            candidateLabel = "Dune: Part Two",
            qTypeRaw = "feature",
            targetYear = 2024,
            itemYear = 2024,
            expectSeries = false
        )
        assertTrue(duneScore >= 115)
    }

    @Test
    fun twoAnchorLinearSyncSolverRecoversBothIntroOffsetAndFpsSpeedDrift() {
        // Simulate a 75-minute episode where .SRT is 23.976fps with a 55s Netflix intro (trueOffset = -55_000ms)
        // while the VOD stream is 25.000fps (trueScale = 1.04271f -> ~2.5 minutes of progressive drift across episode!)
        val trueScale = 1.04271
        val trueOffsetMs = -55_000L

        fun srtMsForMovie(movieMs: Long): Long =
            kotlin.math.round(movieMs * trueScale).toLong() - trueOffsetMs

        val primaryMatches = listOf(
            SubtitleAutoSyncEngine.AnchorMatch(120_000L, srtMsForMovie(120_000L)),
            SubtitleAutoSyncEngine.AnchorMatch(128_000L, srtMsForMovie(128_000L)),
            SubtitleAutoSyncEngine.AnchorMatch(136_000L, srtMsForMovie(136_000L)),
            SubtitleAutoSyncEngine.AnchorMatch(142_000L, 95_000L) // wild outlier must be rejected!
        )
        val secondaryMatches = listOf(
            SubtitleAutoSyncEngine.AnchorMatch(1_920_000L, srtMsForMovie(1_920_000L)),
            SubtitleAutoSyncEngine.AnchorMatch(1_930_000L, srtMsForMovie(1_930_000L)),
            SubtitleAutoSyncEngine.AnchorMatch(1_940_000L, srtMsForMovie(1_940_000L))
        )

        val solved = SubtitleAutoSyncEngine.solveTwoAnchorLinearSync(
            primaryMatches = primaryMatches,
            secondaryMatches = secondaryMatches,
            currentSpeedScale = 1.0f
        )
        assertNotNull(solved)
        val (detectedOffsetMs, detectedScale, inlierCount) = solved!!
        assertEquals(6, inlierCount)
        assertTrue(kotlin.math.abs(detectedScale - 1.04271f) <= 0.0002f)
        assertTrue(kotlin.math.abs(detectedOffsetMs - trueOffsetMs) <= 50L)
    }

    @Test
    fun geminiLowLatencyThinkingConfigAndTurboModelRoutingAreExactAcrossTiers() {
        // 1. Gemini 3 Flash / Flash-Lite -> thinkingLevel = MINIMAL (eliminates default HIGH reasoning delay!)
        val g3FlashCfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-3-flash-preview")
        assertEquals("MINIMAL", g3FlashCfg.getJSONObject("thinkingConfig").getString("thinkingLevel"))

        val g3LiteCfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-3.1-flash-lite-preview")
        assertEquals("MINIMAL", g3LiteCfg.getJSONObject("thinkingConfig").getString("thinkingLevel"))

        // 2. Gemini 3 Pro -> thinkingLevel = LOW (MINIMAL is unsupported on 3.1 Pro)
        val g3ProCfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-3.1-pro-preview")
        assertEquals("LOW", g3ProCfg.getJSONObject("thinkingConfig").getString("thinkingLevel"))

        // 3. Gemini 2.5 Flash -> thinkingBudget = 0; Gemini 2.5 Pro -> thinkingBudget = 128
        val g25FlashCfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-2.5-flash")
        assertEquals(0, g25FlashCfg.getJSONObject("thinkingConfig").getInt("thinkingBudget"))

        val g25ProCfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-2.5-pro")
        assertEquals(128, g25ProCfg.getJSONObject("thinkingConfig").getInt("thinkingBudget"))

        // 4. Gemini 2.0 Flash -> omits thinkingConfig cleanly
        val g20Cfg = GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig("gemini-2.0-flash")
        assertFalse(g20Cfg.has("thinkingConfig"))

        // 5. Turbo Flash routing puts Flash first for blocking 30s opening sprint when user picked a Pro model
        val turboChain = GeminiAudioSubtitleGenerator.buildFastModelChain("gemini-3.1-pro-preview", preferTurboFlash = true)
        assertEquals("gemini-3-flash-preview", turboChain.first())
        assertTrue(turboChain.contains("gemini-3.1-pro-preview"))

        val normalChain = GeminiAudioSubtitleGenerator.buildFastModelChain("gemini-3.1-pro-preview", preferTurboFlash = false)
        assertEquals("gemini-3.1-pro-preview", normalChain.first())
    }

    @Test
    fun companionPlayerTelemetrySerializesBilingualSnapCuesAndState() {
        val telemetry = CompanionPlayerTelemetry(
            isActive = true,
            movieId = "mov_th_01",
            movieTitle = "Gia Tài Của Ngoại",
            episodeId = "ep_01",
            episodeName = "Tập 1",
            streamUrl = "https://cdn.example.com/hls/index.m3u8",
            isPlaying = true,
            positionMs = 42_500L,
            durationMs = 7_200_000L,
            syncOffsetMs = -1_500L,
            subSpeedScale = 1.0f,
            primaryThaiText = "สวัสดีครับอาม่า",
            secondaryText = "Chào ngoại ạ",
            statusBadge = "Sub: ไทย • Đã khớp",
            snapCandidates = listOf(
                SnapCueCandidate(
                    id = 1,
                    startMs = 44_000L,
                    endMs = 46_500L,
                    thaiText = "สวัสดีครับอาม่า",
                    referenceText = "Chào ngoại ạ",
                    isNearestToAnchor = true
                )
            )
        )

        val json = telemetry.toJson()
        assertTrue(json.getBoolean("isActive"))
        assertEquals("Gia Tài Của Ngoại", json.getString("movieTitle"))
        assertEquals(42_500L, json.getLong("positionMs"))
        assertEquals("สวัสดีครับอาม่า", json.getString("primaryThaiText"))

        val snapArr = json.getJSONArray("snapCandidates")
        assertEquals(1, snapArr.length())
        val firstCue = snapArr.getJSONObject(0)
        assertEquals(44_000L, firstCue.getLong("startMs"))
        assertEquals("Chào ngoại ạ", firstCue.getString("referenceText"))
        assertTrue(firstCue.getBoolean("isNearest"))
    }

    @Test
    fun subtitleStyleConfigSupportsIndependentAndLinkedPrimarySecondaryCustomization() {
        val defaultCfg = com.phyll.filmhub.model.SubtitleStyleConfig()
        // Compact frame defaults (no huge 86dp black slab when silent!)
        assertFalse(defaultCfg.hardsubMaskEnabled)
        assertEquals(20f, defaultCfg.fontSizeSp, 0.01f)
        assertEquals(16f, defaultCfg.secondaryFontSizeSp, 0.01f)
        assertEquals(0.78f, defaultCfg.boxOpacity, 0.01f)
        assertEquals(com.phyll.filmhub.model.SubtitleColorChoice.CINEMA_YELLOW, defaultCfg.primaryColor)
        assertEquals(com.phyll.filmhub.model.SubtitleColorChoice.WHITE, defaultCfg.secondaryColor)
        assertFalse(defaultCfg.linkPrimarySecondaryStyle)

        // Independent Primary vs Secondary customization
        val independent = defaultCfg.copy(
            fontSizeSp = 26f,
            secondaryFontSizeSp = 18f,
            primaryColor = com.phyll.filmhub.model.SubtitleColorChoice.CYAN,
            secondaryColor = com.phyll.filmhub.model.SubtitleColorChoice.SAKURA_PINK,
            linkPrimarySecondaryStyle = false
        )
        assertEquals(26f, independent.fontSizeSp, 0.01f)
        assertEquals(18f, independent.secondaryFontSizeSp, 0.01f)
        assertEquals("#00E5FF", independent.primaryColor.hexCss)
        assertEquals("#FF80AB", independent.secondaryColor.hexCss)

        // Linked "Chỉnh Tất Cả" customization
        val linked = independent.copy(
            fontSizeSp = 24f,
            secondaryFontSizeSp = 20f,
            primaryColor = com.phyll.filmhub.model.SubtitleColorChoice.MINT_GREEN,
            secondaryColor = com.phyll.filmhub.model.SubtitleColorChoice.MINT_GREEN,
            linkPrimarySecondaryStyle = true
        )
        assertTrue(linked.linkPrimarySecondaryStyle)
        assertEquals(linked.primaryColor, linked.secondaryColor)
        assertEquals("#69F0AE", linked.primaryColor.hexCss)
    }

    @Test
    fun companionHttpServerServesSimultaneousSubtitleStudioAndDispatchesPrimarySecondaryAllEvents() {
        val receivedEvents = mutableListOf<com.phyll.filmhub.companion.CompanionEvent>()
        val server = com.phyll.filmhub.companion.CompanionHttpServer(
            port = 18993,
            onEvent = { ev -> synchronized(receivedEvents) { receivedEvents.add(ev) } }
        )
        try {
            server.start()
            Thread.sleep(120L)

            // 1. Verify Mobile Web Portal HTML includes the new simultaneous Subtitle Studio & 2.5s poll debounce
            val html = java.net.URL("http://127.0.0.1:18993/").readText()
            assertTrue(html.contains("tab-sub"))
            assertTrue(html.contains("nudgeTargetSubSize('ALL'"))
            assertTrue(html.contains("nudgeTargetSubSize('PRIMARY'"))
            assertTrue(html.contains("nudgeTargetSubSize('SECONDARY'"))
            assertTrue(html.contains("lastPhoneStyleEditMs"))
            assertTrue(html.contains("toggle_sub_drawer"))

            fun postJson(body: String): String {
                val conn = (java.net.URL("http://127.0.0.1:18993/api/action").openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                return conn.inputStream.bufferedReader().readText()
            }

            // 2. Test simultaneous ALL, PRIMARY, and SECONDARY subtitle style updates via /api/action
            val resAll = postJson("""{"type":"sub_style","target":"ALL","sizeDeltaSp":2,"color":"CYAN","linkPrimarySecondaryStyle":true}""")
            assertTrue(resAll.contains("\"ok\":true"))

            val resPri = postJson("""{"type":"sub_style","target":"PRIMARY","fontSizeSp":26,"color":"CINEMA_YELLOW","linkPrimarySecondaryStyle":false}""")
            assertTrue(resPri.contains("\"ok\":true"))

            val resSec = postJson("""{"type":"sub_style","target":"SECONDARY","secondaryFontSizeSp":18,"color":"SAKURA_PINK","dualSubEnabled":true,"linkPrimarySecondaryStyle":false}""")
            assertTrue(resSec.contains("\"ok\":true"))

            synchronized(receivedEvents) {
                assertEquals(3, receivedEvents.size)
                val evAll = receivedEvents[0] as com.phyll.filmhub.companion.CompanionEvent.UpdateSubtitleStyle
                assertEquals("ALL", evAll.target)
                assertEquals(2f, evAll.sizeDeltaSp ?: 0f, 0.01f)
                assertEquals("CYAN", evAll.colorName)
                assertEquals(true, evAll.linkPrimarySecondary)

                val evPri = receivedEvents[1] as com.phyll.filmhub.companion.CompanionEvent.UpdateSubtitleStyle
                assertEquals("PRIMARY", evPri.target)
                assertEquals(26f, evPri.fontSizeSp ?: 0f, 0.01f)
                assertEquals(false, evPri.linkPrimarySecondary)

                val evSec = receivedEvents[2] as com.phyll.filmhub.companion.CompanionEvent.UpdateSubtitleStyle
                assertEquals("SECONDARY", evSec.target)
                assertEquals(18f, evSec.secondaryFontSizeSp ?: 0f, 0.01f)
                assertEquals("SAKURA_PINK", evSec.colorName)
                assertEquals(true, evSec.dualSubEnabled)
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun adbTcpClientEncodesAospRsaKeyAndExecutesLoopbackSyncInstallAndUninstall() {
        val kpg = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = kpg.generateKeyPair()
        val pubStruct = com.phyll.filmhub.tvmanager.AdbTcpClient.encodeAdbPublicKeyStruct(
            pair.public as java.security.interfaces.RSAPublicKey
        )
        assertEquals(524, pubStruct.size)
        val bbStruct = java.nio.ByteBuffer.wrap(pubStruct).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, bbStruct.int) // 2048-bit modulus = 64 words
        assertEquals(65537, bbStruct.getInt(520))

        val sig = com.phyll.filmhub.tvmanager.AdbTcpClient.signAdbToken(ByteArray(20) { it.toByte() }, pair.private)
        assertEquals(256, sig.size)

        val hwParsed = com.phyll.filmhub.tvmanager.AdbTcpClient.parseHardwareInfoOutput(
            "MODEL=ViettelTV_4K_Box\nBRAND=Viettel\nPKG=package:/data/app/com.phyll.filmhub/base.apk\nVER=    versionName=2.8.0"
        )
        assertEquals("ViettelTV_4K_Box", hwParsed.model)
        assertTrue(hwParsed.packageInstalled)
        assertEquals("2.8.0", hwParsed.appVersion)

        // Full loopback ADB daemon simulation verifying handshake, SYNC SEND/DATA/DONE & pm install + pm uninstall
        val fakeApk = java.io.File.createTempFile("phyll_test", ".apk").apply {
            deleteOnExit()
            writeBytes(ByteArray(12_345) { (it % 251).toByte() })
        }
        val receivedSyncBytes = java.io.ByteArrayOutputStream()
        val executedCommands = java.util.Collections.synchronizedList(mutableListOf<String>())
        val mockDaemon = java.net.ServerSocket(0)
        val daemonPort = mockDaemon.localPort

        fun writePkt(out: java.io.OutputStream, cmd: Int, a0: Int, a1: Int, payload: ByteArray) {
            val sum = com.phyll.filmhub.tvmanager.AdbTcpClient.computeAdbChecksum(payload)
            val hdr = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(cmd).putInt(a0).putInt(a1).putInt(payload.size).putInt(sum).putInt(cmd xor -1)
                .array()
            out.write(hdr)
            if (payload.isNotEmpty()) out.write(payload)
            out.flush()
        }

        fun readPkt(inp: java.io.InputStream): Triple<Int, Pair<Int, Int>, ByteArray> {
            val hdr = ByteArray(24)
            var off = 0
            while (off < 24) {
                val r = inp.read(hdr, off, 24 - off)
                if (r < 0) throw java.io.EOFException()
                off += r
            }
            val bb = java.nio.ByteBuffer.wrap(hdr).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val cmd = bb.int
            val a0 = bb.int
            val a1 = bb.int
            val len = bb.int
            bb.int
            bb.int
            val body = ByteArray(len)
            var boff = 0
            while (boff < len) {
                val r = inp.read(body, boff, len - boff)
                if (r < 0) throw java.io.EOFException()
                boff += r
            }
            return Triple(cmd, a0 to a1, body)
        }

        val serverThread = Thread {
            while (!mockDaemon.isClosed) {
                val sock = runCatching { mockDaemon.accept() }.getOrNull() ?: break
                sock.use { s ->
                    val inp = java.io.BufferedInputStream(s.getInputStream())
                    val out = s.getOutputStream()
                    val cnxn = readPkt(inp)
                    assertEquals(com.phyll.filmhub.tvmanager.AdbTcpClient.A_CNXN, cnxn.first)
                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_AUTH, com.phyll.filmhub.tvmanager.AdbTcpClient.ADB_AUTH_TOKEN, 0, ByteArray(20) { 7 })
                    val authSig = readPkt(inp)
                    assertEquals(com.phyll.filmhub.tvmanager.AdbTcpClient.A_AUTH, authSig.first)
                    assertEquals(256, authSig.third.size)
                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_CNXN, com.phyll.filmhub.tvmanager.AdbTcpClient.ADB_VERSION, 65536, "device::\u0000".toByteArray())

                    var syncLocalId = -1
                    var shellLocalId = -1
                    var curShellService = ""
                    while (true) {
                        val pkt = runCatching { readPkt(inp) }.getOrNull() ?: break
                        when (pkt.first) {
                            com.phyll.filmhub.tvmanager.AdbTcpClient.A_OPEN -> {
                                val lid = pkt.second.first
                                val svc = String(pkt.third, Charsets.UTF_8).trimEnd('\u0000')
                                if (svc == "sync:") {
                                    syncLocalId = lid
                                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_OKAY, 100, lid, ByteArray(0))
                                } else {
                                    shellLocalId = lid
                                    curShellService = svc
                                    executedCommands.add(svc)
                                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_OKAY, 200, lid, ByteArray(0))
                                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_WRTE, 200, lid, "Success\n".toByteArray())
                                    readPkt(inp) // A_OKAY
                                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_CLSE, 200, lid, ByteArray(0))
                                }
                            }
                            com.phyll.filmhub.tvmanager.AdbTcpClient.A_WRTE -> {
                                val lid = pkt.second.first
                                receivedSyncBytes.write(pkt.third)
                                writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_OKAY, 100, lid, ByteArray(0))
                                val cur = String(receivedSyncBytes.toByteArray(), Charsets.US_ASCII)
                                if (cur.contains("DONE") && !cur.contains("QUIT") && lid == syncLocalId) {
                                    val okaySync = ByteArray(8).apply {
                                        this[0] = 'O'.code.toByte(); this[1] = 'K'.code.toByte()
                                        this[2] = 'A'.code.toByte(); this[3] = 'Y'.code.toByte()
                                    }
                                    writePkt(out, com.phyll.filmhub.tvmanager.AdbTcpClient.A_WRTE, 100, lid, okaySync)
                                }
                            }
                            com.phyll.filmhub.tvmanager.AdbTcpClient.A_CLSE -> {
                                if (pkt.second.first == syncLocalId || pkt.second.first == shellLocalId) {
                                    if (curShellService.contains("uninstall") || curShellService.contains("am start")) break
                                }
                            }
                        }
                    }
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val client = com.phyll.filmhub.tvmanager.AdbTcpClient(keyStorageDir = null)
            val progressReports = mutableListOf<Int>()
            client.pushAndInstallApk("127.0.0.1", daemonPort, fakeApk) { pct, _ ->
                progressReports.add(pct)
            }
            assertTrue(progressReports.contains(100))
            assertTrue(executedCommands.any { it.contains("pm install") })
            assertTrue(executedCommands.any { it.contains("am start") })

            client.uninstallPackage("127.0.0.1", daemonPort)
            assertTrue(executedCommands.any { it.contains("pm uninstall com.phyll.filmhub") })
        } finally {
            runCatching { mockDaemon.close() }
        }
    }

    @Test
    fun companionHttpServerServesDeviceInfoStreamedApkAndPlayMoviePayload() {
        val fakeApk = java.io.File.createTempFile("phyll_dist", ".apk").apply {
            deleteOnExit()
            writeBytes("PK_TEST_APK_PAYLOAD_2_8_0".toByteArray())
        }
        val receivedEvents = mutableListOf<com.phyll.filmhub.companion.CompanionEvent>()
        val server = com.phyll.filmhub.companion.CompanionHttpServer(
            port = 18995,
            deviceInfoProvider = {
                org.json.JSONObject()
                    .put("ok", true)
                    .put("version", "2.8.0")
                    .put("hardwareModel", "ViettelTV_4K_Box")
            },
            apkSourcePathProvider = { fakeApk.absolutePath },
            onEvent = { ev -> synchronized(receivedEvents) { receivedEvents.add(ev) } }
        )
        try {
            server.start()
            Thread.sleep(120L)

            val infoJson = java.net.URL("http://127.0.0.1:18995/api/info").readText()
            assertTrue(infoJson.contains("\"version\":\"2.8.0\""))
            assertTrue(infoJson.contains("ViettelTV_4K_Box"))

            val downloadedApkBytes = java.net.URL("http://127.0.0.1:18995/download.apk").openStream().use { it.readBytes() }
            assertEquals("PK_TEST_APK_PAYLOAD_2_8_0", String(downloadedApkBytes))

            val payload = org.json.JSONObject()
                .put("type", "play_movie_payload")
                .put("movieId", "m_thai_99")
                .put("movieTitle", "Định Mệnh")
                .put("countryCode", "th")
                .put("episodeId", "ep_5")
                .put("episodeName", "Tập 5")
                .put("streamUrl", "https://example.com/ep5.m3u8")
                .toString()

            val conn = (java.net.URL("http://127.0.0.1:18995/api/action").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().readText()
            assertTrue(resp.contains("\"ok\":true"))

            synchronized(receivedEvents) {
                assertEquals(1, receivedEvents.size)
                val ev = receivedEvents[0] as com.phyll.filmhub.companion.CompanionEvent.PlayMoviePayload
                assertEquals("m_thai_99", ev.movieId)
                assertEquals("Tập 5", ev.episodeName)
                assertEquals("https://example.com/ep5.m3u8", ev.streamUrl)
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun liveTvPureKotlinAdbAndCompanionVerificationWhenRequested() {
        val targetIp = (System.getProperty("liveTvIp") ?: System.getenv("LIVE_TV_IP"))?.trim().orEmpty()
        if (targetIp.isEmpty()) return

        val apkPath = (System.getProperty("liveApkPath") ?: System.getenv("LIVE_APK_PATH"))?.trim().orEmpty()
        val adbClient = com.phyll.filmhub.tvmanager.AdbTcpClient(keyStorageDir = null)
        val remoteClient = com.phyll.filmhub.tvmanager.RemoteTvControllerClient(adbClient)

        val probe = adbClient.testConnectionAndProbe(targetIp, allowPublicKeyPrompt = false)
        println("LIVE_PROBE($targetIp): model=${probe.model}, installed=${probe.packageInstalled}, ver=${probe.appVersion}, needsAuth=${probe.needsAuthorization}")
        assertTrue(!probe.needsAuthorization)
        assertTrue(probe.model.isNotBlank())

        if (apkPath.isNotEmpty()) {
            val apkFile = java.io.File(apkPath)
            assertTrue(apkFile.exists())
            val pcts = mutableListOf<Int>()
            adbClient.pushAndInstallApk(targetIp, apkFile = apkFile) { pct, msg ->
                if (pct % 20 == 0 || pct >= 90) println("LIVE_ADB_PROGRESS: $pct% - $msg")
                pcts.add(pct)
            }
            assertTrue(pcts.contains(100))
            Thread.sleep(3_000L)
        }

        val probedTv = kotlinx.coroutines.runBlocking { remoteClient.probeSingleTv(targetIp) }
        println("LIVE_TV_DEVICE: name=${probedTv.displayTitle}, adb=${probedTv.adbOnline}, comp=${probedTv.companionOnline}, ver=${probedTv.appVersion}")
        assertTrue(probedTv.adbOnline)
        assertTrue(probedTv.companionOnline)

        val infoJson = java.net.URL("http://$targetIp:8899/api/info").readText()
        println("LIVE_API_INFO: $infoJson")
        assertTrue(infoJson.contains("\"version\":\"3."))
        assertTrue(infoJson.contains("\"isTvHardware\":true"))

        // Verify full remote play_movie_payload + return to Home on live TV
        val playPayload = org.json.JSONObject()
            .put("type", "play_movie_payload")
            .put("movieId", "verify_v28_live")
            .put("movieTitle", "Phyll Cinema v2.8 Test")
            .put("countryCode", "th")
            .put("episodeId", "ep_1")
            .put("episodeName", "Tập 1")
            .put("streamUrl", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
            .toString()
        val connPlay = (java.net.URL("http://$targetIp:8899/api/action").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json")
        }
        connPlay.outputStream.use { it.write(playPayload.toByteArray(Charsets.UTF_8)) }
        assertTrue(connPlay.inputStream.bufferedReader().readText().contains("\"ok\":true"))
        Thread.sleep(1_200L)

        val liveStateAfterPlay = kotlinx.coroutines.runBlocking { remoteClient.fetchLiveState(targetIp) }
        println("LIVE_STATE_AFTER_PLAY: screen=${liveStateAfterPlay?.screen}, online=${liveStateAfterPlay?.online}")
        assertEquals("PLAYER", liveStateAfterPlay?.screen)

        // Return TV cleanly to Home
        kotlinx.coroutines.runBlocking {
            remoteClient.sendAction(targetIp, org.json.JSONObject().put("type", "nav").put("target", "home"))
        }

        val dlBytes = java.net.URL("http://$targetIp:8899/download.apk").openStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val r = inp.read(buf)
                if (r <= 0) break
                total += r
            }
            total
        }
        println("LIVE_DOWNLOAD_APK_BYTES: $dlBytes")
        assertTrue(dlBytes > 5_000_000L)
    }

    @Test
    fun appUpdaterParsesGitHubReleaseAndComparesVersionsCorrectly() {
        val current = com.phyll.filmhub.updater.AppVersionInfo("3.0.1", 101)
        val releaseJson = """
            {
              "tag_name": "v3.0.4",
              "name": "ChillFilm v3.0.4 (Build 104)",
              "body": "Bản mới\nversionCode: 104",
              "published_at": "2026-11-21T00:00:00Z",
              "assets": [
                {
                  "name": "ChillFilm-tv360-32bit.apk",
                  "size": 14000000,
                  "browser_download_url": "https://github.com/PrimiasArt/Chillfilm.net/releases/download/v3.0.4/ChillFilm-tv360-32bit.apk"
                },
                {
                  "name": "ChillFilm-latest.apk",
                  "size": 15200000,
                  "browser_download_url": "https://github.com/PrimiasArt/Chillfilm.net/releases/download/v3.0.4/ChillFilm-latest.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = com.phyll.filmhub.updater.AppUpdater.parseGitHubReleaseJson(releaseJson)
        assertNotNull(parsed)
        assertEquals("3.0.4", parsed!!.versionName)
        assertEquals(104, parsed.versionCode)
        assertEquals(15200000L, parsed.assetSizeBytes)
        assertTrue(parsed.downloadUrl.endsWith("ChillFilm-latest.apk"))
        assertTrue(com.phyll.filmhub.updater.AppUpdater.isUpdateAvailable(current, parsed))

        val olderVersionJson = """{"versionName":"3.0.1","versionCode":101,"downloadUrl":"https://example.com/app.apk"}"""
        val parsedOlder = com.phyll.filmhub.updater.AppUpdater.parseVersionJson(olderVersionJson)
        assertNotNull(parsedOlder)
        assertTrue(!com.phyll.filmhub.updater.AppUpdater.isUpdateAvailable(current, parsedOlder!!))
    }

    @Test
    fun universalMultiLanguageAiSubtitleCatalogSupportsPresetsAndCustomLanguages() {
        val vi = com.phyll.filmhub.model.SubtitleLanguageCatalog.resolve("vie")
        assertEquals("vi", vi.code)
        assertEquals("VI", vi.shortBadge)

        val ja = com.phyll.filmhub.model.SubtitleLanguageCatalog.resolve("ja")
        assertEquals("JA", ja.shortBadge)
        assertEquals("jpn", ja.openSubtitlesId)

        val custom = com.phyll.filmhub.model.SubtitleLanguageCatalog.resolve("Dutch")
        assertEquals("Dutch", custom.code)
        assertTrue(custom.geminiInstructionName.contains("Dutch"))

        assertTrue(com.phyll.filmhub.model.SubtitleLanguageCatalog.isTrackMatchingLanguage("kor", "ko"))
        assertTrue(com.phyll.filmhub.model.SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage("안녕하세요", "ko"))
        assertTrue(com.phyll.filmhub.model.SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage("Chào bạn nhé", "vi"))
        assertFalse(com.phyll.filmhub.model.SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage("Hello world", "th"))

        val cfg = SubtitleStyleConfig(preferredPrimaryLang = "ko", secondaryLangCode = "vi", dualSubEnabled = true)
        assertEquals("KO", cfg.resolvedPrimaryLanguage().shortBadge)
        assertEquals("vi", cfg.resolvedSecondaryLangCode())
        assertEquals("Tiếng Việt", cfg.resolvedSecondaryDisplayName())
    }

    @Test
    fun persistentEpisodeSubtitleCacheSavesAndRestoresInstantlyWithoutRetranslation() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "chillfilm_sub_cache_test_${System.currentTimeMillis()}")
        try {
            val movie = com.phyll.filmhub.model.MovieItem(
                id = "kkphim_test_movie_1",
                title = "Phim Test NKI",
                originalTitle = "NKI Cinema Test",
                providerId = "kkphim",
                providerName = "KKPhim"
            )
            val episode = com.phyll.filmhub.model.Episode(
                id = "ep_01",
                name = "Tập 1",
                streamUrl = "https://cdn.example.com/token=abc123/index.m3u8"
            )
            val sourceCues = listOf(
                SubtitleCue(1, 1_000L, 4_000L, "Hello everyone"),
                SubtitleCue(2, 5_000L, 8_000L, "Welcome to ChillFilm"),
                SubtitleCue(3, 9_000L, 12_000L, "Subtitles are cached forever")
            )
            val translatedVi = listOf(
                SubtitleCue(1, 1_000L, 4_000L, "Xin chào mọi người"),
                SubtitleCue(2, 5_000L, 8_000L, "Chào mừng tới ChillFilm"),
                SubtitleCue(3, 9_000L, 12_000L, "Phụ đề đã được lưu vĩnh viễn")
            )

            // 1. First viewing saves translated bundle to persistent dir
            val writerManager = ThaiSubtitleManager(cacheDir = null, persistentSubDir = tempDir)
            val savedOk = writerManager.saveEpisodeSubCache(
                movie = movie,
                episode = episode,
                bundle = com.phyll.filmhub.subtitle.SavedEpisodeSubtitleBundle(
                    movieId = movie.id,
                    episodeId = episode.id,
                    primaryLangCode = "vi",
                    secondaryLangCode = "en",
                    sourceLangCode = "en",
                    sourcePlatform = "OpenSubtitles",
                    isAiAudio = false,
                    completedAudioSlots = emptySet(),
                    translatedCueIds = setOf(1, 2, 3),
                    sourceCues = sourceCues,
                    primaryCues = translatedVi,
                    secondaryCues = sourceCues,
                    isFullyTranslated = true,
                    savedAtMs = System.currentTimeMillis()
                )
            )
            assertTrue(savedOk)
            writerManager.release()

            // 2. Re-opening the same episode later (even with refreshed CDN token!) restores instantly from disk!
            val readerManager = ThaiSubtitleManager(cacheDir = null, persistentSubDir = tempDir)
            val refreshedEpisode = episode.copy(streamUrl = "https://cdn.example.com/token=NEW_TOKEN_999/index.m3u8")
            val restored = readerManager.loadEpisodeSubCache(movie, refreshedEpisode, "vi")
            assertNotNull(restored)
            assertEquals(3, restored!!.primaryCues.size)
            assertEquals("Xin chào mọi người", restored.primaryCues[0].text)
            assertEquals("Hello everyone", restored.secondaryCues[0].text)
            assertTrue(restored.isFullyTranslated)

            // Different language must NOT collide with Vietnamese cache
            assertNull(readerManager.loadEpisodeSubCache(movie, refreshedEpisode, "th"))
            readerManager.release()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun appInterfaceStringsDefaultToEnglishAndSupportFiveLanguages() {
        assertEquals(com.phyll.filmhub.ui.i18n.AppLanguage.EN, com.phyll.filmhub.ui.i18n.AppLanguage.fromCode(null))
        assertEquals(com.phyll.filmhub.ui.i18n.AppLanguage.EN, com.phyll.filmhub.ui.i18n.AppLanguage.fromCode("unknown"))
        assertEquals(5, com.phyll.filmhub.ui.i18n.AppLanguage.entries.size)

        val en = com.phyll.filmhub.ui.i18n.AppStrings.forLanguage(com.phyll.filmhub.ui.i18n.AppLanguage.EN)
        val vi = com.phyll.filmhub.ui.i18n.AppStrings.forLanguage(com.phyll.filmhub.ui.i18n.AppLanguage.VI)
        val th = com.phyll.filmhub.ui.i18n.AppStrings.forLanguage(com.phyll.filmhub.ui.i18n.AppLanguage.TH)
        val zh = com.phyll.filmhub.ui.i18n.AppStrings.forLanguage(com.phyll.filmhub.ui.i18n.AppLanguage.ZH)
        val ko = com.phyll.filmhub.ui.i18n.AppStrings.forLanguage(com.phyll.filmhub.ui.i18n.AppLanguage.KO)

        assertEquals("Watch Now", en.watchNow)
        assertEquals("Xem Ngay", vi.watchNow)
        assertEquals("ดูเลย", th.watchNow)
        assertEquals("立即播放", zh.watchNow)
        assertEquals("지금 시청", ko.watchNow)
    }
}
