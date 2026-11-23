package com.phyll.filmhub.subtitle

import com.phyll.filmhub.cloud.CloudSyncClient
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
import com.phyll.filmhub.model.SubtitleLanguageCatalog
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class SavedEpisodeSubtitleBundle(
    val movieId: String,
    val episodeId: String,
    val primaryLangCode: String,
    val secondaryLangCode: String,
    val sourceLangCode: String,
    val sourcePlatform: String,
    val isAiAudio: Boolean,
    val completedAudioSlots: Set<Int>,
    val translatedCueIds: Set<Int>,
    val sourceCues: List<SubtitleCue>,
    val primaryCues: List<SubtitleCue>,
    val secondaryCues: List<SubtitleCue>,
    val isFullyTranslated: Boolean,
    val savedAtMs: Long,
    val translatedRanges: List<Pair<Long, Long>> = emptyList(),
    val audioTranslatedUntilMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

class ThaiSubtitleManager(
    cacheDir: File?,
    persistentSubDir: File? = cacheDir
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistentRoot: File? = persistentSubDir ?: cacheDir
    private val episodeSubCacheDir: File? = persistentRoot?.let { File(it, "episode_sub_cache_v1") }
    private val episodeCacheLock = Any()
    private val translator = SubtitleTranslator(persistentRoot?.let { File(it, "phyll_sub_cache_v2") })
    private val audioSubGenerator = GeminiAudioSubtitleGenerator(
        persistentRoot?.let { File(it, "phyll_gemini_audio_subs_v1") }
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
    private var currentBadge: String = "AI Sub (Đang chuẩn bị...)"
    @Volatile
    private var isTranslatingFlag: Boolean = false
    @Volatile
    private var activePrimaryLangCode: String = "th"
    @Volatile
    private var activeSecondaryLangCode: String = "vi"
    private var secondaryTranslationJob: Job? = null

    @Volatile
    private var aiAudioSyntheticTrack: SubtitleTrackOption? = null
    @Volatile
    private var activeMovieRef: MovieItem? = null
    @Volatile
    private var activeEpisodeRef: Episode? = null
    @Volatile
    private var activeSourceLangCode: String = "auto"
    @Volatile
    private var activeSourcePlatform: String = ""
    @Volatile
    private var activeTranslatedCueIds: Set<Int> = emptySet()
    @Volatile
    private var activeIsFullyTranslated: Boolean = false
    @Volatile
    private var activeIsAiAudio: Boolean = false
    @Volatile
    private var activeCompletedAudioSlots: Set<Int> = emptySet()
    @Volatile
    private var activeTranslatedRanges: List<Pair<Long, Long>> = emptyList()
    @Volatile
    private var activeTotalDurationMs: Long = 0L
    @Volatile
    private var bilingualReferenceCues: List<SubtitleCue> = emptyList()
    private val rejectedTrackIdsForEpisode = ConcurrentHashMap.newKeySet<String>()
    private var autoSyncCallback: ((Long, Float, String, Boolean) -> Unit)? = null

    @Volatile
    private var lastCloudSubtitleUploadMs: Long = 0L

    private fun hasAiAccess(geminiApiKey: String): Boolean =
        geminiApiKey.isNotBlank() || CloudSyncClient.canUseServerAi()

    private var preFlightJob: Job? = null
    private var translationJob: Job? = null
    private var liveCueJob: Job? = null
    private var rollingAudioJob: Job? = null
    private var autoSyncJob: Job? = null

    /**
     * PRE-FLIGHT SUBTITLE GUARANTEE (v3.1.5 Persistent Sub Cache + Series-Smart):
     * 0. Checks persistent episode subtitle storage FIRST — if this episode was already translated
     *    in the requested language, restores instantly (<30ms) with ZERO re-download / ZERO re-translation!
     * 1. If this series was locked to `preferGeminiAudioSub`, jumps straight to Gemini Rolling Audio
     *    Subtitle Generation (resuming only uncompleted slots if partially saved).
     * 2. Otherwise searches OpenSubtitles.org, YIFY, OS-v3 & YouTube IN PARALLEL with stream-timeline
     *    inspection, rejecting wrong-length / wrong-movie .SRT candidates automatically.
     * 3. Saves translated subtitles progressively to permanent app storage after every batch/chunk.
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
        secondaryTranslationJob?.cancel()
        liveCueJob?.cancel()
        rollingAudioJob?.cancel()
        autoSyncJob?.cancel()
        _isAutoSyncing.value = false
        _autoSyncFeedback.value = null
        aiAudioSyntheticTrack = null
        activeMovieRef = movie
        activeEpisodeRef = episode
        activeSourceLangCode = "auto"
        activeSourcePlatform = ""
        activeTranslatedCueIds = emptySet()
        activeIsFullyTranslated = false
        activeIsAiAudio = false
        activeCompletedAudioSlots = emptySet()
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

        val targetLang = config.resolvedPrimaryLanguage()
        activePrimaryLangCode = targetLang.code
        activeSecondaryLangCode = config.resolvedSecondaryLangCode()
        val searchDisplay = movie.originalTitle.ifBlank { movie.title }

        preFlightJob = scope.launch {
            // STEP 0A: INSTANT RESTORE FROM PERSISTENT SUBTITLE STORAGE (TRANSLATE ONCE, LOAD INSTANTLY FOREVER!)
            if (movie.contentType != ContentType.LIVE_CHANNEL) {
                var cached = withContext(Dispatchers.IO) {
                    loadEpisodeSubCache(movie, episode, targetLang.code)
                }
                // STEP 0B: SHARED NKI CLOUD SUBTITLE POOL ON VERCEL BLOB (<150ms, 0 GEMINI TOKENS!)
                // Check Cloud whenever local cache is missing OR not yet 100% complete (so Web's longer progress merges seamlessly!)
                if ((cached == null || !cached.isFullyTranslated) && CloudSyncClient.isSignedIn()) {
                    if (cached == null) {
                        _preFlightState.value = PreFlightSubtitleState(
                            isPreparing = true,
                            stepTitle = "☁️ Checking NKI Cloud Subtitles (${targetLang.shortBadge})...",
                            stepDetail = "$searchDisplay • ${episode.name}",
                            progressFraction = 0.08f,
                            searchedQuery = searchDisplay
                        )
                    }
                    val cloudHit = CloudSyncClient.fetchCloudSubtitle(
                        rawMovieSlug = movie.detailLookupId ?: movie.id,
                        rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                        langCode = targetLang.code
                    )
                    if (cloudHit != null && cloudHit.primaryCues.size >= 3 &&
                        !CloudSyncClient.hasWrongScriptForLang(cloudHit.primaryCues, targetLang.code)
                    ) {
                        val hydrated = SavedEpisodeSubtitleBundle(
                            movieId = movie.id,
                            episodeId = episode.id,
                            primaryLangCode = cloudHit.primaryLangCode,
                            secondaryLangCode = cloudHit.secondaryLangCode,
                            sourceLangCode = cloudHit.sourceLangCode,
                            sourcePlatform = "☁️ ${cloudHit.sourcePlatform.ifBlank { "NKI Cloud" }}",
                            isAiAudio = cloudHit.isAiAudio,
                            completedAudioSlots = cloudHit.completedAudioSlots,
                            translatedCueIds = cloudHit.primaryCues.mapTo(HashSet()) { it.id },
                            sourceCues = cloudHit.sourceCues,
                            primaryCues = cloudHit.primaryCues,
                            secondaryCues = cloudHit.secondaryCues,
                            isFullyTranslated = cloudHit.isFullyTranslated,
                            savedAtMs = cloudHit.savedAtMs,
                            translatedRanges = cloudHit.translatedRanges,
                            audioTranslatedUntilMs = cloudHit.audioTranslatedUntilMs,
                            totalDurationMs = cloudHit.totalDurationMs
                        )
                        val merged = if (cached != null) mergeSubtitleBundlesMonotonic(cached, hydrated) else hydrated
                        withContext(Dispatchers.IO) {
                            saveEpisodeSubCache(movie, episode, merged, uploadToCloud = false)
                        }
                        cached = merged
                    }
                }
                if (cached != null && cached.primaryCues.size >= 3) {
                    restoreFromSavedBundle(
                        cached = cached,
                        movie = movie,
                        episode = episode,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        startPositionMs = startPositionMs,
                        onReadyToPlay = onReadyToPlay
                    )
                    return@launch
                }
            }

            // Fast-path for 100-episode series where viewer (or Ep 1 auto-failover) locked Gemini Audio mode!
            if (seriesProfile?.preferGeminiAudioSub == true &&
                hasAiAccess(geminiApiKey) &&
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
                return@launch
            }

            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = true,
                stepTitle = "🔎 Bước 1/2: Đang tìm phụ đề (${targetLang.displayName}) & khớp độ dài...",
                stepDetail = "$searchDisplay • ${episode.name}",
                progressFraction = 0.15f,
                searchedQuery = searchDisplay
            )

            // 1. Discover real subtitles AND inspect stream duration IN PARALLEL (with speculative opening audio prefetch!)
            var realStreamDurationMs = 0L
            var speculativePrefetchJob: Job? = null
            if (movie.contentType != ContentType.LIVE_CHANNEL) {
                coroutineScope {
                    val subsDef = async {
                        withTimeoutOrNull(5_800L) {
                            runCatching { onlineFetcher.discoverSubtitles(movie, episode, preferredLangCode = targetLang.code) }
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
                        hasAiAccess(geminiApiKey) &&
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

            val orderedExtCandidates = orderExternalCandidatesForTarget(
                tracks = combined,
                targetLangCode = targetLang.code,
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

            // STAGE 1: Try Native Human tracks matching target language across all platforms FIRST!
            val nativeHumanTargetTracks = orderedExtCandidates.filter {
                SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLang.code) &&
                    it.externalRef?.isAutoGenerated != true
            }
            val aiFallbackTracks = orderedExtCandidates.filterNot {
                SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLang.code) &&
                    it.externalRef?.isAutoGenerated != true
            }

            var chosenTrack: SubtitleTrackOption? = null
            var loadedCues: List<SubtitleCue> = emptyList()

            for ((idx, candidate) in nativeHumanTargetTracks.withIndex()) {
                val ref = candidate.externalRef ?: continue
                _preFlightState.value = _preFlightState.value.copy(
                    stepTitle = "✨ Đã thấy phụ đề ${targetLang.displayName} gốc trên ${ref.sourcePlatform}!",
                    stepDetail = "${ref.label} (#${idx + 1}/${nativeHumanTargetTracks.size})",
                    progressFraction = 0.42f
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

            // STAGE 2: If no native track exists for target language -> load best human track for AI Translation!
            if (chosenTrack == null) {
                for ((idx, candidate) in aiFallbackTracks.withIndex()) {
                    val ref = candidate.externalRef ?: continue
                    _preFlightState.value = _preFlightState.value.copy(
                        stepTitle = "🤖 Bước 2/2: Nạp bản ${ref.sourcePlatform} để AI dịch sang ${targetLang.displayName}...",
                        stepDetail = "${ref.label} (#${idx + 1}/${aiFallbackTracks.size})",
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

        val targetLang = config.resolvedPrimaryLanguage()
        val cleanModel = config.geminiModelId.ifBlank { "gemini-3-flash-preview" }
        val aiReady = hasAiAccess(geminiApiKey)
        if (config.autoGenerateAudioSubWhenMissing && aiReady) {
            startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = startPositionMs,
                config = config,
                geminiApiKey = geminiApiKey,
                onReadyToPlay = onReadyToPlay
            )
        } else if (config.autoGenerateAudioSubWhenMissing && !aiReady) {
            currentBadge = "Chưa có Sub rời — Nạp Gemini Key để AI tự nghe phim"
            _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                noSubtitleFound = true,
                needsGeminiKeyQr = true,
                searchedQuery = searchDisplay,
                stepTitle = "🔐 Chưa có Sub rời — Nạp Gemini Key để AI tự nghe phim tạo Sub ${targetLang.shortBadge}!",
                stepDetail = "Hỗ trợ dịch mọi ngôn ngữ (${targetLang.displayName} • Model: $cleanModel). Hoặc bấm Phát ngay!"
            )
        } else {
            currentBadge = "Chưa tìm thấy Sub rời trên các nền tảng"
            _renderState.value = ActiveSubtitleRenderState(statusBadge = currentBadge)
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = false,
                noSubtitleFound = true,
                needsGeminiKeyQr = false,
                searchedQuery = searchDisplay,
                stepTitle = "⚠️ Chưa tìm thấy Sub trên kho cho \"$searchDisplay\"",
                stepDetail = "Bạn có thể bật Gemini Audio tạo Sub ${targetLang.displayName}, tìm bằng tên tiếng Anh khác, nạp .SRT hoặc Phát luôn."
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
        preloadedSlots: Set<Int> = emptySet(),
        preloadedCues: List<SubtitleCue> = emptyList(),
        preloadedRanges: List<Pair<Long, Long>> = emptyList(),
        onReadyToPlay: (() -> Unit)? = null
    ) {
        preFlightJob?.cancel()
        translationJob?.cancel()
        liveCueJob?.cancel()
        rollingAudioJob?.cancel()
        aiAudioSyntheticTrack = null

        val cleanKey = geminiApiKey.trim()
        val cleanModel = config.geminiModelId.trim().removePrefix("models/").ifBlank { "gemini-3-flash-preview" }
        val targetLang = config.resolvedPrimaryLanguage()
        activePrimaryLangCode = targetLang.code
        activeIsAiAudio = true
        activeSourceLangCode = targetLang.code
        activeSourcePlatform = "Gemini Audio"
        val searchDisplay = movie.originalTitle.ifBlank { movie.title }

        if (!hasAiAccess(cleanKey)) {
            currentBadge = "🔐 Cần nạp Gemini API Key trước"
            _renderState.value = _renderState.value.copy(statusBadge = currentBadge, isTranslating = false)
            if (onReadyToPlay != null) {
                _preFlightState.value = PreFlightSubtitleState(
                    isPreparing = false,
                    noSubtitleFound = true,
                    needsGeminiKeyQr = true,
                    searchedQuery = searchDisplay,
                    stepTitle = "🔐 Chưa có Gemini Key — Nạp Key để AI tự nghe phim tạo Sub ${targetLang.shortBadge}!",
                    stepDetail = "Chọn Model ($cleanModel) để Gemini nghe trực tiếp âm thanh và dịch sang ${targetLang.displayName}."
                )
            }
            return
        }

        val startMs = initialPositionMs.coerceAtLeast(0L)
        currentPositionMs = startMs
        isTranslatingFlag = true
        if (preloadedCues.isEmpty()) {
            secondaryCues = emptyList()
        }

        if (onReadyToPlay != null) {
            _preFlightState.value = PreFlightSubtitleState(
                isPreparing = true,
                stepTitle = "🎙️ Kết nối Gemini ($cleanModel) nghe phim & tạo Sub ${targetLang.displayName}...",
                stepDetail = "Bước 1/2: Đang tách luồng Audio siêu nhẹ từ ${movie.title} (${episode.name})...",
                progressFraction = 0.36f,
                searchedQuery = searchDisplay
            )
        } else {
            currentBadge = if (preloadedCues.isNotEmpty()) {
                "💾 Sub ${targetLang.shortBadge} • Đã lưu (${preloadedCues.size} câu)"
            } else {
                "🎙️ Gemini ($cleanModel) đang tạo Sub ${targetLang.shortBadge}..."
            }
            refreshOverlayAt(currentPositionMs, config)
        }

        rollingAudioJob = scope.launch {
            val timeline = audioSubGenerator.inspectStreamTimeline(episode)
            if (timeline == null) {
                isTranslatingFlag = false
                if (preloadedCues.isEmpty()) {
                    currentBadge = "⚠️ Luồng phim này không hỗ trợ tách Audio tạo Sub AI"
                    refreshOverlayAt(currentPositionMs, config)
                }
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
            activeTotalDurationMs = totalDurationMs
            val totalSlots = ((totalDurationMs + slotSizeMs - 1L) / slotSizeMs).toInt().coerceIn(1, 480)
            // Reconstruct completed 60s slots from BOTH saved ranges (Web 38s -> Android 60s!), legacy slots, AND cue timestamps!
            val completedSlots = HashSet<Int>(
                reconstructCompletedSlots(
                    preloadedSlots = preloadedSlots,
                    preloadedRanges = preloadedRanges,
                    preloadedCues = preloadedCues,
                    totalSlots = totalSlots,
                    slotSizeMs = slotSizeMs
                )
            )
            activeTranslatedRanges = CloudSyncClient.mergeTimeRanges(
                preloadedRanges + completedSlots.map { idx -> (idx * slotSizeMs) to ((idx + 1) * slotSizeMs).coerceAtMost(totalDurationMs) }
            )
            val failedSlotCounts = HashMap<Int, Int>()
            val inFlightJobs = HashMap<Int, Job>()
            val prefetchedSlots = HashSet<Int>()
            val accumulatedCuesBySlot = ConcurrentHashMap<Int, List<SubtitleCue>>()
            if (preloadedCues.isNotEmpty()) {
                val grouped = preloadedCues.groupBy { cue ->
                    (cue.startMs.coerceAtLeast(0L) / slotSizeMs).toInt().coerceIn(0, totalSlots - 1)
                }
                for ((slotIdx, slotList) in grouped) {
                    accumulatedCuesBySlot[slotIdx] = slotList
                }
            }
            val cacheNamespace = "${movie.id}_${episode.id}"
                .replace(Regex("[^A-Za-z0-9_]"), "_")
                .takeLast(48)

            val synthTrack = SubtitleTrackOption(
                id = "ai_audio_${episode.id}",
                label = "⚡ Gemini Audio -> ${targetLang.shortBadge} ($cleanModel)",
                languageCode = targetLang.code,
                isEmbedded = false,
                isThai = targetLang.code == "th"
            )
            aiAudioSyntheticTrack = synthTrack
            _availableTracks.value = buildCombinedTracks()
            _selectedTrackId.value = synthTrack.id

            fun rebuildMergedAiCues(persistToDisk: Boolean = true) {
                val merged = accumulatedCuesBySlot.entries
                    .sortedBy { it.key }
                    .flatMap { it.value }
                    .sortedBy { it.startMs }
                    .mapIndexed { idx, cue -> cue.copy(id = idx + 1) }
                sourceCues = merged
                primaryThaiCues = merged
                activeCompletedAudioSlots = completedSlots.toSet()
                activeTranslatedRanges = CloudSyncClient.mergeTimeRanges(
                    activeTranslatedRanges + completedSlots.map { idx ->
                        (idx * slotSizeMs) to ((idx + 1) * slotSizeMs).coerceAtMost(totalDurationMs)
                    }
                )
                activeIsFullyTranslated = completedSlots.size >= totalSlots
                activeTranslatedCueIds = merged.mapTo(HashSet()) { it.id }
                refreshOverlayAt(currentPositionMs, config)
                if (persistToDisk && merged.size >= 3) {
                    persistActiveEpisodeSubtitleCacheAsync(config)
                }
            }

            // 1. 30-SECOND FAST-BOOT MICRO-SPRINT:
            // Skip completely if initialSlot is ALREADY cached on disk from a previous viewing!
            val initialSlot = (startMs / slotSizeMs).toInt().coerceIn(0, totalSlots - 1)
            if (initialSlot !in completedSlots) {
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
                        stepTitle = "⚡ Gemini Turbo đang tạo nhanh 30s Sub ${targetLang.displayName} ($sprintRangeLabel)...",
                        stepDetail = "Chế độ Siêu Tốc (~3-4 giây là chiếu ngay • Các phút tiếp theo tự dịch song song ngầm)...",
                        progressFraction = 0.76f,
                        searchedQuery = searchDisplay
                    )
                }
                currentBadge = "⚡ Gemini Turbo (${targetLang.shortBadge}) $sprintRangeLabel..."
                refreshOverlayAt(currentPositionMs, config)

                // Speculatively pre-download next minute's audio while Gemini processes the opening 30s!
                if (initialSlot + 1 < totalSlots && (initialSlot + 1) !in completedSlots) {
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
                            preferTurboFlash = true,
                            targetLangCode = targetLang.code
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
                            preferTurboFlash = true,
                            targetLangCode = targetLang.code
                        )
                    }

                    // Wait ONLY for the 30s half where the viewer's playhead sits!
                    firstBlockingResult = if (viewerInSecondHalf) partBDef.await() else partADef.await()
                    val firstHalfChunk = firstBlockingResult.getOrNull()
                    if (firstHalfChunk != null) {
                        accumulatedCuesBySlot[initialSlot] = firstHalfChunk.cues
                        rebuildMergedAiCues(persistToDisk = true)

                        // Complete the companion 30s half asynchronously without blocking video start!
                        val otherDef = if (viewerInSecondHalf) partADef else partBDef
                        inFlightJobs[initialSlot] = launch {
                            val secondHalf = otherDef.await().getOrNull()
                            val combinedSlotCues = (firstHalfChunk.cues + (secondHalf?.cues ?: emptyList()))
                                .sortedBy { it.startMs }
                            accumulatedCuesBySlot[initialSlot] = combinedSlotCues
                            completedSlots.add(initialSlot)
                            inFlightJobs.remove(initialSlot)
                            rebuildMergedAiCues(persistToDisk = true)
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
                        preferTurboFlash = true,
                        targetLangCode = targetLang.code
                    )
                    firstBlockingResult.getOrNull()?.let { chunk ->
                        completedSlots.add(initialSlot)
                        accumulatedCuesBySlot[initialSlot] = chunk.cues
                        rebuildMergedAiCues(persistToDisk = true)
                    }
                }

                if (firstBlockingResult.getOrNull() == null && preloadedCues.isEmpty()) {
                    isTranslatingFlag = false
                    val errMsg = firstBlockingResult.exceptionOrNull()?.message ?: "Không kết nối được Gemini API"
                    val isAuthErr = errMsg.contains("401") ||
                        errMsg.contains("403") ||
                        errMsg.contains("API key not valid", ignoreCase = true) ||
                        errMsg.contains("API_KEY_INVALID", ignoreCase = true)
                    currentBadge = "⚠️ Gemini lỗi: ${errMsg.take(52)}"
                    refreshOverlayAt(currentPositionMs, config)

                    if (onReadyToPlay != null) {
                        _preFlightState.value = PreFlightSubtitleState(
                            isPreparing = false,
                            noSubtitleFound = true,
                            needsGeminiKeyQr = isAuthErr,
                            searchedQuery = searchDisplay,
                            stepTitle = "⚠️ Gemini ($cleanModel) báo lỗi: ${errMsg.take(90)}",
                            stepDetail = if (isAuthErr) {
                                "API Key hiện tại bị từ chối (401/403). Vui lòng kiểm tra lại Gemini API Key hoặc bấm Phát luôn."
                            } else {
                                "Thử đổi Model khác (VD: gemini-2.5-flash) hoặc tìm Sub online theo tên tiếng Anh, hoặc bấm Phát luôn."
                            }
                        )
                    }
                    return@launch
                }
            } else {
                rebuildMergedAiCues(persistToDisk = false)
            }

            val hasMoreSlots = completedSlots.size < totalSlots
            isTranslatingFlag = hasMoreSlots
            currentBadge = if (hasMoreSlots) {
                "Sub ${targetLang.shortBadge} • Gemini (${primaryThaiCues.size} câu)"
            } else {
                "Sub ${targetLang.shortBadge} • Gemini (${primaryThaiCues.size} câu)"
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
                    currentBadge = "Sub ${targetLang.shortBadge} • Gemini $slotRange"
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
                                    preferTurboFlash = true,
                                    targetLangCode = targetLang.code
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
                                    preferTurboFlash = true,
                                    targetLangCode = targetLang.code
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
                                preferTurboFlash = (candidateSlot <= viewerSlot + 1),
                                targetLangCode = targetLang.code
                            )
                            var slotCues = slotRes.getOrNull()?.cues
                            // Auto-recover empty or early-truncated 60s slots by splitting into two 30s halves!
                            val stoppedTooEarly = slotCues != null && slotDurMs >= 42_000L && (
                                slotCues.isEmpty() ||
                                    ((slotCues.lastOrNull()?.endMs ?: 0L) - slotStartMs) < (slotDurMs * 35L / 100L)
                                )
                            if (stoppedTooEarly) {
                                val hA = audioSubGenerator.generateThaiCuesForWindow(
                                    cacheNamespace = cacheNamespace,
                                    timeline = timeline,
                                    windowStartMs = slotStartMs,
                                    windowDurationMs = halfSprintMs,
                                    apiKey = cleanKey,
                                    modelId = cleanModel,
                                    preferTurboFlash = true,
                                    targetLangCode = targetLang.code
                                ).getOrNull()?.cues.orEmpty()
                                val hB = audioSubGenerator.generateThaiCuesForWindow(
                                    cacheNamespace = cacheNamespace,
                                    timeline = timeline,
                                    windowStartMs = slotStartMs + halfSprintMs,
                                    windowDurationMs = (slotDurMs - halfSprintMs).coerceAtLeast(8_000L),
                                    apiKey = cleanKey,
                                    modelId = cleanModel,
                                    preferTurboFlash = true,
                                    targetLangCode = targetLang.code
                                ).getOrNull()?.cues.orEmpty()
                                val combined = mergeCuesChronological(slotCues.orEmpty(), hA + hB)
                                if (combined.isNotEmpty()) slotCues = combined
                            }
                            if (slotCues != null) {
                                completedSlots.add(candidateSlot)
                                accumulatedCuesBySlot[candidateSlot] = slotCues
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
                    currentBadge = "Sub ${targetLang.shortBadge} • Gemini (${primaryThaiCues.size} câu • +${bufferedUntilMin}p)"
                    refreshOverlayAt(currentPositionMs, config)
                }

                delay(400L)
            }

            inFlightJobs.values.forEach { it.join() }
            isTranslatingFlag = false
            currentBadge = "Sub ${targetLang.shortBadge} • Gemini (${primaryThaiCues.size} câu)"
            refreshOverlayAt(currentPositionMs, config)
        }
    }

    /**
     * Surgical Gap Repair (Android & Smart TV):
     * Scans existing local + Cloud cues against 30s HLS windows, skips 100% of already-translated
     * stretches (0 wasted tokens!), and ONLY translates missing holes!
     */
    suspend fun repairMissingAudioSubGaps(
        movie: MovieItem,
        episode: Episode,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        onProgress: (percent: Int, message: String) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val targetLang = config.resolvedPrimaryLanguage()
        val cleanKey = geminiApiKey.trim()
        val cleanModel = config.geminiModelId.trim().ifBlank { "gemini-2.5-flash" }
        if (!hasAiAccess(cleanKey)) {
            onProgress(0, "⚠️ Đăng nhập NKI Cloud hoặc nhập Gemini API Key để dịch bù đoạn thiếu!")
            return@withContext (0 to 0)
        }
        onProgress(5, "🔍 Đang kiểm tra Sub đã lưu & NKI Cloud (${targetLang.shortBadge})...")
        val localBundle = loadEpisodeSubCache(movie, episode, targetLang.code)
        val cloudBundle = if (CloudSyncClient.isSignedIn()) {
            CloudSyncClient.fetchCloudSubtitle(
                rawMovieSlug = movie.detailLookupId ?: movie.id,
                rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                langCode = targetLang.code
            )?.takeIf { !CloudSyncClient.hasWrongScriptForLang(it.primaryCues, targetLang.code) }
        } else null

        var mergedCues = mergeCuesChronological(
            localBundle?.primaryCues.orEmpty(),
            cloudBundle?.primaryCues.orEmpty()
        )
        val beforeCount = mergedCues.size
        val timeline = audioSubGenerator.inspectStreamTimeline(episode)
        if (timeline == null || timeline.totalDurationMs <= 15_000L) {
            onProgress(0, "⚠️ Luồng này chưa hỗ trợ tách HLS tự động — mở phát trực tiếp để dịch.")
            return@withContext (beforeCount to 0)
        }

        val totalMs = timeline.totalDurationMs
        val sliceMs = 30_000L
        val sliceCount = ((totalMs + sliceMs - 1L) / sliceMs).toInt().coerceAtLeast(1)
        val missingSlices = ArrayList<Int>()
        for (i in 0 until sliceCount) {
            val sStart = i * sliceMs
            val sEnd = minOf(totalMs, sStart + sliceMs)
            val inside = mergedCues.filter { it.endMs >= sStart + 400L && it.startMs <= sEnd - 400L }
            if (inside.isEmpty()) {
                missingSlices.add(i)
            } else if (sEnd - sStart >= 22_000L) {
                var cursor = sStart
                var maxHole = 0L
                for (c in inside) {
                    val cs = c.startMs.coerceAtLeast(sStart)
                    val ce = c.endMs.coerceAtMost(sEnd)
                    if (cs - cursor > maxHole) maxHole = cs - cursor
                    if (ce > cursor) cursor = ce
                }
                if (sEnd - cursor > maxHole) maxHole = sEnd - cursor
                if (maxHole >= 19_000L) missingSlices.add(i)
            }
        }

        if (missingSlices.isEmpty()) {
            onProgress(100, "✅ Đã kiểm tra xong — Sub ${targetLang.shortBadge} đầy đủ ($beforeCount câu), không có đoạn trống!")
            return@withContext (beforeCount to 0)
        }

        onProgress(10, "🔧 Phát hiện ${missingSlices.size}/$sliceCount đoạn trống — chỉ dịch bù các đoạn thiếu...")
        val cacheNamespace = "${episode.streamUrl.hashCode().toUInt().toString(16)}_${targetLang.code}_gap"
        val doneRanges = ArrayList<Pair<Long, Long>>()
        localBundle?.translatedRanges?.let { doneRanges.addAll(it) }
        cloudBundle?.translatedRanges?.let { doneRanges.addAll(it) }

        var processed = 0
        for (sliceIdx in missingSlices) {
            val sStart = (sliceIdx * sliceMs - 3_000L).coerceAtLeast(0L)
            val sDur = minOf(sliceMs + 3_000L, totalMs - sStart).coerceAtLeast(6_000L)
            val res = audioSubGenerator.generateThaiCuesForWindow(
                cacheNamespace = cacheNamespace,
                timeline = timeline,
                windowStartMs = sStart,
                windowDurationMs = sDur,
                apiKey = cleanKey,
                modelId = cleanModel,
                preferTurboFlash = true,
                targetLangCode = targetLang.code
            ).getOrNull()
            processed++
            if (res != null && res.cues.isNotEmpty() && !CloudSyncClient.hasWrongScriptForLang(res.cues, targetLang.code)) {
                mergedCues = mergeCuesChronological(mergedCues, res.cues)
            }
            doneRanges.add(sStart to (sStart + sDur))
            val pct = (12 + (processed * 85) / missingSlices.size).coerceIn(12, 97)
            val addedNow = (mergedCues.size - beforeCount).coerceAtLeast(0)
            onProgress(pct, "🔧 Đang dịch bù $processed/${missingSlices.size} (+$addedNow câu mới • Tổng ${mergedCues.size} câu)")
        }

        val netAdded = (mergedCues.size - beforeCount).coerceAtLeast(0)
        if (mergedCues.size >= 3) {
            val finalRanges = CloudSyncClient.mergeTimeRanges(doneRanges + listOf(0L to totalMs))
            val repairedBundle = SavedEpisodeSubtitleBundle(
                movieId = movie.id,
                episodeId = episode.id,
                primaryLangCode = targetLang.code,
                secondaryLangCode = config.resolvedSecondaryLangCode(),
                sourceLangCode = "auto",
                sourcePlatform = "🎙 AI Audio (Đã vá đoạn thiếu)",
                isAiAudio = true,
                completedAudioSlots = (0 until ((totalMs + 59_999L) / 60_000L).toInt()).toSet(),
                translatedCueIds = mergedCues.mapTo(HashSet()) { it.id },
                sourceCues = mergedCues,
                primaryCues = mergedCues,
                secondaryCues = localBundle?.secondaryCues.orEmpty(),
                isFullyTranslated = true,
                savedAtMs = System.currentTimeMillis(),
                translatedRanges = finalRanges,
                audioTranslatedUntilMs = totalMs,
                totalDurationMs = totalMs
            )
            saveEpisodeSubCache(movie, episode, repairedBundle, uploadToCloud = true)
        }
        val msg = if (netAdded > 0) {
            "✅ Đã dịch bù thêm +$netAdded câu vào ${missingSlices.size} đoạn thiếu (Tổng ${mergedCues.size} câu • Đã lưu Cloud)!"
        } else {
            "✅ Đã kiểm tra xong ${missingSlices.size} đoạn trống (nhạc/im lặng • Tổng ${mergedCues.size} câu)!"
        }
        onProgress(100, msg)
        mergedCues.size to netAdded
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
        secondaryTranslationJob?.cancel()
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
            val targetLang = config.resolvedPrimaryLanguage()
            val found = onlineFetcher.discoverByCustomQuery(
                customQuery = trimmed,
                isSeries = isSeries,
                season = season,
                episode = epNum,
                preferredLangCode = targetLang.code
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

            val ordered = orderExternalCandidatesForTarget(combined, targetLang.code)
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
        activeSourceLangCode = ref.languageCode
        activeSourcePlatform = ref.sourcePlatform
        activeIsAiAudio = false
        activeCompletedAudioSlots = emptySet()

        val targetLang = config.resolvedPrimaryLanguage()
        activePrimaryLangCode = targetLang.code
        activeSecondaryLangCode = config.resolvedSecondaryLangCode()

        val sampleText = loadedCues.take(12).joinToString(" ") { it.text }
        val alreadyInTarget = SubtitleLanguageCatalog.isTrackMatchingLanguage(ref.languageCode, targetLang.code) ||
            SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage(sampleText, targetLang.code)
        secondaryCues = resolveInitialSecondaryCues(ref, loadedCues, config, geminiApiKey)

        if (alreadyInTarget || !config.autoTranslateEnabled) {
            primaryThaiCues = loadedCues
            isTranslatingFlag = false
            activeTranslatedCueIds = loadedCues.mapTo(HashSet()) { it.id }
            activeIsFullyTranslated = true
            currentBadge = buildBadgeText(
                isNativeTarget = alreadyInTarget,
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
            persistActiveEpisodeSubtitleCacheAsync(config)
            onReadyToPlay()
            maybeLaunchSilentStartupAutoSync(ref, config, geminiApiKey)
            return
        }

        // Fast-sprint translate ONLY the 24 cues right at currentPositionMs (~1.8s!)
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
            stepTitle = "⚡ AI đang dịch nhanh ${fastBootSlice.size} câu mở đầu sang ${targetLang.displayName}...",
            stepDetail = "Chế độ Siêu Tốc (~2 giây là chiếu ngay • ${loadedCues.size} câu còn lại dịch song song nền)",
            progressFraction = 0.82f
        )

        val translatedOpening = translator.translateCueBatch(
            cues = fastBootSlice,
            targetLang = targetLang.code,
            mode = config.translationEngine,
            geminiApiKey = geminiApiKey,
            geminiModelId = config.geminiModelId
        )
        val translatedIdSet = HashSet<Int>(loadedCues.size)
        for (tc in translatedOpening) {
            cueIndexMap[tc.id]?.let { pos ->
                workingMutable[pos] = tc
                translatedIdSet.add(tc.id)
            }
        }

        // Order remaining cues from playhead forward, then wrap around earlier cues if resumed mid-episode
        val remainingForward = loadedCues.subList(fastBootEndIdx, loadedCues.size)
            .filterNot { it.id in fastBootIds }
        val remainingBackward = loadedCues.subList(0, startCueIdx)
            .filterNot { it.id in fastBootIds }
        val remainingCues = remainingForward + remainingBackward
        val remainingBatches = remainingCues.chunked(36)

        // Commit opening translated cues & release video playback IMMEDIATELY!
        primaryThaiCues = workingMutable.toList()
        val hasMoreBatches = remainingBatches.isNotEmpty()
        isTranslatingFlag = hasMoreBatches
        activeTranslatedCueIds = translatedIdSet.toSet()
        activeIsFullyTranslated = !hasMoreBatches
        currentBadge = if (hasMoreBatches) {
            "Sub ${targetLang.shortBadge} • AI (${loadedCues.size} câu)"
        } else {
            buildBadgeText(isNativeTarget = false, cueCount = loadedCues.size, config = config)
        }

        _preFlightState.value = PreFlightSubtitleState(
            isPreparing = false,
            loadedCueCount = loadedCues.size,
            firstCueTimeMs = loadedCues.first().startMs
        )
        refreshOverlayAt(currentPositionMs, config)
        persistActiveEpisodeSubtitleCacheAsync(config)
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
                                    targetLang = targetLang.code,
                                    mode = config.translationEngine,
                                    geminiApiKey = geminiApiKey,
                                    geminiModelId = config.geminiModelId
                                )
                            }
                        }.awaitAll()
                    }
                    for (translatedBatch in waveTranslated) {
                        for (tc in translatedBatch) {
                            cueIndexMap[tc.id]?.let { pos ->
                                workingMutable[pos] = tc
                                translatedIdSet.add(tc.id)
                            }
                        }
                    }
                    completedBatches += wave.size
                    primaryThaiCues = workingMutable.toList()
                    activeTranslatedCueIds = translatedIdSet.toSet()
                    activeIsFullyTranslated = completedBatches >= remainingBatches.size
                    val pct = (((completedBatches) * 100) / remainingBatches.size).coerceAtMost(99)
                    currentBadge = "Sub ${targetLang.shortBadge} • AI ($pct%)"
                    refreshOverlayAt(currentPositionMs, config)
                    persistActiveEpisodeSubtitleCacheAsync(config)
                }
                isTranslatingFlag = false
                activeTranslatedCueIds = loadedCues.mapTo(HashSet()) { it.id }
                activeIsFullyTranslated = true
                currentBadge = buildBadgeText(
                    isNativeTarget = false,
                    cueCount = loadedCues.size,
                    config = config
                )
                refreshOverlayAt(currentPositionMs, config)
                persistActiveEpisodeSubtitleCacheAsync(config)
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

    private fun orderExternalCandidatesForTarget(
        tracks: List<SubtitleTrackOption>,
        targetLangCode: String = "th",
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

        val nativeTarget = prioritizePlatform(
            humanOnly.filter { SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) }
        )
        val humanEn = prioritizePlatform(
            humanOnly.filter {
                !SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) &&
                    it.languageCode.lowercase().startsWith("en")
            }
        )
        val humanVi = prioritizePlatform(
            humanOnly.filter {
                !SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) &&
                    it.languageCode.lowercase().startsWith("vi")
            }
        )
        val humanOthers = prioritizePlatform(
            humanOnly.filter {
                !SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) &&
                    !it.languageCode.lowercase().startsWith("en") &&
                    !it.languageCode.lowercase().startsWith("vi")
            }
        )

        val autoTarget = autoOnly.filter { SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) }
        val autoOthers = autoOnly.filterNot { SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLangCode) }

        return nativeTarget + humanEn + humanVi + humanOthers + autoTarget + autoOthers
    }

    fun chooseBestDefaultTrack(
        tracks: List<SubtitleTrackOption>,
        preferredLangCode: String = "th"
    ): SubtitleTrackOption? {
        if (tracks.isEmpty()) return null
        tracks.firstOrNull {
            !it.isEmbedded && SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, preferredLangCode)
        }?.let { return it }
        tracks.firstOrNull {
            it.isEmbedded && SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, preferredLangCode)
        }?.let { return it }

        val preferredFallbackOrder = listOf("en", "eng", "vi", "vie", "th", "tha", "zh", "chi", "ko", "kor")
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
        secondaryTranslationJob?.cancel()
        liveCueJob?.cancel()
        _selectedTrackId.value = track.id

        val targetLang = config.resolvedPrimaryLanguage()
        activePrimaryLangCode = targetLang.code
        activeSecondaryLangCode = config.resolvedSecondaryLangCode()

        if (track.isEmbedded) {
            sourceCues = emptyList()
            primaryThaiCues = emptyList()
            secondaryCues = emptyList()
            onSelectEmbeddedTrack?.invoke(track)
            val isTarget = SubtitleLanguageCatalog.isTrackMatchingLanguage(track.languageCode, targetLang.code)
            currentBadge = if (isTarget) {
                "Sub ${targetLang.shortBadge} (${track.label})"
            } else if (config.autoTranslateEnabled) {
                "Sub ${targetLang.shortBadge} • AI (${track.label})"
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
            activeSourceLangCode = ref.languageCode
            activeSourcePlatform = ref.sourcePlatform
            activeIsAiAudio = false
            activeCompletedAudioSlots = emptySet()
            preloadBilingualReferenceTrackAsync(ref)
            val sampleText = loadedCues.take(12).joinToString(" ") { it.text }
            val alreadyInTarget = SubtitleLanguageCatalog.isTrackMatchingLanguage(ref.languageCode, targetLang.code) ||
                SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage(sampleText, targetLang.code)
            secondaryCues = resolveInitialSecondaryCues(ref, loadedCues, config, geminiApiKey)

            if (alreadyInTarget || !config.autoTranslateEnabled) {
                primaryThaiCues = loadedCues
                isTranslatingFlag = false
                activeTranslatedCueIds = loadedCues.mapTo(HashSet()) { it.id }
                activeIsFullyTranslated = true
                currentBadge = buildBadgeText(
                    isNativeTarget = alreadyInTarget,
                    cueCount = loadedCues.size,
                    config = config
                )
                refreshOverlayAt(currentPositionMs, config)
                persistActiveEpisodeSubtitleCacheAsync(config)
            } else {
                primaryThaiCues = loadedCues
                activeTranslatedCueIds = emptySet()
                activeIsFullyTranslated = false
                currentBadge = "Sub ${targetLang.shortBadge} • AI dịch..."
                refreshOverlayAt(currentPositionMs, config)

                translateCuesProgressively(
                    inputCues = loadedCues,
                    targetLang = targetLang.code,
                    config = config,
                    geminiApiKey = geminiApiKey,
                    isPrimary = true
                )
            }
        }
    }

    fun reapplyConfig(config: SubtitleStyleConfig, geminiApiKey: String) {
        val targetLang = config.resolvedPrimaryLanguage()
        val newSecLang = config.resolvedSecondaryLangCode()
        val primaryChanged = !targetLang.code.equals(activePrimaryLangCode, ignoreCase = true)
        val secondaryChanged = !newSecLang.equals(activeSecondaryLangCode, ignoreCase = true)

        // If user switches primary language, check if that language is ALREADY cached on disk for this episode!
        if (primaryChanged) {
            val movie = activeMovieRef
            val ep = activeEpisodeRef
            if (movie != null && ep != null) {
                scope.launch {
                    val cachedForLang = withContext(Dispatchers.IO) {
                        loadEpisodeSubCache(movie, ep, targetLang.code)
                    }
                    if (cachedForLang != null && cachedForLang.primaryCues.size >= 3) {
                        activePrimaryLangCode = targetLang.code
                        activeSecondaryLangCode = newSecLang
                        restoreFromSavedBundle(
                            cached = cachedForLang,
                            movie = movie,
                            episode = ep,
                            config = config,
                            geminiApiKey = geminiApiKey,
                            startPositionMs = currentPositionMs,
                            onReadyToPlay = {}
                        )
                        return@launch
                    }

                    if (_selectedTrackId.value?.startsWith("ai_audio_") == true && hasAiAccess(geminiApiKey)) {
                        startGeminiRollingAudioSubtitles(
                            movie = movie,
                            episode = ep,
                            initialPositionMs = currentPositionMs,
                            config = config,
                            geminiApiKey = geminiApiKey,
                            onReadyToPlay = null
                        )
                        return@launch
                    }

                    val activeId = _selectedTrackId.value
                    val activeTrack = _availableTracks.value.firstOrNull { it.id == activeId }
                    if (activeTrack != null && activeTrack.externalRef != null) {
                        val nativeTrackForNewLang = _availableTracks.value.firstOrNull {
                            !it.isEmbedded && it.externalRef?.isAutoGenerated != true &&
                                SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetLang.code)
                        }
                        selectTrack(
                            nativeTrackForNewLang ?: activeTrack,
                            config,
                            geminiApiKey,
                            null
                        )
                    } else if (sourceCues.isNotEmpty()) {
                        activePrimaryLangCode = targetLang.code
                        translationJob?.cancel()
                        translationJob = scope.launch {
                            translateCuesProgressively(
                                inputCues = sourceCues,
                                targetLang = targetLang.code,
                                config = config,
                                geminiApiKey = geminiApiKey,
                                isPrimary = true
                            )
                        }
                    }
                }
                return
            }
        }

        if (sourceCues.isNotEmpty()) {
            scope.launch {
                val activeId = _selectedTrackId.value
                val activeTrack = _availableTracks.value.firstOrNull { it.id == activeId }
                val activeRef = activeTrack?.externalRef
                if (activeTrack != null && activeRef != null) {
                    if (secondaryChanged || (config.dualSubEnabled && secondaryCues.isEmpty())) {
                        activeSecondaryLangCode = newSecLang
                        secondaryCues = resolveInitialSecondaryCues(activeRef, sourceCues, config, geminiApiKey)
                        persistActiveEpisodeSubtitleCacheAsync(config)
                    }
                    currentBadge = buildBadgeText(
                        isNativeTarget = SubtitleLanguageCatalog.isTrackMatchingLanguage(activeRef.languageCode, targetLang.code),
                        cueCount = primaryThaiCues.size,
                        config = config
                    )
                    refreshOverlayAt(currentPositionMs, config)
                }
            }
        } else {
            activePrimaryLangCode = targetLang.code
            activeSecondaryLangCode = newSecLang
            refreshOverlayAt(currentPositionMs, config)
        }
    }

    private suspend fun resolveInitialSecondaryCues(
        primaryRef: ExternalSubtitleRef,
        primaryLoadedCues: List<SubtitleCue>,
        config: SubtitleStyleConfig,
        geminiApiKey: String = ""
    ): List<SubtitleCue> {
        secondaryTranslationJob?.cancel()
        val targetCode = config.resolvedSecondaryLangCode()
        if (!config.dualSubEnabled || targetCode == "off") {
            return emptyList()
        }
        val primaryTargetCode = config.resolvedPrimaryLanguage().code
        if (targetCode == "orig") {
            return if (SubtitleLanguageCatalog.isTrackMatchingLanguage(primaryRef.languageCode, primaryTargetCode)) {
                emptyList()
            } else {
                primaryLoadedCues
            }
        }
        // 1. Check if we already have a native human subtitle matching the chosen secondary language
        val matchingNativeRef = externalRefs.firstOrNull {
            it.id != primaryRef.id && SubtitleLanguageCatalog.isTrackMatchingLanguage(it.languageCode, targetCode)
        }
        if (matchingNativeRef != null) {
            val nativeSecondary = loadExternalCues(matchingNativeRef)
            if (nativeSecondary.isNotEmpty()) return nativeSecondary
        }
        // 2. If the primary source track itself is already in the chosen secondary language -> use directly!
        if (SubtitleLanguageCatalog.isTrackMatchingLanguage(primaryRef.languageCode, targetCode)) {
            return primaryLoadedCues
        }
        // 3. Otherwise AI-translate the secondary subtitle into ANY chosen target language in background!
        secondaryTranslationJob = scope.launch {
            translateCuesProgressively(
                inputCues = primaryLoadedCues,
                targetLang = targetCode,
                config = config,
                geminiApiKey = geminiApiKey,
                isPrimary = false
            )
        }
        return primaryLoadedCues
    }

    private suspend fun translateCuesProgressively(
        inputCues: List<SubtitleCue>,
        targetLang: String,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        isPrimary: Boolean,
        existingTranslatedCues: List<SubtitleCue>? = null,
        alreadyTranslatedIds: Set<Int> = emptySet()
    ) {
        val workingMutable = (existingTranslatedCues ?: inputCues).toMutableList()
        val cueIndexMap = HashMap<Int, Int>(workingMutable.size)
        workingMutable.forEachIndexed { idx, cue -> cueIndexMap[cue.id] = idx }

        val pendingCues = if (alreadyTranslatedIds.isEmpty()) {
            inputCues
        } else {
            inputCues.filterNot { it.id in alreadyTranslatedIds }
        }
        val batchSize = 32
        val batches = pendingCues.chunked(batchSize)
        if (batches.isEmpty()) {
            if (isPrimary) {
                activeTranslatedCueIds = inputCues.mapTo(HashSet()) { it.id }
                activeIsFullyTranslated = true
                isTranslatingFlag = false
                persistActiveEpisodeSubtitleCacheAsync(config)
            }
            return
        }

        val translatedIdSet = HashSet<Int>(alreadyTranslatedIds)

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
            cueIndexMap[tc.id]?.let { pos ->
                workingMutable[pos] = tc
                translatedIdSet.add(tc.id)
            }
        }
        val restIndices = orderedBatchIndices.drop(1)
        val badgeLang = SubtitleLanguageCatalog.resolve(targetLang).shortBadge
        if (isPrimary) {
            primaryThaiCues = workingMutable.toList()
            isTranslatingFlag = restIndices.isNotEmpty()
            activeTranslatedCueIds = translatedIdSet.toSet()
            activeIsFullyTranslated = restIndices.isEmpty()
            currentBadge = if (isTranslatingFlag) {
                "Sub $badgeLang • AI (${inputCues.size} câu)"
            } else {
                buildBadgeText(isNativeTarget = false, cueCount = inputCues.size, config = config)
            }
        } else {
            secondaryCues = workingMutable.toList()
        }
        refreshOverlayAt(currentPositionMs, config)
        persistActiveEpisodeSubtitleCacheAsync(config)

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
                    cueIndexMap[tc.id]?.let { pos ->
                        workingMutable[pos] = tc
                        translatedIdSet.add(tc.id)
                    }
                }
            }
            doneCount += wave.size
            val snapshot = workingMutable.toList()
            if (isPrimary) {
                primaryThaiCues = snapshot
                isTranslatingFlag = doneCount < orderedBatchIndices.size
                activeTranslatedCueIds = translatedIdSet.toSet()
                activeIsFullyTranslated = !isTranslatingFlag
                currentBadge = if (isTranslatingFlag) {
                    val pct = ((doneCount * 100) / orderedBatchIndices.size).coerceAtMost(99)
                    "Sub $badgeLang • AI ($pct%)"
                } else {
                    buildBadgeText(isNativeTarget = false, cueCount = inputCues.size, config = config)
                }
            } else {
                secondaryCues = snapshot
            }
            refreshOverlayAt(currentPositionMs, config)
            persistActiveEpisodeSubtitleCacheAsync(config)
        }
        if (isPrimary) {
            isTranslatingFlag = false
            activeTranslatedCueIds = inputCues.mapTo(HashSet()) { it.id }
            activeIsFullyTranslated = true
        }
        refreshOverlayAt(currentPositionMs, config)
        persistActiveEpisodeSubtitleCacheAsync(config)
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

        val targetLang = config.resolvedPrimaryLanguage()
        val isAlreadyTarget = SubtitleLanguageCatalog.isTrackMatchingLanguage(activeTrack.languageCode, targetLang.code) ||
            SubtitleLanguageCatalog.isTextAlreadyInTargetLanguage(cleaned, targetLang.code)
        if (isAlreadyTarget || !config.autoTranslateEnabled) {
            liveEmbeddedTranslatedThai = cleaned
            liveEmbeddedSecondary = null
            refreshOverlayAt(currentPositionMs, config)
            return
        }

        liveEmbeddedSecondary = if (config.dualSubEnabled && config.resolvedSecondaryLangCode() != "off") {
            cleaned
        } else {
            null
        }
        liveCueJob?.cancel()
        liveCueJob = scope.launch {
            val translated = translator.translateSingleText(
                text = cleaned,
                targetLang = targetLang.code,
                mode = config.translationEngine,
                geminiApiKey = geminiApiKey,
                geminiModelId = config.geminiModelId
            )
            liveEmbeddedTranslatedThai = translated
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
            val secondary = if (config.dualSubEnabled && config.resolvedSecondaryLangCode() != "off") {
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
        val targetLang = config.resolvedPrimaryLanguage()

        // Show visual confirmation banner during opening 5 seconds if dialogue starts later in intro!
        if (primary == null && cues.isNotEmpty() && positionMs in 100L..5_500L && nextCueMs > 5_500L) {
            val mins = (nextCueMs / 60000L)
            val secs = (nextCueMs / 1000L) % 60L
            primary = "🎬 Đã nạp ${cues.size} câu Sub ${targetLang.shortBadge} • Thoại bắt đầu lúc %02d:%02d".format(mins, secs)
        } else if (primary == null && cues.isEmpty() && positionMs in 100L..6_000L &&
            _selectedTrackId.value?.startsWith("ai_audio_") == true
        ) {
            primary = "🎙️ Gemini AI (${targetLang.shortBadge}) đang bật • Đang dịch các đoạn thoại tiếp theo..."
        }

        val secondary = if (config.dualSubEnabled && config.resolvedSecondaryLangCode() != "off") {
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
        isNativeTarget: Boolean,
        cueCount: Int,
        config: SubtitleStyleConfig,
        platform: String = ""
    ): String {
        val targetLang = config.resolvedPrimaryLanguage()
        val platTag = if (platform.isNotBlank()) "$platform • " else ""
        val mainPart = if (isNativeTarget) {
            "Sub ${targetLang.shortBadge} Gốc ($platTag$cueCount câu)"
        } else {
            "Sub ${targetLang.shortBadge} AI ($platTag$cueCount câu)"
        }
        val secCode = config.resolvedSecondaryLangCode()
        return if (config.dualSubEnabled && secCode != "off") {
            "$mainPart + ${config.resolvedSecondaryDisplayName()}"
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
            val nextCandidate = orderExternalCandidatesForTarget(_availableTracks.value, config.resolvedPrimaryLanguage().code)
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
        if (movie != null && hasAiAccess(geminiApiKey)) {
            val targetLang = config.resolvedPrimaryLanguage()
            val msg = "🎙️ Sub rời không khớp bản cắt -> Đã chuyển sang Gemini Nghe Phim Tạo Sub ${targetLang.shortBadge}!"
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
            _autoSyncFeedback.value = "⚠️ Bản Sub tải về khác bản cắt của phim! Hãy bấm 'Đổi Bản Sub' hoặc nạp Gemini Key để AI tự nghe phim."
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

    private suspend fun restoreFromSavedBundle(
        cached: SavedEpisodeSubtitleBundle,
        movie: MovieItem,
        episode: Episode,
        config: SubtitleStyleConfig,
        geminiApiKey: String,
        startPositionMs: Long,
        onReadyToPlay: () -> Unit
    ) {
        val targetLang = config.resolvedPrimaryLanguage()
        val secLang = config.resolvedSecondaryLangCode()
        activePrimaryLangCode = targetLang.code
        activeSecondaryLangCode = secLang
        activeSourceLangCode = cached.sourceLangCode.ifBlank { "auto" }
        activeSourcePlatform = cached.sourcePlatform.ifBlank { "Đã lưu" }
        activeTranslatedCueIds = cached.translatedCueIds
        activeIsFullyTranslated = cached.isFullyTranslated
        activeIsAiAudio = cached.isAiAudio
        activeCompletedAudioSlots = cached.completedAudioSlots
        activeTranslatedRanges = cached.translatedRanges
        activeTotalDurationMs = cached.totalDurationMs

        sourceCues = cached.sourceCues.ifEmpty { cached.primaryCues }
        primaryThaiCues = cached.primaryCues

        if (cached.isAiAudio) {
            val synthTrack = SubtitleTrackOption(
                id = "ai_audio_${episode.id}",
                label = "💾 Gemini Audio -> ${targetLang.shortBadge} (Đã lưu • ${cached.primaryCues.size} câu)",
                languageCode = targetLang.code,
                isEmbedded = false,
                isThai = targetLang.code == "th"
            )
            aiAudioSyntheticTrack = synthTrack
            _availableTracks.value = buildCombinedTracks()
            _selectedTrackId.value = synthTrack.id
        } else {
            val savedRef = ExternalSubtitleRef(
                id = "saved_${episode.id}_${targetLang.code}",
                label = "💾 Sub ${targetLang.shortBadge} Đã lưu (${cached.primaryCues.size} câu)",
                languageCode = activeSourceLangCode,
                urlOrContent = "",
                isRawContent = false,
                sourcePlatform = activeSourcePlatform
            )
            parsedExternalCuesCache[savedRef.id] = sourceCues
            externalRefs.removeAll { it.id == savedRef.id }
            externalRefs.add(0, savedRef)
            _availableTracks.value = buildCombinedTracks()
            _selectedTrackId.value = "ext_${savedRef.id}"
        }

        // Restore secondary cues cleanly (zero re-translation if secondary language matches!)
        secondaryCues = when {
            !config.dualSubEnabled || secLang == "off" -> emptyList()
            cached.secondaryCues.isNotEmpty() && cached.secondaryLangCode.equals(secLang, ignoreCase = true) ->
                cached.secondaryCues
            secLang == "orig" || SubtitleLanguageCatalog.isTrackMatchingLanguage(activeSourceLangCode, secLang) ->
                sourceCues
            else -> {
                secondaryTranslationJob?.cancel()
                secondaryTranslationJob = scope.launch {
                    translateCuesProgressively(
                        inputCues = sourceCues,
                        targetLang = secLang,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        isPrimary = false
                    )
                }
                sourceCues
            }
        }

        isTranslatingFlag = false
        currentBadge = "💾 Sub ${targetLang.shortBadge} • Đã lưu (${cached.primaryCues.size} câu)"
        _preFlightState.value = PreFlightSubtitleState(
            isPreparing = false,
            loadedCueCount = cached.primaryCues.size,
            firstCueTimeMs = cached.primaryCues.firstOrNull()?.startMs ?: -1L
        )
        refreshOverlayAt(currentPositionMs, config)
        onReadyToPlay()

        // If a previous session was exited mid-translation, finish ONLY the remaining cues in background!
        if (!cached.isAiAudio && !cached.isFullyTranslated && config.autoTranslateEnabled && sourceCues.isNotEmpty()) {
            val remainingCount = sourceCues.count { it.id !in cached.translatedCueIds }
            if (remainingCount > 0) {
                translationJob?.cancel()
                translationJob = scope.launch {
                    translateCuesProgressively(
                        inputCues = sourceCues,
                        targetLang = targetLang.code,
                        config = config,
                        geminiApiKey = geminiApiKey,
                        isPrimary = true,
                        existingTranslatedCues = cached.primaryCues,
                        alreadyTranslatedIds = cached.translatedCueIds
                    )
                }
            }
        } else if (cached.isAiAudio && !cached.isFullyTranslated && hasAiAccess(geminiApiKey)) {
            startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = startPositionMs,
                config = config,
                geminiApiKey = geminiApiKey,
                preloadedSlots = cached.completedAudioSlots,
                preloadedCues = cached.primaryCues,
                preloadedRanges = cached.translatedRanges,
                onReadyToPlay = null
            )
        }
    }

    private fun episodeSubCacheFile(movie: MovieItem, episode: Episode, primaryLangCode: String): File? {
        val dir = episodeSubCacheDir ?: return null
        val mId = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
        val epKey = CloudSyncClient.normalizeEpisodeKey(episode.name.ifBlank { episode.id })
        val streamPart = if (mId.isBlank() || mId in setOf("unknown", "direct", "local", "external", "custom")) {
            episode.streamUrl.substringBefore('?').trim().lowercase()
        } else {
            ""
        }
        val rawKey = "$mId|$epKey|$streamPart|${primaryLangCode.trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return File(dir, "ep_$digest.json")
    }

    private fun legacyEpisodeSubCacheFile(movie: MovieItem, episode: Episode, primaryLangCode: String): File? {
        val dir = episodeSubCacheDir ?: return null
        val mId = movie.id.trim().lowercase()
        val epId = episode.id.trim().lowercase()
        val epName = episode.name.trim().lowercase()
        val streamPart = if (mId.isBlank() || mId in setOf("direct", "local", "external", "custom")) {
            episode.streamUrl.substringBefore('?').trim().lowercase()
        } else {
            ""
        }
        val rawKey = "$mId|$epId|$epName|$streamPart|${primaryLangCode.trim().lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return File(dir, "ep_$digest.json")
    }

    private fun reconstructCompletedSlots(
        preloadedSlots: Set<Int>,
        preloadedRanges: List<Pair<Long, Long>>,
        preloadedCues: List<SubtitleCue>,
        totalSlots: Int,
        slotSizeMs: Long
    ): Set<Int> {
        val out = HashSet<Int>()
        // 1. Reconstruct from exact millisecond time-ranges (bridges Web 38s windows -> Android 60s slots cleanly!)
        val mergedRanges = CloudSyncClient.mergeTimeRanges(preloadedRanges)
        if (mergedRanges.isNotEmpty()) {
            for (slotIdx in 0 until totalSlots) {
                val sStart = slotIdx * slotSizeMs
                val sEnd = sStart + slotSizeMs
                var overlap = 0L
                for ((rs, re) in mergedRanges) {
                    if (re <= sStart) continue
                    if (rs >= sEnd) break
                    overlap += (minOf(sEnd, re) - maxOf(sStart, rs)).coerceAtLeast(0L)
                }
                if (overlap >= (slotSizeMs * 55L) / 100L) {
                    out.add(slotIdx)
                }
            }
        }
        // 2. Include Android's native 60s slot indices ONLY if ranges were empty (avoid confusing Web 38s indices!)
        if (mergedRanges.isEmpty()) {
            for (slotIdx in preloadedSlots) {
                if (slotIdx in 0 until totalSlots) out.add(slotIdx)
            }
        }
        // 3. Reconstruct from actual cue timestamps so minutes already translated on Web are NEVER re-translated!
        if (preloadedCues.isNotEmpty()) {
            for (slotIdx in 0 until totalSlots) {
                if (slotIdx in out) continue
                val innerStart = slotIdx * slotSizeMs + 2_000L
                val innerEnd = (slotIdx + 1) * slotSizeMs - 2_000L
                val hasCueInside = preloadedCues.any { c ->
                    val mid = (c.startMs + c.endMs.coerceAtLeast(c.startMs)) / 2L
                    mid in innerStart..innerEnd
                }
                if (hasCueInside) out.add(slotIdx)
            }
        }
        return out
    }

    private fun mergeCuesChronological(a: List<SubtitleCue>, b: List<SubtitleCue>): List<SubtitleCue> {
        val combined = (a + b)
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startMs }
        val out = ArrayList<SubtitleCue>(combined.size)
        for (c in combined) {
            val prev = out.lastOrNull()
            if (prev != null && kotlin.math.abs(prev.startMs - c.startMs) < 900L && prev.text == c.text) continue
            out.add(
                SubtitleCue(
                    id = out.size + 1,
                    startMs = c.startMs.coerceAtLeast(0L),
                    endMs = c.endMs.coerceAtLeast(c.startMs + 450L),
                    text = c.text.trim()
                )
            )
        }
        return out
    }

    private fun mergeSubtitleBundlesMonotonic(
        existing: SavedEpisodeSubtitleBundle,
        incoming: SavedEpisodeSubtitleBundle
    ): SavedEpisodeSubtitleBundle {
        val isAudio = existing.isAiAudio || incoming.isAiAudio
        if (isAudio) {
            val mergedPrimary = mergeCuesChronological(existing.primaryCues, incoming.primaryCues)
            val mergedSource = mergeCuesChronological(existing.sourceCues, incoming.sourceCues).ifEmpty { mergedPrimary }
            val mergedRanges = CloudSyncClient.mergeTimeRanges(existing.translatedRanges + incoming.translatedRanges)
            val maxRangeEnd = mergedRanges.lastOrNull()?.second ?: 0L
            val maxCueEnd = mergedPrimary.lastOrNull()?.endMs ?: 0L
            return existing.copy(
                sourcePlatform = incoming.sourcePlatform.ifBlank { existing.sourcePlatform },
                isAiAudio = true,
                completedAudioSlots = existing.completedAudioSlots + incoming.completedAudioSlots,
                translatedCueIds = mergedPrimary.mapTo(HashSet()) { it.id },
                sourceCues = mergedSource,
                primaryCues = mergedPrimary,
                secondaryCues = incoming.secondaryCues.ifEmpty { existing.secondaryCues },
                isFullyTranslated = existing.isFullyTranslated || incoming.isFullyTranslated,
                savedAtMs = maxOf(existing.savedAtMs, incoming.savedAtMs),
                translatedRanges = mergedRanges,
                audioTranslatedUntilMs = maxOf(existing.audioTranslatedUntilMs, incoming.audioTranslatedUntilMs, maxRangeEnd, maxCueEnd),
                totalDurationMs = maxOf(existing.totalDurationMs, incoming.totalDurationMs)
            )
        }
        if (existing.isFullyTranslated && !incoming.isFullyTranslated && incoming.primaryCues.size <= existing.primaryCues.size) {
            return existing
        }
        return if (incoming.primaryCues.size >= existing.primaryCues.size || incoming.isFullyTranslated) incoming else existing
    }

    private fun persistActiveEpisodeSubtitleCacheAsync(config: SubtitleStyleConfig) {
        val movie = activeMovieRef ?: return
        val episode = activeEpisodeRef ?: return
        val prim = primaryThaiCues
        if (prim.size < 3 || episodeSubCacheDir == null) return
        val langCode = activePrimaryLangCode.ifBlank { config.resolvedPrimaryLanguage().code }
        if (CloudSyncClient.hasWrongScriptForLang(prim, langCode)) return

        val ranges = CloudSyncClient.mergeTimeRanges(activeTranslatedRanges)
        val audioUntil = maxOf(
            ranges.lastOrNull()?.second ?: 0L,
            if (activeIsAiAudio) (prim.lastOrNull()?.endMs ?: 0L) else 0L
        )
        val bundle = SavedEpisodeSubtitleBundle(
            movieId = movie.id,
            episodeId = episode.id,
            primaryLangCode = langCode,
            secondaryLangCode = activeSecondaryLangCode.ifBlank { config.resolvedSecondaryLangCode() },
            sourceLangCode = activeSourceLangCode,
            sourcePlatform = activeSourcePlatform,
            isAiAudio = activeIsAiAudio,
            completedAudioSlots = activeCompletedAudioSlots,
            translatedCueIds = activeTranslatedCueIds,
            sourceCues = sourceCues.ifEmpty { prim },
            primaryCues = prim,
            secondaryCues = secondaryCues,
            isFullyTranslated = activeIsFullyTranslated,
            savedAtMs = System.currentTimeMillis(),
            translatedRanges = ranges,
            audioTranslatedUntilMs = audioUntil,
            totalDurationMs = activeTotalDurationMs
        )
        scope.launch(Dispatchers.IO) {
            saveEpisodeSubCache(movie, episode, bundle, uploadToCloud = true)
        }
    }

    internal fun saveEpisodeSubCache(
        movie: MovieItem,
        episode: Episode,
        bundle: SavedEpisodeSubtitleBundle,
        uploadToCloud: Boolean = false
    ): Boolean {
        if (CloudSyncClient.hasWrongScriptForLang(bundle.primaryCues, bundle.primaryLangCode)) return false
        val targetFile = episodeSubCacheFile(movie, episode, bundle.primaryLangCode) ?: return false
        var finalBundle = bundle
        val savedLocal = runCatching {
            synchronized(episodeCacheLock) {
                targetFile.parentFile?.mkdirs()
                // Monotonic merge with existing valid local file so progress NEVER shrinks!
                val existing = readBundleFromFile(targetFile, movie, episode, bundle.primaryLangCode)
                if (existing != null) {
                    finalBundle = mergeSubtitleBundlesMonotonic(existing, bundle)
                }
                val root = JSONObject().apply {
                    put("v", 2)
                    put("movieId", finalBundle.movieId)
                    put("episodeId", finalBundle.episodeId)
                    put("primaryLangCode", finalBundle.primaryLangCode)
                    put("secondaryLangCode", finalBundle.secondaryLangCode)
                    put("sourceLangCode", finalBundle.sourceLangCode)
                    put("sourcePlatform", finalBundle.sourcePlatform)
                    put("isAiAudio", finalBundle.isAiAudio)
                    put("isFullyTranslated", finalBundle.isFullyTranslated)
                    put("savedAtMs", finalBundle.savedAtMs)
                    put("audioTranslatedUntilMs", finalBundle.audioTranslatedUntilMs)
                    put("totalDurationMs", finalBundle.totalDurationMs)
                    put("completedAudioSlots", JSONArray().apply {
                        finalBundle.completedAudioSlots.sorted().forEach { put(it) }
                    })
                    put("translatedRanges", JSONArray().apply {
                        finalBundle.translatedRanges.forEach { (s, e) -> put(JSONArray().put(s).put(e)) }
                    })
                    put("translatedCueIds", JSONArray().apply {
                        if (!finalBundle.isFullyTranslated) {
                            finalBundle.translatedCueIds.forEach { put(it) }
                        }
                    })
                    put("sourceCues", encodeCueListJson(finalBundle.sourceCues))
                    put("primaryCues", encodeCueListJson(finalBundle.primaryCues))
                    put("secondaryCues", encodeCueListJson(finalBundle.secondaryCues))
                }
                val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
                tmp.writeText(root.toString(), Charsets.UTF_8)
                if (targetFile.exists()) targetFile.delete()
                if (!tmp.renameTo(targetFile)) {
                    targetFile.writeText(root.toString(), Charsets.UTF_8)
                    tmp.delete()
                }
            }
            true
        }.getOrDefault(false)

        if (uploadToCloud && CloudSyncClient.isSignedIn() && finalBundle.primaryCues.size >= 3) {
            val now = System.currentTimeMillis()
            if (finalBundle.isFullyTranslated || now - lastCloudSubtitleUploadMs >= 22_000L) {
                lastCloudSubtitleUploadMs = now
                scope.launch(Dispatchers.IO) {
                    CloudSyncClient.uploadCloudSubtitle(
                        rawMovieSlug = movie.detailLookupId ?: movie.id,
                        rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                        primaryLangCode = finalBundle.primaryLangCode,
                        secondaryLangCode = finalBundle.secondaryLangCode,
                        sourceLangCode = finalBundle.sourceLangCode,
                        sourcePlatform = finalBundle.sourcePlatform.ifBlank { "NKI Android" },
                        isAiAudio = finalBundle.isAiAudio,
                        isFullyTranslated = finalBundle.isFullyTranslated,
                        completedAudioSlots = finalBundle.completedAudioSlots,
                        sourceCues = finalBundle.sourceCues,
                        primaryCues = finalBundle.primaryCues,
                        secondaryCues = finalBundle.secondaryCues,
                        translatedRanges = finalBundle.translatedRanges,
                        audioTranslatedUntilMs = finalBundle.audioTranslatedUntilMs,
                        totalDurationMs = finalBundle.totalDurationMs
                    )
                }
            }
        }
        return savedLocal
    }

    private fun readBundleFromFile(
        file: File,
        movie: MovieItem,
        episode: Episode,
        primaryLangCode: String
    ): SavedEpisodeSubtitleBundle? {
        if (!file.exists() || file.length() < 20L) return null
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val obj = JSONObject(raw)
            val primary = decodeCueListJson(obj.optJSONArray("primaryCues"))
            if (primary.isEmpty()) return null
            val resolvedLang = obj.optString("primaryLangCode", primaryLangCode)
            // Self-heal poisoned local cache (e.g. Chinese Hanzi saved under Vietnamese `vi`)!
            if (CloudSyncClient.hasWrongScriptForLang(primary, resolvedLang)) {
                runCatching { file.delete() }
                return null
            }
            val source = decodeCueListJson(obj.optJSONArray("sourceCues")).ifEmpty { primary }
            val secondary = decodeCueListJson(obj.optJSONArray("secondaryCues"))
            val isFull = obj.optBoolean("isFullyTranslated", true)
            val slots = HashSet<Int>()
            obj.optJSONArray("completedAudioSlots")?.let { arr ->
                for (i in 0 until arr.length()) slots.add(arr.optInt(i))
            }
            val ranges = CloudSyncClient.decodeTimeRanges(obj.optJSONArray("translatedRanges"))
            val transIds = if (isFull) {
                primary.mapTo(HashSet()) { it.id }
            } else {
                HashSet<Int>().also { set ->
                    obj.optJSONArray("translatedCueIds")?.let { arr ->
                        for (i in 0 until arr.length()) set.add(arr.optInt(i))
                    }
                }
            }
            SavedEpisodeSubtitleBundle(
                movieId = obj.optString("movieId", movie.id),
                episodeId = obj.optString("episodeId", episode.id),
                primaryLangCode = resolvedLang,
                secondaryLangCode = obj.optString("secondaryLangCode", ""),
                sourceLangCode = obj.optString("sourceLangCode", "auto"),
                sourcePlatform = obj.optString("sourcePlatform", "Đã lưu"),
                isAiAudio = obj.optBoolean("isAiAudio", false),
                completedAudioSlots = slots,
                translatedCueIds = transIds,
                sourceCues = source,
                primaryCues = primary,
                secondaryCues = secondary,
                isFullyTranslated = isFull,
                savedAtMs = obj.optLong("savedAtMs", 0L),
                translatedRanges = ranges,
                audioTranslatedUntilMs = obj.optLong("audioTranslatedUntilMs", ranges.lastOrNull()?.second ?: 0L),
                totalDurationMs = obj.optLong("totalDurationMs", 0L)
            )
        }.getOrNull()
    }

    internal fun loadEpisodeSubCache(
        movie: MovieItem,
        episode: Episode,
        primaryLangCode: String
    ): SavedEpisodeSubtitleBundle? {
        val canonicalFile = episodeSubCacheFile(movie, episode, primaryLangCode)
        val legacyFile = legacyEpisodeSubCacheFile(movie, episode, primaryLangCode)
        val found = synchronized(episodeCacheLock) {
            canonicalFile?.let { readBundleFromFile(it, movie, episode, primaryLangCode) }
                ?: legacyFile?.takeIf { it.absolutePath != canonicalFile?.absolutePath }
                    ?.let { readBundleFromFile(it, movie, episode, primaryLangCode) }
        }
        if (found != null) return found

        // Offline Download Subtitle Fallback (works 100% in Airplane Mode!)
        val root = persistentRoot ?: return null
        val slug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
        val epKey = CloudSyncClient.normalizeEpisodeKey(episode.name.ifBlank { episode.id })
        val offlineCandidates = listOfNotNull(
            File(File(root.parentFile ?: root, "chillfilm_offline_subs"), "${slug}__${epKey}.json"),
            File(File(root, "chillfilm_offline_subs"), "${slug}__${epKey}.json")
        )
        val offlineFile = offlineCandidates.firstOrNull { it.exists() && it.length() >= 12L } ?: return null
        return runCatching {
            val obj = JSONObject(offlineFile.readText(Charsets.UTF_8))
            val lang = obj.optString("lang", primaryLangCode)
            val cues = decodeCueListJson(obj.optJSONArray("cues"))
            if (cues.size < 3 || CloudSyncClient.hasWrongScriptForLang(cues, lang)) return@runCatching null
            SavedEpisodeSubtitleBundle(
                movieId = movie.id,
                episodeId = episode.id,
                primaryLangCode = lang,
                secondaryLangCode = "",
                sourceLangCode = "auto",
                sourcePlatform = "📥 Offline AI Sub",
                isAiAudio = false,
                completedAudioSlots = emptySet(),
                translatedCueIds = cues.mapTo(HashSet()) { it.id },
                sourceCues = cues,
                primaryCues = cues,
                secondaryCues = emptyList(),
                isFullyTranslated = true,
                savedAtMs = offlineFile.lastModified()
            )
        }.getOrNull()
    }

    private fun encodeCueListJson(cues: List<SubtitleCue>): JSONArray {
        val arr = JSONArray()
        for (c in cues) {
            arr.put(JSONArray().apply {
                put(c.id)
                put(c.startMs)
                put(c.endMs)
                put(c.text)
            })
        }
        return arr
    }

    private fun decodeCueListJson(arr: JSONArray?): List<SubtitleCue> {
        if (arr == null || arr.length() == 0) return emptyList()
        val list = ArrayList<SubtitleCue>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            if (row.length() < 4) continue
            list.add(
                SubtitleCue(
                    id = row.optInt(0, i + 1),
                    startMs = row.optLong(1, 0L),
                    endMs = row.optLong(2, 0L),
                    text = row.optString(3, "")
                )
            )
        }
        return list
    }

    fun clearAutoSyncFeedback() {
        _autoSyncFeedback.value = null
    }

    fun release() {
        preFlightJob?.cancel()
        translationJob?.cancel()
        secondaryTranslationJob?.cancel()
        rollingAudioJob?.cancel()
        autoSyncJob?.cancel()
        aiAudioSyntheticTrack = null
        liveCueJob?.cancel()
        scope.cancel()
    }
}
