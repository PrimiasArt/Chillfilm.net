package com.phyll.filmhub.subtitle

import com.phyll.filmhub.model.ActiveSubtitleRenderState
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.PreFlightSubtitleState
import com.phyll.filmhub.model.SecondarySubLang
import com.phyll.filmhub.model.SeriesSubtitleProfile
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.SubtitleTrackOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ThaiSubtitleManager(
    cacheDir: File?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translator = SubtitleTranslator(cacheDir?.let { File(it, "phyll_sub_cache_v2") })
    private val audioSubGenerator = GeminiAudioSubtitleGenerator(
        cacheDir?.let { File(it, "phyll_gemini_audio_subs_v1") }
    )
    private val autoSyncEngine = SubtitleAutoSyncEngine(audioSubGenerator, cacheDir)
    private val onlineFetcher = OnlineSubtitleFetcher()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val parsedExternalCuesCache = ConcurrentHashMap<String, List<SubtitleCue>>()

    private val _availableTracks = MutableStateFlow<List<SubtitleTrackOption>>(emptyList())
    val availableTracks: StateFlow<List<SubtitleTrackOption>> = _availableTracks.asStateFlow()

    private val _selectedTrackId = MutableStateFlow<String?>(null)
    val selectedTrackId: StateFlow<String?> = _selectedTrackId.asStateFlow()

    private val _preFlightState = MutableStateFlow(PreFlightSubtitleState())
    val preFlightState: StateFlow<PreFlightSubtitleState> = _preFlightState.asStateFlow()

    private val _renderState = MutableStateFlow(ActiveSubtitleRenderState())
    val renderState: StateFlow<ActiveSubtitleRenderState> = _renderState.asStateFlow()

    private val _isAutoSyncing = MutableStateFlow(false)
    val isAutoSyncing: StateFlow<Boolean> = _isAutoSyncing.asStateFlow()

    private val _autoSyncFeedback = MutableStateFlow<String?>(null)
    val autoSyncFeedback: StateFlow<String?> = _autoSyncFeedback.asStateFlow()

    private var externalRefs: MutableList<ExternalSubtitleRef> = mutableListOf()
    private var embeddedTracks: List<SubtitleTrackOption> = emptyList()

    @Volatile
    private var sourceCues: List<SubtitleCue> = emptyList()
    @Volatile
    private var primaryThaiCues: List<SubtitleCue> = emptyList()
    @Volatile
    private var secondaryCues: List<SubtitleCue> = emptyList()

    @Volatile
    private var liveEmbeddedRawText: String? = null
    @Volatile
    private var liveEmbeddedTranslatedThai: String? = null
    @Volatile
    private var liveEmbeddedSecondary: String? = null

    @Volatile
    private var currentPositionMs: Long = 0L
    @Volatile
    private var currentBadge: String = "Sub: ไทย (Đang chuẩn bị...)"
    @Volatile
    private var isTranslatingFlag: Boolean = false

    @Volatile
    private var aiAudioSyntheticTrack: SubtitleTrackOption? = null
    @Volatile
    private var activeMovieRef: MovieItem? = null
    @Volatile
    private var activeEpisodeRef: Episode? = null
    @Volatile
    private var bilingualReferenceCues: List<SubtitleCue> = emptyList()
    private val rejectedTrackIdsForEpisode = ConcurrentHashMap.newKeySet<String>()
    private var autoSyncCallback: ((Long, Float, String, Boolean) -> Unit)? = null

    private var preFlightJob: Job? = null
    private var translationJob: Job? = null
    private var liveCueJob: Job? = null
    private var rollingAudioJob: Job? = null
    private var autoSyncJob: Job? = null

    /**
     * PRE-FLIGHT SUBTITLE GUARANTEE (v2.3 Series-Smart):
     * 1. If this series was locked to `preferGeminiAudioSub` (e.g. a 100-episode drama with no valid .SRT),
     *    jumps straight to Gemini Rolling Audio-to-Thai Subtitle Generation on every episode!
     * 2. Otherwise searches OpenSubtitles.org, YIFY, OS-v3 & YouTube IN PARALLEL with stream-timeline
     *    inspection, rejecting any wrong-length / wrong-movie .SRT candidates automatically.
     * 3. Applies saved per-series `(syncOffsetMs, subSpeedScale)` immediately from 0:00 and verifies
     *    via Two-Anchor Audio Alignment in the background!
     */
    fun prepareForMovieBeforePlay(
        movie: MovieItem,
        episode: Episode,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        seriesProfile: SeriesSubtitleProfile? = null,
        startPositionMs: Long = 0L,
        onAutoDetectedSync: ((Long, Float, String, Boolean) -> Unit)? = null,
        onReadyToPlay: () -> Unit
    ) {
        preFlightJob?.cancel()
        translationJob?.cancel()
        liveCueJob?.cancel()
        rollingAudioJob?.cancel()
        autoSyncJob?.cancel()
        _isAutoSyncing.value = false
        _autoSyncFeedback.value = null
        aiAudioSyntheticTrack = null
        activeMovieRef = movie
        activeEpisodeRef = episode
        bilingualReferenceCues = emptyList()
        rejectedTrackIdsForEpisode.clear()
        autoSyncCallback = onAutoDetectedSync

        externalRefs = episode.subtitles.toMutableList()
        embeddedTracks = emptyList()
        aiAudioSyntheticTrack = null
        sourceCues = emptyList()
        primaryThaiCues = emptyList()
        secondaryCues = emptyList()
        liveEmbeddedRawText = null
        liveEmbeddedTranslatedThai = null
        liveEmbeddedSecondary = null
        currentPositionMs = startPositionMs.coerceAtLeast(0L)
        isTranslatingFlag = false

        val searchDisplay = movie.originalTitle.ifBlank { movie.title }

        // Fast-path for 100-episode series where viewer (or Ep 1 auto-failover) locked Gemini Audio mode!
        if (seriesProfile?.preferGeminiAudioSub == true &&
            geminiApiKey.isNotBlank() &&
            movie.contentType != ContentType.LIVE_CHANNEL
        ) {
            startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = startPositionMs,
                config = config.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                geminiApiKey = geminiApiKey,
                onReadyToPlay = onReadyToPlay
            )
            return
        }

        _preFlightState.value = PreFlightSubtitleState(
            isPreparing = true,
            stepTitle = "🔎 Bước 1/2: Đang tìm Sub Thái gốc & kiểm tra độ dài khớp phim...",
            stepDetail = "$searchDisplay • ${episode.name}",
            progressFraction = 0.15f,
            searchedQuery = searchDisplay
        )

        preFlightJob = scope.launch {
            // 1. Discover real subtitles AND inspect stream duration IN PARALLEL (with speculative opening audio prefetch!)
            var realStreamDurationMs = 0L
            var speculativePrefetchJob: Job? = null
            if (movie.contentType != ContentType.LIVE_CHANNEL) {
                coroutineScope {
                    val subsDef = async {
                        withTimeoutOrNull(5_800L) {
                            runCatching { onlineFetcher.discoverSubtitles(movie, episode) }
                                .getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                    val timelineDef = async {
                        runCatching { audioSubGenerator.inspectStreamTimeline(episode) }
                            .getOrNull()
                    }
                    val timeline = timelineDef.await()
                    if (timeline != null &&
                        timeline !is GeminiAudioSubtitleGenerator.StreamAudioTimeline.DirectRangeFallback
                    ) {
                        realStreamDurationMs = timeline.totalDurationMs
                    }

                    // While OpenSubtitles/IMDb is still querying, speculatively pre-extract the opening 30s audio
                    // in background so if no .SRT exists, Gemini Audio Subtitles start with 0ms download wait!
                    if (timeline != null &&
                        !subsDef.isCompleted &&
                        geminiApiKey.isNotBlank() &&
                        config.autoGenerateAudioSubWhenMissing &&
                        externalRefs.isEmpty()
                    ) {
                        val openingSlotStart = ((startPositionMs.coerceAtLeast(0L) / 60_000L) * 60_000L)
                        speculativePrefetchJob = this@ThaiSubtitleManager.scope.launch(Dispatchers.IO) {
                            audioSubGenerator.prefetchAudioWindow(
                                timeline = timeline,
                                windowStartMs = openingSlotStart,
                                windowDurationMs = 30_000L
                            )
                        }
                    }

                    val onlineSubs = subsDef.await()
                    if (onlineSubs.isNotEmpty()) {
                        speculativePrefetchJob?.cancel()
                    }

                    for (sub in onlineSubs) {
                        if (externalRefs.none { it.urlOrContent == sub.urlOrContent }) {
                            externalRefs.add(sub)
                        }
                    }
                }
            }

            val combined = buildCombinedTracks()
            _availableTracks.value = combined

            val orderedExtCandidates = orderExternalCandidatesThaiFirst(
                tracks = combined,
                preferredPlatform = seriesProfile?.preferredPlatform.orEmpty()
            )
            if (orderedExtCandidates.isEmpty()) {
                handleMissingExternalSubtitles(
                    movie = movie,
                    episode = episode,
                    searchDisplay = searchDisplay,
                    startPositionMs = startPositionMs,
                    config = config,
                    geminiApiKey = geminiApiKey,
                    onReadyToPlay = onReadyToPlay
                )
                return@launch
            }

            // STAGE 1: Try Native Human Thai tracks across all major platforms FIRST (validating runtime compatibility!)
            val nativeHumanThaiTracks = orderedExtCandidates.filter {
                it.isThai && it.externalRef?.isAutoGenerated != true
            }
            val aiFallbackTracks = orderedExtCandidates.filterNot {
                it.isThai && it.externalRef?.isAutoGenerated != true
            }

            var chosenTrack: SubtitleTrackOption? = null
            var loadedCues: List<SubtitleCue> = emptyList()

            for ((idx, candidate) in nativeHumanThaiTracks.withIndex()) {
                val ref = candidate.externalRef ?: continue
                _preFlightState.value = _preFlightState.value.copy(
                    stepTitle = "🇹🇭 Đã thấy Sub Thái gốc trên ${ref.sourcePlatform}! Đang kiểm tra khớp độ dài...",
                    stepDetail = "${ref.label} (Nguồn Thái gốc #${idx + 1}/${nativeHumanThaiTracks.size})",
                    progressFraction = 0.42f
                )
                val parsed = loadExternalCues(ref)
                val sample = parsed.take(15).joinToString(" ") { it.text }
                if (parsed.size >= 5 &&
                    SubtitleTranslator.containsThaiScript(sample) &&
                    isSubtitleDurationCompatibleWithStream(parsed, realStreamDurationMs, ref.sourcePlatform)
                ) {
                    chosenTrack = candidate
                    loadedCues = parsed
                    break
                } else if (parsed.isNotEmpty()) {
                    rejectedTrackIdsForEpisode.add(candidate.id)
                }
            }

            // STAGE 2: Only if NO major platform had a compatible Native Human Thai subtitle -> switch to AI Translation!
            if (chosenTrack == null) {
                for ((idx, candidate) in aiFallbackTracks.withIndex()) {
                    val ref = candidate.externalRef ?: continue
                    _preFlightState.value = _preFlightState.value.copy(
                        stepTitle = "🤖 Bước 2/2: Chuyển qua Sub ${ref.sourcePlatform} khớp độ dài để dịch AI...",
                        stepDetail = "Đang nạp ${ref.label} (#${idx + 1}/${aiFallbackTracks.size})",
                        progressFraction = 0.40f
                    )
                    val parsed = loadExternalCues(ref)
                    if (parsed.size >= 5 &&
                        isSubtitleDurationCompatibleWithStream(parsed, realStreamDurationMs, ref.sourcePlatform)
                    ) {
                        chosenTrack = candidate
                        loadedCues = parsed
                        break
                    } else if (parsed.isNotEmpty()) {
                        rejectedTrackIdsForEpisode.add(candidate.id)
                    }
                }
            }

            if (chosenTrack == null || loadedCues.isEmpty()) {
                handleMissingExternalSubtitles(
                    movie = movie,
                    episode = episode,
                    searchDisplay = searchDisplay,
                    startPositionMs = startPositionMs,
                    config = config,
                    geminiApiKey = geminiApiKey,
                    onReadyToPlay = onReadyToPlay
                )
                return@launch
            }

            // Preload a Vietnamese or English reference track in background so 1-Tap Visual Snap is always bilingual!
            preloadBilingualReferenceTrackAsync(chosenTrack.externalRef)

            // 3. Apply Native Thai immediately OR AI pre-translate opening cues into Thai BEFORE video starts!
            applyLoadedExternalTrackWithPreFlight(
                track = chosenTrack,
                loadedCues = loadedCues,
                config = config,
                geminiApiKey = geminiApiKey,
                onReadyToPlay = onReadyToPlay
            )
        }
    }

    /**
     * Rejects `.SRT` files that clearly belong to a different format (e.g., a 2-hour movie `.SRT`
     * applied to a 45-minute series episode, or a 10-minute partial clip `.SRT` on a 2-hour movie).
     */
    private fun isSubtitleDurationCompatibleWithStream(
        cues: List<SubtitleCue>,
        streamDurationMs: Long,
        sourcePlatform: String
    ): Boolean {
        if (sourcePlatform.startsWith("YouTube", ignoreCase = true)) return true
        if (streamDurationMs < 600_000L) return true // Ignore short clips < 10 mins
        val substantive = cues.filter { SubtitleAutoSyncEngine.isSubstantiveDialogueCue(it) }.ifEmpty { cues }
        val lastCueEndMs = substantive.lastOrNull()?.endMs ?: return false

        // Reject if .SRT extends > 30% + 3 mins beyond actual episode duration (catches 120m movie SRT on 45m episode!)
        val maxAllowedMs = (streamDurationMs * 1.30).toLong() + 180_000L
        if (lastCueEndMs > maxAllowedMs) return false

        // Reject if episode >= 20 mins yet .SRT ends before 40% of the episode (partial/trailer SRT!)
        if (streamDurationMs >= 1_200_000L && lastCueEndMs < (streamDurationMs * 0.40).toLong()) {
            return false
        }
        return true
    }

    private fun preloadBilingualReferenceTrackAsync(chosenRef: ExternalSubtitleRef?) {
        if (chosenRef == null) return
        if (!chosenRef.isThai) {
            bilingualReferenceCues = emptyList() // `sourceCues` itself already holds Vi/En original text!
            return
        }
        val viOrEnRef = externalRefs.firstOrNull {
            it.id != chosenRef.id && it.languageCode.lowercase().startsWith("vi")
        } ?: externalRefs.firstOrNull {
            it.id != chosenRef.id && it.languageCode.lowercase().startsWith("en")
        } ?: return

        scope.launch(Dispatchers.IO) {
            val loaded = loadExternalCues(viOrEnRef)
            if (loaded.size >= 5) {
                bilingualReferenceCues = loaded
            }
        }
    }

    private fun handleMissingExternalSubtitles(
        movie: MovieItem,
        episode: Episode,
        searchDisplay: String,
        startPositionMs: Long,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onReadyToPlay: () -> Unit
    ) {
        if (movie.contentType == ContentType.LIVE_CHANNEL) {
            currentBadge = "Kênh Trực Tiếp"
            _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
            _preFlightState.value = PreFlightSubtitleState(isPreparing = false)
            onReadyToPlay()
            return
        }

        val cleanModel = config.geminiModelId.ifBlank { "gemini-3-flash-preview" }
        if (config.autoGenerateAudioSubWhenMissing && geminiApiKey.isNotBlank()) {
            startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = startPositionMs,
                config = config,
                geminiApiKey = geminiApiKey,
                onReadyToPlay = onReadyToPlay
            )
        } else if (config.autoGenerateAudioSubWhenMissing && geminiApiKey.isBlank()) {
            currentBadge = "Chưa có Sub rời — Quét QR nạp Gemini Key để AI tự nghe phim"
            _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                noSubtitleFound = true,
                needsGeminiKeyQr = true,
                searchedQuery = searchDisplay,
                stepTitle = "🔐 Chưa có Sub trên các kho lớn — Quét QR nạp Gemini Key để AI tự tạo Sub ไทย!",
                stepDetail = "Key chỉ lưu tạm trên RAM (thoát App tự xóa). Quét QR để chọn Model ($cleanModel) & để Gemini tự nghe phim dịch Sub ไทย ngay!"
            )
        } else {
            currentBadge = "Chưa tìm thấy Sub rời trên các nền tảng"
            _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                noSubtitleFound = true,
                needsGeminiKeyQr = false,
                searchedQuery = searchDisplay,
                stepTitle = "⚠️ Chưa tìm thấy Sub trên OpenSubtitles / YIFY / YouTube cho \"$searchDisplay\"",
                stepDetail = "Bạn có thể bật Gemini Audio tạo Sub ไทย, tìm bằng tên tiếng Anh khác, bắn file .SRT qua QR, hoặc bấm Phát luôn."
            )
        }
    }

    /**
     * Connects to the user's chosen Gemini model to listen directly to the movie's audio stream:
     * - Pre-generates the initial 2-minute window before releasing playback (if [onReadyToPlay] != null).
     * - Continuously generates upcoming 2-minute slots up to 6 minutes ahead of the viewer's playhead,
     *   jumping immediately to the new position whenever the viewer seeks!
     */
    fun startGeminiRollingAudioSubtitles(
        movie: MovieItem,
        episode: Episode,
        initialPositionMs: Long = currentPositionMs,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onReadyToPlay: (() -> Unit)? = null
    ) {
        preFlightJob?.cancel()
        translationJob?.cancel()
        liveCueJob?.cancel()
        rollingAudioJob?.cancel()
        aiAudioSyntheticTrack = null

        val cleanKey = geminiApiKey.trim()
        val cleanModel = config.geminiModelId.trim().removePrefix("models/").ifBlank { "gemini-3-flash-preview" }
        val searchDisplay = movie.originalTitle.ifBlank { movie.title }

        if (cleanKey.isEmpty()) {
            currentBadge = "🔐 Cần quét QR nạp Gemini Key tạm vào RAM trước"
            _renderState.value = _renderState.value.copy(statusBadge = currentBadge, isTranslating = false)
            if (onReadyToPlay != null) {
                _preFlightState.value = PreFlightSubtitleState(
                    isPreparing = false,
                    noSubtitleFound = true,
                    needsGeminiKeyQr = true,
                    searchedQuery = searchDisplay,
                    stepTitle = "🔐 Chưa có Gemini Key trong RAM — Quét QR để AI tự nghe phim tạo Sub ไทย!",
                    stepDetail = "Key chỉ lưu tạm trên RAM (thoát App tự xóa). Quét QR chọn Model ($cleanModel) để bắt đầu ngay."
                )
            }
            return
        }

        val startMs = initialPositionMs.coerceAtLeast(0L)
        currentPositionMs = startMs
        isTranslatingFlag = true
        secondaryCues = emptyList()

        if (onReadyToPlay != null) {
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = true,
                stepTitle = "🎙️ Chưa có Sub rời -> Kết nối Gemini ($cleanModel) tự nghe phim & tạo Sub ไทย...",
                stepDetail = "Bước 1/2: Đang tách luồng Audio siêu nhẹ từ ${movie.title} (${episode.name})...",
                progressFraction = 0.36f,
                searchedQuery = searchDisplay
            )
        } else {
            currentBadge = "🎙️ Đang kết nối Gemini ($cleanModel) phân tích luồng Audio..."
            refreshOverlayAt(currentPositionMs, config)
        }

        rollingAudioJob = scope.launch {
            val timeline = audioSubGenerator.inspectStreamTimeline(episode)
            if (timeline == null) {
                isTranslatingFlag = false
                currentBadge = "⚠️ Luồng phim này không hỗ trợ tách Audio tạo Sub AI"
                refreshOverlayAt(currentPositionMs, config)
                if (onReadyToPlay != null) {
                    _preFlightState.value = PreFlightSubtitleState(
                        isPreparing = false,
                        noSubtitleFound = true,
                        searchedQuery = searchDisplay,
                        stepTitle = "⚠️ Không trích xuất được luồng Audio trước từ nguồn phim này",
                        stepDetail = "Bạn có thể gửi file .SRT qua QR hoặc bấm Phát luôn."
                    )
                }
                return@launch
            }

            val slotSizeMs = 60_000L // 1-minute nimble slots (2x faster turnaround, zero timeout)
            val halfSprintMs = 30_000L // 30-second micro-sprint for ~3s instant startup & seek!
            val totalDurationMs = timeline.totalDurationMs.coerceAtLeast(slotSizeMs)
            val totalSlots = ((totalDurationMs + slotSizeMs - 1L) / slotSizeMs).toInt().coerceIn(1, 480)
            val completedSlots = HashSet<Int>()
            val failedSlotCounts = HashMap<Int, Int>()
            val inFlightJobs = HashMap<Int, Job>()
            val prefetchedSlots = HashSet<Int>()
            val accumulatedCuesBySlot = ConcurrentHashMap<Int, List<SubtitleCue>>()
            val cacheNamespace = "${movie.id}_${episode.id}"
                .replace(Regex("[^A-Za-z0-9_]"), "_")
                .takeLast(48)

            val synthTrack = SubtitleTrackOption(
                id = "ai_audio_${episode.id}",
                label = "⚡ Gemini Turbo Audio -> ไทย ($cleanModel)",
                languageCode = "th",
                isEmbedded = false,
                isThai = true
            )
            aiAudioSyntheticTrack = synthTrack
            _availableTracks.value = buildCombinedTracks()
            _selectedTrackId.value = synthTrack.id

            fun rebuildMergedAiCues() {
                val merged = accumulatedCuesBySlot.entries
                    .sortedBy { it.key }
                    .flatMap { it.value }
                    .sortedBy { it.startMs }
                    .mapIndexed { idx, cue -> cue.copy(id = idx + 1) }
                sourceCues = merged
                primaryThaiCues = merged
                refreshOverlayAt(currentPositionMs, config)
            }

            // 1. 30-SECOND FAST-BOOT MICRO-SPRINT:
            // Splits initial 60s slot into Part A (0..30s) + Part B (30..60s) IN PARALLEL!
            // Releases video playback the instant the viewer's 30s half finishes (~3-4 seconds!)
            val initialSlot = (startMs / slotSizeMs).toInt().coerceIn(0, totalSlots - 1)
            val initialSlotStartMs = initialSlot * slotSizeMs
            val initialSlotDurMs = minOf(slotSizeMs, (totalDurationMs - initialSlotStartMs).coerceAtLeast(10_000L))
            val viewerInSecondHalf = (startMs - initialSlotStartMs) >= 26_000L && initialSlotDurMs > 36_000L

            val sprintStartSec = ((if (viewerInSecondHalf) initialSlotStartMs + halfSprintMs else initialSlotStartMs) / 1000L)
            val sprintEndSec = sprintStartSec + 30L
            val sprintRangeLabel = "%02d:%02d-%02d:%02d".format(
                sprintStartSec / 60L,
                sprintStartSec % 60L,
                sprintEndSec / 60L,
                sprintEndSec % 60L
            )

            if (onReadyToPlay != null) {
                _preFlightState.value = PreFlightSubtitleState(
                    isPreparing = true,
                    stepTitle = "⚡ Gemini Turbo đang tạo nhanh 30s Sub ไทย mở đầu ($sprintRangeLabel)...",
                    stepDetail = "Chế độ Siêu Tốc (~3-4 giây là chiếu ngay • Các phút tiếp theo tự dịch song song ngầm)...",
                    progressFraction = 0.76f,
                    searchedQuery = searchDisplay
                )
            }
            currentBadge = "⚡ Gemini Turbo đang tạo nhanh Sub đoạn $sprintRangeLabel..."
            refreshOverlayAt(currentPositionMs, config)

            // Speculatively pre-download next minute's audio while Gemini processes the opening 30s!
            if (initialSlot + 1 < totalSlots) {
                prefetchedSlots.add(initialSlot + 1)
                launch(Dispatchers.IO) {
                    audioSubGenerator.prefetchAudioWindow(
                        timeline = timeline,
                        windowStartMs = (initialSlot + 1) * slotSizeMs,
                        windowDurationMs = slotSizeMs
                    )
                }
            }

            val firstBlockingResult: Result<GeminiAudioSubtitleGenerator.ChunkSubtitleResult>
            if (initialSlotDurMs > 36_000L) {
                val partADef = async(Dispatchers.IO) {
                    audioSubGenerator.generateThaiCuesForWindow(
                        cacheNamespace = cacheNamespace,
                        timeline = timeline,
                        windowStartMs = initialSlotStartMs,
                        windowDurationMs = halfSprintMs,
                        apiKey = cleanKey,
                        modelId = cleanModel,
                        preferTurboFlash = true
                    )
                }
                val partBDef = async(Dispatchers.IO) {
                    audioSubGenerator.generateThaiCuesForWindow(
                        cacheNamespace = cacheNamespace,
                        timeline = timeline,
                        windowStartMs = initialSlotStartMs + halfSprintMs,
                        windowDurationMs = initialSlotDurMs - halfSprintMs,
                        apiKey = cleanKey,
                        modelId = cleanModel,
                        preferTurboFlash = true
                    )
                }

                // Wait ONLY for the 30s half where the viewer's playhead sits!
                firstBlockingResult = if (viewerInSecondHalf) partBDef.await() else partADef.await()
                val firstHalfChunk = firstBlockingResult.getOrNull()
                if (firstHalfChunk != null) {
                    accumulatedCuesBySlot[initialSlot] = firstHalfChunk.cues
                    rebuildMergedAiCues()

                    // Complete the companion 30s half asynchronously without blocking video start!
                    val otherDef = if (viewerInSecondHalf) partADef else partBDef
                    inFlightJobs[initialSlot] = launch {
                        val secondHalf = otherDef.await().getOrNull()
                        val combinedSlotCues = (firstHalfChunk.cues + (secondHalf?.cues ?: emptyList()))
                            .sortedBy { it.startMs }
                        accumulatedCuesBySlot[initialSlot] = combinedSlotCues
                        completedSlots.add(initialSlot)
                        inFlightJobs.remove(initialSlot)
                        rebuildMergedAiCues()
                    }
                } else {
                    partADef.cancel()
                    partBDef.cancel()
                }
            } else {
                firstBlockingResult = audioSubGenerator.generateThaiCuesForWindow(
                    cacheNamespace = cacheNamespace,
                    timeline = timeline,
                    windowStartMs = initialSlotStartMs,
                    windowDurationMs = initialSlotDurMs,
                    apiKey = cleanKey,
                    modelId = cleanModel,
                    preferTurboFlash = true
                )
                firstBlockingResult.getOrNull()?.let { chunk ->
                    completedSlots.add(initialSlot)
                    accumulatedCuesBySlot[initialSlot] = chunk.cues
                    rebuildMergedAiCues()
                }
            }

            if (firstBlockingResult.getOrNull() == null) {
                isTranslatingFlag = false
                val errMsg = firstBlockingResult.exceptionOrNull()?.message ?: "Không kết nối được Gemini API"
                val looksLikeAuthOrModelErr = errMsg.contains("400") ||
                    errMsg.contains("401") ||
                    errMsg.contains("403") ||
                    errMsg.contains("404") ||
                    errMsg.contains("API key", ignoreCase = true)
                currentBadge = "⚠️ Gemini lỗi: ${errMsg.take(52)}"
                refreshOverlayAt(currentPositionMs, config)

                if (onReadyToPlay != null) {
                    _preFlightState.value = PreFlightSubtitleState(
                        isPreparing = false,
                        noSubtitleFound = true,
                        needsGeminiKeyQr = looksLikeAuthOrModelErr,
                        searchedQuery = searchDisplay,
                        stepTitle = "⚠️ Gemini ($cleanModel) báo lỗi: ${errMsg.take(85)}",
                        stepDetail = "Hãy quét QR kiểm tra lại API Key hoặc chọn Model khác (VD: gemini-3-flash-preview / gemini-2.5-flash), hoặc bấm Phát luôn."
                    )
                }
                return@launch
            }

            val hasMoreSlots = completedSlots.size < totalSlots
            isTranslatingFlag = hasMoreSlots
            currentBadge = if (hasMoreSlots) {
                "Sub: ไทย Gemini Turbo (${primaryThaiCues.size} câu • Đang đệm song song 2 luồng...)"
            } else {
                "Sub: ไทย Gemini Audio ($cleanModel • ${primaryThaiCues.size} câu)"
            }
            refreshOverlayAt(currentPositionMs, config)

            if (onReadyToPlay != null) {
                _preFlightState.value = PreFlightSubtitleState(
                    isPreparing = false,
                    loadedCueCount = primaryThaiCues.size,
                    firstCueTimeMs = primaryThaiCues.firstOrNull()?.startMs ?: -1L
                )
                onReadyToPlay()
            }

            // 2. 2-WORKER PARALLEL ROLLING PIPELINE (6 minutes ahead + 400ms instant seek preemption!):
            val maxConcurrentWorkers = 2
            while (isActive && completedSlots.size < totalSlots) {
                // Clean up completed worker jobs
                val doneKeys = inFlightJobs.filterValues { !it.isActive }.keys.toList()
                doneKeys.forEach { inFlightJobs.remove(it) }

                val viewerSlot = (currentPositionMs.coerceAtLeast(0L) / slotSizeMs)
                    .toInt()
                    .coerceIn(0, totalSlots - 1)
                val maxAheadSlot = minOf(totalSlots - 1, viewerSlot + 5) // 6 full minutes lookahead!

                // If viewer sought far away from in-flight background jobs, preempt stale distant jobs immediately!
                if (viewerSlot !in completedSlots &&
                    viewerSlot !in inFlightJobs.keys &&
                    inFlightJobs.size >= maxConcurrentWorkers
                ) {
                    val staleSlot = inFlightJobs.keys.firstOrNull {
                        it < viewerSlot || it > viewerSlot + 5
                    }
                    if (staleSlot != null) {
                        inFlightJobs.remove(staleSlot)?.cancel()
                    }
                }

                // Launch up to 2 concurrent slot workers prioritized from viewerSlot outward
                while (inFlightJobs.size < maxConcurrentWorkers) {
                    val candidateSlot = (viewerSlot..maxAheadSlot).firstOrNull { slot ->
                        slot !in completedSlots &&
                            slot !in inFlightJobs.keys &&
                            (failedSlotCounts[slot] ?: 0) < 2
                    } ?: break

                    val slotStartMs = candidateSlot * slotSizeMs
                    val slotDurMs = minOf(slotSizeMs, (totalDurationMs - slotStartMs).coerceAtLeast(8_000L))
                    val isUrgentSeekSlot = (candidateSlot == viewerSlot)
                    val slotRange = formatSlotRange(candidateSlot, slotSizeMs)

                    isTranslatingFlag = true
                    currentBadge = "Sub: ไทย Gemini (${primaryThaiCues.size} câu • Dịch song song $slotRange...)"
                    refreshOverlayAt(currentPositionMs, config)

                    val workerJob = launch {
                        if (isUrgentSeekSlot && slotDurMs > 36_000L) {
                            // Viewer sought directly into an unbuffered minute -> split 30s + 30s turbo sprint!
                            val pADef = async(Dispatchers.IO) {
                                audioSubGenerator.generateThaiCuesForWindow(
                                    cacheNamespace = cacheNamespace,
                                    timeline = timeline,
                                    windowStartMs = slotStartMs,
                                    windowDurationMs = halfSprintMs,
                                    apiKey = cleanKey,
                                    modelId = cleanModel,
                                    preferTurboFlash = true
                                )
                            }
                            val pBDef = async(Dispatchers.IO) {
                                audioSubGenerator.generateThaiCuesForWindow(
                                    cacheNamespace = cacheNamespace,
                                    timeline = timeline,
                                    windowStartMs = slotStartMs + halfSprintMs,
                                    windowDurationMs = slotDurMs - halfSprintMs,
                                    apiKey = cleanKey,
                                    modelId = cleanModel,
                                    preferTurboFlash = true
                                )
                            }
                            val seekSecondHalf = (currentPositionMs - slotStartMs) >= 26_000L
                            val firstRes = if (seekSecondHalf) pBDef.await() else pADef.await()
                            firstRes.getOrNull()?.let { half1 ->
                                accumulatedCuesBySlot[candidateSlot] = half1.cues
                                rebuildMergedAiCues()
                            }
                            val secondRes = if (seekSecondHalf) pADef.await() else pBDef.await()
                            val c1 = firstRes.getOrNull()?.cues
                            val c2 = secondRes.getOrNull()?.cues
                            if (c1 != null || c2 != null) {
                                accumulatedCuesBySlot[candidateSlot] =
                                    (c1.orEmpty() + c2.orEmpty()).sortedBy { it.startMs }
                                completedSlots.add(candidateSlot)
                                rebuildMergedAiCues()
                            } else {
                                failedSlotCounts[candidateSlot] = (failedSlotCounts[candidateSlot] ?: 0) + 1
                            }
                        } else {
                            val slotRes = audioSubGenerator.generateThaiCuesForWindow(
                                cacheNamespace = cacheNamespace,
                                timeline = timeline,
                                windowStartMs = slotStartMs,
                                windowDurationMs = slotDurMs,
                                apiKey = cleanKey,
                                modelId = cleanModel,
                                preferTurboFlash = (candidateSlot <= viewerSlot + 1)
                            )
                            val chunk = slotRes.getOrNull()
                            if (chunk != null) {
                                completedSlots.add(candidateSlot)
                                accumulatedCuesBySlot[candidateSlot] = chunk.cues
                                rebuildMergedAiCues()
                            } else {
                                failedSlotCounts[candidateSlot] = (failedSlotCounts[candidateSlot] ?: 0) + 1
                            }
                        }
                    }
                    inFlightJobs[candidateSlot] = workerJob
                }

                // Speculatively pre-download the NEXT upcoming slot's audio while Gemini GPUs are busy!
                val nextPrefetchSlot = (viewerSlot..maxAheadSlot).firstOrNull { slot ->
                    slot !in completedSlots &&
                        slot !in inFlightJobs.keys &&
                        slot !in prefetchedSlots
                }
                if (nextPrefetchSlot != null) {
                    prefetchedSlots.add(nextPrefetchSlot)
                    launch(Dispatchers.IO) {
                        audioSubGenerator.prefetchAudioWindow(
                            timeline = timeline,
                            windowStartMs = nextPrefetchSlot * slotSizeMs,
                            windowDurationMs = slotSizeMs
                        )
                    }
                }

                if (inFlightJobs.isEmpty()) {
                    isTranslatingFlag = false
                    val bufferedUntilMin = ((maxAheadSlot + 1) * slotSizeMs) / 60_000L
                    currentBadge = "Sub: ไทย Gemini ($cleanModel • ${primaryThaiCues.size} câu • Đệm trước ${bufferedUntilMin}p)"
                    refreshOverlayAt(currentPositionMs, config)
                }

                delay(400L)
            }

            inFlightJobs.values.forEach { it.join() }
            isTranslatingFlag = false
            currentBadge = "Sub: ไทย Gemini Audio ($cleanModel • ${primaryThaiCues.size} câu • 100%)"
            refreshOverlayAt(currentPositionMs, config)
        }
    }

    private fun formatSlotRange(slotIndex: Int, slotSizeMs: Long): String {
        val startSec = (slotIndex * slotSizeMs) / 1000L
        val endSec = ((slotIndex + 1) * slotSizeMs) / 1000L
        return "%02d:%02d-%02d:%02d".format(
            startSec / 60L,
            startSec % 60L,
            endSec / 60L,
            endSec % 60L
        )
    }

    /**
     * Allows user to search OpenSubtitles manually by custom English title / IMDb tt-ID right on TV.
     */
    fun searchAndLoadCustomQuery(
        customQuery: String,
        movie: MovieItem,
        episode: Episode,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onReadyToPlay: () -> Unit
    ) {
        val trimmed = customQuery.trim()
        if (trimmed.isEmpty()) return

        preFlightJob?.cancel()
        translationJob?.cancel()
        rollingAudioJob?.cancel()
        aiAudioSyntheticTrack = null

        val season = onlineFetcher.extractSeasonNumber(trimmed, movie.title)
        val epNum = onlineFetcher.extractEpisodeNumber(episode.name)
        val isSeries = movie.contentType == ContentType.SERIES ||
            (movie.servers.firstOrNull()?.episodes?.size ?: 0) > 1

        _preFlightState.value = PreFlightSubtitleState(
            isPreparing = true,
            stepTitle = "🔍 Đang tìm phụ đề cho \"$trimmed\"...",
            stepDetail = if (isSeries) "Season $season • Tập $epNum" else "Phim lẻ",
            progressFraction = 0.2f,
            searchedQuery = trimmed
        )

        preFlightJob = scope.launch {
            val found = onlineFetcher.discoverByCustomQuery(
                customQuery = trimmed,
                isSeries = isSeries,
                season = season,
                episode = epNum
            )
            if (found.isEmpty()) {
                _preFlightState.value = PreFlightSubtitleState(
                    isPreparing = false,
                    noSubtitleFound = true,
                    searchedQuery = trimmed,
                    stepTitle = "⚠️ Không tìm thấy Sub trên OpenSubtitles cho \"$trimmed\"",
                    stepDetail = "Mẹo: Nhập tên gốc tiếng Anh của phim hoặc mã IMDb (VD: tt15239678)."
                )
                return@launch
            }

            externalRefs.clear()
            externalRefs.addAll(found)
            val combined = buildCombinedTracks()
            _availableTracks.value = combined

            val ordered = orderExternalCandidatesThaiFirst(combined)
            for (cand in ordered) {
                val ref = cand.externalRef ?: continue
                val cues = loadExternalCues(ref)
                if (cues.size >= 3) {
                    applyLoadedExternalTrackWithPreFlight(
                        track = cand,
                        loadedCues = cues,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = onReadyToPlay
                    )
                    return@launch
                }
            }

            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                noSubtitleFound = true,
                searchedQuery = trimmed,
                stepTitle = "⚠️ Đã thấy danh sách nhưng tải file .SRT thất bại",
                stepDetail = "Hãy thử lại hoặc bấm Phát luôn."
            )
        }
    }

    fun dismissPreFlightAndPlayAnyway(onReadyToPlay: () -> Unit) {
        preFlightJob?.cancel()
        _preFlightState.value = PreFlightSubtitleState(isPreparing = false, noSubtitleFound = false)
        onReadyToPlay()
    }

    private suspend fun applyLoadedExternalTrackWithPreFlight(
        track: SubtitleTrackOption,
        loadedCues: List<SubtitleCue>,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onReadyToPlay: () -> Unit
    ) {
        val ref = track.externalRef ?: return
        _selectedTrackId.value = track.id
        sourceCues = loadedCues

        val sampleText = loadedCues.take(12).joinToString(" ") { it.text }
        val alreadyThai = ref.isThai || SubtitleTranslator.containsThaiScript(sampleText)
        secondaryCues = resolveInitialSecondaryCues(ref, loadedCues, config)

        if (alreadyThai || !config.autoTranslateToThai) {
            primaryThaiCues = loadedCues
            isTranslatingFlag = false
            currentBadge = buildBadgeText(
                isNativeThai = alreadyThai,
                cueCount = loadedCues.size,
                config = config,
                platform = ref.sourcePlatform
            )
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                loadedCueCount = loadedCues.size,
                firstCueTimeMs = loadedCues.first().startMs
            )
            refreshOverlayAt(currentPositionMs, config)
            onReadyToPlay()
            maybeLaunchSilentStartupAutoSync(ref, config, geminiApiKey)
            return
        }

        // Non-Thai real .SRT -> Fast-sprint translate ONLY the 24 cues right at currentPositionMs (~1.8s!)
        // BEFORE releasing playback, then translate all remaining batches 3-way IN PARALLEL in background!
        val workingMutable = loadedCues.toMutableList()
        val cueIndexMap = HashMap<Int, Int>(loadedCues.size)
        loadedCues.forEachIndexed { idx, cue -> cueIndexMap[cue.id] = idx }

        val effectiveStartMs = (currentPositionMs * config.subSpeedScale.toDouble()).toLong() - config.syncOffsetMs
        val startCueIdx = loadedCues.indexOfFirst { it.endMs >= (effectiveStartMs - 3_000L).coerceAtLeast(0L) }
            .coerceAtLeast(0)
        val fastBootEndIdx = minOf(loadedCues.size, startCueIdx + 24)
        val fastBootSlice = loadedCues.subList(startCueIdx, fastBootEndIdx)
        val fastBootIds = fastBootSlice.mapTo(HashSet()) { it.id }

        _preFlightState.value = _preFlightState.value.copy(
            isPreparing = true,
            stepTitle = "⚡ Đang AI dịch nhanh ${fastBootSlice.size} câu mở đầu sang Tiếng Thái...",
            stepDetail = "Chế độ Siêu Tốc (~2 giây là chiếu ngay • ${loadedCues.size} câu còn lại dịch song song 3 luồng ngầm)",
            progressFraction = 0.82f
        )

        val translatedOpening = translator.translateCueBatch(
            cues = fastBootSlice,
            targetLang = "th",
            mode = config.translationEngine,
            geminiApiKey = geminiApiKey,
            geminiModelId = config.geminiModelId
        )
        for (tc in translatedOpening) {
            cueIndexMap[tc.id]?.let { pos -> workingMutable[pos] = tc }
        }

        // Order remaining cues from playhead forward, then wrap around earlier cues if resumed mid-episode
        val remainingForward = loadedCues.subList(fastBootEndIdx, loadedCues.size)
            .filterNot { it.id in fastBootIds }
        val remainingBackward = loadedCues.subList(0, startCueIdx)
            .filterNot { it.id in fastBootIds }
        val remainingCues = remainingForward + remainingBackward
        val remainingBatches = remainingCues.chunked(36)

        // Commit opening Thai cues & release video playback IMMEDIATELY!
        primaryThaiCues = workingMutable.toList()
        val hasMoreBatches = remainingBatches.isNotEmpty()
        isTranslatingFlag = hasMoreBatches
        currentBadge = if (hasMoreBatches) {
            "Sub: ไทย AI (${loadedCues.size} câu • Dịch song song nền...)"
        } else {
            buildBadgeText(isNativeThai = false, cueCount = loadedCues.size, config = config)
        }

        _preFlightState.value = PreFlightSubtitleState(
            isPreparing = false,
            loadedCueCount = loadedCues.size,
            firstCueTimeMs = loadedCues.first().startMs
        )
        refreshOverlayAt(currentPositionMs, config)
        onReadyToPlay()
        maybeLaunchSilentStartupAutoSync(ref, config, geminiApiKey)

        // Continue translating the remaining movie cues 3 batches IN PARALLEL in background!
        if (hasMoreBatches) {
            translationJob = scope.launch {
                var completedBatches = 0
                for (wave in remainingBatches.chunked(3)) {
                    if (!isActive) return@launch
                    val waveTranslated = coroutineScope {
                        wave.map { batch ->
                            async(Dispatchers.IO) {
                                translator.translateCueBatch(
                                    cues = batch,
                                    targetLang = "th",
                                    mode = config.translationEngine,
                                    geminiApiKey = geminiApiKey,
                                    geminiModelId = config.geminiModelId
                                )
                            }
                        }.awaitAll()
                    }
                    for (translatedBatch in waveTranslated) {
                        for (tc in translatedBatch) {
                            cueIndexMap[tc.id]?.let { pos -> workingMutable[pos] = tc }
                        }
                    }
                    completedBatches += wave.size
                    primaryThaiCues = workingMutable.toList()
                    val pct = (((completedBatches) * 100) / remainingBatches.size).coerceAtMost(99)
                    currentBadge = "Sub: ไทย AI (${loadedCues.size} câu • $pct%)"
                    refreshOverlayAt(currentPositionMs, config)
                }
                isTranslatingFlag = false
                currentBadge = buildBadgeText(
                    isNativeThai = false,
                    cueCount = loadedCues.size,
                    config = config
                )
                refreshOverlayAt(currentPositionMs, config)
            }
        }
    }

    fun updateEmbeddedTracks(
        exoTextTracks: List<SubtitleTrackOption>,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onSelectEmbeddedTrack: ((SubtitleTrackOption) -> Unit)?
    ) {
        embeddedTracks = exoTextTracks
        val combined = buildCombinedTracks()
        _availableTracks.value = combined

        // Only auto-select embedded track if no external .SRT track is currently loaded
        if (_selectedTrackId.value == null && primaryThaiCues.isEmpty() && exoTextTracks.isNotEmpty()) {
            val bestEmbedded = chooseBestDefaultTrack(exoTextTracks) ?: return
            selectTrack(bestEmbedded, config, geminiApiKey, onSelectEmbeddedTrack)
        }
    }

    fun addAndSelectExternalSubtitle(
        newSub: ExternalSubtitleRef,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onReadyToPlay: (() -> Unit)? = null
    ) {
        rollingAudioJob?.cancel()
        aiAudioSyntheticTrack = null
        externalRefs.removeAll { it.id == newSub.id }
        externalRefs.add(0, newSub)
        parsedExternalCuesCache.remove(newSub.id)
        val combined = buildCombinedTracks()
        _availableTracks.value = combined
        val targetOption = combined.firstOrNull { it.id == "ext_${newSub.id}" } ?: return

        if (onReadyToPlay != null) {
            preFlightJob?.cancel()
            preFlightJob = scope.launch {
                val cues = loadExternalCues(newSub)
                if (cues.isNotEmpty()) {
                    applyLoadedExternalTrackWithPreFlight(
                        track = targetOption,
                        loadedCues = cues,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = onReadyToPlay
                    )
                } else {
                    onReadyToPlay()
                }
            }
        } else {
            selectTrack(targetOption, config, geminiApiKey, onSelectEmbeddedTrack = null)
        }
    }

    private fun buildCombinedTracks(): List<SubtitleTrackOption> {
        val extOptions = externalRefs.map { ref ->
            SubtitleTrackOption(
                id = "ext_${ref.id}",
                label = ref.label,
                languageCode = ref.languageCode,
                isEmbedded = false,
                isThai = ref.isThai,
                externalRef = ref
            )
        }
        return listOfNotNull(aiAudioSyntheticTrack) + extOptions + embeddedTracks
    }

    private fun orderExternalCandidatesThaiFirst(
        tracks: List<SubtitleTrackOption>,
        preferredPlatform: String = ""
    ): List<SubtitleTrackOption> {
        val extOnly = tracks.filter { !it.isEmbedded && it.externalRef != null }
        val humanOnly = extOnly.filter { it.externalRef?.isAutoGenerated != true }
        val autoOnly = extOnly.filter { it.externalRef?.isAutoGenerated == true }

        fun prioritizePlatform(list: List<SubtitleTrackOption>): List<SubtitleTrackOption> {
            if (preferredPlatform.isBlank()) return list
            val matched = list.filter {
                it.externalRef?.sourcePlatform.equals(preferredPlatform, ignoreCase = true)
            }
            val rest = list.filterNot {
                it.externalRef?.sourcePlatform.equals(preferredPlatform, ignoreCase = true)
            }
            return matched + rest
        }

        val nativeHumanThai = prioritizePlatform(humanOnly.filter { it.isThai })
        val humanVi = prioritizePlatform(
            humanOnly.filter { !it.isThai && it.languageCode.lowercase().startsWith("vi") }
        )
        val humanEn = prioritizePlatform(
            humanOnly.filter { !it.isThai && it.languageCode.lowercase().startsWith("en") }
        )
        val humanOthers = prioritizePlatform(
            humanOnly.filter {
                !it.isThai &&
                    !it.languageCode.lowercase().startsWith("en") &&
                    !it.languageCode.lowercase().startsWith("vi")
            }
        )

        // Auto-generated (YouTube Auto-CC) ONLY after all human tracks across OpenSubtitles/YIFY/YouTube Official
        val autoNonThai = autoOnly.filter { !it.isThai }
        val autoThai = autoOnly.filter { it.isThai }

        return nativeHumanThai + humanVi + humanEn + humanOthers + autoNonThai + autoThai
    }

    fun chooseBestDefaultTrack(tracks: List<SubtitleTrackOption>): SubtitleTrackOption? {
        if (tracks.isEmpty()) return null
        tracks.firstOrNull { !it.isEmbedded && it.isThai }?.let { return it }
        tracks.firstOrNull { it.isEmbedded && it.isThai }?.let { return it }

        val preferredFallbackOrder = listOf("en", "eng", "vi", "vie", "zh", "chi", "ko", "kor")
        for (lang in preferredFallbackOrder) {
            tracks.firstOrNull { !it.isEmbedded && it.languageCode.lowercase().startsWith(lang) }
                ?.let { return it }
        }
        tracks.firstOrNull { !it.isEmbedded }?.let { return it }

        for (lang in preferredFallbackOrder) {
            tracks.firstOrNull { it.isEmbedded && it.languageCode.lowercase().startsWith(lang) }
                ?.let { return it }
        }
        return tracks.first()
    }

    fun selectTrack(
        track: SubtitleTrackOption,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onSelectEmbeddedTrack: ((SubtitleTrackOption) -> Unit)?
    ) {
        if (track.id == aiAudioSyntheticTrack?.id) {
            _selectedTrackId.value = track.id
            refreshOverlayAt(currentPositionMs, config)
            return
        }
        rollingAudioJob?.cancel()
        translationJob?.cancel()
        liveCueJob?.cancel()
        _selectedTrackId.value = track.id

        if (track.isEmbedded) {
            sourceCues = emptyList()
            primaryThaiCues = emptyList()
            secondaryCues = emptyList()
            onSelectEmbeddedTrack?.invoke(track)
            currentBadge = if (track.isThai) {
                "Sub: ไทย (${track.label})"
            } else if (config.autoTranslateToThai) {
                "Sub: ไทย AI (${track.label})"
            } else {
                "Sub: ${track.label}"
            }
            _renderState.value = _renderState.value.copy(statusBadge = currentBadge, isTranslating = false)
            return
        }

        val ref = track.externalRef ?: return
        translationJob = scope.launch {
            isTranslatingFlag = true
            _renderState.value = _renderState.value.copy(
                statusBadge = "Đang nạp ${ref.label}...",
                isTranslating = true
            )

            val loadedCues = loadExternalCues(ref)
            if (loadedCues.isEmpty()) {
                isTranslatingFlag = false
                currentBadge = "Không đọc được file Sub (${ref.label})"
                _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
                return@launch
            }

            sourceCues = loadedCues
            preloadBilingualReferenceTrackAsync(ref)
            val sampleText = loadedCues.take(12).joinToString(" ") { it.text }
            val alreadyThai = ref.isThai || SubtitleTranslator.containsThaiScript(sampleText)
            secondaryCues = resolveInitialSecondaryCues(ref, loadedCues, config)

            if (alreadyThai || !config.autoTranslateToThai) {
                primaryThaiCues = loadedCues
                isTranslatingFlag = false
                currentBadge = buildBadgeText(
                    isNativeThai = alreadyThai,
                    cueCount = loadedCues.size,
                    config = config
                )
                refreshOverlayAt(currentPositionMs, config)
            } else {
                primaryThaiCues = loadedCues
                currentBadge = "Sub: ไทย (Đang AI dịch ${loadedCues.size} câu...)"
                refreshOverlayAt(currentPositionMs, config)

                translateCuesProgressively(
                    inputCues = loadedCues,
                    targetLang = "th",
                    config = config,
                    geminiApiKey = geminiApiKey,
                    isPrimary = true
                )
            }
        }
    }

    fun reapplyConfig(config: SubtitleStyleConfig, geminiApiKey: String) {
        if (sourceCues.isNotEmpty()) {
            scope.launch {
                val activeId = _selectedTrackId.value
                val activeRef = _availableTracks.value.firstOrNull { it.id == activeId }?.externalRef
                if (activeRef != null) {
                    secondaryCues = resolveInitialSecondaryCues(activeRef, sourceCues, config)
                    val sample = primaryThaiCues.take(10).joinToString(" ") { it.text }
                    val primaryAlreadyThai = SubtitleTranslator.containsThaiScript(sample)
                    if (config.autoTranslateToThai && !primaryAlreadyThai) {
                        selectTrack(
                            _availableTracks.value.first { it.id == activeId },
                            config,
                            geminiApiKey,
                            null
                        )
                    } else {
                        currentBadge = buildBadgeText(
                            isNativeThai = activeRef.isThai,
                            cueCount = primaryThaiCues.size,
                            config = config
                        )
                        refreshOverlayAt(currentPositionMs, config)
                    }
                }
            }
        } else {
            refreshOverlayAt(currentPositionMs, config)
        }
    }

    private suspend fun resolveInitialSecondaryCues(
        primaryRef: ExternalSubtitleRef,
        primaryLoadedCues: List<SubtitleCue>,
        config: SubtitleStyleConfig
    ): List<SubtitleCue> {
        if (!config.dualSubEnabled || config.secondaryLang == SecondarySubLang.OFF) {
            return emptyList()
        }
        if (config.secondaryLang == SecondarySubLang.ORIGINAL) {
            return if (primaryRef.isThai) emptyList() else primaryLoadedCues
        }
        val targetCode = config.secondaryLang.langCode
        val matchingNativeRef = externalRefs.firstOrNull {
            it.id != primaryRef.id && it.languageCode.lowercase().startsWith(targetCode)
        }
        if (matchingNativeRef != null) {
            val nativeSecondary = loadExternalCues(matchingNativeRef)
            if (nativeSecondary.isNotEmpty()) return nativeSecondary
        }
        if (!primaryRef.isThai) {
            return primaryLoadedCues
        }
        return emptyList()
    }

    private suspend fun translateCuesProgressively(
        inputCues: List<SubtitleCue>,
        targetLang: String,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        isPrimary: Boolean
    ) {
        val batchSize = 32
        val batches = inputCues.chunked(batchSize)
        if (batches.isEmpty()) return

        val workingMutable = inputCues.toMutableList()
        val cueIndexMap = HashMap<Int, Int>(inputCues.size)
        inputCues.forEachIndexed { idx, cue -> cueIndexMap[cue.id] = idx }

        val anchorMs = (currentPositionMs - 5_000L).coerceAtLeast(0L)
        val startBatchIdx = batches.indexOfFirst { batch ->
            batch.last().endMs >= anchorMs
        }.coerceAtLeast(0)

        val orderedBatchIndices = (startBatchIdx until batches.size).toList() +
            (0 until startBatchIdx).toList()

        // 1. Immediate single-batch sprint for the viewer's active scene (~1.5s)
        val firstBatchIdx = orderedBatchIndices.first()
        val firstTranslated = translator.translateCueBatch(
            cues = batches[firstBatchIdx],
            targetLang = targetLang,
            mode = config.translationEngine,
            geminiApiKey = geminiApiKey,
            geminiModelId = config.geminiModelId
        )
        for (tc in firstTranslated) {
            cueIndexMap[tc.id]?.let { pos -> workingMutable[pos] = tc }
        }
        val restIndices = orderedBatchIndices.drop(1)
        if (isPrimary) {
            primaryThaiCues = workingMutable.toList()
            isTranslatingFlag = restIndices.isNotEmpty()
            currentBadge = if (isTranslatingFlag) {
                "Sub: ไทย AI (${inputCues.size} câu • Dịch song song...)"
            } else {
                buildBadgeText(isNativeThai = false, cueCount = inputCues.size, config = config)
            }
        } else {
            secondaryCues = workingMutable.toList()
        }
        refreshOverlayAt(currentPositionMs, config)

        // 2. 3-way parallel waves for all remaining batches!
        var doneCount = 1
        for (wave in restIndices.chunked(3)) {
            if (!scope.isActive) return
            val waveResults = coroutineScope {
                wave.map { bIdx ->
                    async(Dispatchers.IO) {
                        translator.translateCueBatch(
                            cues = batches[bIdx],
                            targetLang = targetLang,
                            mode = config.translationEngine,
                            geminiApiKey = geminiApiKey,
                            geminiModelId = config.geminiModelId
                        )
                    }
                }.awaitAll()
            }
            for (translatedBatch in waveResults) {
                for (tc in translatedBatch) {
                    cueIndexMap[tc.id]?.let { pos -> workingMutable[pos] = tc }
                }
            }
            doneCount += wave.size
            val snapshot = workingMutable.toList()
            if (isPrimary) {
                primaryThaiCues = snapshot
                isTranslatingFlag = doneCount < orderedBatchIndices.size
                currentBadge = if (isTranslatingFlag) {
                    val pct = ((doneCount * 100) / orderedBatchIndices.size).coerceAtMost(99)
                    "Sub: ไทย AI (${inputCues.size} câu • $pct%)"
                } else {
                    buildBadgeText(isNativeThai = false, cueCount = inputCues.size, config = config)
                }
            } else {
                secondaryCues = snapshot
            }
            refreshOverlayAt(currentPositionMs, config)
        }
        isTranslatingFlag = false
        refreshOverlayAt(currentPositionMs, config)
    }

    fun onEmbeddedPlayerCues(
        rawCueText: String?,
        config: SubtitleStyleConfig,
        geminiApiKey: String
    ) {
        val activeTrack = _availableTracks.value.firstOrNull { it.id == _selectedTrackId.value }
        if (activeTrack == null || !activeTrack.isEmbedded) return

        val cleaned = rawCueText?.trim()?.takeIf { it.isNotEmpty() }
        liveEmbeddedRawText = cleaned
        if (cleaned == null) {
            liveEmbeddedTranslatedThai = null
            liveEmbeddedSecondary = null
            refreshOverlayAt(currentPositionMs, config)
            return
        }

        val isAlreadyThai = activeTrack.isThai || SubtitleTranslator.containsThaiScript(cleaned)
        if (isAlreadyThai || !config.autoTranslateToThai) {
            liveEmbeddedTranslatedThai = cleaned
            liveEmbeddedSecondary = null
            refreshOverlayAt(currentPositionMs, config)
            return
        }

        liveEmbeddedSecondary = if (config.dualSubEnabled && config.secondaryLang != SecondarySubLang.OFF) {
            cleaned
        } else {
            null
        }
        liveCueJob?.cancel()
        liveCueJob = scope.launch {
            val thai = translator.translateSingleText(
                text = cleaned,
                targetLang = "th",
                mode = config.translationEngine,
                geminiApiKey = geminiApiKey,
                geminiModelId = config.geminiModelId
            )
            liveEmbeddedTranslatedThai = thai
            refreshOverlayAt(currentPositionMs, config)
        }
    }

    fun onPlaybackPosition(positionMs: Long, config: SubtitleStyleConfig) {
        currentPositionMs = positionMs
        refreshOverlayAt(positionMs, config)
    }

    private fun refreshOverlayAt(positionMs: Long, config: SubtitleStyleConfig) {
        if (!config.subtitlesEnabled) {
            _renderState.value = ActiveSubtitleRenderState(
                primaryThaiText = null,
                secondaryText = null,
                statusBadge = "Sub: Đang tắt",
                isTranslating = false,
                totalCuesLoaded = primaryThaiCues.size
            )
            return
        }

        val activeTrack = _availableTracks.value.firstOrNull { it.id == _selectedTrackId.value }
        if (activeTrack?.isEmbedded == true) {
            val primary = liveEmbeddedTranslatedThai ?: liveEmbeddedRawText
            val secondary = if (config.dualSubEnabled && config.secondaryLang != SecondarySubLang.OFF) {
                liveEmbeddedSecondary?.takeIf { it != primary }
            } else {
                null
            }
            _renderState.value = ActiveSubtitleRenderState(
                primaryThaiText = primary,
                secondaryText = secondary,
                statusBadge = currentBadge,
                isTranslating = isTranslatingFlag
            )
            return
        }

        val cues = primaryThaiCues
        val effectiveMs = (positionMs * config.subSpeedScale.toDouble()).toLong() - config.syncOffsetMs
        var primary = SubtitleParser.findActiveText(cues, effectiveMs)
        val nextCueMs = findNextCueStartMs(cues, effectiveMs)

        // Show visual confirmation banner during opening 5 seconds if dialogue starts later in intro!
        if (primary == null && cues.isNotEmpty() && positionMs in 100L..5_500L && nextCueMs > 5_500L) {
            val mins = (nextCueMs / 60000L)
            val secs = (nextCueMs / 1000L) % 60L
            primary = "🎬 Đã nạp sẵn ${cues.size} câu Sub ไทย • Thoại bắt đầu lúc %02d:%02d".format(mins, secs)
        } else if (primary == null && cues.isEmpty() && positionMs in 100L..6_000L &&
            _selectedTrackId.value?.startsWith("ai_audio_") == true
        ) {
            primary = "🎙️ Gemini AI đang bật (Đoạn dạo đầu chưa có lời thoại • Đang dịch tiếp các phút sau...)"
        }

        val secondary = if (config.dualSubEnabled && config.secondaryLang != SecondarySubLang.OFF) {
            SubtitleParser.findActiveText(secondaryCues, effectiveMs)?.takeIf { it != primary }
        } else {
            null
        }

        _renderState.value = ActiveSubtitleRenderState(
            primaryThaiText = primary,
            secondaryText = secondary,
            statusBadge = currentBadge,
            isTranslating = isTranslatingFlag,
            totalCuesLoaded = cues.size,
            nextCueStartMs = nextCueMs
        )
    }

    private fun findNextCueStartMs(cues: List<SubtitleCue>, effectiveMs: Long): Long {
        if (cues.isEmpty()) return -1L
        var low = 0
        var high = cues.lastIndex
        var ans = -1L
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs > effectiveMs) {
                ans = cues[mid].startMs
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return ans
    }

    private fun buildBadgeText(
        isNativeThai: Boolean,
        cueCount: Int,
        config: SubtitleStyleConfig,
        platform: String = ""
    ): String {
        val platTag = if (platform.isNotBlank()) "$platform • " else ""
        val mainPart = if (isNativeThai) {
            "Sub: ไทย Gốc ($platTag$cueCount câu)"
        } else {
            "Sub: ไทย AI ($platTag$cueCount câu)"
        }
        return if (config.dualSubEnabled && config.secondaryLang != SecondarySubLang.OFF) {
            "$mainPart + ${config.secondaryLang.displayName}"
        } else {
            mainPart
        }
    }

    private suspend fun loadExternalCues(ref: ExternalSubtitleRef): List<SubtitleCue> =
        withContext(Dispatchers.IO) {
            parsedExternalCuesCache[ref.id]?.let { return@withContext it }

            val parsed = runCatching {
                if (ref.isRawContent) {
                    SubtitleParser.parse(ref.urlOrContent)
                } else if (ref.urlOrContent.startsWith("http://", ignoreCase = true) ||
                    ref.urlOrContent.startsWith("https://", ignoreCase = true)
                ) {
                    val urlLower = ref.urlOrContent.lowercase()
                    val userAgent = when {
                        urlLower.contains("opensubtitles.org") -> "TemporaryUserAgent"
                        urlLower.contains("youtube.com") ->
                            "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
                        else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 PhyllFilmHub/2.1"
                    }
                    val req = Request.Builder()
                        .url(ref.urlOrContent)
                        .header("User-Agent", userAgent)
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) emptyList()
                        else {
                            val bytes = resp.body?.bytes() ?: ByteArray(0)
                            SubtitleParser.parseBytes(bytes)
                        }
                    }
                } else {
                    val f = File(ref.urlOrContent)
                    if (f.exists()) SubtitleParser.parseBytes(f.readBytes()) else emptyList()
                }
            }.getOrDefault(emptyList())

            if (parsed.isNotEmpty()) {
                parsedExternalCuesCache[ref.id] = parsed
            }
            parsed
        }

    private fun maybeLaunchSilentStartupAutoSync(
        ref: ExternalSubtitleRef,
        config: SubtitleStyleConfig,
        geminiApiKey: String
    ) {
        if (!config.autoSyncDownloadedSub) return
        // YouTube's built-in timedtext tracks are already frame-locked to the YouTube video
        if (ref.sourcePlatform.startsWith("YouTube", ignoreCase = true)) return
        val cb = autoSyncCallback ?: return
        autoSyncCurrentSubtitle(
            aroundPositionMs = currentPositionMs,
            config = config,
            geminiApiKey = geminiApiKey,
            isSilentBackgroundCheck = true,
            onApplyDetectedSync = cb
        )
    }

    /**
     * Two-Anchor Multimodal Subtitle Synchronization + Automatic Wrong-Release Failover:
     * 1. Extracts two ~38s audio clips concurrently to solve BOTH `syncOffsetMs` (±4 minutes)
     *    and `subSpeedScale` (`23.976 <-> 25 fps` linear drift) in ~3-4 seconds.
     * 2. If actors clearly speak yet ZERO `.SRT` cues match (`mismatchConfirmed == true`),
     *    automatically switches to the next `.SRT` release or falls back to Gemini Rolling Audio Subtitles!
     */
    fun autoSyncCurrentSubtitle(
        episode: Episode? = activeEpisodeRef,
        aroundPositionMs: Long = currentPositionMs,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        isSilentBackgroundCheck: Boolean = false,
        onApplyDetectedSync: (Long, Float, String, Boolean) -> Unit
    ) {
        if (_selectedTrackId.value?.startsWith("ai_audio_") == true) {
            if (!isSilentBackgroundCheck) {
                _autoSyncFeedback.value = "✨ Sub Gemini Audio tạo trực tiếp từ tiếng phim nên luôn khớp 100%!"
            }
            return
        }
        val ep = episode ?: return
        val activeSource = sourceCues.ifEmpty { primaryThaiCues }
        if (activeSource.size < 4) {
            if (!isSilentBackgroundCheck) {
                _autoSyncFeedback.value = "⚠️ Chưa có file phụ đề rời để tự khớp"
            }
            return
        }

        autoSyncJob?.cancel()
        autoSyncJob = scope.launch {
            _isAutoSyncing.value = true
            if (!isSilentBackgroundCheck) {
                _autoSyncFeedback.value = "🎯 AI đang nghe 2 mốc thoại (đầu & giữa tập) để tự khớp giờ + chống trôi FPS..."
            }

            val res = autoSyncEngine.detectBestSyncOffset(
                episode = ep,
                sourceCues = activeSource,
                aroundPositionMs = aroundPositionMs,
                currentSyncOffsetMs = config.syncOffsetMs,
                subSpeedScale = config.subSpeedScale,
                geminiApiKey = geminiApiKey,
                geminiModelId = config.geminiModelId
            )

            _isAutoSyncing.value = false
            val detected = res.getOrNull()
            if (detected != null) {
                if (detected.mismatchConfirmed) {
                    handleConfirmedMismatchedTrack(
                        episode = ep,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        onApplyDetectedSync = onApplyDetectedSync
                    )
                    return@launch
                }

                val deltaOffset = kotlin.math.abs(detected.detectedOffsetMs - config.syncOffsetMs)
                val deltaScale = kotlin.math.abs(detected.detectedSpeedScale - config.subSpeedScale)
                if (isSilentBackgroundCheck && deltaOffset < 650L && deltaScale < 0.0015f) {
                    // Already tightly locked across both offset and FPS scale!
                    return@launch
                }

                val secStr = "%+.1fs".format(detected.detectedOffsetMs / 1000.0)
                val summary = "🎯 Đã tự khớp Sub chuẩn ($secStr • ${detected.methodLabel} • Đã nhớ cho cả bộ)"
                _autoSyncFeedback.value = summary
                onApplyDetectedSync(
                    detected.detectedOffsetMs,
                    detected.detectedSpeedScale,
                    summary,
                    false
                )
            } else if (!isSilentBackgroundCheck) {
                val errMsg = res.exceptionOrNull()?.message ?: "Chưa bắt được câu thoại rõ ràng ở đoạn này"
                _autoSyncFeedback.value = "⚠️ $errMsg"
            }
        }
    }

    private suspend fun handleConfirmedMismatchedTrack(
        episode: Episode,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onApplyDetectedSync: (Long, Float, String, Boolean) -> Unit
    ) {
        _selectedTrackId.value?.let { rejectedTrackIdsForEpisode.add(it) }

        // Try up to 2 alternative external .SRT releases before switching directly to Gemini Audio Subtitles
        if (rejectedTrackIdsForEpisode.size <= 2) {
            val nextCandidate = orderExternalCandidatesThaiFirst(_availableTracks.value)
                .firstOrNull { it.id !in rejectedTrackIdsForEpisode }
            val nextRef = nextCandidate?.externalRef
            if (nextCandidate != null && nextRef != null) {
                val nextCues = loadExternalCues(nextRef)
                if (nextCues.size >= 5) {
                    _autoSyncFeedback.value = "🔄 Bản Sub đầu lệch bản cắt -> Đang tự đổi qua ${nextRef.label} & khớp lại..."
                    onApplyDetectedSync(0L, 1.0f, _autoSyncFeedback.value.orEmpty(), false)
                    selectTrack(
                        track = nextCandidate,
                        config = config.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        onSelectEmbeddedTrack = null
                    )
                    delay(1_200L)
                    autoSyncCurrentSubtitle(
                        episode = episode,
                        aroundPositionMs = currentPositionMs,
                        config = config.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        isSilentBackgroundCheck = false,
                        onApplyDetectedSync = onApplyDetectedSync
                    )
                    return
                }
            }
        }

        // All downloaded .SRT releases are wrong/mismatched for this video -> switch directly to Gemini Rolling Audio!
        val movie = activeMovieRef
        if (movie != null && geminiApiKey.isNotBlank()) {
            val msg = "🎙️ Các bản Sub rời trên mạng không khớp bản cắt này -> Đã tự chuyển sang Gemini Nghe Phim Tạo Sub ไทย (Áp dụng cả bộ)!"
            _autoSyncFeedback.value = msg
            onApplyDetectedSync(0L, 1.0f, msg, true)
            startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = currentPositionMs,
                config = config.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                geminiApiKey = geminiApiKey,
                onReadyToPlay = null
            )
        } else {
            _autoSyncFeedback.value = "⚠️ Bản Sub tải về khác bản cắt của phim! Hãy bấm '🔄 Đổi Bản Sub' hoặc quét QR nạp Gemini Key để AI tự nghe phim tạo Sub."
        }
    }

    fun getActiveExternalPlatformName(): String {
        val activeId = _selectedTrackId.value ?: return ""
        return _availableTracks.value
            .firstOrNull { it.id == activeId }
            ?.externalRef
            ?.sourcePlatform
            .orEmpty()
    }

    /**
     * Cycles immediately to the next downloaded external .SRT release (e.g. BluRay -> WEB-DL)
     * in 1 click when the first release comes from a different movie cut.
     */
    fun cycleToNextExternalTrack(
        config: SubtitleStyleConfig,
        geminiApiKey: String
    ): SubtitleTrackOption? {
        val extTracks = _availableTracks.value.filter { !it.isEmbedded && it.externalRef != null }
        if (extTracks.size < 2) return null
        val curIdx = extTracks.indexOfFirst { it.id == _selectedTrackId.value }
        val nextTrack = extTracks[(curIdx + 1) % extTracks.size]
        selectTrack(nextTrack, config, geminiApiKey, onSelectEmbeddedTrack = null)
        return nextTrack
    }

    /**
     * Returns up to [limit] bilingual dialogue cues across ±4 minutes around [aroundPositionMs],
     * pairing each Thai line with its Vietnamese / English reference text so the viewer can
     * identify the exact sentence just spoken and snap sync in 1 click!
     */
    fun getBilingualCuesForVisualSnap(
        aroundPositionMs: Long,
        config: SubtitleStyleConfig,
        limit: Int = 28
    ): List<SnapCueCandidate> {
        val thaiList = primaryThaiCues.ifEmpty { sourceCues }
        if (thaiList.isEmpty()) return emptyList()

        val srcMap = sourceCues.associateBy { it.id }
        val refPool = bilingualReferenceCues

        val effectiveCenter = (aroundPositionMs * config.subSpeedScale.toDouble()).toLong() - config.syncOffsetMs
        val substantive = thaiList.filter { SubtitleAutoSyncEngine.isSubstantiveDialogueCue(it) }
            .ifEmpty { thaiList }

        val windowCues = substantive.filter {
            kotlin.math.abs(it.startMs - effectiveCenter) <= 240_000L
        }.ifEmpty { substantive }

        val nearestId = windowCues.minByOrNull { kotlin.math.abs(it.startMs - effectiveCenter) }?.id ?: -1

        return windowCues
            .sortedBy { kotlin.math.abs(it.startMs - effectiveCenter) }
            .take(limit)
            .sortedBy { it.startMs }
            .map { cue ->
                val cleanThai = cue.text.replace('\n', ' ').trim()
                val directSrc = srcMap[cue.id]?.text?.replace('\n', ' ')?.trim().orEmpty()
                val refText = when {
                    directSrc.isNotEmpty() &&
                        !directSrc.equals(cleanThai, ignoreCase = true) &&
                        !SubtitleTranslator.containsThaiScript(directSrc) -> directSrc
                    refPool.isNotEmpty() -> {
                        refPool.minByOrNull { kotlin.math.abs(it.startMs - cue.startMs) }
                            ?.takeIf { kotlin.math.abs(it.startMs - cue.startMs) <= 3_800L }
                            ?.text
                            ?.replace('\n', ' ')
                            ?.trim()
                            .orEmpty()
                    }
                    else -> ""
                }
                SnapCueCandidate(
                    id = cue.id,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    thaiText = cleanThai,
                    referenceText = refText,
                    isNearestToAnchor = (cue.id == nearestId)
                )
            }
    }

    fun clearAutoSyncFeedback() {
        _autoSyncFeedback.value = null
    }

    fun release() {
        preFlightJob?.cancel()
        translationJob?.cancel()
        rollingAudioJob?.cancel()
        autoSyncJob?.cancel()
        aiAudioSyntheticTrack = null
        liveCueJob?.cancel()
        scope.cancel()
    }
}
