package com.phyll.filmhub.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Rational
import com.phyll.filmhub.cloud.CloudSyncClient
import java.util.Locale
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import com.phyll.filmhub.companion.CompanionPlayerTelemetry
import com.phyll.filmhub.companion.PlayerRemoteCommand
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.PreFlightSubtitleState
import com.phyll.filmhub.model.SeriesSubtitleProfile
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.SubtitleTrackOption
import com.phyll.filmhub.subtitle.HlsPlaylistSanitizer
import com.phyll.filmhub.subtitle.SubtitleParser
import com.phyll.filmhub.subtitle.ThaiSubtitleManager
import com.phyll.filmhub.ui.components.ChillAspectRatioMode
import com.phyll.filmhub.ui.components.ChillAudioEffectsController
import com.phyll.filmhub.ui.components.ChillEqualizerPreset
import com.phyll.filmhub.ui.components.ChillGestureAndLockOverlay
import com.phyll.filmhub.ui.components.ChillGestureFeedback
import com.phyll.filmhub.ui.components.ChillPlayerMenu
import com.phyll.filmhub.ui.components.ChillPlayerMenuOverlay
import com.phyll.filmhub.ui.components.ThaiCinemaSubtitleOverlay
import com.phyll.filmhub.ui.i18n.LocalAppStrings
import com.phyll.filmhub.ui.theme.AmberBoost
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
import com.phyll.filmhub.ui.theme.BrandNeonGlassBorderBrush
import com.phyll.filmhub.ui.theme.CinemaGlass
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.LiveRed
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.tvFocusableClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movie: MovieItem,
    episode: Episode,
    startPositionMs: Long,
    subtitleConfig: SubtitleStyleConfig,
    geminiApiKey: String,
    discoveredGeminiModels: List<String>,
    incomingCompanionSub: ExternalSubtitleRef?,
    seriesProfile: SeriesSubtitleProfile? = null,
    remoteCommand: PlayerRemoteCommand? = null,
    isTvHardware: Boolean = false,
    isMinimized: Boolean = false,
    skipIntroSec: Int = 85,
    modifier: Modifier = Modifier,
    onSaveGeminiKeyInline: (String, String) -> Unit = { _, _ -> },
    onConsumeRemoteCommand: (Long) -> Unit = {},
    onReportTelemetry: (CompanionPlayerTelemetry) -> Unit = {},
    onSaveSeriesProfile: ((SeriesSubtitleProfile) -> SeriesSubtitleProfile) -> Unit = {},
    onConsumeCompanionSub: () -> Unit,
    onSelectGeminiModel: (String) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onUpdateSubtitleConfig: ((SubtitleStyleConfig) -> SubtitleStyleConfig) -> Unit,
    onResetSubtitleConfig: () -> Unit,
    onSaveProgress: (Long, Long) -> Unit,
    onSwitchEpisode: (Episode) -> Unit,
    onOpenQrCompanion: () -> Unit,
    onBeforeLaunchSystemOverlay: () -> Unit = {},
    onAfterSystemOverlayResult: () -> Unit = {},
    onMinimizePlayer: () -> Unit = {},
    onRestorePlayer: () -> Unit = {},
    onExitPlayer: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = LocalIsTvMode.current
    val strings = LocalAppStrings.current
    val playerScope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var showSubtitleDrawer by remember { mutableStateOf(false) }
    var showFastSnapModal by remember { mutableStateOf(false) }
    var snapAnchorMovieMs by remember { mutableLongStateOf(0L) }
    var wasPlayingBeforeSnap by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember(episode.id, episode.streamUrl) { mutableLongStateOf(startPositionMs) }
    var durationMs by remember(episode.id, episode.streamUrl) { mutableLongStateOf(0L) }
    var pendingInitialSeekMs by remember(episode.id, episode.streamUrl) { mutableLongStateOf(startPositionMs) }
    var resumeNoticeText by remember(episode.id, episode.streamUrl) { mutableStateOf<String?>(null) }
    var skipIntroDismissed by remember(episode.id, episode.streamUrl) { mutableStateOf(false) }
    var autoNextCancelled by remember(episode.id, episode.streamUrl) { mutableStateOf(false) }
    var autoNextCountdownSec by remember(episode.id, episode.streamUrl) { mutableIntStateOf(0) }
    var lastUserInteractionTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // PrimiasArt/Chill-Player Core Features: Lock, Gestures, DSP 200% Boost + Equalizer, Aspect Ratio, Speed, Sleep Timer, PiP
    var isLocked by remember { mutableStateOf(false) }
    var lockHintVisible by remember { mutableStateOf(false) }
    var activeChillMenu by remember { mutableStateOf(ChillPlayerMenu.NONE) }
    var aspectRatioMode by remember { mutableStateOf(ChillAspectRatioMode.FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var equalizerPreset by remember { mutableStateOf(ChillEqualizerPreset.FLAT) }
    var sleepTimerMinutes by remember { mutableStateOf<Int?>(null) }
    var brightnessLevel by remember { mutableFloatStateOf(1.0f) }
    val chillAudioEffects = remember { ChillAudioEffectsController(context) }
    var volumeScale by remember { mutableFloatStateOf(chillAudioEffects.getCurrentNormalizedSystemVolume().coerceAtLeast(0.5f)) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var gestureFeedback by remember { mutableStateOf(ChillGestureFeedback.NONE) }
    var gesturePercent by remember { mutableIntStateOf(100) }
    var seekOffsetSecs by remember { mutableIntStateOf(0) }
    var doubleTapSide by remember { mutableIntStateOf(0) }
    var isLandscapeRotated by remember { mutableStateOf(false) }

    // #2 Sync active movie slug so Android/TV inherits shared Neon AI Character Glossary automatically
    LaunchedEffect(movie.id) {
        CloudSyncClient.activeMovieSlug = CloudSyncClient.normalizeMovieSlug(movie.id)
    }

    // #3 Native Android TextToSpeech AI Voice Dubbing + ExoPlayer Auto-Ducking
    var ttsDubEnabled by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var lastSpokenSubText by remember { mutableStateOf("") }
    val ttsEngine = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
        instance
    }

    val surfaceFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    val subtitleManager = remember {
        ThaiSubtitleManager(
            cacheDir = context.cacheDir,
            persistentSubDir = File(context.filesDir, "chillfilm_saved_subs_v1")
        )
    }

    val renderState by subtitleManager.renderState.collectAsState()
    val preFlightState by subtitleManager.preFlightState.collectAsState()
    val availableTracks by subtitleManager.availableTracks.collectAsState()
    val selectedTrackId by subtitleManager.selectedTrackId.collectAsState()
    val isAutoSyncing by subtitleManager.isAutoSyncing.collectAsState()
    val autoSyncFeedback by subtitleManager.autoSyncFeedback.collectAsState()

    val isPreFlightBlocking = preFlightState.isPreparing || preFlightState.noSubtitleFound

    fun applyAndSaveSeriesSync(
        newOffsetMs: Long,
        newSpeedScale: Float = subtitleConfig.subSpeedScale,
        lockGeminiAudio: Boolean? = null
    ) {
        onUpdateSubtitleConfig {
            it.copy(syncOffsetMs = newOffsetMs, subSpeedScale = newSpeedScale)
        }
        val activePlat = subtitleManager.getActiveExternalPlatformName()
        onSaveSeriesProfile { existing ->
            existing.copy(
                syncOffsetMs = newOffsetMs,
                subSpeedScale = newSpeedScale,
                preferredPlatform = activePlat.ifBlank { existing.preferredPlatform },
                preferGeminiAudioSub = lockGeminiAudio ?: existing.preferGeminiAudioSub
            )
        }
    }

    // Restore this series/movie's learned (syncOffsetMs, subSpeedScale) across ALL 100 episodes of the same series,
    // while preventing a completely different movie from inheriting an unrelated offset!
    LaunchedEffect(movie.id) {
        val savedOffset = seriesProfile?.syncOffsetMs ?: 0L
        val savedScale = seriesProfile?.subSpeedScale ?: 1.0f
        if (subtitleConfig.syncOffsetMs != savedOffset || subtitleConfig.subSpeedScale != savedScale) {
            onUpdateSubtitleConfig {
                it.copy(syncOffsetMs = savedOffset, subSpeedScale = savedScale)
            }
        }
    }

    LaunchedEffect(autoSyncFeedback) {
        if (autoSyncFeedback != null) {
            delay(5_200L)
            subtitleManager.clearAutoSyncFeedback()
        }
    }

    val allEpisodes = remember(movie.id) { movie.servers.flatMap { it.episodes } }
    val nextEpisode = remember(episode.id, allEpisodes) {
        val idx = allEpisodes.indexOfFirst { it.id == episode.id }
        if (idx >= 0 && idx + 1 < allEpisodes.size) allEpisodes[idx + 1] else null
    }

    // Build Media3 ExoPlayer — buffers video frames immediately, strips injected mid-roll ad discontinuities
    // via HlsPlaylistSanitizer so mid-episode subtitles NEVER jump +30.2s, and waits (`playWhenReady = false`)
    // until Pre-Flight Subtitle Loader finishes loading real Native Thai or AI-translated Thai subtitles!
    var usedFallbackStream by remember(episode.id) { mutableStateOf(false) }

    val exoPlayer = remember(episode.streamUrl, episode.audioStreamUrl) {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(HlsPlaylistSanitizer.createOkHttpInterceptor())
            .build()

        val httpFactory = OkHttpDataSource.Factory(okHttp)
            .setUserAgent(
                episode.headers["User-Agent"]
                    ?: "Mozilla/5.0 (Linux; Android 12; TV360) AppleWebKit/537.36 PhyllFilmHub/2.3"
            )
        if (episode.headers.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(episode.headers)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguages("th", "tha", "vi", "en")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()

                if (episode.audioStreamUrl.isNotBlank()) {
                    // YouTube 1080p/720p Full HD Adaptive Video + M4A Audio MergingMediaSource
                    val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(episode.streamUrl))
                    val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(episode.audioStreamUrl))
                    setMediaSource(MergingMediaSource(true, true, videoSource, audioSource))
                } else {
                    setMediaItem(MediaItem.fromUri(episode.streamUrl))
                }

                if (startPositionMs > 1_000L) {
                    seekTo(startPositionMs)
                }
                // Hold playback until Pre-Flight Subtitle Guarantee finishes!
                playWhenReady = false
                prepare()
            }
    }

    fun openFastSnapModal() {
        wasPlayingBeforeSnap = exoPlayer.isPlaying
        snapAnchorMovieMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        exoPlayer.pause()
        showSubtitleDrawer = false
        showFastSnapModal = true
    }

    fun closeFastSnapModal(resumeIfWasPlaying: Boolean = true) {
        showFastSnapModal = false
        if (resumeIfWasPlaying && wasPlayingBeforeSnap) {
            exoPlayer.play()
        }
    }

    fun applyVolumeScale(newScale: Float) {
        val clamped = newScale.coerceIn(0f, 2.0f)
        volumeScale = clamped
        chillAudioEffects.attachToSession(exoPlayer.audioSessionId, clamped, equalizerPreset)
        chillAudioEffects.applyVolumeScale(clamped)
    }

    fun applyBrightness(newLevel: Float) {
        val clamped = newLevel.coerceIn(0.08f, 1.0f)
        brightnessLevel = clamped
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = clamped
            win.attributes = lp
        }
    }

    LaunchedEffect(sleepTimerMinutes) {
        val mins = sleepTimerMinutes
        if (mins != null && mins > 0) {
            delay(mins * 60_000L)
            exoPlayer.pause()
            sleepTimerMinutes = null
        }
    }

    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(2_400L)
            lockHintVisible = false
        }
    }

    LaunchedEffect(resumeNoticeText) {
        if (resumeNoticeText != null) {
            delay(3_400L)
            resumeNoticeText = null
        }
    }

    fun applyImmersiveFullscreen(enable: Boolean) {
        val win = activity?.window ?: return
        if (enable) {
            WindowCompat.setDecorFitsSystemWindows(win, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = win.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                win.attributes = lp
            }
            val controller = WindowInsetsControllerCompat(win, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                win.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(win, true)
            val controller = WindowInsetsControllerCompat(win, view)
            controller.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // Re-enforce true edge-to-edge immersive fullscreen ONLY when expanded;
    // when minimized into the bottom-right pop-out, immediately restore normal system bars & portrait freedom!
    LaunchedEffect(
        isMinimized,
        configuration.orientation,
        isLandscapeRotated,
        showControls,
        isLocked,
        showSubtitleDrawer,
        activeChillMenu
    ) {
        if (isMinimized) {
            applyImmersiveFullscreen(false)
            showSubtitleDrawer = false
            showFastSnapModal = false
            activeChillMenu = ChillPlayerMenu.NONE
            isLocked = false
            if (isLandscapeRotated) {
                isLandscapeRotated = false
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            applyImmersiveFullscreen(true)
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!isMinimized) {
            applyImmersiveFullscreen(true)
        }
        onDispose {
            applyImmersiveFullscreen(false)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            chillAudioEffects.releaseEffects()
            subtitleManager.release()
            runCatching {
                ttsEngine?.stop()
                ttsEngine?.shutdown()
            }
        }
    }

    // #3 Wire TTS Auto-Ducking to active subtitle text + playback state
    DisposableEffect(ttsEngine, exoPlayer) {
        ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                activity?.runOnUiThread { runCatching { exoPlayer.volume = 0.30f } }
            }
            override fun onDone(utteranceId: String?) {
                activity?.runOnUiThread { runCatching { exoPlayer.volume = 1.0f } }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                activity?.runOnUiThread { runCatching { exoPlayer.volume = 1.0f } }
            }
        })
        onDispose {
            runCatching { exoPlayer.volume = 1.0f }
        }
    }

    LaunchedEffect(ttsDubEnabled, ttsReady, renderState.primaryThaiText, isPlaying, isMinimized) {
        if (!ttsDubEnabled || !ttsReady || !isPlaying || isMinimized) {
            runCatching { ttsEngine?.stop() }
            runCatching { exoPlayer.volume = 1.0f }
            if (!ttsDubEnabled) lastSpokenSubText = ""
            return@LaunchedEffect
        }
        val line = renderState.primaryThaiText?.trim().orEmpty()
        if (line.isNotBlank() && line != lastSpokenSubText) {
            lastSpokenSubText = line
            val langCode = subtitleConfig.resolvedPrimaryLanguage().code.lowercase()
            val loc = when (langCode) {
                "vi" -> Locale("vi", "VN")
                "th" -> Locale("th", "TH")
                "zh" -> Locale.SIMPLIFIED_CHINESE
                "ko" -> Locale.KOREAN
                "ja" -> Locale.JAPANESE
                else -> Locale.US
            }
            runCatching {
                ttsEngine?.language = loc
                ttsEngine?.setSpeechRate(1.15f)
                ttsEngine?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "nki_dub_${System.currentTimeMillis()}")
            }
        }
    }

    // Persist playback position immediately if app goes to background or user locks phone
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val posNow = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                val durNow = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                if (posNow > 2_000L) {
                    onSaveProgress(posNow, durNow)
                }
            } else if (event == Lifecycle.Event.ON_RESUME && !isMinimized) {
                applyImmersiveFullscreen(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer) {
        playerError = null
        val initialOffset = seriesProfile?.syncOffsetMs ?: 0L
        val initialScale = seriesProfile?.subSpeedScale ?: 1.0f
        if (subtitleConfig.syncOffsetMs != initialOffset || subtitleConfig.subSpeedScale != initialScale) {
            onUpdateSubtitleConfig {
                it.copy(syncOffsetMs = initialOffset, subSpeedScale = initialScale)
            }
        }
        // Launch Pre-Flight Subtitle Discovery + Persistent Cache Restore + AI Pre-Translation BEFORE playing!
        subtitleManager.prepareForMovieBeforePlay(
            movie = movie,
            episode = episode,
            config = subtitleConfig.copy(
                syncOffsetMs = initialOffset,
                subSpeedScale = initialScale
            ),
            geminiApiKey = geminiApiKey,
            seriesProfile = seriesProfile,
            startPositionMs = startPositionMs,
            onAutoDetectedSync = { newOffsetMs, newScale, _, lockGeminiAudio ->
                applyAndSaveSeriesSync(
                    newOffsetMs = newOffsetMs,
                    newSpeedScale = newScale,
                    lockGeminiAudio = if (lockGeminiAudio) true else null
                )
            },
            onReadyToPlay = {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                lastUserInteractionTick = System.currentTimeMillis()
            }
        )

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    val posNow = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                    val durNow = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                    if (posNow > 2_000L) {
                        onSaveProgress(posNow, durNow)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val realDur = exoPlayer.duration.coerceAtLeast(0L)
                    if (realDur > 0L) durationMs = realDur
                    // HLS streams often ignore seekTo() issued prior to STATE_READY -> apply guaranteed seek now!
                    if (pendingInitialSeekMs > 2_000L) {
                        val safeTarget = if (realDur > 15_000L) {
                            pendingInitialSeekMs.coerceAtMost(realDur - 5_000L).coerceAtLeast(0L)
                        } else {
                            pendingInitialSeekMs
                        }
                        pendingInitialSeekMs = 0L
                        if (kotlin.math.abs(exoPlayer.currentPosition - safeTarget) > 1_500L) {
                            exoPlayer.seekTo(safeTarget)
                        }
                        positionMs = safeTarget
                        resumeNoticeText = strings.resumeFromFmt.format(formatDurationMs(safeTarget))
                    }
                    if (exoPlayer.audioSessionId > 0) {
                        chillAudioEffects.attachToSession(exoPlayer.audioSessionId, volumeScale, equalizerPreset)
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (nextEpisode != null && !autoNextCancelled && autoNextCountdownSec <= 0) {
                        autoNextCountdownSec = 5
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    chillAudioEffects.attachToSession(audioSessionId, volumeScale, equalizerPreset)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!usedFallbackStream && episode.fallbackStreamUrl.isNotBlank()) {
                    usedFallbackStream = true
                    val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.setMediaItem(MediaItem.fromUri(episode.fallbackStreamUrl))
                    if (resumeAt > 1_000L) {
                        exoPlayer.seekTo(resumeAt)
                    }
                    exoPlayer.prepare()
                    return
                }
                isBuffering = false
                playerError = "Không phát được luồng video này (${error.errorCodeName}). Hãy thử đổi Server/Tập khác."
            }

            override fun onTracksChanged(tracks: Tracks) {
                val discoveredTextTracks = ArrayList<SubtitleTrackOption>()
                tracks.groups.forEachIndexed { groupIdx, group ->
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (trackIdx in 0 until group.length) {
                            val fmt = group.getTrackFormat(trackIdx)
                            val lang = fmt.language.orEmpty().ifBlank { "und" }
                            val rawLabel = fmt.label.orEmpty().ifBlank { lang.uppercase() }
                            val isThai = lang.equals("th", true) ||
                                lang.equals("tha", true) ||
                                lang.startsWith("th-", true) ||
                                rawLabel.contains("thai", true) ||
                                rawLabel.contains("ไทย")
                            discoveredTextTracks.add(
                                SubtitleTrackOption(
                                    id = "exo_${groupIdx}_$trackIdx",
                                    label = "HLS/MKV: $rawLabel",
                                    languageCode = lang,
                                    isEmbedded = true,
                                    isThai = isThai,
                                    exoGroupIndex = groupIdx,
                                    exoTrackIndex = trackIdx
                                )
                            )
                        }
                    }
                }
                subtitleManager.updateEmbeddedTracks(
                    exoTextTracks = discoveredTextTracks,
                    config = subtitleConfig,
                    geminiApiKey = geminiApiKey,
                    onSelectEmbeddedTrack = { chosen ->
                        selectExoEmbeddedTextTrack(exoPlayer, chosen)
                    }
                )
            }

            override fun onCues(cueGroup: CueGroup) {
                val combinedText = cueGroup.cues
                    .mapNotNull { it.text?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    .joinToString("\n")
                    .ifBlank { null }
                subtitleManager.onEmbeddedPlayerCues(combinedText, subtitleConfig, geminiApiKey)
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            onReportTelemetry(CompanionPlayerTelemetry(isActive = false))
            val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
            val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
            if (finalPos > 2_000L) {
                onSaveProgress(finalPos, finalDur)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Execute remote playback & subtitle commands sent from the Smartphone Web Companion in real time!
    LaunchedEffect(remoteCommand?.id) {
        val cmd = remoteCommand ?: return@LaunchedEffect
        lastUserInteractionTick = System.currentTimeMillis()
        when (cmd.action.lowercase()) {
            "toggle_play" -> {
                if (isPreFlightBlocking) {
                    subtitleManager.dismissPreFlightAndPlayAnyway {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                } else {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            }
            "play" -> exoPlayer.play()
            "pause" -> exoPlayer.pause()
            "seek_to" -> {
                val target = cmd.valueLong.coerceAtLeast(0L)
                exoPlayer.seekTo(target)
                positionMs = target
            }
            "seek_rel" -> {
                val target = (exoPlayer.currentPosition + cmd.valueLong).coerceAtLeast(0L)
                exoPlayer.seekTo(target)
                positionMs = target
            }
            "next_episode" -> {
                nextEpisode?.let { onSwitchEpisode(it) }
            }
            "auto_sync" -> {
                if (!isAutoSyncing) {
                    subtitleManager.autoSyncCurrentSubtitle(
                        episode = episode,
                        aroundPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        config = subtitleConfig,
                        geminiApiKey = geminiApiKey,
                        isSilentBackgroundCheck = false,
                        onApplyDetectedSync = { newOffsetMs, newScale, _, lockGeminiAudio ->
                            applyAndSaveSeriesSync(
                                newOffsetMs = newOffsetMs,
                                newSpeedScale = newScale,
                                lockGeminiAudio = if (lockGeminiAudio) true else null
                            )
                        }
                    )
                }
            }
            "gemini_audio" -> {
                applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = true)
                subtitleManager.startGeminiRollingAudioSubtitles(
                    movie = movie,
                    episode = episode,
                    initialPositionMs = exoPlayer.currentPosition.coerceAtLeast(startPositionMs),
                    config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                    geminiApiKey = geminiApiKey,
                    onReadyToPlay = if (isPreFlightBlocking) {
                        {
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }
                    } else null
                )
            }
            "open_snap_modal" -> {
                openFastSnapModal()
            }
            "snap_cue" -> {
                val chosenCueStartMs = cmd.valueLong
                val anchorMs = if (cmd.secondLong > 0L) cmd.secondLong else exoPlayer.currentPosition.coerceAtLeast(0L)
                val adjustedAnchorMs = (anchorMs - 350L).coerceAtLeast(0L)
                val snappedOffsetMs =
                    (adjustedAnchorMs * subtitleConfig.subSpeedScale.toDouble()).toLong() - chosenCueStartMs
                applyAndSaveSeriesSync(
                    newOffsetMs = snappedOffsetMs,
                    newSpeedScale = subtitleConfig.subSpeedScale,
                    lockGeminiAudio = false
                )
            }
            "nudge_sub" -> {
                applyAndSaveSeriesSync(
                    newOffsetMs = subtitleConfig.syncOffsetMs + cmd.valueLong,
                    newSpeedScale = subtitleConfig.subSpeedScale
                )
            }
            "reset_sync" -> {
                applyAndSaveSeriesSync(0L, 1.0f)
            }
            "toggle_dual_sub" -> {
                onUpdateSubtitleConfig { it.copy(dualSubEnabled = !it.dualSubEnabled) }
            }
            "toggle_mask" -> {
                onUpdateSubtitleConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) }
            }
            "toggle_sub_drawer" -> {
                showSubtitleDrawer = !showSubtitleDrawer
            }
            "open_sub_drawer" -> {
                showSubtitleDrawer = true
            }
            "skip_preflight" -> {
                subtitleManager.dismissPreFlightAndPlayAnyway {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            }
            "exit_player" -> {
                val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                onExitPlayer()
            }
        }
        onConsumeRemoteCommand(cmd.id)
    }

    // If the user enters/syncs a brand-new Gemini API Key while waiting on a no-sub Pre-Flight screen,
    // immediately start Gemini Audio Subtitle generation (guarded by lastTriedKey so failed models never loop!).
    var lastAutoTriggeredGeminiKey by remember(episode.id) { mutableStateOf(geminiApiKey) }
    LaunchedEffect(geminiApiKey, preFlightState.needsGeminiKeyQr) {
        if (preFlightState.needsGeminiKeyQr &&
            geminiApiKey.isNotBlank() &&
            geminiApiKey != lastAutoTriggeredGeminiKey
        ) {
            lastAutoTriggeredGeminiKey = geminiApiKey
            subtitleManager.startGeminiRollingAudioSubtitles(
                movie = movie,
                episode = episode,
                initialPositionMs = exoPlayer.currentPosition.coerceAtLeast(startPositionMs),
                config = subtitleConfig,
                geminiApiKey = geminiApiKey,
                onReadyToPlay = {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    lastUserInteractionTick = System.currentTimeMillis()
                }
            )
        }
    }

    LaunchedEffect(
        subtitleConfig.preferredPrimaryLang,
        subtitleConfig.autoTranslateToThai,
        subtitleConfig.dualSubEnabled,
        subtitleConfig.secondaryLang,
        subtitleConfig.secondaryLangCode,
        subtitleConfig.translationEngine,
        subtitleConfig.geminiModelId,
        geminiApiKey
    ) {
        if (!isPreFlightBlocking) {
            subtitleManager.reapplyConfig(subtitleConfig, geminiApiKey)
        }
    }

    // Wireless subtitle uploaded from Phone QR Companion (works both during Pre-Flight and mid-movie!)
    LaunchedEffect(incomingCompanionSub) {
        val sub = incomingCompanionSub ?: return@LaunchedEffect
        subtitleManager.addAndSelectExternalSubtitle(
            newSub = sub,
            config = subtitleConfig,
            geminiApiKey = geminiApiKey,
            onReadyToPlay = if (isPreFlightBlocking) {
                {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            } else null
        )
        onConsumeCompanionSub()
    }

    val localSubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onAfterSystemOverlayResult()
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = SubtitleParser.decodeBytesSmart(bytes)
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "usb_sub.srt"
                    val newRef = ExternalSubtitleRef(
                        id = "usb_${System.currentTimeMillis()}",
                        label = "USB: $fileName",
                        languageCode = if (fileName.contains("th", true)) "th" else "vi",
                        urlOrContent = decoded,
                        isRawContent = true
                    )
                    subtitleManager.addAndSelectExternalSubtitle(newRef, subtitleConfig, geminiApiKey)
                }
            }
        }
    }

    LaunchedEffect(exoPlayer, subtitleConfig) {
        var tickCount = 0
        var cachedSnapCues: List<SnapCueCandidate> = emptyList()
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pendingInitialSeekMs <= 2_000L || pos > 2_000L) {
                positionMs = pos
            }
            if (dur > 0L) durationMs = dur
            subtitleManager.onPlaybackPosition(positionMs, subtitleConfig)

            tickCount++
            if (tickCount % 10 == 1) {
                cachedSnapCues = subtitleManager.getBilingualCuesForVisualSnap(
                    aroundPositionMs = positionMs,
                    config = subtitleConfig,
                    limit = 16
                )
            }
            if (tickCount % 4 == 0) {
                onReportTelemetry(
                    CompanionPlayerTelemetry(
                        isActive = true,
                        movieId = movie.id,
                        movieTitle = movie.title,
                        episodeId = episode.id,
                        episodeName = episode.name,
                        streamUrl = episode.streamUrl,
                        posterUrl = movie.posterUrl,
                        isPlaying = exoPlayer.isPlaying,
                        isBuffering = isBuffering,
                        isPreFlightBlocking = isPreFlightBlocking,
                        preFlightStep = preFlightState.stepTitle.ifBlank { preFlightState.stepDetail },
                        positionMs = positionMs,
                        durationMs = durationMs,
                        syncOffsetMs = subtitleConfig.syncOffsetMs,
                        subSpeedScale = subtitleConfig.subSpeedScale,
                        dualSubEnabled = subtitleConfig.dualSubEnabled,
                        hardsubMaskEnabled = subtitleConfig.hardsubMaskEnabled,
                        primaryThaiText = renderState.primaryThaiText.orEmpty(),
                        secondaryText = renderState.secondaryText.orEmpty(),
                        statusBadge = renderState.statusBadge,
                        isAutoSyncing = isAutoSyncing,
                        snapCandidates = cachedSnapCues
                    )
                )
            }
            // Save watch progress every ~3 seconds so abrupt exits NEVER restart from 00:00!
            if (tickCount % 38 == 0 && exoPlayer.isPlaying && positionMs > 2_000L) {
                onSaveProgress(positionMs, durationMs)
            }
            delay(80L)
        }
    }

    LaunchedEffect(
        showControls,
        showSubtitleDrawer,
        showFastSnapModal,
        isPreFlightBlocking,
        activeChillMenu,
        lastUserInteractionTick,
        isPlaying
    ) {
        if (showControls &&
            activeChillMenu == ChillPlayerMenu.NONE &&
            !showSubtitleDrawer &&
            !showFastSnapModal &&
            !isPreFlightBlocking &&
            isPlaying
        ) {
            delay(5_000L)
            showControls = false
            runCatching { surfaceFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isMinimized, showControls, showSubtitleDrawer, showFastSnapModal, isPreFlightBlocking) {
        if (!isMinimized && !showSubtitleDrawer && !showFastSnapModal && !isPreFlightBlocking) {
            runCatching { view.requestFocusFromTouch() }
            if (showControls && !isLocked) {
                runCatching { playPauseFocusRequester.requestFocus() }
            } else {
                runCatching { surfaceFocusRequester.requestFocus() }
            }
        }
    }

    BackHandler(enabled = !isMinimized) {
        when {
            isLocked -> {
                lockHintVisible = true
                showControls = true
            }
            activeChillMenu != ChillPlayerMenu.NONE -> {
                activeChillMenu = ChillPlayerMenu.NONE
            }
            showFastSnapModal -> closeFastSnapModal(resumeIfWasPlaying = true)
            showSubtitleDrawer -> showSubtitleDrawer = false
            isPreFlightBlocking -> onExitPlayer()
            else -> {
                val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                onMinimizePlayer()
            }
        }
    }

    val containerModifier = if (isMinimized) {
        modifier
            .navigationBarsPadding()
            .padding(end = 12.dp, bottom = 84.dp)
            .width(if (isTv) 264.dp else 192.dp)
            .aspectRatio(16f / 9f)
            .shadow(18.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(14.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onRestorePlayer() })
            }
    } else {
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
            .focusRequester(surfaceFocusRequester)
            .focusProperties {
                canFocus = !showSubtitleDrawer && !showFastSnapModal && !isPreFlightBlocking
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                if (isPreFlightBlocking || showFastSnapModal) return@onKeyEvent false

                lastUserInteractionTick = System.currentTimeMillis()
                val keyCode = keyEvent.nativeKeyEvent.keyCode

                if (isLocked) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                        isLocked = false
                        lockHintVisible = false
                        showControls = true
                    } else {
                        lockHintVisible = true
                        showControls = true
                    }
                    return@onKeyEvent true
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        return@onKeyEvent true
                    }
                    KeyEvent.KEYCODE_CAPTIONS -> {
                        showSubtitleDrawer = !showSubtitleDrawer
                        return@onKeyEvent true
                    }
                }

                if (!showControls && !showSubtitleDrawer) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            openFastSnapModal()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showControls = true
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(isLocked, isPreFlightBlocking, showFastSnapModal, showSubtitleDrawer, viewportSize) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isPreFlightBlocking || showFastSnapModal || showSubtitleDrawer) return@detectTapGestures
                        if (isLocked) {
                            lockHintVisible = true
                            return@detectTapGestures
                        }
                        lastUserInteractionTick = System.currentTimeMillis()
                        val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                        when {
                            offset.x < w * 0.35f -> {
                                val nextPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(nextPos)
                                positionMs = nextPos
                                doubleTapSide = -1
                                playerScope.launch {
                                    delay(550L)
                                    if (doubleTapSide == -1) doubleTapSide = 0
                                }
                            }
                            offset.x > w * 0.65f -> {
                                val nextPos = (exoPlayer.currentPosition + 10_000L).let {
                                    if (durationMs > 0L) it.coerceAtMost(durationMs) else it
                                }
                                exoPlayer.seekTo(nextPos)
                                positionMs = nextPos
                                doubleTapSide = 1
                                playerScope.launch {
                                    delay(550L)
                                    if (doubleTapSide == 1) doubleTapSide = 0
                                }
                            }
                            else -> {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                showControls = true
                            }
                        }
                    },
                    onTap = {
                        if (isPreFlightBlocking || showFastSnapModal) return@detectTapGestures
                        lastUserInteractionTick = System.currentTimeMillis()
                        if (isLocked) {
                            lockHintVisible = true
                            showControls = true
                        } else if (activeChillMenu != ChillPlayerMenu.NONE) {
                            activeChillMenu = ChillPlayerMenu.NONE
                        } else if (showSubtitleDrawer) {
                            showSubtitleDrawer = false
                        } else {
                            showControls = !showControls
                        }
                    }
                )
            }
            .pointerInput(isLocked, isPreFlightBlocking, showFastSnapModal, showSubtitleDrawer, viewportSize) {
                var dragMode = ChillGestureFeedback.NONE
                var startX = 0f
                var accumDx = 0f
                var accumDy = 0f
                var initialBright = 1.0f
                var initialVol = 1.0f
                detectDragGestures(
                    onDragStart = { start ->
                        if (isLocked || isPreFlightBlocking || showFastSnapModal || showSubtitleDrawer) {
                            if (isLocked) lockHintVisible = true
                            return@detectDragGestures
                        }
                        dragMode = ChillGestureFeedback.NONE
                        startX = start.x
                        accumDx = 0f
                        accumDy = 0f
                        initialBright = brightnessLevel
                        initialVol = volumeScale
                    },
                    onDrag = { change, dragAmount ->
                        if (isLocked || isPreFlightBlocking || showFastSnapModal || showSubtitleDrawer) return@detectDragGestures
                        change.consume()
                        accumDx += dragAmount.x
                        accumDy += dragAmount.y
                        val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                        val h = viewportSize.height.toFloat().coerceAtLeast(1f)
                        if (dragMode == ChillGestureFeedback.NONE &&
                            (kotlin.math.abs(accumDx) > 22f || kotlin.math.abs(accumDy) > 22f)
                        ) {
                            dragMode = if (kotlin.math.abs(accumDy) > kotlin.math.abs(accumDx)) {
                                if (startX < w * 0.5f) ChillGestureFeedback.BRIGHTNESS else ChillGestureFeedback.VOLUME
                            } else {
                                ChillGestureFeedback.SEEK
                            }
                        }
                        when (dragMode) {
                            ChillGestureFeedback.BRIGHTNESS -> {
                                val nextB = (initialBright - (accumDy / (h * 0.65f))).coerceIn(0.08f, 1.0f)
                                applyBrightness(nextB)
                                gestureFeedback = ChillGestureFeedback.BRIGHTNESS
                                gesturePercent = (nextB * 100).toInt()
                            }
                            ChillGestureFeedback.VOLUME -> {
                                val nextV = (initialVol - (accumDy / (h * 0.55f)) * 2.0f).coerceIn(0f, 2.0f)
                                applyVolumeScale(nextV)
                                gestureFeedback = ChillGestureFeedback.VOLUME
                                gesturePercent = (nextV * 100).toInt()
                            }
                            ChillGestureFeedback.SEEK -> {
                                val secs = ((accumDx / w) * 90f).toInt()
                                seekOffsetSecs = secs
                                gestureFeedback = ChillGestureFeedback.SEEK
                            }
                            ChillGestureFeedback.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (dragMode == ChillGestureFeedback.SEEK && seekOffsetSecs != 0) {
                            val target = (exoPlayer.currentPosition + seekOffsetSecs * 1000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            positionMs = target
                        }
                        dragMode = ChillGestureFeedback.NONE
                        gestureFeedback = ChillGestureFeedback.NONE
                        seekOffsetSecs = 0
                    },
                    onDragCancel = {
                        dragMode = ChillGestureFeedback.NONE
                        gestureFeedback = ChillGestureFeedback.NONE
                        seekOffsetSecs = 0
                    }
                )
            }
    }

    Box(modifier = containerModifier) {
        // 1. Hardware Video Surface (SAME AndroidView in both Fullscreen & Bottom-Right Pop-out -> ZERO reload!)
        val enforced = if (isMinimized) null else aspectRatioMode.enforcedRatio
        val videoSurfaceModifier = if (enforced != null) {
            Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(enforced)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    subtitleView?.visibility = View.GONE
                    keepScreenOn = true
                    resizeMode = aspectRatioMode.resizeMode
                    player = exoPlayer
                }
            },
            update = { pv ->
                if (pv.player !== exoPlayer) {
                    pv.player = exoPlayer
                }
                if (pv.resizeMode != aspectRatioMode.resizeMode) {
                    pv.resizeMode = aspectRatioMode.resizeMode
                }
            },
            modifier = videoSurfaceModifier
        )

        // 2. Anti-Overlap Framed Subtitle Overlay (Expanded mode)
        if (!isMinimized && !isPreFlightBlocking) {
            ThaiCinemaSubtitleOverlay(
                renderState = renderState,
                config = subtitleConfig,
                previewSampleWhenEmpty = showSubtitleDrawer
            )
        }

        // 2a. Auto-Next 5s Countdown Trigger + Floating Skip Intro (+85s customizable) & Auto-Next Overlays
        LaunchedEffect(positionMs, durationMs, nextEpisode?.id, autoNextCancelled, isMinimized) {
            if (!isMinimized && nextEpisode != null && !autoNextCancelled && autoNextCountdownSec == 0) {
                if (durationMs > 45_000L && (durationMs - positionMs) in 1_000L..15_000L) {
                    autoNextCountdownSec = 5
                }
            }
        }
        LaunchedEffect(autoNextCountdownSec, nextEpisode?.id) {
            val targetNext = nextEpisode
            if (autoNextCountdownSec > 0 && targetNext != null) {
                delay(1_000L)
                if (autoNextCountdownSec == 1) {
                    autoNextCountdownSec = 0
                    val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                    val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                    if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                    onSwitchEpisode(targetNext)
                } else if (autoNextCountdownSec > 1) {
                    autoNextCountdownSec -= 1
                }
            }
        }

        val effectiveSkipSec = skipIntroSec.coerceIn(10, 300)
        val maxIntroWindowMs = maxOf(180_000L, (effectiveSkipSec + 95) * 1000L)
        val showSkipIntroPill = !isMinimized &&
            !isPreFlightBlocking &&
            !isLocked &&
            !skipIntroDismissed &&
            autoNextCountdownSec == 0 &&
            positionMs in 3_000L..maxIntroWindowMs &&
            (durationMs == 0L || durationMs > (effectiveSkipSec + 50) * 1000L)

        if (showSkipIntroPill) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (showControls) 112.dp else 28.dp)
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(99.dp),
                        focusedBorderColor = EmeraldNeon,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                        onClick = {
                            val target = (exoPlayer.currentPosition + effectiveSkipSec * 1000L).let {
                                if (durationMs > 10_000L) it.coerceAtMost(durationMs - 5_000L) else it
                            }
                            exoPlayer.seekTo(target)
                            positionMs = target
                            skipIntroDismissed = true
                        }
                    )
                    .background(Color(0x9910121A))
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Skip Intro (+${effectiveSkipSec}s)",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip Intro",
                    tint = EmeraldNeon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (!isMinimized && autoNextCountdownSec > 0 && nextEpisode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 112.dp)
                    .width(268.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .background(Color(0xCC10121A))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NEXT EPISODE IN ${autoNextCountdownSec}s",
                    color = EmeraldNeon,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${movie.title} • ${nextEpisode.name}",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = EmeraldNeon,
                                onClick = {
                                    autoNextCountdownSec = 0
                                    val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                                    val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                                    if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                                    onSwitchEpisode(nextEpisode)
                                }
                            )
                            .background(EmeraldPrimary)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶ Watch Now",
                            color = Color(0xFF06120A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                onClick = {
                                    autoNextCancelled = true
                                    autoNextCountdownSec = 0
                                }
                            )
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2b. Floating Bottom-Right Pop-out Mini-Player Controls (Tap to expand, X to close!)
        if (isMinimized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x6606070D),
                                Color.Transparent,
                                Color(0xCC06070D)
                            )
                        )
                    )
            ) {
                // Top-right explicit X Close button (ONLY way to completely shut down the Pop-out!)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .tvFocusableClickable(
                            shape = CircleShape,
                            focusedBorderColor = LiveRed,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                            onClick = {
                                val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                                val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                                if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                                onExitPlayer()
                            }
                        )
                        .background(Color(0xCC0D1120)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.miniCloseDesc,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Bottom mini bar: Movie title + Play/Pause + Expand
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .tvFocusableClickable(
                                shape = CircleShape,
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            )
                            .background(Color(0xB80F1220)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .tvFocusableClickable(
                                shape = CircleShape,
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                onClick = onRestorePlayer
                            )
                            .background(Color(0xB80F1220)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = strings.miniExpandDesc,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // 3. Buffering Spinner (only shown after Pre-Flight finishes)
        if (isBuffering && !isPreFlightBlocking) {
            CircularProgressIndicator(
                color = ThaiGold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isMinimized) 26.dp else 48.dp)
            )
        }

        // 4. PRE-FLIGHT SUBTITLE LOADER & GUARANTEE GATE (Before Video Starts!)
        if (!isMinimized && isPreFlightBlocking) {
            PreFlightSubtitleGateOverlay(
                movie = movie,
                episode = episode,
                state = preFlightState,
                hasGeminiKeyInRam = geminiApiKey.isNotBlank(),
                isTvHardware = isTvHardware,
                geminiModelId = subtitleConfig.geminiModelId,
                onSaveGeminiKeyInline = onSaveGeminiKeyInline,
                onStartGeminiAudioSub = {
                    exoPlayer.pause()
                    subtitleManager.startGeminiRollingAudioSubtitles(
                        movie = movie,
                        episode = episode,
                        initialPositionMs = exoPlayer.currentPosition.coerceAtLeast(startPositionMs),
                        config = subtitleConfig,
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = {
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                            lastUserInteractionTick = System.currentTimeMillis()
                        }
                    )
                },
                onCustomSearch = { query ->
                    exoPlayer.pause()
                    subtitleManager.searchAndLoadCustomQuery(
                        customQuery = query,
                        movie = movie,
                        episode = episode,
                        config = subtitleConfig,
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = {
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }
                    )
                },
                onOpenQrUpload = onOpenQrCompanion,
                onSkipAndPlayNow = {
                    subtitleManager.dismissPreFlightAndPlayAnyway {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                },
                onCancelExit = onExitPlayer
            )
        }

        if (!isMinimized) {
            playerError?.let { errMsg ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CinemaObsidian.copy(alpha = 0.94f))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = errMsg, color = LiveRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(shape = RoundedCornerShape(8.dp), onClick = onExitPlayer)
                            .background(ThaiGold)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = strings.backToChooseSource, color = CinemaObsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Minimalist, 100% Overflow-Proof 2026 Cinema HUD (Portrait Phone, Landscape Phone, Tablet & TV)
        AnimatedVisibility(
            visible = !isMinimized && showControls && !isLocked && !showSubtitleDrawer && !showFastSnapModal && !isPreFlightBlocking && activeChillMenu == ChillPlayerMenu.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val primaryLangBadge = subtitleConfig.resolvedPrimaryLanguage().shortBadge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xB806070D),
                                Color(0x2206070D),
                                Color(0x2206070D),
                                Color(0xCC06070D)
                            )
                        )
                    )
            ) {
                // 5a. Clean Floating Top Bar (Back -> Pop-out Mini-Player + Title + Max 3 Compact Actions)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .displayCutoutPadding()
                        .padding(horizontal = if (isTv) 24.dp else 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .tvFocusableClickable(
                                    shape = CircleShape,
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = {
                                        val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                                        val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                                        if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                                        onMinimizePlayer()
                                    }
                                )
                                .background(CinemaGlass),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backBtn,
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movie.title,
                                color = TextPrimary,
                                fontSize = if (isTv) 16.sp else 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${episode.name} • ${renderState.statusBadge}",
                                color = EmeraldNeon,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // #3 1-Tap AI Voice Dubbing (Auto-Ducking) Pill
                        Row(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = if (ttsDubEnabled) EmeraldNeon else CinemaGlassBorder,
                                    onClick = {
                                        lastUserInteractionTick = System.currentTimeMillis()
                                        ttsDubEnabled = !ttsDubEnabled
                                    }
                                )
                                .background(if (ttsDubEnabled) EmeraldPrimary.copy(alpha = 0.28f) else CinemaGlass)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (ttsDubEnabled) "DUB ON" else "DUB",
                                color = if (ttsDubEnabled) EmeraldNeon else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        // #1 1-Tap Hardsub Cover Mask Pill
                        Row(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = if (subtitleConfig.hardsubMaskEnabled) EmeraldNeon else CinemaGlassBorder,
                                    onClick = {
                                        lastUserInteractionTick = System.currentTimeMillis()
                                        onUpdateSubtitleConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) }
                                    }
                                )
                                .background(if (subtitleConfig.hardsubMaskEnabled) EmeraldPrimary.copy(alpha = 0.28f) else CinemaGlass)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (subtitleConfig.hardsubMaskEnabled) "MASK ON" else "MASK",
                                color = if (subtitleConfig.hardsubMaskEnabled) EmeraldNeon else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        // Primary Universal AI Subtitle Pill
                        Row(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = EmeraldNeon,
                                    onClick = {
                                        lastUserInteractionTick = System.currentTimeMillis()
                                        activeChillMenu = ChillPlayerMenu.NONE
                                        showSubtitleDrawer = true
                                    }
                                )
                                .background(BrandEmeraldVioletBrush)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                contentDescription = "Phụ đề",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = primaryLangBadge,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }

                        // Rotate Screen Button (Phones)
                        if (!isTv && activity != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .tvFocusableClickable(
                                        shape = CircleShape,
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        onClick = {
                                            lastUserInteractionTick = System.currentTimeMillis()
                                            val nextLand = !isLandscapeRotated
                                            isLandscapeRotated = nextLand
                                            activity.requestedOrientation = if (nextLand) {
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                        }
                                    )
                                    .background(if (isLandscapeRotated) VioletGlow.copy(alpha = 0.28f) else CinemaGlass),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ScreenRotation,
                                    contentDescription = "Xoay ngang",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Unified More Menu (Speed / Equalizer + 200% Boost / Aspect / Sleep / PiP / Auto-Sync)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .tvFocusableClickable(
                                    shape = CircleShape,
                                    focusedBorderColor = VioletGlow,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = {
                                        lastUserInteractionTick = System.currentTimeMillis()
                                        activeChillMenu = ChillPlayerMenu.MORE
                                    }
                                )
                                .background(CinemaGlass),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn phát",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 5b. Centered Cinema Transport Controls (-10s • Play/Pause • +10s)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerControlIconBtn(
                        icon = Icons.Default.Replay10,
                        contentDesc = "Tua lùi 10s",
                        onClick = {
                            lastUserInteractionTick = System.currentTimeMillis()
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        }
                    )

                    PlayerControlIconBtn(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDesc = if (isPlaying) "Tạm dừng" else "Phát",
                        isPrimary = true,
                        modifier = Modifier.focusRequester(playPauseFocusRequester),
                        onClick = {
                            lastUserInteractionTick = System.currentTimeMillis()
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    )

                    PlayerControlIconBtn(
                        icon = Icons.Default.Forward10,
                        contentDesc = "Tua tới 10s",
                        onClick = {
                            lastUserInteractionTick = System.currentTimeMillis()
                            exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L)
                        }
                    )
                }

                // 5c. Sleek Bottom Capsule: Seekbar + Exactly 4 Clean Equal-Width Utility Actions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = if (isTv) 28.dp else 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CinemaGlass)
                        .border(1.dp, CinemaGlassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (movie.contentType != ContentType.LIVE_CHANNEL && durationMs > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatDurationMs(positionMs),
                                color = EmeraldNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                                onValueChange = { newVal ->
                                    lastUserInteractionTick = System.currentTimeMillis()
                                    exoPlayer.seekTo(newVal.toLong())
                                    positionMs = newVal.toLong()
                                },
                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldNeon,
                                    activeTrackColor = EmeraldPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                                )
                            )
                            Text(
                                text = formatDurationMs(durationMs),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Single clean 4-action bottom strip (zero overflow on any width!)
                    val volPct = (volumeScale * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomHudActionChip(
                            icon = Icons.Default.Lock,
                            label = strings.lockBtn,
                            tint = TextPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                isLocked = true
                                activeChillMenu = ChillPlayerMenu.NONE
                                showControls = false
                                lockHintVisible = false
                            }
                        )

                        BottomHudActionChip(
                            icon = if (volPct > 100) Icons.Default.VolumeUp else Icons.Default.Speed,
                            label = if (volPct > 100) "$volPct%" else "${playbackSpeed}x",
                            tint = if (volPct > 100) AmberBoost else VioletGlow,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                lastUserInteractionTick = System.currentTimeMillis()
                                activeChillMenu = if (volPct > 100) ChillPlayerMenu.EQUALIZER else ChillPlayerMenu.SPEED
                            }
                        )

                        BottomHudActionChip(
                            icon = Icons.Default.Sync,
                            label = if (subtitleConfig.syncOffsetMs != 0L) {
                                "%+.1fs".format(subtitleConfig.syncOffsetMs / 1000.0)
                            } else strings.syncSubBtn,
                            tint = EmeraldNeon,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                lastUserInteractionTick = System.currentTimeMillis()
                                openFastSnapModal()
                            }
                        )

                        if (nextEpisode != null) {
                            BottomHudActionChip(
                                icon = Icons.Default.SkipNext,
                                label = strings.nextEpBtn,
                                tint = EmeraldNeon,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val finalPos = maxOf(exoPlayer.currentPosition.coerceAtLeast(0L), positionMs)
                                    val finalDur = maxOf(exoPlayer.duration.coerceAtLeast(0L), durationMs)
                                    if (finalPos > 2_000L) onSaveProgress(finalPos, finalDur)
                                    onSwitchEpisode(nextEpisode)
                                }
                            )
                        } else {
                            BottomHudActionChip(
                                icon = Icons.Default.AspectRatio,
                                label = strings.aspectBtn,
                                tint = TextPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    lastUserInteractionTick = System.currentTimeMillis()
                                    activeChillMenu = ChillPlayerMenu.ASPECT
                                }
                            )
                        }
                    }
                }
            }
        }

        // 5a. Chill-Player Edge Lock, Gesture Bars & Double-Tap Ripples (ALWAYS active above HUD when expanded)
        if (!isMinimized && !isPreFlightBlocking && !showFastSnapModal && !showSubtitleDrawer) {
            ChillGestureAndLockOverlay(
                isLocked = isLocked,
                showControls = showControls,
                lockHintVisible = lockHintVisible,
                gestureFeedback = gestureFeedback,
                gesturePercent = gesturePercent,
                seekOffsetSecs = seekOffsetSecs,
                targetSeekPreviewMs = (positionMs + seekOffsetSecs * 1000L).coerceAtLeast(0L),
                durationMs = durationMs,
                doubleTapSide = doubleTapSide,
                onToggleLock = {
                    val nextLocked = !isLocked
                    isLocked = nextLocked
                    if (nextLocked) {
                        activeChillMenu = ChillPlayerMenu.NONE
                        showControls = false
                    } else {
                        showControls = true
                    }
                    lockHintVisible = false
                    lastUserInteractionTick = System.currentTimeMillis()
                },
                formatTimeFn = ::formatDurationMs
            )

            // Floating Chill-Player Responsive Popups (Speed, Equalizer + 200% Boost, Aspect, Sleep, PiP, AI Sync)
            ChillPlayerMenuOverlay(
                activeMenu = activeChillMenu,
                aspectRatio = aspectRatioMode,
                playbackSpeed = playbackSpeed,
                equalizerPreset = equalizerPreset,
                volumeScale = volumeScale,
                sleepTimerMinutes = sleepTimerMinutes,
                canEnterPip = !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null,
                isAutoSyncing = isAutoSyncing,
                showTvQrOption = isTvHardware,
                onEnterPip = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                        runCatching {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            activity.enterPictureInPictureMode(params)
                        }
                    }
                },
                onTriggerAiAutoSync = {
                    if (!isAutoSyncing) {
                        subtitleManager.autoSyncCurrentSubtitle(
                            episode = episode,
                            aroundPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            config = subtitleConfig,
                            geminiApiKey = geminiApiKey,
                            isSilentBackgroundCheck = false,
                            onApplyDetectedSync = { newOffsetMs, newScale, _, lockGeminiAudio ->
                                applyAndSaveSeriesSync(
                                    newOffsetMs = newOffsetMs,
                                    newSpeedScale = newScale,
                                    lockGeminiAudio = if (lockGeminiAudio) true else null
                                )
                            }
                        )
                    }
                },
                onOpenQrCompanion = onOpenQrCompanion,
                onNavigateMenu = { menu ->
                    lastUserInteractionTick = System.currentTimeMillis()
                    activeChillMenu = menu
                },
                onSelectAspectRatio = { mode ->
                    aspectRatioMode = mode
                    lastUserInteractionTick = System.currentTimeMillis()
                },
                onSelectPlaybackSpeed = { rate ->
                    playbackSpeed = rate
                    exoPlayer.setPlaybackSpeed(rate)
                    lastUserInteractionTick = System.currentTimeMillis()
                },
                onSelectEqualizer = { preset ->
                    equalizerPreset = preset
                    chillAudioEffects.attachToSession(exoPlayer.audioSessionId, volumeScale, preset)
                    chillAudioEffects.applyEqualizerPreset(preset)
                    lastUserInteractionTick = System.currentTimeMillis()
                },
                onChangeVolumeScale = { nextVol ->
                    applyVolumeScale(nextVol)
                    lastUserInteractionTick = System.currentTimeMillis()
                },
                onSelectSleepTimer = { mins ->
                    sleepTimerMinutes = mins
                    lastUserInteractionTick = System.currentTimeMillis()
                }
            )
        }

        // Auto-Resume Toast Pill
        AnimatedVisibility(
            visible = !isMinimized && resumeNoticeText != null && autoSyncFeedback == null && !isPreFlightBlocking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(CinemaObsidian.copy(alpha = 0.90f))
                    .border(1.dp, EmeraldNeon, RoundedCornerShape(99.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = resumeNoticeText.orEmpty(),
                    color = EmeraldNeon,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Auto-Sync Toast Feedback Pill (visible even when OSD controls auto-hide!)
        AnimatedVisibility(
            visible = !isMinimized && autoSyncFeedback != null && !isPreFlightBlocking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaSurfaceElevated.copy(alpha = 0.96f))
                    .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = autoSyncFeedback.orEmpty(),
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5b. Pause-Locked 3-Second Bilingual Cue Snap Modal (TV Remote 1-Click Sync + Series Memory)
        if (!isMinimized && showFastSnapModal && !isPreFlightBlocking) {
            val bilingualSnapCues = remember(
                snapAnchorMovieMs,
                selectedTrackId,
                subtitleConfig.syncOffsetMs,
                subtitleConfig.subSpeedScale
            ) {
                subtitleManager.getBilingualCuesForVisualSnap(
                    aroundPositionMs = snapAnchorMovieMs,
                    config = subtitleConfig,
                    limit = 28
                )
            }
            QuickBilingualSnapModal(
                anchorMovieMs = snapAnchorMovieMs,
                currentOffsetMs = subtitleConfig.syncOffsetMs,
                currentSpeedScale = subtitleConfig.subSpeedScale,
                bilingualCues = bilingualSnapCues,
                isAutoSyncing = isAutoSyncing,
                hasGeminiKeyInRam = geminiApiKey.isNotBlank(),
                isTvHardware = isTvHardware,
                totalSeriesEpisodes = allEpisodes.size.coerceAtLeast(1),
                onPickCue = { chosenCue ->
                    val adjustedAnchorMs = (snapAnchorMovieMs - 350L).coerceAtLeast(0L)
                    val snappedOffsetMs =
                        (adjustedAnchorMs * subtitleConfig.subSpeedScale.toDouble()).toLong() - chosenCue.startMs
                    applyAndSaveSeriesSync(
                        newOffsetMs = snappedOffsetMs,
                        newSpeedScale = subtitleConfig.subSpeedScale,
                        lockGeminiAudio = false
                    )
                    closeFastSnapModal(resumeIfWasPlaying = true)
                },
                onNudgeOffsetMs = { deltaMs ->
                    applyAndSaveSeriesSync(
                        newOffsetMs = subtitleConfig.syncOffsetMs + deltaMs,
                        newSpeedScale = subtitleConfig.subSpeedScale
                    )
                },
                onResetOffsetZero = {
                    applyAndSaveSeriesSync(0L, 1.0f)
                },
                onTriggerTwoAnchorAiSync = {
                    closeFastSnapModal(resumeIfWasPlaying = true)
                    subtitleManager.autoSyncCurrentSubtitle(
                        episode = episode,
                        aroundPositionMs = snapAnchorMovieMs,
                        config = subtitleConfig,
                        geminiApiKey = geminiApiKey,
                        isSilentBackgroundCheck = false,
                        onApplyDetectedSync = { newOffsetMs, newScale, _, lockGeminiAudio ->
                            applyAndSaveSeriesSync(
                                newOffsetMs = newOffsetMs,
                                newSpeedScale = newScale,
                                lockGeminiAudio = if (lockGeminiAudio) true else null
                            )
                        }
                    )
                },
                onSwitchSeriesToGeminiAudio = {
                    applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = true)
                    closeFastSnapModal(resumeIfWasPlaying = true)
                    subtitleManager.startGeminiRollingAudioSubtitles(
                        movie = movie,
                        episode = episode,
                        initialPositionMs = snapAnchorMovieMs,
                        config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = null
                    )
                },
                onOpenQrForKey = {
                    closeFastSnapModal(resumeIfWasPlaying = false)
                    onOpenQrCompanion()
                },
                onDismissAndResume = {
                    closeFastSnapModal(resumeIfWasPlaying = true)
                }
            )
        }

        // 6. Live Subtitle Customization Drawer
        if (!isMinimized && showSubtitleDrawer) {
            val nearbySnapCues = remember(
                positionMs,
                selectedTrackId,
                subtitleConfig.syncOffsetMs,
                subtitleConfig.subSpeedScale
            ) {
                subtitleManager.getBilingualCuesForVisualSnap(
                    aroundPositionMs = positionMs,
                    config = subtitleConfig,
                    limit = 16
                )
            }
            val isLockedGeminiForSeries = seriesProfile?.preferGeminiAudioSub == true
            SubtitleSettingsSheet(
                config = subtitleConfig,
                availableTracks = availableTracks,
                selectedTrackId = selectedTrackId,
                defaultSearchQuery = movie.originalTitle.ifBlank { movie.title },
                discoveredModels = discoveredGeminiModels,
                hasGeminiKeyInRam = geminiApiKey.isNotBlank(),
                isTvHardware = isTvHardware,
                isAutoSyncing = isAutoSyncing,
                preferGeminiAudioForSeries = isLockedGeminiForSeries,
                totalSeriesEpisodes = allEpisodes.size.coerceAtLeast(1),
                nearbyCuesForSnap = nearbySnapCues,
                onSelectTrack = { chosen ->
                    val lockAi = chosen.id.startsWith("ai_audio_")
                    applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = if (lockAi) null else false)
                    subtitleManager.selectTrack(
                        track = chosen,
                        config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        onSelectEmbeddedTrack = { emb ->
                            selectExoEmbeddedTextTrack(exoPlayer, emb)
                        }
                    )
                },
                onSearchOnlineSub = { customQ ->
                    showSubtitleDrawer = false
                    exoPlayer.pause()
                    applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = false)
                    subtitleManager.searchAndLoadCustomQuery(
                        customQuery = customQ,
                        movie = movie,
                        episode = episode,
                        config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = {
                            exoPlayer.playWhenReady = true
                            exoPlayer.play()
                        }
                    )
                },
                onSelectGeminiModel = onSelectGeminiModel,
                onSaveGeminiKeyInline = onSaveGeminiKeyInline,
                onRefreshGeminiModels = onRefreshGeminiModels,
                onTriggerGeminiAudioSubNow = {
                    showSubtitleDrawer = false
                    applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = allEpisodes.size > 1)
                    subtitleManager.startGeminiRollingAudioSubtitles(
                        movie = movie,
                        episode = episode,
                        initialPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey,
                        onReadyToPlay = null
                    )
                },
                onTogglePreferGeminiAudioForSeries = {
                    val nextLock = !isLockedGeminiForSeries
                    onSaveSeriesProfile { it.copy(preferGeminiAudioSub = nextLock) }
                    if (nextLock && geminiApiKey.isNotBlank()) {
                        showSubtitleDrawer = false
                        onUpdateSubtitleConfig { it.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f) }
                        subtitleManager.startGeminiRollingAudioSubtitles(
                            movie = movie,
                            episode = episode,
                            initialPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                            geminiApiKey = geminiApiKey,
                            onReadyToPlay = null
                        )
                    }
                },
                onAutoSyncNow = {
                    subtitleManager.autoSyncCurrentSubtitle(
                        episode = episode,
                        aroundPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                        config = subtitleConfig,
                        geminiApiKey = geminiApiKey,
                        isSilentBackgroundCheck = false,
                        onApplyDetectedSync = { newOffsetMs, newScale, _, lockGeminiAudio ->
                            applyAndSaveSeriesSync(
                                newOffsetMs = newOffsetMs,
                                newSpeedScale = newScale,
                                lockGeminiAudio = if (lockGeminiAudio) true else null
                            )
                        }
                    )
                },
                onOpenFastSnapModal = {
                    openFastSnapModal()
                },
                onCycleNextSubRelease = {
                    applyAndSaveSeriesSync(0L, 1.0f, lockGeminiAudio = false)
                    subtitleManager.cycleToNextExternalTrack(
                        config = subtitleConfig.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f),
                        geminiApiKey = geminiApiKey
                    )
                },
                onSnapCueToCurrentTime = { chosenCue ->
                    val curMovieMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val snappedOffsetMs =
                        (curMovieMs * subtitleConfig.subSpeedScale.toDouble()).toLong() - chosenCue.startMs
                    applyAndSaveSeriesSync(
                        newOffsetMs = snappedOffsetMs,
                        newSpeedScale = subtitleConfig.subSpeedScale,
                        lockGeminiAudio = false
                    )
                },
                onAdjustSyncManual = { newOffsetMs, newScaleOpt ->
                    applyAndSaveSeriesSync(
                        newOffsetMs = newOffsetMs,
                        newSpeedScale = newScaleOpt ?: subtitleConfig.subSpeedScale
                    )
                },
                onUpdateConfig = onUpdateSubtitleConfig,
                onResetDefault = {
                    onSaveSeriesProfile {
                        it.copy(syncOffsetMs = 0L, subSpeedScale = 1.0f, preferGeminiAudioSub = false)
                    }
                    onResetSubtitleConfig()
                },
                onPickLocalSubFile = {
                    onBeforeLaunchSystemOverlay()
                    localSubPicker.launch(arrayOf("*/*"))
                },
                onLoadSubFromUrl = { url ->
                    val newRef = ExternalSubtitleRef(
                        id = "url_${System.currentTimeMillis()}",
                        label = "URL Sub (${url.substringAfterLast('/').take(18)})",
                        languageCode = if (url.contains("th", true)) "th" else "vi",
                        urlOrContent = url,
                        isRawContent = false
                    )
                    subtitleManager.addAndSelectExternalSubtitle(newRef, subtitleConfig, geminiApiKey)
                },
                onOpenQrUpload = onOpenQrCompanion,
                onClose = { showSubtitleDrawer = false }
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickBilingualSnapModal(
    anchorMovieMs: Long,
    currentOffsetMs: Long,
    currentSpeedScale: Float,
    bilingualCues: List<SnapCueCandidate>,
    isAutoSyncing: Boolean,
    hasGeminiKeyInRam: Boolean,
    isTvHardware: Boolean = false,
    totalSeriesEpisodes: Int,
    onPickCue: (SnapCueCandidate) -> Unit,
    onNudgeOffsetMs: (Long) -> Unit,
    onResetOffsetZero: () -> Unit,
    onTriggerTwoAnchorAiSync: () -> Unit,
    onSwitchSeriesToGeminiAudio: () -> Unit,
    onOpenQrForKey: () -> Unit,
    onDismissAndResume: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val view = LocalView.current
    val nearestIndex = remember(bilingualCues) {
        bilingualCues.indexOfFirst { it.isNearestToAnchor }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (nearestIndex - 2).coerceAtLeast(0)
    )
    val nearestItemFocus = remember { FocusRequester() }
    val fallbackActionFocus = remember { FocusRequester() }

    LaunchedEffect(bilingualCues.size, nearestIndex) {
        runCatching { view.requestFocusFromTouch() }
        if (bilingualCues.isNotEmpty()) {
            runCatching { listState.scrollToItem((nearestIndex - 2).coerceAtLeast(0)) }
            delay(60L)
            runCatching { nearestItemFocus.requestFocus() }
        } else {
            delay(60L)
            runCatching { fallbackActionFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB070A10))
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.78f else 0.96f)
                .fillMaxHeight(if (isTv) 0.86f else 0.90f)
                .clip(RoundedCornerShape(20.dp))
                .background(CinemaSurface)
                .border(1.2.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val seriesBadge = if (totalSeriesEpisodes > 1) {
                        " • Tự nhớ cho cả $totalSeriesEpisodes tập"
                    } else ""
                    Text(
                        text = "⚡ Chạm / Bấm OK Vào Câu Nhân Vật Vừa Nói (Đang dừng tại ${formatDurationMs(anchorMovieMs)})$seriesBadge",
                        color = ThaiGold,
                        fontSize = if (isTv) 16.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val offsetSec = "%+.1fs".format(currentOffsetMs / 1000.0)
                    val fpsTag = if (kotlin.math.abs(currentSpeedScale - 1.0f) >= 0.0005f) {
                        " • Tốc độ FPS: %.4fx".format(currentSpeedScale)
                    } else ""
                    Text(
                        text = "Độ lệch đang áp dụng: $offsetSec$fpsTag  •  Có kèm nghĩa Việt/Anh để nhận diện câu thoại trong 3 giây!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = ElectricCyan,
                            onClick = onDismissAndResume
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "▶ Đóng & Phát Tiếp",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = (if (bilingualCues.isEmpty()) Modifier.focusRequester(fallbackActionFocus) else Modifier)
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(9.dp),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = ElectricCyan,
                            onClick = onTriggerTwoAnchorAiSync
                        )
                        .background(ElectricCyan)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isAutoSyncing) "⏳ AI Đang Nghe 2 Điểm..." else "🎯 Để AI Tự Nghe 2 Điểm Khớp Giờ + FPS",
                        color = CinemaObsidian,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (hasGeminiKeyInRam) {
                    val lockSeriesSuffix = if (totalSeriesEpisodes > 1) " (Khóa Cả $totalSeriesEpisodes Tập)" else ""
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(9.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = ThaiGold,
                                onClick = onSwitchSeriesToGeminiAudio
                            )
                            .background(ThaiGold)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "🎙️ Gemini Nghe Phim Tạo Sub AI$lockSeriesSuffix",
                            color = CinemaObsidian,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else if (isTvHardware) {
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(9.dp),
                                focusedBorderColor = ThaiGold,
                                onClick = onOpenQrForKey
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "🔐 Quét QR Nạp Gemini Key Trên TV",
                            color = ThaiGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                QuickOsdPill(label = "⏪ -5s", onClick = { onNudgeOffsetMs(-5_000L) })
                QuickOsdPill(label = "⏪ -0.5s", onClick = { onNudgeOffsetMs(-500L) })
                QuickOsdPill(label = "Về 0.0s", onClick = onResetOffsetZero)
                QuickOsdPill(label = "⏩ +0.5s", onClick = { onNudgeOffsetMs(500L) })
                QuickOsdPill(label = "⏩ +5s", onClick = { onNudgeOffsetMs(5_000L) })
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (bilingualCues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaObsidian)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không có câu thoại nào quanh mốc này trong file .SRT. Bạn có thể bấm nút '🎙️ Gemini Nghe Phim Tạo Sub ไทย' ở trên để AI tự nghe trực tiếp từ tiếng phim (khớp 100% không cần dò)!",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    itemsIndexed(bilingualCues, key = { _, item -> item.id }) { idx, cue ->
                        val isNearest = idx == nearestIndex
                        val rowMod = if (isNearest) {
                            Modifier.focusRequester(nearestItemFocus)
                        } else {
                            Modifier
                        }
                        Column(
                            modifier = rowMod
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = ThaiGold,
                                    unfocusedBorderColor = if (isNearest) {
                                        ElectricCyan.copy(alpha = 0.75f)
                                    } else {
                                        Color(0xFF2D3748)
                                    },
                                    onClick = { onPickCue(cue) }
                                )
                                .background(
                                    if (isNearest) CinemaSurfaceElevated else CinemaObsidian.copy(alpha = 0.90f)
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${formatDurationMs(cue.startMs)}]  ${cue.thaiText}",
                                    color = if (isNearest) ThaiGold else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isNearest) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📍 Gần nhất",
                                        color = ElectricCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            if (cue.referenceText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "💬 ${cue.referenceText}",
                                    color = ElectricCyan.copy(alpha = 0.92f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreFlightSubtitleGateOverlay(
    movie: MovieItem,
    episode: Episode,
    state: PreFlightSubtitleState,
    hasGeminiKeyInRam: Boolean,
    isTvHardware: Boolean = false,
    geminiModelId: String,
    onSaveGeminiKeyInline: (String, String) -> Unit = { _, _ -> },
    onStartGeminiAudioSub: () -> Unit,
    onCustomSearch: (String) -> Unit,
    onOpenQrUpload: () -> Unit,
    onSkipAndPlayNow: () -> Unit,
    onCancelExit: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val view = LocalView.current
    val primaryBtnFocus = remember { FocusRequester() }
    var customQuery by remember(state.searchedQuery) {
        mutableStateOf(state.searchedQuery.ifBlank { movie.originalTitle.ifBlank { movie.title } })
    }
    var inlineGeminiKey by remember { mutableStateOf("") }

    LaunchedEffect(state.noSubtitleFound) {
        runCatching { view.requestFocusFromTouch() }
        delay(50L)
        runCatching { primaryBtnFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB070A10))
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.62f else 0.94f)
                .clip(RoundedCornerShape(20.dp))
                .background(CinemaSurface)
                .border(1.2.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🎬 ${movie.title} • ${episode.name}",
                color = ThaiGold,
                fontSize = if (isTv) 20.sp else 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (state.isPreparing) {
                CircularProgressIndicator(
                    color = ElectricCyan,
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.5.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = state.stepTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.stepDetail,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { state.progressFraction.coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ThaiGold,
                    trackColor = CinemaSurfaceElevated
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .focusRequester(primaryBtnFocus)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = Color(0xFF374151),
                                onClick = onSkipAndPlayNow
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(text = "Bỏ qua chờ / Phát luôn", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = onCancelExit
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(text = "Quay lại", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else if (state.noSubtitleFound) {
                Text(
                    text = state.stepTitle,
                    color = Color(0xFFFBBF24),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.stepDetail,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = customQuery,
                    onValueChange = { customQuery = it },
                    singleLine = true,
                    placeholder = { Text("Nhập tên tiếng Anh hoặc mã IMDb (VD: Dune, tt15239678)", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThaiGold,
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedContainerColor = CinemaObsidian,
                        unfocusedContainerColor = CinemaObsidian,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // On Android Phones/Tablets: direct inline Gemini API Key input (NEVER show a TV QR Code!)
                if (!isTvHardware && (!hasGeminiKeyInRam || state.needsGeminiKeyQr)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inlineGeminiKey,
                            onValueChange = { inlineGeminiKey = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text("Dán Gemini API Key (lưu vĩnh viễn trên máy)...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = Color(0xFF374151),
                                focusedContainerColor = CinemaObsidian,
                                unfocusedContainerColor = CinemaObsidian,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = {
                                        val clean = inlineGeminiKey.trim()
                                        if (clean.isNotEmpty()) {
                                            onSaveGeminiKeyInline(clean, geminiModelId)
                                            inlineGeminiKey = ""
                                        }
                                    }
                                )
                                .background(EmeraldPrimary)
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "Lưu Key",
                                color = CinemaObsidian,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasGeminiKeyInRam) {
                        Box(
                            modifier = Modifier
                                .focusRequester(primaryBtnFocus)
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = ThaiGold,
                                    onClick = onStartGeminiAudioSub
                                )
                                .background(ThaiGold)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "🎙️ Gemini ($geminiModelId) Nghe Phim Tạo Sub AI",
                                color = CinemaObsidian,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else if (isTvHardware && state.needsGeminiKeyQr) {
                        Box(
                            modifier = Modifier
                                .focusRequester(primaryBtnFocus)
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = ThaiGold,
                                    onClick = onOpenQrUpload
                                )
                                .background(ThaiGold)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "📱 Quét QR Từ Điện Thoại (Nạp Key Lên TV)",
                                color = CinemaObsidian,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    val searchModifier = if (!hasGeminiKeyInRam && (!isTvHardware || !state.needsGeminiKeyQr)) {
                        Modifier.focusRequester(primaryBtnFocus)
                    } else {
                        Modifier
                    }
                    Box(
                        modifier = searchModifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ElectricCyan.copy(alpha = 0.7f),
                                onClick = { onCustomSearch(customQuery) }
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(text = "🔍 Tìm Sub Theo Tên Này", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    if (isTvHardware) {
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = ElectricCyan,
                                    onClick = onOpenQrUpload
                                )
                                .background(CinemaSurfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(text = "📱 QR Điện Thoại (Gửi Key / File .SRT)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = onSkipAndPlayNow
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = "▶ Vẫn Phát Phim Luôn", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = onCancelExit
                            )
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = "Quay Lại", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickOsdPill(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(99.dp),
                focusedBorderColor = VioletGlow,
                unfocusedBorderColor = if (active) EmeraldNeon.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                onClick = onClick
            )
            .background(if (active) EmeraldPrimary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (active) EmeraldNeon else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(UnstableApi::class)
private fun selectExoEmbeddedTextTrack(exoPlayer: ExoPlayer, chosen: SubtitleTrackOption) {
    val groups = exoPlayer.currentTracks.groups
    val group = groups.getOrNull(chosen.exoGroupIndex) ?: return
    if (chosen.exoTrackIndex !in 0 until group.length) return
    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(chosen.exoTrackIndex))
    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(override)
        .build()
}

@Composable
private fun BottomHudActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                onClick = onClick
            )
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerControlIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(if (isPrimary) 62.dp else 46.dp)
            .tvFocusableClickable(
                shape = CircleShape,
                focusedBorderColor = if (isPrimary) Color.White else EmeraldNeon,
                unfocusedBorderColor = if (isPrimary) EmeraldNeon else Color.White.copy(alpha = 0.18f),
                onClick = onClick
            )
            .background(
                brush = if (isPrimary) BrandEmeraldVioletBrush else Brush.horizontalGradient(
                    listOf(Color(0xB80E1222), Color(0xB80E1222))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color.White,
            modifier = Modifier.size(if (isPrimary) 30.dp else 22.dp)
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
