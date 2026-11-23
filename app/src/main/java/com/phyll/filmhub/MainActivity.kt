package com.phyll.filmhub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.phyll.filmhub.ui.AppScreen
import com.phyll.filmhub.ui.MainViewModel
import com.phyll.filmhub.ui.components.WatchPartyHubDialog
import com.phyll.filmhub.ui.i18n.AppStrings
import com.phyll.filmhub.ui.i18n.LocalAppStrings
import com.phyll.filmhub.ui.screens.CompanionQrDialog
import com.phyll.filmhub.ui.screens.DetailScreen
import com.phyll.filmhub.ui.screens.DownloadsScreen
import com.phyll.filmhub.ui.screens.HomeScreen
import com.phyll.filmhub.ui.screens.PlayerScreen
import com.phyll.filmhub.ui.screens.SettingsSourcesScreen
import com.phyll.filmhub.ui.screens.TvManagerScreen
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.PhyllFilmHubTheme
import com.phyll.filmhub.ui.theme.ThaiGold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var delayedStopWipeJob: Job? = null

    override fun onStart() {
        super.onStart()
        delayedStopWipeJob?.cancel()
        delayedStopWipeJob = null
        viewModel.onAppForegrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attachWindowForStream(window)
        handleExternalVideoIntent(intent)

        setContent {
            val currentScreen by viewModel.currentScreen.collectAsState()
            val activePlaybackSession by viewModel.activePlaybackSession.collectAsState()
            val isPlayerMinimized by viewModel.isPlayerMinimized.collectAsState()
            val selectedCountry by viewModel.selectedCountry.collectAsState()
            val sections by viewModel.sections.collectAsState()
            val isLoadingCatalog by viewModel.isLoadingCatalog.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            val isSearching by viewModel.isSearching.collectAsState()
            val aiVibeSummary by viewModel.aiVibeSummary.collectAsState()
            val detailSubState by viewModel.detailSubState.collectAsState()

            val subtitleConfig by viewModel.prefs.subtitleConfig.collectAsState()
            val geminiApiKey by viewModel.prefs.geminiApiKey.collectAsState()
            val discoveredGeminiModels by viewModel.prefs.discoveredGeminiModels.collectAsState()
            val promptQrOnStartup by viewModel.prefs.promptQrOnStartup.collectAsState()
            val deviceUiMode by viewModel.prefs.deviceUiMode.collectAsState()
            val appLanguage by viewModel.prefs.appLanguage.collectAsState()
            val cloudSession by viewModel.prefs.cloudSession.collectAsState()
            val cloudHealth by viewModel.cloudHealth.collectAsState()
            val cloudSyncBusy by viewModel.cloudSyncBusy.collectAsState()
            val adminVaultSnapshot by viewModel.adminVaultSnapshot.collectAsState()
            val watchHistory by viewModel.prefs.watchHistory.collectAsState()
            val favoriteIds by viewModel.prefs.favoriteIds.collectAsState()
            val myList by viewModel.prefs.myList.collectAsState()
            val skipIntroSec by viewModel.prefs.skipIntroSec.collectAsState()
            val customProviders by viewModel.prefs.customProviders.collectAsState()
            val seriesProfiles by viewModel.prefs.seriesProfiles.collectAsState()

            val showQrDialog by viewModel.showQrCompanionDialog.collectAsState()
            val companionLanUrl by viewModel.companionLanUrl.collectAsState()
            val bannerMessage by viewModel.bannerMessage.collectAsState()
            val incomingCompanionSub by viewModel.incomingCompanionSubtitle.collectAsState()
            val remotePlayerCommand by viewModel.remotePlayerCommand.collectAsState()

            val managedTvDevices by viewModel.prefs.managedTvDevices.collectAsState()
            val selectedTvIp by viewModel.prefs.selectedTvIp.collectAsState()
            val controlTargetMode by viewModel.prefs.controlTargetMode.collectAsState()
            val isScanningTvs by viewModel.isScanningTvs.collectAsState()
            val adbTaskProgress by viewModel.adbTaskProgress.collectAsState()
            val remoteTvState by viewModel.remoteTvState.collectAsState()
            val tvScreenFrameBytes by viewModel.tvScreenFrameBytes.collectAsState()
            val appUpdateState by viewModel.appUpdateState.collectAsState()
            val offlineDownloads by viewModel.offlineDownloads.collectAsState()
            val watchPartyState by viewModel.watchPartyState.collectAsState()
            val watchPartySyncCommand by viewModel.watchPartySyncCommand.collectAsState()
            val isTvHardware = viewModel.isTvHardware
            val selectedTvDevice = managedTvDevices.firstOrNull { it.ip == selectedTvIp }
            val isPlayerFullscreenActive = activePlaybackSession != null && !isPlayerMinimized

            LaunchedEffect(bannerMessage) {
                if (bannerMessage != null) {
                    delay(3_500L)
                    viewModel.dismissBanner()
                }
            }

            BackHandler(enabled = watchPartyState.showHubDialog) {
                viewModel.dismissWatchPartyHub()
            }

            BackHandler(enabled = showQrDialog) {
                viewModel.setQrDialogVisible(false)
            }

            BackHandler(enabled = currentScreen !is AppScreen.Home && !showQrDialog && !isPlayerFullscreenActive) {
                viewModel.navigateBack()
            }

            BackHandler(enabled = currentScreen is AppScreen.Home && !showQrDialog && !isPlayerFullscreenActive) {
                if (searchQuery.isNotBlank()) {
                    viewModel.updateSearchQuery("")
                } else {
                    finish()
                }
            }

            CompositionLocalProvider(LocalAppStrings provides AppStrings.forLanguage(appLanguage)) {
            PhyllFilmHubTheme(deviceUiMode = deviceUiMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CinemaObsidian)
                ) {
                    when (val screen = currentScreen) {
                        is AppScreen.Home -> {
                            HomeScreen(
                                selectedCountry = selectedCountry,
                                sections = sections,
                                watchHistory = watchHistory,
                                myList = myList,
                                downloadsCount = offlineDownloads.size,
                                isInWatchParty = watchPartyState.isInRoom,
                                isLoading = isLoadingCatalog,
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                isSearching = isSearching,
                                aiVibeSummary = aiVibeSummary,
                                hasSessionGeminiKey = geminiApiKey.isNotBlank() || cloudSession?.serverAiReady == true,
                                activeGeminiModel = subtitleConfig.geminiModelId,
                                companionLanUrl = companionLanUrl,
                                isTvHardware = isTvHardware,
                                controlTargetMode = controlTargetMode,
                                selectedTvDevice = selectedTvDevice,
                                isRemoteTvOnline = remoteTvState.online,
                                currentAppLanguage = appLanguage,
                                onSelectAppLanguage = viewModel::setAppLanguage,
                                onSelectCountry = viewModel::selectCountry,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onExecuteSearch = viewModel::executeSearch,
                                onExecuteAiVibeSearch = viewModel::executeAiVibeSearch,
                                onMovieClick = viewModel::openMovieDetail,
                                onQuickPlayMovie = viewModel::quickPlayMovie,
                                onResumeHistory = viewModel::resumeFromWatchHistory,
                                onRemoveContinueWatching = viewModel::removeContinueWatchingItem,
                                onRemoveMyListItem = viewModel::removeMyListItem,
                                onRefreshMyListFromCloud = { viewModel.pullCloudState(silent = true, force = true) },
                                onOpenDownloads = viewModel::openDownloads,
                                onOpenWatchParty = { viewModel.openWatchPartyHub(initialTab = 0) },
                                onToggleControlTargetMode = viewModel::toggleControlTargetMode,
                                onOpenTvManager = viewModel::openTvManager,
                                onOpenQrCompanion = { viewModel.setQrDialogVisible(true) },
                                onOpenSettingsSources = viewModel::openSettingsSources,
                                onToggleUiMode = viewModel::toggleDeviceUiMode,
                                onRefresh = viewModel::refreshCatalog,
                                onBeforeLaunchSystemOverlay = viewModel::markExternalOverlayLaunching,
                                onAfterSystemOverlayResult = viewModel::clearExternalOverlayLaunching,
                                onVoiceError = {
                                    viewModel.showBanner("Voice search unavailable on this device.")
                                }
                            )
                        }

                        is AppScreen.Detail -> {
                            DetailScreen(
                                movie = screen.movie,
                                isResolving = screen.isResolving,
                                isFavorite = (myList.isNotEmpty() || favoriteIds.isNotEmpty()) && viewModel.prefs.isMovieInMyList(screen.movie),
                                isTvHardware = isTvHardware,
                                controlTargetMode = controlTargetMode,
                                selectedTvName = selectedTvDevice?.displayTitle.orEmpty(),
                                savedWatchProgress = watchHistory.firstOrNull { it.movieId == screen.movie.id }
                                    ?: viewModel.prefs.getLatestMovieWatchProgress(screen.movie.id),
                                detailSubState = detailSubState,
                                offlineDownloads = offlineDownloads,
                                isEpisodeWatched = { ep -> viewModel.prefs.isEpisodeWatched(screen.movie.id, ep.id) },
                                onPreTranslateEpisode = { ep -> viewModel.preTranslateDetailEpisode(screen.movie, ep) },
                                onRepairSubGaps = { ep -> viewModel.repairDetailEpisodeSubGaps(screen.movie, ep) },
                                onDownloadEpisode = { ep -> viewModel.downloadEpisodeOffline(screen.movie, ep) },
                                onPauseDownload = viewModel::pauseOfflineDownload,
                                onResumeDownload = viewModel::resumeOfflineDownload,
                                onOpenDownloads = viewModel::openDownloads,
                                onExportSrt = { ep -> viewModel.exportEpisodeSrt(screen.movie, ep) },
                                onStartWatchParty = { ep ->
                                    viewModel.openWatchPartyHub(initialTab = 2, movie = screen.movie, episode = ep)
                                },
                                onPlayEpisode = { ep -> viewModel.startTargetAwarePlayback(screen.movie, ep) },
                                onPlayOnPhone = { ep -> viewModel.startPlayback(screen.movie, ep) },
                                onPlayOnTv = { ep -> viewModel.playMovieOnSelectedTv(screen.movie, ep) },
                                onToggleFavorite = { viewModel.toggleMyList(screen.movie) },
                                onOpenQrCompanion = { viewModel.setQrDialogVisible(true) },
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        is AppScreen.Downloads -> {
                            DownloadsScreen(
                                downloads = offlineDownloads,
                                onPlayOffline = viewModel::playOfflineDownload,
                                onPause = viewModel::pauseOfflineDownload,
                                onResume = viewModel::resumeOfflineDownload,
                                onExportSrt = viewModel::exportOfflineRecordSrt,
                                onDelete = viewModel::deleteOfflineDownload,
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        is AppScreen.Player -> {
                            // Handled persistently by activePlaybackSession overlay below
                        }

                        is AppScreen.SettingsSources -> {
                            SettingsSourcesScreen(
                                customProviders = customProviders,
                                subtitleConfig = subtitleConfig,
                                geminiApiKey = geminiApiKey,
                                discoveredGeminiModels = discoveredGeminiModels,
                                promptQrOnStartup = promptQrOnStartup,
                                deviceUiMode = deviceUiMode,
                                currentAppLanguage = appLanguage,
                                onSelectAppLanguage = viewModel::setAppLanguage,
                                cloudSession = cloudSession,
                                cloudHealth = cloudHealth,
                                cloudSyncBusy = cloudSyncBusy,
                                adminVaultSnapshot = adminVaultSnapshot,
                                onCloudLogin = viewModel::cloudLogin,
                                onCloudRegister = viewModel::cloudRegister,
                                onCloudUpdateProfile = viewModel::cloudUpdateProfile,
                                onCloudLogout = viewModel::cloudLogout,
                                onCloudSyncNow = { viewModel.pullCloudState(silent = false, force = true) },
                                onLoadAdminVault = viewModel::loadAdminVault,
                                onAdminSaveServerKey = viewModel::adminSaveServerGeminiKey,
                                onAdminSavePolicy = viewModel::adminSaveGlobalPolicy,
                                onAdminCreateInvite = viewModel::adminCreateInviteCode,
                                onAdminRevokeInvite = viewModel::adminRevokeInviteCode,
                                onAdminToggleMemberStatus = viewModel::adminToggleMemberStatus,
                                appUpdateState = appUpdateState,
                                hasSelectedTv = !isTvHardware && selectedTvIp != null,
                                isTvHardware = isTvHardware,
                                onCheckForUpdates = viewModel::checkForAppUpdates,
                                onDownloadAndInstallUpdate = viewModel::downloadAndInstallAppUpdate,
                                onOpenInstallPermission = viewModel::openInstallPermissionAndRetry,
                                onPushUpdateToTv = viewModel::pushDownloadedUpdateToSelectedTv,
                                onOpenTvManager = viewModel::openTvManager,
                                onAddProvider = { name, url, type ->
                                    viewModel.addCustomVodProvider(name, url, type)
                                },
                                onToggleProvider = viewModel.prefs::toggleCustomProvider,
                                onRemoveProvider = viewModel.prefs::removeCustomProvider,
                                onPlayDirectUrl = viewModel::playDirectStreamUrl,
                                onSaveSessionGeminiKey = { key, modelId ->
                                    viewModel.setSessionGeminiCredentials(key, modelId)
                                },
                                onWipeSessionGeminiKey = {
                                    viewModel.wipeGeminiKeyFromRam(silent = false, clearPhoneVault = true)
                                },
                                onSelectGeminiModel = { modelId ->
                                    viewModel.prefs.setSelectedGeminiModel(modelId)
                                    viewModel.showBanner("✨ Gemini Model: $modelId")
                                },
                                onRefreshGeminiModels = {
                                    viewModel.refreshGeminiModelsFromApi(silent = false)
                                },
                                onTogglePromptQrOnStartup = viewModel.prefs::setPromptQrOnStartup,
                                onUpdateSubtitleConfig = viewModel::updateSubtitleStyle,
                                onSetDeviceUiMode = viewModel::setDeviceUiMode,
                                onClearCache = viewModel::clearCacheForTvBox,
                                onClearWatchHistory = {
                                    viewModel.prefs.clearWatchHistory()
                                    viewModel.showBanner("Watch history cleared")
                                },
                                onOpenQrCompanion = { viewModel.setQrDialogVisible(true) },
                                onBeforeLaunchSystemOverlay = viewModel::markExternalOverlayLaunching,
                                onAfterSystemOverlayResult = viewModel::clearExternalOverlayLaunching,
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        is AppScreen.TvManager -> {
                            TvManagerScreen(
                                managedTvs = managedTvDevices,
                                selectedTvIp = selectedTvIp,
                                controlTargetMode = controlTargetMode,
                                isScanningTvs = isScanningTvs,
                                adbProgress = adbTaskProgress,
                                remoteState = remoteTvState,
                                tvScreenFrameBytes = tvScreenFrameBytes,
                                savedGeminiKey = geminiApiKey,
                                discoveredGeminiModels = discoveredGeminiModels,
                                localSubtitleConfig = subtitleConfig,
                                onScanLan = { viewModel.scanLanForTvs(silent = false) },
                                onAddManualTvIp = viewModel::addOrProbeTvByIp,
                                onSelectTv = viewModel::selectManagedTv,
                                onRenameTv = viewModel::renameManagedTv,
                                onRemoveTv = viewModel::removeManagedTv,
                                onInstallAppToTv = viewModel::installAppOnTv,
                                onUninstallAppFromTv = viewModel::uninstallAppFromTv,
                                onLaunchAppOnTv = viewModel::launchAppOnTv,
                                onDismissAdbProgress = viewModel::dismissAdbProgress,
                                onSetControlTargetMode = viewModel::setControlTargetMode,
                                onSaveGeminiKey = { k, m -> viewModel.setSessionGeminiCredentials(k, m) },
                                onSyncKeyToTvNow = { viewModel.syncCredentialsToSelectedTvIfOnline(silent = false) },
                                onWipeGeminiKey = { viewModel.wipeGeminiKeyFromRam(silent = false, clearPhoneVault = true) },
                                onRefreshGeminiModels = { viewModel.refreshGeminiModelsFromApi(silent = false) },
                                onSendTouchKey = viewModel::sendRemoteTouchKey,
                                onSendTap = viewModel::sendRemoteTap,
                                onSendSwipe = viewModel::sendRemoteSwipe,
                                onSendPlayerCmd = { cmd, value -> viewModel.sendRemotePlayerCommand(cmd, value) },
                                onSendSearchToTv = { q ->
                                    viewModel.sendRemoteAction(org.json.JSONObject().put("type", "search").put("query", q))
                                },
                                onPushDirectStreamToTv = viewModel::pushDirectStreamToTv,
                                onPushSubtitleFileToTv = viewModel::pushSubtitleBytesToTv,
                                onUpdateRemoteSubStyle = viewModel::updateRemoteTvSubtitleStyle,
                                onResetRemoteSubStyleDefault = viewModel::resetRemoteTvSubtitleStyleDefault,
                                onStartScreenStream = { w, q -> viewModel.startTvScreenStream(w, q) },
                                onStopScreenStream = viewModel::stopTvScreenStream,
                                onBeforeLaunchSystemOverlay = viewModel::markExternalOverlayLaunching,
                                onAfterSystemOverlayResult = viewModel::clearExternalOverlayLaunching,
                                onGoPickMoviesForTv = {
                                    viewModel.setControlTargetMode(com.phyll.filmhub.model.ControlTargetMode.REMOTE_TV)
                                    viewModel.navigateHome()
                                },
                                onBack = { viewModel.navigateBack() }
                            )
                        }
                    }

                    // Persistent Player Overlay: stays mounted seamlessly across Fullscreen <-> Bottom-Right Mini Pop-out!
                    activePlaybackSession?.let { session ->
                        PlayerScreen(
                            movie = session.movie,
                            episode = session.episode,
                            startPositionMs = session.startPositionMs,
                            subtitleConfig = subtitleConfig,
                            geminiApiKey = geminiApiKey,
                            discoveredGeminiModels = discoveredGeminiModels,
                            incomingCompanionSub = incomingCompanionSub,
                            seriesProfile = seriesProfiles[session.movie.id],
                            remoteCommand = remotePlayerCommand,
                            isTvHardware = isTvHardware,
                            isMinimized = isPlayerMinimized,
                            skipIntroSec = skipIntroSec,
                            watchPartyState = watchPartyState,
                            watchPartySyncCommand = watchPartySyncCommand,
                            modifier = if (isPlayerMinimized) {
                                Modifier.align(Alignment.BottomEnd)
                            } else {
                                Modifier.fillMaxSize()
                            },
                            onConsumeWatchPartySync = viewModel::consumeWatchPartySyncCommand,
                            onReportPlayerToParty = viewModel::reportLocalPlayerToParty,
                            onUpdatePartySyncBadge = viewModel::updatePartySyncBadge,
                            onOpenWatchPartyHub = {
                                viewModel.openWatchPartyHub(
                                    initialTab = 2,
                                    movie = session.movie,
                                    episode = session.episode
                                )
                            },
                            onTogglePartyPanel = viewModel::togglePartyPanel,
                            onForcePartyResync = viewModel::forcePartyResync,
                            onSendPartyChat = viewModel::sendPartyChat,
                            onSendPartyReaction = viewModel::sendPartyReaction,
                            onStartPartyCountdown = viewModel::startPartyCountdown,
                            onTogglePartyControlMode = viewModel::togglePartyControlMode,
                            onTogglePartyVisibility = viewModel::togglePartyVisibility,
                            onClaimPartyHost = viewModel::claimPartyHost,
                            onQueueSearch = viewModel::partyQuickSearch,
                            onQueueAddMovie = viewModel::partyAddMovieToQueue,
                            onQueueVote = viewModel::partyVoteQueue,
                            onQueuePlay = viewModel::partyPlayQueueItem,
                            onQueueRemove = viewModel::partyRemoveQueueItem,
                            onLeaveWatchParty = { viewModel.leaveWatchParty(silent = false) },
                            onSaveGeminiKeyInline = { key, modelId ->
                                viewModel.setSessionGeminiCredentials(key, modelId)
                            },
                            onConsumeRemoteCommand = viewModel::consumeRemotePlayerCommand,
                            onReportTelemetry = viewModel::updatePlayerTelemetry,
                            onSaveSeriesProfile = { transform ->
                                viewModel.prefs.saveSeriesSubtitleProfile(session.movie.id, transform)
                            },
                            onConsumeCompanionSub = viewModel::consumeIncomingCompanionSubtitle,
                            onSelectGeminiModel = { modelId ->
                                viewModel.prefs.setSelectedGeminiModel(modelId)
                                viewModel.showBanner("✨ Gemini Model: $modelId")
                            },
                            onRefreshGeminiModels = {
                                viewModel.refreshGeminiModelsFromApi(silent = false)
                            },
                            onUpdateSubtitleConfig = viewModel::updateSubtitleStyle,
                            onResetSubtitleConfig = viewModel.prefs::resetSubtitleConfigToThaiDefault,
                            onSaveProgress = { pos, dur ->
                                viewModel.saveWatchProgress(session.movie, session.episode, pos, dur, immediateCommit = true)
                            },
                            onSwitchEpisode = { nextEp ->
                                viewModel.startPlayback(session.movie, nextEp, 0L)
                            },
                            onOpenQrCompanion = { viewModel.setQrDialogVisible(true) },
                            onBeforeLaunchSystemOverlay = viewModel::markExternalOverlayLaunching,
                            onAfterSystemOverlayResult = viewModel::clearExternalOverlayLaunching,
                            onMinimizePlayer = viewModel::minimizePlayer,
                            onRestorePlayer = viewModel::restoreMinimizedPlayer,
                            onExitPlayer = viewModel::closePlayerSession
                        )
                    }

                    if (watchPartyState.showHubDialog) {
                        WatchPartyHubDialog(
                            state = watchPartyState,
                            defaultNickname = cloudSession?.displayName?.takeIf { it.isNotBlank() }
                                ?: cloudSession?.username?.takeIf { it.isNotBlank() }
                                ?: if (isTvHardware) "Smart TV" else "ChillFan",
                            onDismiss = viewModel::dismissWatchPartyHub,
                            onRefreshLobby = viewModel::refreshPartyLobby,
                            onJoinRoom = viewModel::joinWatchParty,
                            onCreateRoom = viewModel::createWatchParty,
                            onQuickSearchMovies = viewModel::partyQuickSearch,
                            onPickMovieForCreate = viewModel::pickMovieForPartyCreate
                        )
                    }

                    if (isTvHardware && showQrDialog) {
                        CompanionQrDialog(
                            lanUrl = companionLanUrl,
                            hasSessionGeminiKey = geminiApiKey.isNotBlank(),
                            activeGeminiModel = subtitleConfig.geminiModelId,
                            promptQrOnStartup = promptQrOnStartup,
                            onTogglePromptOnStartup = viewModel.prefs::setPromptQrOnStartup,
                            onWipeSessionKey = { viewModel.wipeGeminiKeyFromRam(silent = false, clearPhoneVault = true) },
                            onDismiss = { viewModel.setQrDialogVisible(false) }
                        )
                    }

                    // High-contrast Cinema Toast Banner (TV & Mobile readable)
                    AnimatedVisibility(
                        visible = bannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ThaiGold)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = bannerMessage.orEmpty(),
                                color = CinemaObsidian,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            }
        }
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.detachWindowForStream(window)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalVideoIntent(intent)
    }

    private fun handleExternalVideoIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val dataUri = intent.data ?: return
            val name = dataUri.lastPathSegment?.substringAfterLast('/') ?: "Video từ USB"
            viewModel.playDirectStreamUrl(name, dataUri.toString())
        }
    }
}
