package com.phyll.filmhub

import com.phyll.filmhub.cloud.CloudSyncClient
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.offline.OfflineDownloadManager
import com.phyll.filmhub.subtitle.SavedEpisodeSubtitleBundle
import com.phyll.filmhub.subtitle.ThaiSubtitleManager
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidV35FeaturesTest {

    @Test
    fun wrongScriptGuard_blocksChineseUnderVietnameseAndThai_butAllowsValidTargets() {
        val chineseCues = listOf(
            SubtitleCue(1, 0L, 3_000L, "你好，今天天气真好"),
            SubtitleCue(2, 3_500L, 7_000L, "我们一起去看电影吧"),
            SubtitleCue(3, 7_500L, 11_000L, "不要忘记带上爆米花"),
            SubtitleCue(4, 11_500L, 15_000L, "这部电影非常精彩")
        )
        assertTrue(CloudSyncClient.hasWrongScriptForLang(chineseCues, "vi"))
        assertTrue(CloudSyncClient.hasWrongScriptForLang(chineseCues, "th"))
        assertTrue(CloudSyncClient.hasWrongScriptForLang(chineseCues, "en"))
        assertFalse(CloudSyncClient.hasWrongScriptForLang(chineseCues, "zh"))

        val vietnameseCues = listOf(
            SubtitleCue(1, 0L, 3_000L, "Xin chào, hôm nay thời tiết thật đẹp"),
            SubtitleCue(2, 3_500L, 7_000L, "Chúng ta cùng đi xem phim nhé"),
            SubtitleCue(3, 7_500L, 11_000L, "Đừng quên mang theo bắp rang bơ"),
            SubtitleCue(4, 11_500L, 15_000L, "Bộ phim này thực sự rất tuyệt vời")
        )
        assertFalse(CloudSyncClient.hasWrongScriptForLang(vietnameseCues, "vi"))
        assertTrue(CloudSyncClient.hasWrongScriptForLang(vietnameseCues, "th"))
    }

    @Test
    fun mergeTimeRanges_mergesWeb38sWindowsAndPreservesGaps() {
        val raw = listOf(
            0L to 38_000L,
            37_000L to 76_000L,
            75_000L to 114_000L,
            240_000L to 278_000L,
            277_500L to 316_000L
        )
        val merged = CloudSyncClient.mergeTimeRanges(raw)
        assertEquals(2, merged.size)
        assertEquals(0L to 114_000L, merged[0])
        assertEquals(240_000L to 316_000L, merged[1])

        val decoded = CloudSyncClient.decodeTimeRanges(
            JSONArray()
                .put(JSONArray().put(0L).put(38_000L))
                .put(JSONArray().put(37_000L).put(120_000L))
        )
        assertEquals(listOf(0L to 120_000L), decoded)
    }

    @Test
    fun episodeSubtitleCache_blocksPoisonedSave_andMergesMonotonically() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "chillfilm_v35_test_${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val mgr = ThaiSubtitleManager(cacheDir = tmpDir, persistentSubDir = tmpDir)
            val movie = MovieItem(id = "modern_test-drama", title = "Test Drama", detailLookupId = "test-drama")
            val ep = Episode(id = "ep-01", name = "Tập 01", streamUrl = "https://example.com/1.m3u8")

            // 1. Poisoned Chinese Hanzi under `vi` must be rejected
            val badBundle = SavedEpisodeSubtitleBundle(
                movieId = movie.id,
                episodeId = ep.id,
                primaryLangCode = "vi",
                secondaryLangCode = "",
                sourceLangCode = "zh",
                sourcePlatform = "Gemini Audio",
                isAiAudio = true,
                completedAudioSlots = setOf(0),
                translatedCueIds = setOf(1, 2, 3, 4),
                sourceCues = emptyList(),
                primaryCues = listOf(
                    SubtitleCue(1, 1000L, 4000L, "你好世界，这是一个测试"),
                    SubtitleCue(2, 5000L, 8000L, "今天我们要去哪里吃饭"),
                    SubtitleCue(3, 9000L, 12000L, "我非常喜欢看这部电影"),
                    SubtitleCue(4, 13000L, 16000L, "请不要忘记明天的会议")
                ),
                secondaryCues = emptyList(),
                isFullyTranslated = false,
                savedAtMs = 1000L
            )
            assertFalse(mgr.saveEpisodeSubCache(movie, ep, badBundle, uploadToCloud = false))
            assertNull(mgr.loadEpisodeSubCache(movie, ep, "vi"))

            // 2. Valid 20-minute Vietnamese AI Audio bundle saved
            val bigCues = (1..20).map { i ->
                val start = (i - 1) * 60_000L + 1_000L
                SubtitleCue(i, start, start + 4_000L, "Câu thoại tiếng Việt số $i trong bộ phim này")
            }
            val bigBundle = SavedEpisodeSubtitleBundle(
                movieId = movie.id,
                episodeId = ep.id,
                primaryLangCode = "vi",
                secondaryLangCode = "",
                sourceLangCode = "auto",
                sourcePlatform = "NKI AI Audio",
                isAiAudio = true,
                completedAudioSlots = (0..19).toSet(),
                translatedCueIds = bigCues.mapTo(HashSet()) { it.id },
                sourceCues = bigCues,
                primaryCues = bigCues,
                secondaryCues = emptyList(),
                isFullyTranslated = false,
                savedAtMs = 2000L,
                translatedRanges = listOf(0L to 1_200_000L),
                audioTranslatedUntilMs = 1_200_000L,
                totalDurationMs = 2_400_000L
            )
            assertTrue(mgr.saveEpisodeSubCache(movie, ep, bigBundle, uploadToCloud = false))

            // 3. Smaller partial save (e.g. only first 5 minutes) must NEVER erase minutes 5-20!
            val smallCues = bigCues.take(5)
            val smallBundle = bigBundle.copy(
                completedAudioSlots = (0..4).toSet(),
                primaryCues = smallCues,
                sourceCues = smallCues,
                translatedRanges = listOf(0L to 300_000L),
                audioTranslatedUntilMs = 300_000L,
                savedAtMs = 3000L
            )
            assertTrue(mgr.saveEpisodeSubCache(movie, ep, smallBundle, uploadToCloud = false))

            val reloaded = mgr.loadEpisodeSubCache(movie, ep, "vi")
            assertNotNull(reloaded)
            assertEquals(20, reloaded!!.primaryCues.size)
            assertEquals(20, reloaded.completedAudioSlots.size)
            assertTrue(reloaded.audioTranslatedUntilMs >= 1_200_000L)
            mgr.release()
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun offlineDownloadId_isCanonicalAcrossMoviePrefixVariants() {
        val m1 = MovieItem(id = "modern_nu-hoang-nuoc-mat", title = "Queen of Tears")
        val m2 = MovieItem(id = "kkphim_nu-hoang-nuoc-mat", title = "Queen of Tears", detailLookupId = "nu-hoang-nuoc-mat")
        val ep1 = Episode(id = "1", name = "Tập 01", streamUrl = "https://cdn.test/1.m3u8")
        val ep2 = Episode(id = "tap-1", name = "EP 1", streamUrl = "https://cdn.test/1.m3u8")
        assertEquals(
            OfflineDownloadManager.buildDownloadId(m1, ep1),
            OfflineDownloadManager.buildDownloadId(m2, ep2)
        )
        assertEquals("nu-hoang-nuoc-mat__tap-1", OfflineDownloadManager.buildDownloadId(m1, ep1))
    }

    @Test
    fun mergeCuesChronological_neverExponentiallyDoublesEqualTimestampCues() {
        val mgr = ThaiSubtitleManager(cacheDir = null)
        try {
            val base = listOf(
                SubtitleCue(1, 12_000L, 14_500L, "Tiêu Viêm cẩn thận!"),
                SubtitleCue(2, 12_000L, 15_000L, "Đấu Đế đã xuất hiện rồi!"),
                SubtitleCue(3, 12_400L, 15_200L, "Chạy mau đi!")
            )
            var acc = base
            // Merging the same overlapping cues 25 times previously exploded to 2^25 = 33M cues!
            repeat(25) {
                acc = mgr.mergeCuesChronological(acc, base)
            }
            assertEquals(3, acc.size)
            // Slightly shifted boundary overlap (+600ms) with identical text also deduplicates cleanly
            val shifted = listOf(SubtitleCue(99, 12_600L, 15_300L, "Tiêu Viêm cẩn thận!"))
            val merged = mgr.mergeCuesChronological(acc, shifted)
            assertEquals(3, merged.size)
        } finally {
            mgr.release()
        }
    }
}
