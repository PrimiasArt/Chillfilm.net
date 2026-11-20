package com.phyll.filmhub.subtitle

import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Multimodal Audio-to-Thai-Subtitle Rolling Engine powered by user-selected Google Gemini models.
 *
 * When a movie or YouTube video has NO external .SRT / TimedText subtitles on any major platform:
 * 1. Extracts lightweight pure audio chunks (~1 MB per 2 minutes) directly from HLS (.m3u8 -> MPEG-TS
 *    -> pure ADTS .aac) or YouTube DASH (.m4a fMP4 ftyp+moov+sidx subsegments) with zero FFmpeg bloat.
 * 2. Sends the audio chunk to the user's chosen Gemini model (e.g. gemini-3-flash-preview,
 *    gemini-3.1-pro-preview, gemini-2.5-pro, etc.) to transcribe spoken dialogue (any language)
 *    and translate directly into time-aligned Cinema Thai subtitles.
 * 3. Pre-loads the opening ~2 minutes before playback starts, then continuously generates
 *    upcoming chunks ahead of the viewer's current playback position (jumping immediately on seek).
 */
class GeminiAudioSubtitleGenerator(
    private val cacheDir: File? = null
) {
    private val streamClient = OkHttpClient.Builder()
        .addInterceptor(HlsPlaylistSanitizer.createOkHttpInterceptor())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val geminiClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val aesKeyCache = ConcurrentHashMap<String, ByteArray>()
    private val timelineCache = ConcurrentHashMap<String, StreamAudioTimeline>()
    private val audioChunkCache = ConcurrentHashMap<String, ExtractedAudioChunk>()

    init {
        cacheDir?.mkdirs()
    }

    sealed class StreamAudioTimeline {
        abstract val totalDurationMs: Long

        data class HlsTimeline(
            val segments: List<HlsSegment>,
            val headers: Map<String, String>,
            override val totalDurationMs: Long
        ) : StreamAudioTimeline()

        data class DashM4aTimeline(
            val audioUrl: String,
            val initHeaderBytes: ByteArray,
            val subsegments: List<DashSubsegment>,
            val headers: Map<String, String>,
            override val totalDurationMs: Long
        ) : StreamAudioTimeline()

        data class DirectRangeFallback(
            val mediaUrl: String,
            val mimeType: String,
            val headers: Map<String, String>,
            override val totalDurationMs: Long = 120_000L
        ) : StreamAudioTimeline()
    }

    data class HlsSegment(
        val sequenceNumber: Long,
        val url: String,
        val startMs: Long,
        val durationMs: Long,
        val aesKeyUrl: String? = null,
        val aesIvHex: String? = null
    )

    data class DashSubsegment(
        val startMs: Long,
        val durationMs: Long,
        val byteStart: Long,
        val byteEndInclusive: Long
    )

    data class ExtractedAudioChunk(
        val actualStartMs: Long,
        val actualEndMs: Long,
        val mimeType: String,
        val audioBytes: ByteArray
    )

    data class ChunkSubtitleResult(
        val actualStartMs: Long,
        val actualEndMs: Long,
        val cues: List<SubtitleCue>
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

        private val EXTINF_REGEX = Regex("""#EXTINF:([\d.]+)""")
        private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""")
        private val KEY_METHOD_REGEX = Regex("""METHOD=([A-Z0-9-]+)""")
        private val KEY_URI_REGEX = Regex("""URI="([^"]+)"""")
        private val KEY_IV_REGEX = Regex("""IV=0x([0-9A-Fa-f]+)""")

        /**
         * Builds a model-specific ultra-low-latency `generationConfig` JSON object:
         * - Gemini 3 Flash / Flash-Lite (`gemini-3-flash-preview`, `gemini-3.1-flash-lite-preview`):
         *   sets `thinkingConfig.thinkingLevel = "MINIMAL"` (avoids the slow default `HIGH` reasoning pass!).
         * - Gemini 3 Pro (`gemini-3.1-pro-preview`):
         *   sets `thinkingConfig.thinkingLevel = "LOW"` (lowest tier accepted by Gemini 3 Pro).
         * - Gemini 2.5 Flash / Flash-Lite (`gemini-2.5-flash`):
         *   sets `thinkingConfig.thinkingBudget = 0` (zero thinking tokens = instant output).
         * - Gemini 2.5 Pro (`gemini-2.5-pro`):
         *   sets `thinkingConfig.thinkingBudget = 128` (minimum bound accepted by Gemini 2.5 Pro).
         * - Gemini 2.0 / non-thinking models: omits `thinkingConfig` cleanly.
         */
        fun buildLowLatencyGenerationConfig(
            modelId: String,
            temperature: Double = 0.15,
            includeThinkingConfig: Boolean = true
        ): JSONObject {
            val cfg = JSONObject()
                .put("temperature", temperature)
                .put("responseMimeType", "application/json")
            if (!includeThinkingConfig) return cfg

            val clean = modelId.trim().removePrefix("models/").lowercase()
            when {
                clean.startsWith("gemini-3") && clean.contains("flash") -> {
                    cfg.put("thinkingConfig", JSONObject().put("thinkingLevel", "MINIMAL"))
                }
                clean.startsWith("gemini-3") -> {
                    cfg.put("thinkingConfig", JSONObject().put("thinkingLevel", "LOW"))
                }
                clean.startsWith("gemini-2.5") && clean.contains("flash") -> {
                    cfg.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                }
                clean.startsWith("gemini-2.5") -> {
                    cfg.put("thinkingConfig", JSONObject().put("thinkingBudget", 128))
                }
            }
            return cfg
        }

        /**
         * Builds an ordered model fallback chain. When `preferTurboFlash` is true (used for the
         * blocking 30-second opening micro-sprint so playback starts in ~3 seconds), routes through
         * a fast Flash model first if the user selected a heavy Pro model.
         */
        fun buildFastModelChain(
            preferredModelId: String,
            preferTurboFlash: Boolean = false
        ): List<String> {
            val preferred = preferredModelId.trim().removePrefix("models/").ifBlank { "gemini-3-flash-preview" }
            return if (preferTurboFlash && preferred.contains("pro", ignoreCase = true)) {
                linkedSetOf(
                    "gemini-3-flash-preview",
                    "gemini-2.5-flash",
                    preferred,
                    "gemini-2.0-flash"
                ).toList()
            } else {
                linkedSetOf(
                    preferred,
                    "gemini-2.5-flash",
                    "gemini-2.0-flash"
                ).toList()
            }
        }

        /**
         * Pure-Kotlin RFC 4648 Base64 encoder (works identically on Android API 24..35 and JVM unit tests).
         */
        fun encodeBase64(data: ByteArray): String {
            if (data.isEmpty()) return ""
            val outLen = ((data.size + 2) / 3) * 4
            val sb = StringBuilder(outLen)
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0

                sb.append(BASE64_ALPHABET[b0 ushr 2])
                sb.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                if (i + 1 < data.size) {
                    sb.append(BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
                } else {
                    sb.append('=')
                }
                if (i + 2 < data.size) {
                    sb.append(BASE64_ALPHABET[b2 and 0x3F])
                } else {
                    sb.append('=')
                }
                i += 3
            }
            return sb.toString()
        }

        /**
         * Demuxes raw MPEG-2 Transport Stream (.ts) bytes into pure elementary ADTS AAC / MPEG audio bytes.
         * Automatically skips any fake PNG/JPEG/HTML wrapper bytes prepended by VOD CDNs.
         */
        fun extractElementaryAudioFromMpegTs(tsBytes: ByteArray, outStream: ByteArrayOutputStream): String {
            if (tsBytes.size < 564) return "audio/aac"

            // Locate true 188-byte MPEG-TS packet alignment (3 consecutive 0x47 sync bytes)
            // Scans up to 128 KB to skip any fake PNG/JPEG headers prepended by Asian VOD CDNs
            var startOffset = -1
            val maxScan = minOf(131_072, tsBytes.size - 376)
            for (offset in 0..maxScan) {
                if (tsBytes[offset] == 0x47.toByte() &&
                    tsBytes[offset + 188] == 0x47.toByte() &&
                    tsBytes[offset + 376] == 0x47.toByte()
                ) {
                    startOffset = offset
                    break
                }
            }
            if (startOffset < 0) return "audio/aac"

            var audioPid = -1
            var p = startOffset
            val beforeSize = outStream.size()

            while (p <= tsBytes.size - 188) {
                if (tsBytes[p] != 0x47.toByte()) {
                    // Re-sync if stream had a glitch byte
                    p++
                    continue
                }
                val b1 = tsBytes[p + 1].toInt() and 0xFF
                val tei = (b1 and 0x80) != 0
                if (!tei) {
                    val pusi = (b1 and 0x40) != 0
                    val pktPid = ((b1 and 0x1F) shl 8) or (tsBytes[p + 2].toInt() and 0xFF)
                    val afc = (tsBytes[p + 3].toInt() ushr 4) and 0x03

                    if (afc == 1 || afc == 3) {
                        var payloadOff = p + 4
                        if (afc == 3) {
                            val adaptLen = tsBytes[p + 4].toInt() and 0xFF
                            payloadOff = p + 5 + adaptLen
                        }
                        if (payloadOff < p + 188) {
                            if (pusi &&
                                payloadOff + 9 <= p + 188 &&
                                tsBytes[payloadOff] == 0.toByte() &&
                                tsBytes[payloadOff + 1] == 0.toByte() &&
                                tsBytes[payloadOff + 2] == 1.toByte()
                            ) {
                                val streamId = tsBytes[payloadOff + 3].toInt() and 0xFF
                                // ISO/IEC 13818-1 Audio PES stream_id range: 0xC0..0xDF
                                if (streamId in 0xC0..0xDF) {
                                    if (audioPid == -1) {
                                        audioPid = pktPid
                                    }
                                    if (pktPid == audioPid) {
                                        val pesHeaderDataLen = tsBytes[payloadOff + 8].toInt() and 0xFF
                                        val esStart = payloadOff + 9 + pesHeaderDataLen
                                        if (esStart < p + 188) {
                                            outStream.write(tsBytes, esStart, (p + 188) - esStart)
                                        }
                                    }
                                }
                            } else if (audioPid != -1 && pktPid == audioPid) {
                                outStream.write(tsBytes, payloadOff, (p + 188) - payloadOff)
                            }
                        }
                    }
                }
                p += 188
            }

            // Detect whether extracted elementary stream is ADTS AAC (0xFF 0xF1 / 0xFF 0xF9) or MP3
            if (outStream.size() > beforeSize + 2) {
                val probe = outStream.toByteArray()
                if (probe.size >= 2 &&
                    probe[0] == 0xFF.toByte() &&
                    (probe[1].toInt() and 0xF6) == 0xF0
                ) {
                    return "audio/aac"
                }
            }
            return "audio/aac"
        }
    }

    /**
     * Queries Google's Generative Language API for all live Gemini models available to this API key.
     */
    suspend fun fetchAvailableGeminiModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100&key=$cleanKey")
                .get()
                .build()

            geminiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models")
                    ?: return@withContext emptyList()

                val models = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val rawName = m.optString("name").removePrefix("models/").trim()
                    if (!rawName.startsWith("gemini-", ignoreCase = true)) continue
                    val lower = rawName.lowercase()
                    if (lower.contains("embedding") ||
                        lower.contains("imagen") ||
                        lower.contains("veo") ||
                        lower.contains("tts") ||
                        lower.contains("aqa")
                    ) {
                        continue
                    }
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    if (methods.contains("generateContent")) {
                        models.add(rawName)
                    }
                }

                models.sortedWith(
                    compareByDescending<String> { modelVersionRank(it) }
                        .thenBy { if (it.contains("flash")) 0 else 1 }
                        .thenBy { it }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun modelVersionRank(modelId: String): Int {
        val lower = modelId.lowercase()
        return when {
            lower.startsWith("gemini-3.5") -> 350
            lower.startsWith("gemini-3.1") -> 310
            lower.startsWith("gemini-3") -> 300
            lower.startsWith("gemini-2.5") -> 250
            lower.startsWith("gemini-2.0") -> 200
            else -> 100
        }
    }

    /**
     * Inspects the Episode's stream (HLS .m3u8, YouTube DASH M4A, or direct MP4) and builds a
     * millisecond-indexed timeline so any 2-minute window can be extracted on demand.
     */
    suspend fun inspectStreamTimeline(episode: Episode): StreamAudioTimeline? = withContext(Dispatchers.IO) {
        val cacheKey = "${episode.streamUrl.trim()}|${episode.audioStreamUrl.trim()}"
        timelineCache[cacheKey]?.let { return@withContext it }

        val headers = episode.headers

        // 1. Prefer YouTube dedicated M4A audio stream if available (pure audio, ultra fast!)
        val resolved: StreamAudioTimeline? = if (episode.audioStreamUrl.isNotBlank()) {
            parseDashM4aSidxTimeline(episode.audioStreamUrl, headers)
                ?: StreamAudioTimeline.DirectRangeFallback(
                    mediaUrl = episode.audioStreamUrl,
                    mimeType = "audio/mp4",
                    headers = headers
                )
        } else {
            // 2. Check HLS .m3u8 VOD playlist
            val url = episode.streamUrl.trim()
            val hls = if (url.contains(".m3u8", ignoreCase = true) || looksLikeM3u8(url, headers)) {
                parseHlsMediaTimeline(url, headers)
            } else {
                null
            }
            hls ?: if (url.startsWith("http", ignoreCase = true)) {
                // 3. Direct MP4 / progressive fallback
                StreamAudioTimeline.DirectRangeFallback(
                    mediaUrl = url,
                    mimeType = "audio/mp4",
                    headers = headers
                )
            } else {
                null
            }
        }

        if (resolved != null) {
            if (timelineCache.size > 24) timelineCache.clear()
            timelineCache[cacheKey] = resolved
        }
        resolved
    }

    /**
     * Extracts audio for `windowStartMs .. windowStartMs + windowDurationMs`, calls the user's
     * chosen Gemini model with low-latency thinking configuration, and returns time-shifted SubtitleCues.
     */
    suspend fun generateThaiCuesForWindow(
        cacheNamespace: String,
        timeline: StreamAudioTimeline,
        windowStartMs: Long,
        windowDurationMs: Long,
        apiKey: String,
        modelId: String,
        preferTurboFlash: Boolean = false
    ): Result<ChunkSubtitleResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            require(cleanKey.isNotEmpty()) { "Chưa có Gemini API Key trong RAM" }
            val cleanModel = modelId.trim().removePrefix("models/").ifBlank { "gemini-3-flash-preview" }

            // Check subtitle cue cache on disk (contains ONLY Thai subtitle cues, NEVER any API key!)
            val bucketKey = "${cacheNamespace}_${cleanModel}_${windowStartMs / 1000L}_${windowDurationMs / 1000L}"
            readCachedChunk(bucketKey)?.let { return@runCatching it }

            val audioChunk = extractAudioChunk(timeline, windowStartMs, windowDurationMs)
                ?: error("Không trích xuất được âm thanh từ luồng phim ở mốc ${windowStartMs / 1000}s")

            if (audioChunk.audioBytes.size < 1024) {
                return@runCatching ChunkSubtitleResult(
                    actualStartMs = audioChunk.actualStartMs,
                    actualEndMs = audioChunk.actualEndMs,
                    cues = emptyList()
                )
            }

            val cues = callGeminiMultimodalForThaiSubtitles(
                audioBytes = audioChunk.audioBytes,
                mimeType = audioChunk.mimeType,
                chunkStartOffsetMs = audioChunk.actualStartMs,
                chunkDurationMs = (audioChunk.actualEndMs - audioChunk.actualStartMs).coerceAtLeast(5_000L),
                apiKey = cleanKey,
                modelId = cleanModel,
                preferTurboFlash = preferTurboFlash
            )

            val result = ChunkSubtitleResult(
                actualStartMs = audioChunk.actualStartMs,
                actualEndMs = audioChunk.actualEndMs,
                cues = cues
            )
            writeCachedChunk(bucketKey, result)
            result
        }
    }

    /**
     * Speculatively pre-downloads and demuxes an audio window into `audioChunkCache` so subsequent
     * Gemini transcription or Auto-Sync runs with 0ms network download wait!
     */
    suspend fun prefetchAudioWindow(
        timeline: StreamAudioTimeline,
        windowStartMs: Long,
        windowDurationMs: Long
    ) {
        withContext(Dispatchers.IO) {
            runCatching { extractAudioChunk(timeline, windowStartMs, windowDurationMs) }
        }
    }

    suspend fun extractAudioWindow(
        timeline: StreamAudioTimeline,
        windowStartMs: Long,
        windowDurationMs: Long
    ): ExtractedAudioChunk? = withContext(Dispatchers.IO) {
        extractAudioChunk(timeline, windowStartMs, windowDurationMs)
    }

    private fun buildAudioChunkCacheKey(
        timeline: StreamAudioTimeline,
        windowStartMs: Long,
        windowDurationMs: Long
    ): String {
        val streamId = when (timeline) {
            is StreamAudioTimeline.HlsTimeline -> timeline.segments.firstOrNull()?.url.orEmpty()
            is StreamAudioTimeline.DashM4aTimeline -> timeline.audioUrl
            is StreamAudioTimeline.DirectRangeFallback -> timeline.mediaUrl
        }
        return "${streamId.hashCode()}_${windowStartMs / 500L}_${windowDurationMs / 500L}"
    }

    private suspend fun extractAudioChunk(
        timeline: StreamAudioTimeline,
        windowStartMs: Long,
        windowDurationMs: Long
    ): ExtractedAudioChunk? = coroutineScope {
        val cacheKey = buildAudioChunkCacheKey(timeline, windowStartMs, windowDurationMs)
        audioChunkCache[cacheKey]?.let { return@coroutineScope it }

        val windowEndMs = windowStartMs + windowDurationMs

        val extracted: ExtractedAudioChunk? = when (timeline) {
            is StreamAudioTimeline.HlsTimeline -> {
                val matching = timeline.segments.filter { seg ->
                    val segEnd = seg.startMs + seg.durationMs
                    segEnd > windowStartMs && seg.startMs < windowEndMs
                }.take(36)

                if (matching.isEmpty()) return@coroutineScope null
                val actualStart = matching.first().startMs
                val actualEnd = matching.last().let { it.startMs + it.durationMs }

                // Download 8 TS segments concurrently AND demux each segment immediately into ~22 KB pure AAC
                // inside the worker so 1 MB .ts video buffers are discarded immediately (zero TV Box GC spikes!)
                val demuxedPieces = ArrayList<Pair<String, ByteArray>>(matching.size)
                for (batch in matching.chunked(8)) {
                    val downloaded = batch.map { seg ->
                        async(Dispatchers.IO) {
                            val tsBytes = downloadHlsSegmentDecrypted(seg, timeline.headers)
                                ?: return@async null
                            val segAacOut = ByteArrayOutputStream(32_768)
                            val mime = extractElementaryAudioFromMpegTs(tsBytes, segAacOut)
                            val bytes = segAacOut.toByteArray()
                            if (bytes.isNotEmpty()) Pair(mime, bytes) else null
                        }
                    }.awaitAll()
                    downloaded.filterNotNull().forEach { demuxedPieces.add(it) }
                }

                if (demuxedPieces.isEmpty()) return@coroutineScope null
                val totalAacBytes = demuxedPieces.sumOf { it.second.size }
                if (totalAacBytes <= 0) return@coroutineScope null

                val aacOut = ByteArrayOutputStream(totalAacBytes)
                var detectedMime = "audio/aac"
                for ((mime, piece) in demuxedPieces) {
                    detectedMime = mime
                    aacOut.write(piece)
                }

                ExtractedAudioChunk(
                    actualStartMs = actualStart,
                    actualEndMs = actualEnd,
                    mimeType = detectedMime,
                    audioBytes = aacOut.toByteArray()
                )
            }

            is StreamAudioTimeline.DashM4aTimeline -> {
                val matching = timeline.subsegments.filter { sub ->
                    val subEnd = sub.startMs + sub.durationMs
                    subEnd > windowStartMs && sub.startMs < windowEndMs
                }
                if (matching.isEmpty()) return@coroutineScope null

                val actualStart = matching.first().startMs
                val actualEnd = matching.last().let { it.startMs + it.durationMs }
                val firstByte = matching.first().byteStart
                val lastByte = minOf(
                    matching.last().byteEndInclusive,
                    firstByte + 2_500_000L
                )

                val fragmentBytes = httpGetRange(
                    url = timeline.audioUrl,
                    headers = timeline.headers,
                    rangeStart = firstByte,
                    rangeEndInclusive = lastByte
                ) ?: return@coroutineScope null

                val combined = ByteArray(timeline.initHeaderBytes.size + fragmentBytes.size)
                System.arraycopy(timeline.initHeaderBytes, 0, combined, 0, timeline.initHeaderBytes.size)
                System.arraycopy(fragmentBytes, 0, combined, timeline.initHeaderBytes.size, fragmentBytes.size)

                ExtractedAudioChunk(
                    actualStartMs = actualStart,
                    actualEndMs = actualEnd,
                    mimeType = "audio/mp4",
                    audioBytes = combined
                )
            }

            is StreamAudioTimeline.DirectRangeFallback -> {
                if (windowStartMs > 5_000L) return@coroutineScope null
                // Fetch opening ~1.4 MB for fast initial progressive MP4/M4A clip
                val bytes = httpGetRange(
                    url = timeline.mediaUrl,
                    headers = timeline.headers,
                    rangeStart = 0L,
                    rangeEndInclusive = 1_400_000L
                ) ?: return@coroutineScope null
                ExtractedAudioChunk(
                    actualStartMs = 0L,
                    actualEndMs = 60_000L,
                    mimeType = timeline.mimeType,
                    audioBytes = bytes
                )
            }
        }

        if (extracted != null && extracted.audioBytes.isNotEmpty()) {
            if (audioChunkCache.size >= 8) {
                audioChunkCache.clear()
            }
            audioChunkCache[cacheKey] = extracted
        }
        extracted
    }

    private fun callGeminiMultimodalForThaiSubtitles(
        audioBytes: ByteArray,
        mimeType: String,
        chunkStartOffsetMs: Long,
        chunkDurationMs: Long,
        apiKey: String,
        modelId: String,
        preferTurboFlash: Boolean = false
    ): List<SubtitleCue> {
        val durationSec = "%.1f".format(chunkDurationMs / 1000.0)
        val prompt = """
            Thai cinema subtitle creator: listen to this $durationSec-second movie audio clip (starts at 0.0s).
            Transcribe ALL spoken dialogue (any language) directly into natural, concise Cinema Thai (ภาษาไทยธรรมชาติ กระชับ อ่านง่าย).
            Rules:
            - "s" and "e" = float seconds relative to 0.0 of this clip (0.0 <= s < e <= $durationSec), 1.2s-4.8s per cue.
            - Skip music/silence; if no speech, return [].
            Return ONLY JSON array: [{"s":1.2,"e":3.6,"t":"บทพูดภาษาไทย"}]
        """.trimIndent()

        val inlineData = JSONObject()
            .put("mime_type", mimeType)
            .put("data", encodeBase64(audioBytes))

        val contentsArray = JSONArray().put(
            JSONObject().put(
                "parts",
                JSONArray()
                    .put(JSONObject().put("inline_data", inlineData))
                    .put(JSONObject().put("text", prompt))
            )
        )

        val candidateModels = buildFastModelChain(
            preferredModelId = modelId,
            preferTurboFlash = preferTurboFlash
        )

        var lastErrorMsg = "Không kết nối được Gemini API"
        for ((attemptIdx, activeModel) in candidateModels.withIndex()) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$activeModel:generateContent?key=$apiKey"

            // First try with low-latency thinkingConfig; if a custom/future model rejects thinkingConfig (400),
            // immediately retry the same model once without thinkingConfig!
            for (useThinkingCfg in listOf(true, false)) {
                val payload = JSONObject()
                    .put("contents", contentsArray)
                    .put(
                        "generationConfig",
                        buildLowLatencyGenerationConfig(
                            modelId = activeModel,
                            temperature = 0.15,
                            includeThinkingConfig = useThinkingCfg
                        )
                    )

                val req = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                var retrySameModelWithoutThinking = false
                val attemptOutcome = geminiClient.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errDetail = runCatching {
                            JSONObject(respBody).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().take(140)
                        lastErrorMsg = "Gemini ($activeModel) HTTP ${resp.code}: $errDetail"

                        if (useThinkingCfg && resp.code == 400 && errDetail.contains("thinking", ignoreCase = true)) {
                            retrySameModelWithoutThinking = true
                            return@use null
                        }

                        // If invalid API key (401/403), fail immediately; if 404/400/429/503, try fallback model!
                        val isRetryableModelErr = resp.code in listOf(404, 429, 500, 502, 503) ||
                            (resp.code == 400 && errDetail.contains("model", ignoreCase = true))
                        if (!isRetryableModelErr || attemptIdx == candidateModels.lastIndex) {
                            error(lastErrorMsg)
                        }
                        null
                    } else {
                        val root = JSONObject(respBody)
                        val textParts = root.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")

                        if (textParts == null) {
                            emptyList()
                        } else {
                            var rawOutput = ""
                            for (i in 0 until textParts.length()) {
                                val p = textParts.optJSONObject(i) ?: continue
                                if (p.optBoolean("thought", false)) continue
                                val t = p.optString("text").trim()
                                if (t.isNotEmpty()) rawOutput = t
                            }
                            if (rawOutput.isEmpty()) {
                                emptyList()
                            } else {
                                val cleaned = rawOutput
                                    .removePrefix("```json")
                                    .removePrefix("```srt")
                                    .removePrefix("```")
                                    .removeSuffix("```")
                                    .trim()
                                parseGeminiCueOutput(cleaned, chunkStartOffsetMs, chunkDurationMs)
                            }
                        }
                    }
                }

                if (attemptOutcome != null) {
                    return attemptOutcome
                }
                if (!retrySameModelWithoutThinking) {
                    break
                }
            }
        }
        error(lastErrorMsg)
    }

    private fun parseGeminiCueOutput(
        cleaned: String,
        chunkStartOffsetMs: Long,
        chunkDurationMs: Long
    ): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        val maxRelMs = chunkDurationMs + 5_000L

        // 1. Primary JSON array format: [{"s": 1.2, "e": 3.8, "t": "..."}]
        val jsonStart = cleaned.indexOf('[')
        val jsonEnd = cleaned.lastIndexOf(']')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonSlice = cleaned.substring(jsonStart, jsonEnd + 1)
            val parsedJson = runCatching { JSONArray(jsonSlice) }.getOrNull()
            if (parsedJson != null) {
                for (i in 0 until parsedJson.length()) {
                    val obj = parsedJson.optJSONObject(i) ?: continue
                    val text = obj.optString("t").ifBlank { obj.optString("text") }.trim()
                    if (text.isEmpty()) continue
                    val sSec = parseFlexibleSeconds(obj.opt("s") ?: obj.opt("start")) ?: continue
                    val eSec = parseFlexibleSeconds(obj.opt("e") ?: obj.opt("end")) ?: (sSec + 2.5)
                    val relStartMs = (sSec * 1000.0).toLong().coerceIn(0L, maxRelMs)
                    val relEndMs = (eSec * 1000.0).toLong().coerceIn(relStartMs + 400L, maxRelMs + 2_000L)

                    cues.add(
                        SubtitleCue(
                            id = i + 1,
                            startMs = chunkStartOffsetMs + relStartMs,
                            endMs = chunkStartOffsetMs + relEndMs,
                            text = text
                        )
                    )
                }
                if (cues.isNotEmpty()) {
                    return normalizeGeneratedCues(cues)
                }
            }
        }

        // 2. Fallback if model emitted standard SRT/VTT text
        val srtFallback = SubtitleParser.parse(cleaned)
        if (srtFallback.isNotEmpty()) {
            return normalizeGeneratedCues(
                srtFallback.map { c ->
                    c.copy(
                        startMs = chunkStartOffsetMs + c.startMs,
                        endMs = chunkStartOffsetMs + c.endMs
                    )
                }
            )
        }
        return emptyList()
    }

    private fun parseFlexibleSeconds(raw: Any?): Double? {
        if (raw == null) return null
        if (raw is Number) return raw.toDouble()
        val s = raw.toString().trim().replace(',', '.')
        s.toDoubleOrNull()?.let { return it }
        // Handle "MM:SS.mmm" or "HH:MM:SS.mmm" if model formatted timestamp as string
        val parts = s.split(':')
        return when (parts.size) {
            2 -> {
                val m = parts[0].toDoubleOrNull() ?: return null
                val sec = parts[1].toDoubleOrNull() ?: return null
                m * 60.0 + sec
            }
            3 -> {
                val h = parts[0].toDoubleOrNull() ?: return null
                val m = parts[1].toDoubleOrNull() ?: return null
                val sec = parts[2].toDoubleOrNull() ?: return null
                h * 3600.0 + m * 60.0 + sec
            }
            else -> null
        }
    }

    private fun normalizeGeneratedCues(cues: List<SubtitleCue>): List<SubtitleCue> {
        val sorted = cues.sortedBy { it.startMs }
        val result = ArrayList<SubtitleCue>(sorted.size)
        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextStart = sorted.getOrNull(i + 1)?.startMs
            val clampedEnd = if (nextStart != null && nextStart > cur.startMs + 250L && cur.endMs > nextStart) {
                nextStart - 20L
            } else {
                cur.endMs
            }
            result.add(cur.copy(id = i + 1, endMs = clampedEnd))
        }
        return result
    }

    private fun looksLikeM3u8(url: String, headers: Map<String, String>): Boolean {
        val sample = httpGetString(url, headers, maxChars = 512) ?: return false
        return sample.trimStart().startsWith("#EXTM3U")
    }

    private fun parseHlsMediaTimeline(
        entryUrl: String,
        headers: Map<String, String>
    ): StreamAudioTimeline.HlsTimeline? {
        val firstBody = httpGetString(entryUrl, headers) ?: return null
        if (!firstBody.contains("#EXTM3U")) return null

        var mediaPlaylistUrl = entryUrl
        var mediaBody = firstBody

        // If Master playlist, pick the LOWEST bandwidth variant (downloads fastest while keeping full audio!)
        if (firstBody.contains("#EXT-X-STREAM-INF")) {
            val lines = firstBody.lines()
            var bestSubUrl: String? = null
            var lowestBw = Long.MAX_VALUE
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = BANDWIDTH_REGEX.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
                        ?: 1_000_000L
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) {
                        j++
                    }
                    if (j < lines.size) {
                        val resolved = resolveRelativeUrl(entryUrl, lines[j].trim())
                        if (bw < lowestBw) {
                            lowestBw = bw
                            bestSubUrl = resolved
                        }
                    }
                    i = j
                }
                i++
            }
            val chosenUrl = bestSubUrl ?: return null
            mediaPlaylistUrl = chosenUrl
            mediaBody = httpGetString(chosenUrl, headers) ?: return null
        }

        // Guarantee injected mid-roll ad segments are stripped even if playlist URL didn't end in .m3u8
        mediaBody = HlsPlaylistSanitizer.sanitizeMediaPlaylist(mediaBody, mediaPlaylistUrl)

        val segments = ArrayList<HlsSegment>(512)
        var currentTimeMs = 0L
        var pendingDurationMs = 4_000L
        var mediaSeq = 0L
        var activeAesKeyUrl: String? = null
        var activeAesIvHex: String? = null

        for (rawLine in mediaBody.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSeq = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val method = KEY_METHOD_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    if (method == "AES-128") {
                        val keyUri = KEY_URI_REGEX.find(line)?.groupValues?.getOrNull(1)
                        activeAesKeyUrl = keyUri?.let { resolveRelativeUrl(mediaPlaylistUrl, it) }
                        activeAesIvHex = KEY_IV_REGEX.find(line)?.groupValues?.getOrNull(1)
                    } else if (method == "NONE") {
                        activeAesKeyUrl = null
                        activeAesIvHex = null
                    }
                }
                line.startsWith("#EXTINF:") -> {
                    val sec = EXTINF_REGEX.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 4.0
                    pendingDurationMs = (sec * 1000.0).toLong().coerceAtLeast(200L)
                }
                !line.startsWith("#") -> {
                    val segUrl = resolveRelativeUrl(mediaPlaylistUrl, line)
                    segments.add(
                        HlsSegment(
                            sequenceNumber = mediaSeq++,
                            url = segUrl,
                            startMs = currentTimeMs,
                            durationMs = pendingDurationMs,
                            aesKeyUrl = activeAesKeyUrl,
                            aesIvHex = activeAesIvHex
                        )
                    )
                    currentTimeMs += pendingDurationMs
                }
            }
        }

        if (segments.isEmpty()) return null
        return StreamAudioTimeline.HlsTimeline(
            segments = segments,
            headers = headers,
            totalDurationMs = currentTimeMs
        )
    }

    private fun downloadHlsSegmentDecrypted(
        segment: HlsSegment,
        headers: Map<String, String>
    ): ByteArray? {
        val raw = httpGetBytes(segment.url, headers) ?: return null
        val keyUrl = segment.aesKeyUrl ?: return raw

        return try {
            val keyBytes = aesKeyCache[keyUrl] ?: httpGetBytes(keyUrl, headers)?.also {
                if (it.size == 16) aesKeyCache[keyUrl] = it
            } ?: return raw
            if (keyBytes.size != 16) return raw

            val ivBytes = if (!segment.aesIvHex.isNullOrBlank()) {
                hexTo16Bytes(segment.aesIvHex)
            } else {
                ByteBuffer.allocate(16).putLong(0L).putLong(segment.sequenceNumber).array()
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            cipher.doFinal(raw)
        } catch (_: Exception) {
            raw
        }
    }

    private fun hexTo16Bytes(hex: String): ByteArray {
        val padded = hex.padStart(32, '0').takeLast(32)
        return ByteArray(16) { i ->
            padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Parses ISO-BMFF `ftyp` + `moov` + `sidx` boxes from the first 64 KB of a YouTube DASH M4A stream.
     */
    private fun parseDashM4aSidxTimeline(
        audioUrl: String,
        headers: Map<String, String>
    ): StreamAudioTimeline.DashM4aTimeline? {
        return try {
            val head = httpGetRange(audioUrl, headers, 0L, 65_535L) ?: return null
            if (head.size < 64) return null
            val buf = ByteBuffer.wrap(head)

            var pos = 0
            var sidxOffset = -1
            var sidxSize = 0

            while (pos + 8 <= head.size) {
                val boxSize = buf.getInt(pos).toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4) { k -> head[pos + 4 + k] }
                val boxType = String(typeBytes, Charsets.US_ASCII)
                if (boxSize < 8L) break
                if (boxType == "sidx") {
                    sidxOffset = pos
                    sidxSize = boxSize.toInt()
                    break
                }
                if (boxType == "moof" || boxType == "mdat") break
                pos += boxSize.toInt()
            }

            if (sidxOffset <= 0 || sidxOffset + sidxSize > head.size) return null
            val initHeader = head.copyOfRange(0, sidxOffset)

            // Parse ISO 14496-12 `sidx` box
            var cursor = sidxOffset + 8
            val version = head[cursor].toInt() and 0xFF
            cursor += 4 // version(1) + flags(3)
            cursor += 4 // reference_ID(4)
            val timescale = (buf.getInt(cursor).toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
            cursor += 4

            val firstOffset: Long
            if (version == 0) {
                cursor += 4 // earliest_presentation_time(4)
                firstOffset = buf.getInt(cursor).toLong() and 0xFFFFFFFFL
                cursor += 4
            } else {
                cursor += 8 // earliest_presentation_time(8)
                firstOffset = buf.getLong(cursor)
                cursor += 8
            }

            cursor += 2 // reserved(2)
            val refCount = buf.getShort(cursor).toInt() and 0xFFFF
            cursor += 2

            var byteCursor = sidxOffset.toLong() + sidxSize.toLong() + firstOffset
            var timeTicks = 0L
            val subsegments = ArrayList<DashSubsegment>(refCount)

            for (i in 0 until refCount) {
                if (cursor + 12 > head.size) break
                val word0 = buf.getInt(cursor)
                val refSize = (word0.toLong() and 0x7FFFFFFFL)
                val subDurTicks = buf.getInt(cursor + 4).toLong() and 0xFFFFFFFFL
                cursor += 12

                val startMs = (timeTicks * 1000L) / timescale
                val endMs = ((timeTicks + subDurTicks) * 1000L) / timescale
                val durMs = (endMs - startMs).coerceAtLeast(100L)

                if (refSize > 0L) {
                    subsegments.add(
                        DashSubsegment(
                            startMs = startMs,
                            durationMs = durMs,
                            byteStart = byteCursor,
                            byteEndInclusive = byteCursor + refSize - 1L
                        )
                    )
                    byteCursor += refSize
                    timeTicks += subDurTicks
                }
            }

            if (subsegments.isEmpty()) return null
            val totalMs = subsegments.last().let { it.startMs + it.durationMs }
            StreamAudioTimeline.DashM4aTimeline(
                audioUrl = audioUrl,
                initHeaderBytes = initHeader,
                subsegments = subsegments,
                headers = headers,
                totalDurationMs = totalMs
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("http://", true) || relative.startsWith("https://", true)) {
            return relative
        }
        return runCatching { URI(baseUrl).resolve(relative).toString() }.getOrElse { relative }
    }

    private fun httpGetString(
        url: String,
        headers: Map<String, String>,
        maxChars: Int = Int.MAX_VALUE
    ): String? {
        return try {
            val rb = Request.Builder().url(url)
            rb.header(
                "User-Agent",
                headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14) PhyllFilmHub/2.2"
            )
            headers.forEach { (k, v) -> rb.header(k, v) }
            streamClient.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val s = resp.body?.string().orEmpty()
                if (s.length > maxChars) s.substring(0, maxChars) else s
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetBytes(url: String, headers: Map<String, String>): ByteArray? {
        return try {
            val rb = Request.Builder().url(url)
            rb.header(
                "User-Agent",
                headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14) PhyllFilmHub/2.2"
            )
            headers.forEach { (k, v) -> rb.header(k, v) }
            streamClient.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetRange(
        url: String,
        headers: Map<String, String>,
        rangeStart: Long,
        rangeEndInclusive: Long
    ): ByteArray? {
        return try {
            val rb = Request.Builder().url(url)
            rb.header(
                "User-Agent",
                headers["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14) PhyllFilmHub/2.2"
            )
            headers.forEach { (k, v) -> rb.header(k, v) }
            rb.header("Range", "bytes=$rangeStart-$rangeEndInclusive")
            streamClient.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) null else resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readCachedChunk(bucketKey: String): ChunkSubtitleResult? {
        val dir = cacheDir ?: return null
        val safeName = bucketKey.replace(Regex("""[^A-Za-z0-9_.-]"""), "_") + ".json"
        val f = File(dir, safeName)
        if (!f.exists()) return null
        return runCatching {
            val obj = JSONObject(f.readText(Charsets.UTF_8))
            val actualStart = obj.optLong("s", 0L)
            val actualEnd = obj.optLong("e", 0L)
            val arr = obj.optJSONArray("cues") ?: JSONArray()
            val cues = ArrayList<SubtitleCue>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                cues.add(
                    SubtitleCue(
                        id = i + 1,
                        startMs = c.getLong("s"),
                        endMs = c.getLong("e"),
                        text = c.getString("t")
                    )
                )
            }
            ChunkSubtitleResult(actualStart, actualEnd, cues)
        }.getOrNull()
    }

    private fun writeCachedChunk(bucketKey: String, res: ChunkSubtitleResult) {
        val dir = cacheDir ?: return
        runCatching {
            val safeName = bucketKey.replace(Regex("""[^A-Za-z0-9_.-]"""), "_") + ".json"
            val arr = JSONArray()
            for (c in res.cues) {
                arr.put(JSONObject().put("s", c.startMs).put("e", c.endMs).put("t", c.text))
            }
            val root = JSONObject()
                .put("s", res.actualStartMs)
                .put("e", res.actualEndMs)
                .put("cues", arr)
            File(dir, safeName).writeText(root.toString(), Charsets.UTF_8)
        }
    }
}
