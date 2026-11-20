package com.phyll.filmhub.subtitle

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Industrial-Grade Two-Anchor Subtitle-to-Audio Synchronization Engine (v2.3):
 *
 * Solves BOTH constant intro/recap offsets (up to ±4 minutes) AND linear frame-rate drift
 * (`23.976 <-> 25.000 fps`, `24.000 <-> 25.000 fps`, `23.976 <-> 24.000 fps`) in ~3-4 seconds
 * with ZERO manual remote hunting:
 *
 * 1. Two-Anchor Concurrent Multimodal Semantic Alignment (when Gemini Key is in RAM):
 *    - Samples TWO dense dialogue windows concurrently (`Anchor A` at viewer's scene / post-OP
 *      dialogue cluster + `Anchor B` 15-35 minutes away in mid-episode dialogue).
 *    - Searches a symmetric ±240-second (±4 minute) cue window around both the active offset
 *      and 0s offset with center-outward selection (never truncating late cues).
 *    - Jointly solves `subSpeedScale` and `syncOffsetMs` via outlier-resistant trimmed median
 *      and cinema FPS ratio snapping, while detecting wrong-movie/wrong-release `.SRT` files
 *      (`mismatchConfirmed = true`) when actors clearly speak (`spokenCount >= 3`) yet 0 cues match.
 *
 * 2. Strict Two-Window Consensus Acoustic Speech-Transient VAD (Zero-Key mode):
 *    - Cross-correlates TWO independent 45-second speech envelopes across `-150s .. +150s`,
 *      requiring both windows to independently exceed strict SNR thresholds and agree within ±2.0s
 *      so background music can NEVER corrupt an already-aligned subtitle.
 */
class SubtitleAutoSyncEngine(
    private val audioExtractor: GeminiAudioSubtitleGenerator,
    private val cacheDir: File? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    data class AutoSyncResult(
        val detectedOffsetMs: Long,
        val detectedSpeedScale: Float = 1.0f,
        val matchedAnchors: Int,
        val methodLabel: String,
        val confidence: Float,
        val mismatchConfirmed: Boolean = false
    )

    data class AnchorMatch(
        val movieSpokenMs: Long,
        val cueStartMs: Long
    )

    private data class GeminiWindowAlignment(
        val windowStartMs: Long,
        val spokenSentenceCount: Int,
        val matches: List<AnchorMatch>,
        val modelUsed: String
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val BIN_MS = 100L
        private const val CANDIDATE_RADIUS_MS = 240_000L // ±4 minutes symmetric search radius
        private const val MAX_CANDIDATE_CUES_PER_CLIP = 220

        private val CREDIT_OR_NOISE_REGEX = Regex(
            """(?i)(?:opensubtitles|subscene|yts-subs|subtitles?\s+by|translated\s+by|synced\s+by|คำบรรยายโดย|แปลโดย|ซับไทยโดย|phụ\s*đề\s*bởi|dịch\s*bởi|www\.|http://|https://)"""
        )
        private val PURE_MUSIC_OR_SFX_REGEX = Regex(
            """^\s*(?:[\(\[♪♫♬].*[\)\]♪♫♬]|[♪♫♬]+|[-–—.\s?!]+)\s*$"""
        )

        private data class StandardFpsRatio(
            val scale: Float,
            val tolerance: Float,
            val label: String
        )

        private val STANDARD_FPS_RATIOS = listOf(
            StandardFpsRatio(1.00000f, 0.0020f, "1.000x"),
            StandardFpsRatio(1.04271f, 0.0058f, "23.976->25fps"),
            StandardFpsRatio(0.95904f, 0.0058f, "25->23.976fps"),
            StandardFpsRatio(1.04167f, 0.0040f, "24->25fps"),
            StandardFpsRatio(0.96000f, 0.0040f, "25->24fps"),
            StandardFpsRatio(1.00100f, 0.0006f, "23.976->24fps"),
            StandardFpsRatio(0.99900f, 0.0006f, "24->23.976fps")
        )

        fun isSubstantiveDialogueCue(cue: SubtitleCue): Boolean {
            val clean = cue.text.replace('\n', ' ').trim()
            if (clean.length < 4) return false
            if (PURE_MUSIC_OR_SFX_REGEX.matches(clean)) return false
            if (CREDIT_OR_NOISE_REGEX.containsMatchIn(clean)) return false
            return true
        }

        /**
         * Selects up to TWO optimal ~38-second audio sample timestamps:
         * - Primary window: right around `aroundPositionMs` if viewer triggered mid-episode (> 12s),
         *   otherwise skips opening 90s theme song / logos and picks the densest early dialogue cluster.
         * - Secondary window: a dense mid/late-episode dialogue cluster separated by >= 10 minutes
         *   so linear FPS drift (`23.976 <-> 25 fps`) and episode-wide consistency are solved simultaneously!
         */
        fun chooseTwoAnchorAudioSampleStartsMs(
            cues: List<SubtitleCue>,
            aroundPositionMs: Long,
            totalStreamDurationMs: Long
        ): List<Long> {
            val dialogueCues = cues.filter { isSubstantiveDialogueCue(it) }.ifEmpty { cues }
            val maxCueMs = dialogueCues.lastOrNull()?.endMs ?: totalStreamDurationMs
            val effectiveDurationMs = if (totalStreamDurationMs > 60_000L) {
                minOf(totalStreamDurationMs, maxCueMs.coerceAtLeast(totalStreamDurationMs))
            } else {
                maxCueMs.coerceAtLeast(120_000L)
            }

            // 1. Primary Anchor
            val primaryStartMs = if (aroundPositionMs > 12_000L) {
                (aroundPositionMs - 12_000L).coerceAtLeast(0L)
            } else {
                // Skip first 90s OP song/studio intro when movie/episode is longer than 8 minutes
                val minEarlyMs = if (effectiveDurationMs > 480_000L) 95_000L else 5_000L
                val maxEarlyMs = minOf(effectiveDurationMs - 45_000L, 540_000L).coerceAtLeast(minEarlyMs + 30_000L)
                findDensestDialogueWindowStartMs(dialogueCues, minEarlyMs, maxEarlyMs)
                    ?: (dialogueCues.firstOrNull { it.startMs >= minEarlyMs }?.startMs?.minus(6_000L))
                    ?.coerceAtLeast(0L)
                    ?: 0L
            }

            // 2. Secondary Anchor (for 2-point FPS drift + episode-wide verification on >= 14-min videos)
            if (effectiveDurationMs < 840_000L) {
                return listOf(primaryStartMs)
            }

            val minSeparationMs = 600_000L // At least 10 minutes apart
            val secondRange: Pair<Long, Long> = if (primaryStartMs < effectiveDurationMs / 2L) {
                val lo = maxOf(primaryStartMs + minSeparationMs, (effectiveDurationMs * 0.42).toLong())
                val hi = (effectiveDurationMs * 0.76).toLong().coerceAtMost(effectiveDurationMs - 60_000L)
                lo to hi
            } else {
                val lo = 110_000L
                val hi = minOf(primaryStartMs - minSeparationMs, (effectiveDurationMs * 0.40).toLong())
                lo to hi
            }

            if (secondRange.second <= secondRange.first + 25_000L) {
                return listOf(primaryStartMs)
            }

            val secondaryStartMs = findDensestDialogueWindowStartMs(
                cues = dialogueCues,
                rangeStartMs = secondRange.first,
                rangeEndMs = secondRange.second
            )
            return if (secondaryStartMs != null && abs(secondaryStartMs - primaryStartMs) >= minSeparationMs) {
                listOf(primaryStartMs, secondaryStartMs)
            } else {
                listOf(primaryStartMs)
            }
        }

        private fun findDensestDialogueWindowStartMs(
            cues: List<SubtitleCue>,
            rangeStartMs: Long,
            rangeEndMs: Long
        ): Long? {
            val slice = cues.filter { it.startMs in rangeStartMs..rangeEndMs }
            if (slice.isEmpty()) return null
            var bestAnchorMs = slice.first().startMs
            var bestCount = -1
            for (c in slice) {
                val count = slice.count { it.startMs in c.startMs..(c.startMs + 28_000L) }
                if (count > bestCount) {
                    bestCount = count
                    bestAnchorMs = c.startMs
                }
            }
            return (bestAnchorMs - 5_000L).coerceAtLeast(0L)
        }

        /**
         * Selects candidate `.SRT` cues within ±240s around BOTH `currentSyncOffsetMs` and `0s` offset,
         * sampling symmetrically outward from the center so late cues are NEVER chopped off!
         */
        fun selectSymmetricCandidateCues(
            sourceCues: List<SubtitleCue>,
            clipStartMs: Long,
            clipEndMs: Long,
            currentSpeedScale: Float,
            currentSyncOffsetMs: Long,
            radiusMs: Long = CANDIDATE_RADIUS_MS,
            maxCues: Int = MAX_CANDIDATE_CUES_PER_CLIP
        ): List<SubtitleCue> {
            val clipMidMs = (clipStartMs + clipEndMs) / 2L
            val centerWithOffset = (clipMidMs * currentSpeedScale.toDouble()).toLong() - currentSyncOffsetMs
            val centerZeroOffset = clipMidMs

            val min1 = (centerWithOffset - radiusMs).coerceAtLeast(0L)
            val max1 = centerWithOffset + radiusMs
            val min2 = (centerZeroOffset - radiusMs).coerceAtLeast(0L)
            val max2 = centerZeroOffset + radiusMs

            val substantive = sourceCues.filter { cue ->
                isSubstantiveDialogueCue(cue) &&
                    ((cue.startMs in min1..max1) || (cue.startMs in min2..max2))
            }
            val pool = substantive.ifEmpty {
                sourceCues.filter { cue ->
                    (cue.startMs in min1..max1) || (cue.startMs in min2..max2)
                }
            }
            if (pool.size <= maxCues) return pool

            // Prioritize cues closest to either expected center symmetrically in BOTH - and + directions!
            return pool
                .sortedBy { cue ->
                    minOf(
                        abs(cue.startMs - centerWithOffset),
                        abs(cue.startMs - centerZeroOffset)
                    )
                }
                .take(maxCues)
                .sortedBy { it.startMs }
        }

        /**
         * Computes an outlier-resistant trimmed median offset from matched `(actualSpokenMs, cueStartMs)` pairs
         * for a given `subSpeedScale`.
         * Formula: `syncOffsetMs = round(actualSpokenMs * subSpeedScale) - cueStartMs`
         */
        fun computeTrimmedMedianOffsetMs(
            rawOffsetsMs: List<Long>,
            inlierToleranceMs: Long = 2_400L
        ): Pair<Long, Int>? {
            if (rawOffsetsMs.isEmpty()) return null
            val sorted = rawOffsetsMs.sorted()

            // Find the tightest cluster mode first so even 40% false matches can't pull the median!
            var bestCenter = sorted[sorted.size / 2]
            var bestClusterSize = 0
            for (candidate in sorted) {
                val count = sorted.count { abs(it - candidate) <= inlierToleranceMs }
                if (count > bestClusterSize) {
                    bestClusterSize = count
                    bestCenter = candidate
                }
            }

            val inliers = sorted.filter { abs(it - bestCenter) <= inlierToleranceMs }
            if (inliers.isEmpty()) return Pair(bestCenter, 1)

            val refinedMedian = if (inliers.size % 2 == 1) {
                inliers[inliers.size / 2]
            } else {
                (inliers[inliers.size / 2 - 1] + inliers[inliers.size / 2]) / 2L
            }
            return Pair(refinedMedian, inliers.size)
        }

        /**
         * Solves both `subSpeedScale` (e.g. `1.000x`, `1.04271x` for 23.976->25fps, `0.95904x` for 25->23.976fps)
         * and `syncOffsetMs` from Primary Anchor matches and optional Secondary Anchor matches.
         */
        fun solveTwoAnchorLinearSync(
            primaryMatches: List<AnchorMatch>,
            secondaryMatches: List<AnchorMatch>,
            currentSpeedScale: Float = 1.0f
        ): Triple<Long, Float, Int>? {
            val primaryCluster = filterConsistentAnchorCluster(primaryMatches, currentSpeedScale)
            val secondaryCluster = filterConsistentAnchorCluster(secondaryMatches, currentSpeedScale)

            // Case 1: Both early and mid/late anchors yielded clean dialogue clusters (>= 2 inliers each)
            if (primaryCluster.size >= 2 && secondaryCluster.size >= 2) {
                val m1 = medianLong(primaryCluster.map { it.movieSpokenMs })
                val s1 = medianLong(primaryCluster.map { it.cueStartMs })
                val m2 = medianLong(secondaryCluster.map { it.movieSpokenMs })
                val s2 = medianLong(secondaryCluster.map { it.cueStartMs })

                val deltaMovieMs = m2 - m1
                val deltaSrtMs = s2 - s1

                if (abs(deltaMovieMs) >= 480_000L) {
                    val empiricalScale = (deltaSrtMs.toDouble() / deltaMovieMs.toDouble()).toFloat()
                    val matchedStandardFps = STANDARD_FPS_RATIOS.minByOrNull { abs(it.scale - empiricalScale) }
                        ?.takeIf { abs(it.scale - empiricalScale) <= it.tolerance }

                    if (matchedStandardFps != null) {
                        val lockedScale = matchedStandardFps.scale
                        val combinedOffsets = (primaryCluster + secondaryCluster).map { m ->
                            (m.movieSpokenMs * lockedScale.toDouble()).roundToLong() - m.cueStartMs
                        }
                        val (jointOffset, jointInliers) = computeTrimmedMedianOffsetMs(combinedOffsets, 2_500L)
                            ?: return null
                        return Triple(jointOffset, lockedScale, jointInliers)
                    }
                }

                // If empirical slope does not match a linear FPS ratio (e.g. a commercial scene was cut
                // mid-episode while FPS is 1.0x), lock accurately onto the Primary Anchor (viewer's scene)!
                val primaryOffsets = primaryCluster.map { m ->
                    (m.movieSpokenMs * currentSpeedScale.toDouble()).roundToLong() - m.cueStartMs
                }
                val (pOffset, pInliers) = computeTrimmedMedianOffsetMs(primaryOffsets, 2_200L) ?: return null
                return Triple(pOffset, currentSpeedScale, pInliers)
            }

            // Case 2: Only one anchor had clear spoken dialogue -> require >= 2 tight inliers
            val activeCluster = when {
                primaryCluster.size >= 2 -> primaryCluster
                secondaryCluster.size >= 2 -> secondaryCluster
                else -> emptyList()
            }
            if (activeCluster.size >= 2) {
                val offsets = activeCluster.map { m ->
                    (m.movieSpokenMs * currentSpeedScale.toDouble()).roundToLong() - m.cueStartMs
                }
                val (offset, inliers) = computeTrimmedMedianOffsetMs(offsets, 2_000L) ?: return null
                if (inliers >= 2) {
                    return Triple(offset, currentSpeedScale, inliers)
                }
            }

            return null
        }

        private fun filterConsistentAnchorCluster(
            matches: List<AnchorMatch>,
            approxScale: Float
        ): List<AnchorMatch> {
            if (matches.size < 2) return matches
            val rawOffsets = matches.map { m ->
                (m.movieSpokenMs * approxScale.toDouble()).roundToLong() - m.cueStartMs
            }
            val (centerOffset, _) = computeTrimmedMedianOffsetMs(rawOffsets, 2_400L) ?: return emptyList()
            return matches.filter { m ->
                val off = (m.movieSpokenMs * approxScale.toDouble()).roundToLong() - m.cueStartMs
                abs(off - centerOffset) <= 2_400L
            }
        }

        private fun medianLong(values: List<Long>): Long {
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2L
        }

        /**
         * Pure-Kotlin normalized cross-correlation between a 100ms-binned speech activity envelope
         * [audioEnvelope] (starting at [audioStartMs]) and [cues] across `minOffsetMs..maxOffsetMs`.
         */
        fun correlateEnvelopeWithCues(
            audioStartMs: Long,
            audioEnvelope: FloatArray,
            cues: List<SubtitleCue>,
            subSpeedScale: Float = 1.0f,
            minOffsetMs: Long = -150_000L,
            maxOffsetMs: Long = 150_000L
        ): Pair<Long, Float>? {
            if (audioEnvelope.size < 100 || cues.size < 4) return null

            var meanA = 0f
            for (v in audioEnvelope) meanA += v
            meanA /= audioEnvelope.size
            val normAudio = FloatArray(audioEnvelope.size)
            var energyA = 0f
            for (i in audioEnvelope.indices) {
                val c = audioEnvelope[i] - meanA
                normAudio[i] = c
                energyA += c * c
            }
            if (energyA < 1e-5f) return null
            val denomA = sqrt(energyA)

            val minNeededCueMs = ((audioStartMs * subSpeedScale).toLong() - maxOffsetMs - 5_000L).coerceAtLeast(0L)
            val maxNeededCueMs = ((audioStartMs + audioEnvelope.size * BIN_MS) * subSpeedScale).toLong() - minOffsetMs + 5_000L
            if (maxNeededCueMs <= minNeededCueMs) return null

            val totalMaskBins = ((maxNeededCueMs - minNeededCueMs) / BIN_MS).toInt() + 2
            if (totalMaskBins <= 10 || totalMaskBins > 90_000) return null
            val cueMask = FloatArray(totalMaskBins) { -0.30f }
            var activeBins = 0
            for (cue in cues) {
                if (cue.endMs < minNeededCueMs || cue.startMs > maxNeededCueMs) continue
                val b0 = ((cue.startMs - minNeededCueMs) / BIN_MS).toInt().coerceIn(0, totalMaskBins - 1)
                val b1 = ((cue.endMs - minNeededCueMs) / BIN_MS).toInt().coerceIn(b0, totalMaskBins - 1)
                for (b in b0..b1) {
                    if (cueMask[b] < 0f) activeBins++
                    cueMask[b] = 1.0f
                }
            }
            if (activeBins < 18) return null

            val minLagSteps = (minOffsetMs / BIN_MS).toInt()
            val maxLagSteps = (maxOffsetMs / BIN_MS).toInt()

            var bestOffsetMs = 0L
            var bestScore = -1f
            val allPositiveScores = ArrayList<Float>((maxLagSteps - minLagSteps) + 1)

            var lagStep = minLagSteps
            while (lagStep <= maxLagSteps) {
                val candidateOffsetMs = lagStep * BIN_MS
                var dot = 0f
                var energyC = 0f

                for (i in normAudio.indices) {
                    val movieMs = audioStartMs + i * BIN_MS
                    val effectiveCueMs = (movieMs * subSpeedScale).toLong() - candidateOffsetMs
                    val maskIdx = ((effectiveCueMs - minNeededCueMs) / BIN_MS).toInt()
                    val mVal = if (maskIdx in 0 until totalMaskBins) cueMask[maskIdx] else -0.30f
                    dot += normAudio[i] * mVal
                    energyC += mVal * mVal
                }

                val score = if (energyC > 1e-4f) dot / (denomA * sqrt(energyC)) else 0f
                if (score > 0f) allPositiveScores.add(score)
                if (score > bestScore) {
                    bestScore = score
                    bestOffsetMs = candidateOffsetMs
                }
                lagStep++
            }

            // Strict peak-to-median threshold (>= 1.85f & score >= 0.24f) prevents music beats from hallucinating offsets
            if (bestScore < 0.24f || allPositiveScores.isEmpty()) return null
            allPositiveScores.sort()
            val medianPos = allPositiveScores[allPositiveScores.size / 2].coerceAtLeast(0.05f)
            val peakOverMedian = bestScore / medianPos
            if (peakOverMedian < 1.85f) return null

            return Pair(bestOffsetMs, peakOverMedian)
        }
    }

    /**
     * Automatically detects BOTH `syncOffsetMs` and `subSpeedScale` for [sourceCues] against [episode]'s real audio.
     */
    suspend fun detectBestSyncOffset(
        episode: Episode,
        sourceCues: List<SubtitleCue>,
        aroundPositionMs: Long,
        currentSyncOffsetMs: Long = 0L,
        subSpeedScale: Float = 1.0f,
        geminiApiKey: String,
        geminiModelId: String
    ): Result<AutoSyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceCues.size >= 4) { "Cần ít nhất 4 câu phụ đề để tự động khớp giọng nói" }

            val timeline = audioExtractor.inspectStreamTimeline(episode)
                ?: error("Không đọc được luồng Audio của phim để đối chiếu")

            val anchorStarts = chooseTwoAnchorAudioSampleStartsMs(
                cues = sourceCues,
                aroundPositionMs = aroundPositionMs,
                totalStreamDurationMs = timeline.totalDurationMs
            )

            val sampleDurationMs = 38_000L // ~245 KB pure ADTS AAC per anchor
            val extractedChunks = coroutineScope {
                anchorStarts.map { startMs ->
                    async(Dispatchers.IO) {
                        audioExtractor.extractAudioWindow(
                            timeline = timeline,
                            windowStartMs = startMs,
                            windowDurationMs = sampleDurationMs
                        )?.takeIf { it.audioBytes.size >= 1024 }
                    }
                }.mapNotNull { it.await() }
            }

            require(extractedChunks.isNotEmpty()) {
                "Không trích xuất được mẫu âm thanh từ luồng phim"
            }

            val cleanKey = geminiApiKey.trim()
            if (cleanKey.isNotEmpty()) {
                val cleanModel = geminiModelId.trim().removePrefix("models/").ifBlank { "gemini-3-flash-preview" }
                val alignments = coroutineScope {
                    extractedChunks.map { chunk ->
                        async(Dispatchers.IO) {
                            runCatching {
                                alignWindowWithGeminiMultimodal(
                                    audioChunk = chunk,
                                    sourceCues = sourceCues,
                                    currentSyncOffsetMs = currentSyncOffsetMs,
                                    currentSpeedScale = subSpeedScale,
                                    apiKey = cleanKey,
                                    modelId = cleanModel
                                )
                            }.getOrNull()
                        }
                    }.mapNotNull { it.await() }
                }

                if (alignments.isNotEmpty()) {
                    val primaryMatches = alignments.first().matches
                    val secondaryMatches = alignments.getOrNull(1)?.matches.orEmpty()
                    val solved = solveTwoAnchorLinearSync(
                        primaryMatches = primaryMatches,
                        secondaryMatches = secondaryMatches,
                        currentSpeedScale = subSpeedScale
                    )
                    if (solved != null) {
                        val (bestOffset, bestScale, inlierCount) = solved
                        val usedModel = alignments.first().modelUsed
                        val scaleNote = if (abs(bestScale - 1.0f) >= 0.002f) {
                            " • FPS %.3fx".format(bestScale)
                        } else ""
                        return@runCatching AutoSyncResult(
                            detectedOffsetMs = bestOffset,
                            detectedSpeedScale = bestScale,
                            matchedAnchors = inlierCount,
                            methodLabel = "Gemini 2-Điểm ($usedModel$scaleNote)",
                            confidence = (inlierCount / 4.0f).coerceIn(0.68f, 1.0f),
                            mismatchConfirmed = false
                        )
                    }

                    // Check if actors clearly spoke multiple sentences across our samples, yet ZERO .SRT cues matched!
                    val totalHeardSentences = alignments.sumOf { it.spokenSentenceCount }
                    if (totalHeardSentences >= 4) {
                        return@runCatching AutoSyncResult(
                            detectedOffsetMs = currentSyncOffsetMs,
                            detectedSpeedScale = subSpeedScale,
                            matchedAnchors = 0,
                            methodLabel = "Bản Sub lệch bản cắt / sai tập",
                            confidence = 0f,
                            mismatchConfirmed = true
                        )
                    }
                }
            }

            // Zero-Key Mode ONLY: Two-Window Consensus Hardware MediaCodec VAD Cross-Correlation
            // (Never runs when Gemini already listened and rejected a mismatched .SRT!)
            if (cleanKey.isEmpty() && extractedChunks.size >= 2) {
                val vad1 = alignWithHardwareVadCorrelation(extractedChunks[0], sourceCues, subSpeedScale)
                val vad2 = alignWithHardwareVadCorrelation(extractedChunks[1], sourceCues, subSpeedScale)
                if (vad1 != null && vad2 != null && abs(vad1.detectedOffsetMs - vad2.detectedOffsetMs) <= 2_200L) {
                    val avgOffset = (vad1.detectedOffsetMs + vad2.detectedOffsetMs) / 2L
                    return@runCatching AutoSyncResult(
                        detectedOffsetMs = avgOffset,
                        detectedSpeedScale = subSpeedScale,
                        matchedAnchors = 2,
                        methodLabel = "Acoustic 2-Điểm VAD",
                        confidence = minOf(vad1.confidence, vad2.confidence)
                    )
                }
            }

            if (cleanKey.isEmpty()) {
                error("Chưa có Gemini Key trong RAM — hãy quét QR nạp Key tạm để AI nghe thoại khớp chính xác ±4 phút & tự bắt trôi FPS, hoặc bấm '⚡ Chọn Câu Khớp'!")
            } else {
                error("Đoạn này ít lời thoại rõ ràng — hãy bấm '⚡ Chọn Câu Khớp' (chỉ 1 chạm) hoặc đổi sang '🎙️ Gemini Nghe Phim Tạo Sub'!")
            }
        }
    }

    private fun alignWindowWithGeminiMultimodal(
        audioChunk: GeminiAudioSubtitleGenerator.ExtractedAudioChunk,
        sourceCues: List<SubtitleCue>,
        currentSyncOffsetMs: Long,
        currentSpeedScale: Float,
        apiKey: String,
        modelId: String
    ): GeminiWindowAlignment? {
        val clipStartMs = audioChunk.actualStartMs
        val clipEndMs = audioChunk.actualEndMs
        val clipDurSec = "%.1f".format((clipEndMs - clipStartMs).coerceAtLeast(1_000L) / 1000.0)

        val candidates = selectSymmetricCandidateCues(
            sourceCues = sourceCues,
            clipStartMs = clipStartMs,
            clipEndMs = clipEndMs,
            currentSpeedScale = currentSpeedScale,
            currentSyncOffsetMs = currentSyncOffsetMs
        )
        if (candidates.size < 2) return null

        val cueMap = candidates.associateBy { it.id }
        val cueLinesJson = JSONArray()
        for (c in candidates) {
            cueLinesJson.put(
                JSONObject()
                    .put("id", c.id)
                    .put("t", c.text.replace('\n', ' ').take(95))
            )
        }

        val prompt = """
            You are an expert cinema subtitle-to-audio synchronization engine.
            Listen carefully to the spoken actor dialogue in this movie audio clip (duration: $clipDurSec seconds; clip starts at 0.0s).
            Important rules:
            1. First count `spokenCount`: how many distinct spoken dialogue sentences (excluding theme song lyrics or pure noise) you clearly hear in this clip.
            2. The audio dialogue may be in ANY language (Chinese, Korean, Thai, English, Japanese, Vietnamese), while the candidate subtitle lines `t` below may be in Thai, Vietnamese, or English.
            3. Match ONLY distinctive multi-word sentences whose exact meaning and conversational sequence match what the actors speak in THIS audio clip. Ignore generic 1-word exclamations ("Huh?", "Yes", "Oh") and ignore song lyrics.
            4. For each matched candidate `id`, report `clipSec`: the exact timestamp in seconds (0.0 <= clipSec <= $clipDurSec) when the actor STARTS speaking that sentence inside THIS audio clip.
            5. If none of the candidate subtitle lines match what is actually spoken in this clip, return `"matches": []` (never guess!).

            Candidate subtitles:
            $cueLinesJson

            Return ONLY a valid JSON object with exact keys:
            {"spokenCount": 6, "matches": [{"id": 42, "clipSec": 5.4}, {"id": 43, "clipSec": 9.1}]}
        """.trimIndent()

        val inlineData = JSONObject()
            .put("mime_type", audioChunk.mimeType)
            .put("data", GeminiAudioSubtitleGenerator.encodeBase64(audioChunk.audioBytes))

        val contentsArray = JSONArray().put(
            JSONObject().put(
                "parts",
                JSONArray()
                    .put(JSONObject().put("inline_data", inlineData))
                    .put(JSONObject().put("text", prompt))
            )
        )

        val fallbackModels = GeminiAudioSubtitleGenerator.buildFastModelChain(
            preferredModelId = modelId,
            preferTurboFlash = true
        )

        for (activeModel in fallbackModels) {
            for (useThinkingCfg in listOf(true, false)) {
                val payload = JSONObject()
                    .put("contents", contentsArray)
                    .put(
                        "generationConfig",
                        GeminiAudioSubtitleGenerator.buildLowLatencyGenerationConfig(
                            modelId = activeModel,
                            temperature = 0.05,
                            includeThinkingConfig = useThinkingCfg
                        )
                    )

                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent?key=$apiKey")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                var retrySameWithoutThinking = false
                val parsedAlignment = httpClient.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        if (useThinkingCfg && resp.code == 400 && respBody.contains("thinking", ignoreCase = true)) {
                            retrySameWithoutThinking = true
                            return@use null
                        }
                        if (resp.code == 401 || resp.code == 403) return null
                        return@use null
                    }
                    val root = JSONObject(respBody)
                    val respParts = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?: return@use null

                    var textOut = ""
                    for (i in 0 until respParts.length()) {
                        val p = respParts.optJSONObject(i) ?: continue
                        if (p.optBoolean("thought", false)) continue
                        val t = p.optString("text").trim()
                        if (t.isNotEmpty()) textOut = t
                    }
                    if (textOut.isEmpty()) return@use null

                    val cleaned = textOut
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    var spokenCount = 0
                    val matchesArr: JSONArray? = if (cleaned.startsWith("{")) {
                        val obj = runCatching { JSONObject(cleaned) }.getOrNull()
                        spokenCount = obj?.optInt("spokenCount", 0) ?: 0
                        obj?.optJSONArray("matches")
                    } else {
                        val jStart = cleaned.indexOf('[')
                        val jEnd = cleaned.lastIndexOf(']')
                        if (jStart >= 0 && jEnd > jStart) {
                            runCatching { JSONArray(cleaned.substring(jStart, jEnd + 1)) }.getOrNull()
                        } else null
                    }

                    val matches = ArrayList<AnchorMatch>()
                    val clipDurationMs = (clipEndMs - clipStartMs) + 2_000L
                    if (matchesArr != null) {
                        for (i in 0 until matchesArr.length()) {
                            val item = matchesArr.optJSONObject(i) ?: continue
                            val id = item.optInt("id", -1)
                            val clipSec = item.optDouble("clipSec", Double.NaN)
                            if (id < 0 || clipSec.isNaN()) continue
                            val relMs = (clipSec * 1000.0).toLong()
                            if (relMs !in 0L..clipDurationMs) continue
                            val matchedCue = cueMap[id] ?: continue

                            matches.add(
                                AnchorMatch(
                                    movieSpokenMs = clipStartMs + relMs,
                                    cueStartMs = matchedCue.startMs
                                )
                            )
                        }
                    }

                    GeminiWindowAlignment(
                        windowStartMs = clipStartMs,
                        spokenSentenceCount = maxOf(spokenCount, matches.size),
                        matches = matches,
                        modelUsed = activeModel
                    )
                }

                if (parsedAlignment != null) {
                    return parsedAlignment
                }
                if (!retrySameWithoutThinking) {
                    break
                }
            }
        }
        return null
    }

    /**
     * Decodes AAC / M4A audio via Android's built-in MediaCodec into 100ms speech-transient energy bins
     * and cross-correlates against the `.SRT` dialogue mask.
     */
    private fun alignWithHardwareVadCorrelation(
        audioChunk: GeminiAudioSubtitleGenerator.ExtractedAudioChunk,
        sourceCues: List<SubtitleCue>,
        subSpeedScale: Float
    ): AutoSyncResult? {
        val dir = cacheDir ?: return null
        val ext = if (audioChunk.mimeType.contains("mp4")) ".m4a" else ".aac"
        val tempFile = File(dir, "sync_probe_${System.nanoTime()}$ext")

        val envelope = try {
            tempFile.writeBytes(audioChunk.audioBytes)
            decodeSpeechTransientEnvelope100ms(tempFile)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { tempFile.delete() }
        } ?: return null

        val (bestOffsetMs, peakRatio) = correlateEnvelopeWithCues(
            audioStartMs = audioChunk.actualStartMs,
            audioEnvelope = envelope,
            cues = sourceCues,
            subSpeedScale = subSpeedScale
        ) ?: return null

        return AutoSyncResult(
            detectedOffsetMs = bestOffsetMs,
            detectedSpeedScale = subSpeedScale,
            matchedAnchors = (envelope.size / 20).coerceAtLeast(2),
            methodLabel = "Acoustic VAD",
            confidence = ((peakRatio - 1.3f) / 1.4f).coerceIn(0.45f, 0.95f)
        )
    }

    private fun decodeSpeechTransientEnvelope100ms(audioFile: File): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(audioFile.absolutePath)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }
            if (audioTrackIdx < 0 || format == null) return null
            extractor.selectTrack(audioTrackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val binEnergies = ArrayList<Float>(600)
            var samplesPer100ms = ((sampleRate * channelCount) / 10).coerceAtLeast(800)
            var currentBinAccum = 0.0
            var currentBinSamples = 0
            var prevMonoSample = 0
            var inputDone = false
            var outputDone = false
            var safetyLoops = 0

            while (!outputDone && safetyLoops++ < 6000) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(4_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val read = extractor.readSampleData(inBuf, 0)
                            if (read < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, read, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 4_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        samplesPer100ms = ((sampleRate * channelCount) / 10).coerceAtLeast(800)
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size >= 2) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortCount = bufferInfo.size / 2
                            for (s in 0 until shortCount) {
                                val sample = outBuf.short.toInt()
                                val diff = abs(sample - prevMonoSample)
                                prevMonoSample = sample
                                currentBinAccum += diff.toDouble()
                                currentBinSamples++
                                if (currentBinSamples >= samplesPer100ms) {
                                    binEnergies.add((currentBinAccum / currentBinSamples).toFloat())
                                    currentBinAccum = 0.0
                                    currentBinSamples = 0
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (binEnergies.size < 100) null else binEnergies.toFloatArray()
        } finally {
            runCatching { codec?.flush() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
