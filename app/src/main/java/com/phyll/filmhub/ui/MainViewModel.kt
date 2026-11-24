package com.phyll.filmhub.ui

import android.app.Application
import android.os.Build
import android.view.Window
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phyll.filmhub.cloud.AdminVaultSnapshot
import com.phyll.filmhub.cloud.CloudHealthInfo
import com.phyll.filmhub.cloud.CloudSyncClient
import com.phyll.filmhub.cloud.PartyPasscodeRequiredException
import com.phyll.filmhub.cloud.WatchPartyEpisodeMeta
import com.phyll.filmhub.cloud.WatchPartyMovieMeta
import com.phyll.filmhub.offline.OfflineDownloadManager
import com.phyll.filmhub.offline.OfflineEpisodeRecord
import com.phyll.filmhub.subtitle.ThaiSubtitleManager
import com.phyll.filmhub.ui.components.WatchPartySyncCommand
import com.phyll.filmhub.ui.components.WatchPartyUiState
import com.phyll.filmhub.companion.CompanionEvent
import com.phyll.filmhub.companion.CompanionHttpServer
import com.phyll.filmhub.companion.CompanionPlayerTelemetry
import com.phyll.filmhub.companion.PlayerRemoteCommand
import com.phyll.filmhub.companion.TvScreenStreamController
import com.phyll.filmhub.data.CatalogSection
import com.phyll.filmhub.data.MovieRepository
import com.phyll.filmhub.data.PreferencesManager
import com.phyll.filmhub.model.AdbTaskProgress
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.DeviceUiMode
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.ExternalSubtitleRef
import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.RemoteTvLiveState
import com.phyll.filmhub.model.ServerGroup
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleCue
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.model.VodProviderConfig
import com.phyll.filmhub.model.VodProviderType
import com.phyll.filmhub.model.WatchProgress
import com.phyll.filmhub.subtitle.GeminiAudioSubtitleGenerator
import com.phyll.filmhub.subtitle.OnlineSubtitleFetcher
import com.phyll.filmhub.subtitle.SubtitleParser
import com.phyll.filmhub.subtitle.SubtitleTranslator
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.phyll.filmhub.tvmanager.AdbTcpClient
import com.phyll.filmhub.tvmanager.RemoteTvControllerClient
import com.phyll.filmhub.ui.i18n.AppLanguage
import com.phyll.filmhub.updater.AppUpdateUiState
import com.phyll.filmhub.updater.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ActivePlaybackSession(
    val movie: MovieItem,
    val episode: Episode,
    val startPositionMs: Long = 0L
)

data class DetailSubPreTranslateState(
    val movieId: String = "",
    val episodeId: String = "",
    val isRunning: Boolean = false,
    val percent: Int = 0,
    val statusText: String = "",
    val isReady: Boolean = false
)

sealed class AppScreen {
    data object Home : AppScreen()
    data class Detail(val movie: MovieItem, val isResolving: Boolean = false) : AppScreen()
    data class Player(
        val movie: MovieItem,
        val episode: Episode,
        val startPositionMs: Long = 0L
    ) : AppScreen()
    data object SettingsSources : AppScreen()
    data object TvManager : AppScreen()
    data object Downloads : AppScreen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application.applicationContext)
    private val repository = MovieRepository()
    private val geminiInspector = GeminiAudioSubtitleGenerator()
    private val screenStreamController = TvScreenStreamController()
    private val adbClient = AdbTcpClient(File(application.filesDir, "adb_keys"))
    val remoteTvClient = RemoteTvControllerClient(adbClient)
    val appUpdater = AppUpdater()

    val isTvHardware: Boolean = prefs.isTvHardware

    @Volatile
    private var externalPickerLaunchTimestampMs: Long = 0L

    @Volatile
    private var lastRemoteSubStyleEditMs: Long = 0L

    val offlineDownloads: StateFlow<List<OfflineEpisodeRecord>> = OfflineDownloadManager.downloads

    private val _watchPartyState = MutableStateFlow(WatchPartyUiState())
    val watchPartyState: StateFlow<WatchPartyUiState> = _watchPartyState.asStateFlow()

    private val _watchPartySyncCommand = MutableStateFlow<WatchPartySyncCommand?>(null)
    val watchPartySyncCommand: StateFlow<WatchPartySyncCommand?> = _watchPartySyncCommand.asStateFlow()

    private var partyPollJob: Job? = null
    @Volatile private var partyLastMsgId: Long = 0L
    @Volatile private var partyLastReactionTs: Long = 0L
    @Volatile private var partyLastStateUpdatedAt: Long = 0L
    @Volatile private var partyLastActionAt: Long = 0L
    @Volatile private var partyLastMovieKey: String = ""
    @Volatile private var partyLastReportedPosSec: Int = 0
    @Volatile private var partyLastReportedStatus: String = "playing"

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _activePlaybackSession = MutableStateFlow<ActivePlaybackSession?>(null)
    val activePlaybackSession: StateFlow<ActivePlaybackSession?> = _activePlaybackSession.asStateFlow()

    private val _isPlayerMinimized = MutableStateFlow(false)
    val isPlayerMinimized: StateFlow<Boolean> = _isPlayerMinimized.asStateFlow()

    private val screenBackStack = ArrayDeque<AppScreen>()

    private val _selectedCountry = MutableStateFlow(CountryCategory.ALL)
    val selectedCountry: StateFlow<CountryCategory> = _selectedCountry.asStateFlow()

    private val _sections = MutableStateFlow<List<CatalogSection>>(emptyList())
    val sections: StateFlow<List<CatalogSection>> = _sections.asStateFlow()

    private val _isLoadingCatalog = MutableStateFlow(false)
    val isLoadingCatalog: StateFlow<Boolean> = _isLoadingCatalog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MovieItem>>(emptyList())
    val searchResults: StateFlow<List<MovieItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _aiVibeSummary = MutableStateFlow("")
    val aiVibeSummary: StateFlow<String> = _aiVibeSummary.asStateFlow()

    private val _detailSubState = MutableStateFlow(DetailSubPreTranslateState())
    val detailSubState: StateFlow<DetailSubPreTranslateState> = _detailSubState.asStateFlow()

    private var detailSubJob: Job? = null
    private val onlineSubFetcher = OnlineSubtitleFetcher()
    private val detailSubTranslator by lazy {
        SubtitleTranslator(File(getApplication<Application>().cacheDir, "detail_sub_trans"))
    }
    private val detailSubHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(22, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _showQrCompanionDialog = MutableStateFlow(false)
    val showQrCompanionDialog: StateFlow<Boolean> = _showQrCompanionDialog.asStateFlow()

    private val _companionLanUrl = MutableStateFlow<String?>(null)
    val companionLanUrl: StateFlow<String?> = _companionLanUrl.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _incomingCompanionSubtitle = MutableStateFlow<ExternalSubtitleRef?>(null)
    val incomingCompanionSubtitle: StateFlow<ExternalSubtitleRef?> = _incomingCompanionSubtitle.asStateFlow()

    private val _playerTelemetry = MutableStateFlow(CompanionPlayerTelemetry())
    val playerTelemetry: StateFlow<CompanionPlayerTelemetry> = _playerTelemetry.asStateFlow()

    private val _remotePlayerCommand = MutableStateFlow<PlayerRemoteCommand?>(null)
    val remotePlayerCommand: StateFlow<PlayerRemoteCommand?> = _remotePlayerCommand.asStateFlow()

    private val _isScanningTvs = MutableStateFlow(false)
    val isScanningTvs: StateFlow<Boolean> = _isScanningTvs.asStateFlow()

    private val _adbTaskProgress = MutableStateFlow(AdbTaskProgress())
    val adbTaskProgress: StateFlow<AdbTaskProgress> = _adbTaskProgress.asStateFlow()

    private val _remoteTvState = MutableStateFlow(RemoteTvLiveState())
    val remoteTvState: StateFlow<RemoteTvLiveState> = _remoteTvState.asStateFlow()

    private val _tvScreenFrameBytes = MutableStateFlow<ByteArray?>(null)
    val tvScreenFrameBytes: StateFlow<ByteArray?> = _tvScreenFrameBytes.asStateFlow()

    private val _appUpdateState = MutableStateFlow(
        AppUpdateUiState(currentVersion = appUpdater.getInstalledVersion(application.applicationContext))
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _cloudHealth = MutableStateFlow(CloudHealthInfo())
    val cloudHealth: StateFlow<CloudHealthInfo> = _cloudHealth.asStateFlow()

    private val _cloudSyncBusy = MutableStateFlow(false)
    val cloudSyncBusy: StateFlow<Boolean> = _cloudSyncBusy.asStateFlow()

    private val _adminVaultSnapshot = MutableStateFlow<AdminVaultSnapshot?>(null)
    val adminVaultSnapshot: StateFlow<AdminVaultSnapshot?> = _adminVaultSnapshot.asStateFlow()

    private val pendingCloudProgress = LinkedHashMap<String, WatchProgress>()
    @Volatile
    private var lastCloudPullMs: Long = 0L
    @Volatile
    private var lastCloudPushMs: Long = 0L

    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var tvPollJob: Job? = null
    private var tvScreenStreamJob: Job? = null

    private val companionServer = CompanionHttpServer(
        port = 8899,
        screenStreamController = screenStreamController,
        stateSnapshotProvider = { buildCompanionStateJson() },
        catalogJsonProvider = { countryCode, query -> buildCompanionCatalogJson(countryCode, query) },
        movieDetailProvider = { movieId -> resolveMovieJsonForCompanion(movieId) },
        deviceInfoProvider = { buildDeviceInfoJson() },
        apkSourcePathProvider = { getApplication<Application>().applicationInfo.sourceDir },
        onEvent = { event ->
            viewModelScope.launch {
                handleCompanionEvent(event)
            }
        }
    )

    init {
        OfflineDownloadManager.ensureInitialized(application)
        _companionLanUrl.value = companionServer.start()
        if (prefs.isTvHardware && prefs.promptQrOnStartup.value && prefs.geminiApiKey.value.isBlank()) {
            _showQrCompanionDialog.value = true
        }
        if (!prefs.isTvHardware) {
            startPollingSelectedTv()
            if (prefs.managedTvDevices.value.isEmpty()) {
                scanLanForTvs(silent = true)
            }
        }
        refreshCatalog()
        refreshCloudHealth()
        if (prefs.cloudSession.value != null) {
            pullCloudState(silent = true, force = true)
            refreshGeminiModelsFromApi(silent = true)
        }
    }

    fun onAppForegrounded() {
        if (prefs.cloudSession.value != null) {
            pullCloudState(silent = true, force = false)
        }
    }

    fun onAppBackgrounded() {
        flushPendingCloudProgress()
    }

    fun refreshCloudHealth() {
        viewModelScope.launch {
            val h = CloudSyncClient.fetchHealth()
            _cloudHealth.value = h
            if (prefs.cloudSession.value != null) {
                CloudSyncClient.refreshMe().onSuccess { updated ->
                    prefs.saveCloudSession(updated)
                }
            }
        }
    }

    fun cloudLogin(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            showBanner("Enter username and password")
            return
        }
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.login(username, password)
                .onSuccess { session ->
                    prefs.saveCloudSession(session)
                    showBanner("☁️ Signed in as @${session.username} • Auto-Sync Active!")
                    pullCloudState(silent = true, force = true)
                    refreshGeminiModelsFromApi(silent = true)
                    if (session.role == "ADMIN") {
                        loadAdminVault()
                    }
                }
                .onFailure { err ->
                    showBanner("Cloud sign-in failed: ${err.message.orEmpty()}")
                }
            _cloudSyncBusy.value = false
        }
    }

    fun cloudRegister(username: String, password: String, displayName: String, inviteCode: String) {
        if (username.isBlank() || password.length < 6) {
            showBanner("Nhập tên đăng nhập và mật khẩu tối thiểu 6 ký tự")
            return
        }
        if (displayName.trim().isNotBlank() && displayName.trim() == password) {
            showBanner("⚠️ Tên hiển thị không được trùng với mật khẩu!")
            return
        }
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.register(username, password, displayName, inviteCode)
                .onSuccess { session ->
                    prefs.saveCloudSession(session)
                    val roleTag = if (session.role == "ADMIN") "👑 ADMIN" else "☁️ Member"
                    showBanner("$roleTag @${session.username} created & synced!")
                    pullCloudState(silent = true, force = true, migrateLocalOnRegister = true)
                    refreshGeminiModelsFromApi(silent = true)
                    if (session.role == "ADMIN") {
                        loadAdminVault()
                    }
                }
                .onFailure { err ->
                    showBanner("Registration failed: ${err.message.orEmpty()}")
                }
            _cloudSyncBusy.value = false
        }
    }

    fun cloudUpdateProfile(
        displayName: String? = null,
        currentPassword: String? = null,
        newPassword: String? = null,
        revokeOtherSessions: Boolean = false
    ) {
        val cleanName = displayName?.trim()?.takeIf { it.isNotBlank() }
        if (!newPassword.isNullOrBlank()) {
            if (newPassword.length < 6) {
                showBanner("Mật khẩu mới phải có ít nhất 6 ký tự")
                return
            }
            if (cleanName != null && cleanName == newPassword) {
                showBanner("⚠️ Tên hiển thị không được trùng với mật khẩu!")
                return
            }
        }
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.updateProfile(
                displayName = cleanName,
                currentPassword = currentPassword,
                newPassword = newPassword,
                revokeOtherSessions = revokeOtherSessions
            ).onSuccess { updated ->
                prefs.saveCloudSession(updated)
                val msg = when {
                    !newPassword.isNullOrBlank() -> "🔑 Đã đổi mật khẩu & bảo vệ tài khoản thành công!"
                    revokeOtherSessions -> "🛡️ Đã đăng xuất tất cả thiết bị khác!"
                    cleanName != null -> "✅ Đã đổi tên hiển thị thành \"${updated.displayName}\"!"
                    else -> "✅ Đã cập nhật hồ sơ NKI Cloud!"
                }
                showBanner(msg)
            }.onFailure { err ->
                showBanner("Cập nhật hồ sơ thất bại: ${err.message.orEmpty()}")
            }
            _cloudSyncBusy.value = false
        }
    }

    fun cloudLogout() {
        prefs.saveCloudSession(null)
        _adminVaultSnapshot.value = null
        synchronized(pendingCloudProgress) { pendingCloudProgress.clear() }
        showBanner("Signed out of NKI Cloud")
    }

    fun pullCloudState(
        silent: Boolean = true,
        force: Boolean = false,
        migrateLocalOnRegister: Boolean = false
    ) {
        if (!CloudSyncClient.isSignedIn()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCloudPullMs < 15_000L) return
        lastCloudPullMs = now
        viewModelScope.launch {
            if (!silent) _cloudSyncBusy.value = true
            flushPendingCloudProgress()
            CloudSyncClient.refreshMe().onSuccess { updated ->
                prefs.saveCloudSession(updated)
            }
            CloudSyncClient.pullState()
                .onSuccess { res ->
                    prefs.mergeCloudWatchProgress(res.watchProgress)
                    val localOnlyMyList = prefs.mergeCloudMyList(res.myList, migrateLocalOnRegister = migrateLocalOnRegister)
                    if (localOnlyMyList.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            CloudSyncClient.pushMyListUpdates(added = localOnlyMyList)
                        }
                    }
                    if (!silent) showBanner("☁️ Cloud Sync complete (${res.watchProgress.size} watched • ${res.myList.size} My List)")
                }
                .onFailure { err ->
                    if (!silent) showBanner("Sync error: ${err.message.orEmpty()}")
                }
            if (!silent) _cloudSyncBusy.value = false
        }
    }

    fun flushPendingCloudProgress() {
        if (!CloudSyncClient.isSignedIn()) return
        val batch = synchronized(pendingCloudProgress) {
            if (pendingCloudProgress.isEmpty()) return
            val list = pendingCloudProgress.values.toList()
            pendingCloudProgress.clear()
            list
        }
        lastCloudPushMs = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            CloudSyncClient.pushWatchProgress(batch)
        }
    }

    fun loadAdminVault() {
        if (prefs.cloudSession.value?.role != "ADMIN") return
        viewModelScope.launch {
            CloudSyncClient.fetchAdminConfig()
                .onSuccess { snap -> _adminVaultSnapshot.value = snap }
                .onFailure { err -> showBanner("Admin Vault error: ${err.message.orEmpty()}") }
        }
    }

    fun adminSaveServerGeminiKey(apiKey: String) {
        if (apiKey.trim().length < 15) {
            showBanner("Paste a valid Gemini API Key")
            return
        }
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.adminUpdateConfig(geminiApiKey = apiKey.trim())
                .onSuccess {
                    showBanner("🔐 Server Gemini API Key encrypted & updated!")
                    loadAdminVault()
                    refreshCloudHealth()
                    refreshGeminiModelsFromApi(silent = true)
                }
                .onFailure { err -> showBanner("Failed: ${err.message.orEmpty()}") }
            _cloudSyncBusy.value = false
        }
    }

    fun adminSaveGlobalPolicy(defaultModel: String, registrationMode: String) {
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.adminUpdateConfig(defaultModel = defaultModel, registrationMode = registrationMode)
                .onSuccess {
                    showBanner("✅ Updated global AI model & policy!")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("Failed: ${err.message.orEmpty()}") }
            _cloudSyncBusy.value = false
        }
    }

    fun adminCreateInviteCode(onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            _cloudSyncBusy.value = true
            CloudSyncClient.adminCreateInvite(label = "NKI Member", maxUses = 5, dailyAiLimit = 150)
                .onSuccess { inv ->
                    showBanner("🎟 Invite Code: ${inv.code}")
                    onCreated(inv.code)
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("Failed: ${err.message.orEmpty()}") }
            _cloudSyncBusy.value = false
        }
    }

    fun adminRevokeInviteCode(code: String) {
        viewModelScope.launch {
            CloudSyncClient.adminRevokeInvite(code)
                .onSuccess {
                    showBanner("Revoked invite $code")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("Failed: ${err.message.orEmpty()}") }
        }
    }

    fun adminToggleMemberStatus(userId: String, nextStatus: String) {
        viewModelScope.launch {
            CloudSyncClient.adminUpdateUserStatus(userId, nextStatus)
                .onSuccess {
                    showBanner(if (nextStatus == "REVOKED") "🔒 Đã khóa tài khoản!" else "🔓 Đã mở khóa tài khoản!")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminCreateUser(
        username: String,
        displayName: String,
        password: String,
        role: String,
        dailyAiLimit: Int,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            CloudSyncClient.adminCreateUser(username, displayName, password, role, dailyAiLimit)
                .onSuccess {
                    showBanner("✅ Đã tạo tài khoản @$username ($role)!")
                    onCreated()
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminUpdateMember(
        userId: String,
        displayName: String? = null,
        role: String? = null,
        dailyAiLimit: Int? = null
    ) {
        viewModelScope.launch {
            CloudSyncClient.adminUpdateUserDetails(userId, displayName, role, dailyAiLimit)
                .onSuccess {
                    showBanner("✅ Đã cập nhật thông tin & phân quyền tài khoản!")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminResetMemberPassword(userId: String, onTempPassword: (String) -> Unit) {
        viewModelScope.launch {
            CloudSyncClient.adminResetUserPassword(userId)
                .onSuccess { tempPw ->
                    showBanner("🔑 Mật khẩu mới: $tempPw (đã copy & thoát phiên cũ)")
                    onTempPassword(tempPw)
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminResetMemberQuota(userId: String) {
        viewModelScope.launch {
            CloudSyncClient.adminResetUserQuota(userId)
                .onSuccess {
                    showBanner("♻️ Đã reset lượt AI hôm nay về 0!")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminForceLogoutMember(userId: String) {
        viewModelScope.launch {
            CloudSyncClient.adminForceLogoutUser(userId)
                .onSuccess { showBanner("🚪 Đã đăng xuất tài khoản khỏi mọi thiết bị!") }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun adminDeleteMember(userId: String) {
        viewModelScope.launch {
            CloudSyncClient.adminDeleteUser(userId)
                .onSuccess {
                    showBanner("🗑️ Đã xóa vĩnh viễn tài khoản!")
                    loadAdminVault()
                }
                .onFailure { err -> showBanner("⚠️ ${err.message.orEmpty()}") }
        }
    }

    fun attachWindowForStream(window: Window?) {
        screenStreamController.attachWindow(window)
    }

    fun detachWindowForStream(window: Window?) {
        screenStreamController.detachWindow(window)
    }

    fun updatePlayerTelemetry(telemetry: CompanionPlayerTelemetry) {
        _playerTelemetry.value = telemetry
    }

    fun consumeRemotePlayerCommand(commandId: Long) {
        if (_remotePlayerCommand.value?.id == commandId) {
            _remotePlayerCommand.value = null
        }
    }

    fun markExternalOverlayLaunching() {
        externalPickerLaunchTimestampMs = System.currentTimeMillis()
    }

    fun clearExternalOverlayLaunching() {
        externalPickerLaunchTimestampMs = 0L
    }

    fun isWithinExternalOverlayGraceWindow(): Boolean {
        val elapsed = System.currentTimeMillis() - externalPickerLaunchTimestampMs
        return externalPickerLaunchTimestampMs > 0L && elapsed in 0L..60_000L
    }

    fun setSessionGeminiCredentials(
        apiKey: String,
        modelId: String? = null,
        silent: Boolean = false
    ) {
        val cleanInputKey = apiKey.trim()
        prefs.setSessionGeminiCredentials(cleanInputKey, modelId)
        val effectiveKey = prefs.geminiApiKey.value
        val activeModel = prefs.subtitleConfig.value.geminiModelId
        if (cleanInputKey.isNotEmpty()) {
            refreshGeminiModelsFromApi(cleanInputKey, silent = true)
            if (!silent) {
                val msg = if (isTvHardware) {
                    "💾 Đã lưu cố định Gemini Key trên TV ($activeModel)!"
                } else {
                    "💾 Đã lưu cố định Gemini Key trong máy ($activeModel) & tự đồng bộ sang TV!"
                }
                showBanner(msg)
            }
        } else if (!modelId.isNullOrBlank() && !silent) {
            val suffix = if (effectiveKey.isNotBlank()) " (đã giữ nguyên API Key)" else ""
            showBanner("✨ Đã chọn Model Gemini: $activeModel$suffix")
        }
        if (!isTvHardware && effectiveKey.isNotBlank()) {
            syncCredentialsToSelectedTvIfOnline(silent = true)
        }
    }

    fun wipeGeminiKeyFromRam(silent: Boolean = false, clearPhoneVault: Boolean = false) {
        val hadKey = prefs.geminiApiKey.value.isNotBlank()
        prefs.wipeSessionGeminiKeyFromRam(clearPhoneVault = clearPhoneVault)
        if (!silent && hadKey) {
            showBanner(
                if (clearPhoneVault) "🗑 Đã xóa API Key khỏi bộ nhớ Android!"
                else "🧹 Đã xóa sạch Gemini API Key khỏi RAM!"
            )
        }
        if (clearPhoneVault && !isTvHardware) {
            val tvIp = prefs.selectedTvIp.value ?: return
            viewModelScope.launch {
                remoteTvClient.sendAction(tvIp, JSONObject().put("type", "wipe_gemini"))
            }
        }
    }

    fun refreshGeminiModelsFromApi(
        apiKeyOverride: String = prefs.geminiApiKey.value,
        silent: Boolean = false
    ) {
        val cleanKey = apiKeyOverride.trim()
        val hasCloud = CloudSyncClient.isSignedIn()
        if (!hasCloud && cleanKey.isEmpty()) {
            if (!silent) {
                showBanner("☁️ Sign in to NKI Cloud (or paste a Gemini Key) to load live models!")
            }
            return
        }
        viewModelScope.launch {
            var live: List<String> = emptyList()
            if (hasCloud) {
                live = CloudSyncClient.fetchLiveModels().getOrDefault(emptyList())
            }
            if (live.isEmpty() && cleanKey.isNotEmpty()) {
                live = geminiInspector.fetchAvailableGeminiModels(cleanKey)
            }
            if (live.isNotEmpty()) {
                prefs.registerDiscoveredModels(live, replaceWithLive = true)
                if (!silent) {
                    showBanner("✅ Loaded ${live.size} live Gemini models from API!")
                }
            } else if (!silent) {
                showBanner("⚠️ Could not load live models — check NKI Cloud / API Key!")
            }
        }
    }

    fun selectCountry(country: CountryCategory) {
        if (_selectedCountry.value == country && _sections.value.isNotEmpty()) return
        _selectedCountry.value = country
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        refreshCatalog(forceRefresh = false)
    }

    fun refreshCatalog(forceRefresh: Boolean = true) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _isLoadingCatalog.value = true
            val loaded = repository.loadHomeSections(
                selectedCountry = _selectedCountry.value,
                customProviders = prefs.customProviders.value,
                forceRefresh = forceRefresh
            )
            _sections.value = loaded
            _isLoadingCatalog.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _aiVibeSummary.value = ""
            _isSearching.value = false
            return
        }
    }

    fun executeSearch(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        _searchQuery.value = trimmed
        _aiVibeSummary.value = ""
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchAll(trimmed, prefs.customProviders.value)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun executeAiVibeSearch(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            showBanner("✨ Nhập hoặc nói mô tả phim bạn muốn xem (vd: phim tiên hiệp đánh nhau cháy)!")
            return
        }
        _searchQuery.value = trimmed
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _aiVibeSummary.value = "✨ Gemini AI đang phân tích gu phim: \"$trimmed\"..."
            val (summary, items) = CloudSyncClient.searchAiVibe(trimmed)
            if (items.isNotEmpty()) {
                _aiVibeSummary.value = summary.ifBlank { "✨ Đã tìm thấy ${items.size} phim hợp gu!" }
                _searchResults.value = items
            } else {
                _aiVibeSummary.value = ""
                _searchResults.value = repository.searchAll(trimmed, prefs.customProviders.value)
            }
            _isSearching.value = false
        }
    }

    fun removeContinueWatchingItem(movieId: String) {
        prefs.removeWatchHistoryItem(movieId)
        viewModelScope.launch {
            CloudSyncClient.removeWatchProgress(listOf(movieId))
        }
        showBanner("🗑️ Đã xóa khỏi Tiếp tục xem")
    }

    fun preTranslateDetailEpisode(movie: MovieItem, episode: Episode) {
        val targetLang = prefs.subtitleConfig.value.resolvedPrimaryLanguage().code.ifBlank { "vi" }
        detailSubJob?.cancel()
        detailSubJob = viewModelScope.launch {
            _detailSubState.value = DetailSubPreTranslateState(
                movieId = movie.id,
                episodeId = episode.id,
                isRunning = true,
                percent = 8,
                statusText = "☁️ Đang kiểm tra kho Sub NKI Cloud ($targetLang)...",
                isReady = false
            )
            val slug = CloudSyncClient.normalizeMovieSlug(movie.id)
            CloudSyncClient.activeMovieSlug = slug

            // 1. Instant Cloud Cache Hit
            val cloudHit = CloudSyncClient.fetchCloudSubtitle(movie.id, episode.name.ifBlank { episode.id }, targetLang)
            if (cloudHit != null && cloudHit.primaryCues.size >= 3) {
                _detailSubState.value = DetailSubPreTranslateState(
                    movieId = movie.id,
                    episodeId = episode.id,
                    isRunning = false,
                    percent = 100,
                    statusText = "☁️ Đã có sẵn Sub ${targetLang.uppercase()} trên NKI Cloud (${cloudHit.primaryCues.size} câu) — Bấm Xem ngay!",
                    isReady = true
                )
                return@launch
            }

            // 2. Discover online SRT & download
            _detailSubState.value = _detailSubState.value.copy(
                percent = 20,
                statusText = "🔍 Đang tìm phụ đề online cho ${episode.name}..."
            )
            val tracks = onlineSubFetcher.discoverSubtitles(movie, episode, targetLang)
            val picked = tracks.firstOrNull { it.languageCode.equals(targetLang, ignoreCase = true) }
                ?: tracks.firstOrNull { it.languageCode.equals("en", ignoreCase = true) }
                ?: tracks.firstOrNull()

            if (picked == null) {
                _detailSubState.value = DetailSubPreTranslateState(
                    movieId = movie.id,
                    episodeId = episode.id,
                    isRunning = false,
                    percent = 0,
                    statusText = "🎙 Chưa có file SRT rời — Mở phát phim để AI tự nghe Audio dịch trước 10 phút!",
                    isReady = false
                )
                return@launch
            }

            _detailSubState.value = _detailSubState.value.copy(
                percent = 32,
                statusText = "⬇️ Đang tải bản Sub (${picked.label})..."
            )
            val cues = withContext(Dispatchers.IO) {
                runCatching {
                    if (picked.isRawContent) {
                        SubtitleParser.parse(picked.urlOrContent)
                    } else {
                        val req = Request.Builder().url(picked.urlOrContent).build()
                        detailSubHttp.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) emptyList()
                            else SubtitleParser.parseBytes(resp.body?.bytes() ?: ByteArray(0))
                        }
                    }
                }.getOrDefault(emptyList())
            }

            if (cues.size < 3) {
                _detailSubState.value = DetailSubPreTranslateState(
                    movieId = movie.id,
                    episodeId = episode.id,
                    isRunning = false,
                    percent = 0,
                    statusText = "⚠️ Không đọc được bản Sub online — Mở phim để bật AI Audio Sub.",
                    isReady = false
                )
                return@launch
            }

            // 3. Translate in batches with real 0-100% progress & upload to Shared NKI Cloud!
            val needTranslate = !picked.languageCode.equals(targetLang, ignoreCase = true)
            val finalCues = ArrayList<SubtitleCue>(cues.size)
            if (!needTranslate) {
                finalCues.addAll(cues)
            } else {
                val cfg = prefs.subtitleConfig.value
                val apiKey = prefs.geminiApiKey.value
                val mode = if (CloudSyncClient.canUseServerAi() || apiKey.isNotBlank()) {
                    TranslationEngineMode.GEMINI_AI
                } else {
                    TranslationEngineMode.FAST_FREE
                }
                val batchSize = 45
                val totalBatches = ((cues.size + batchSize - 1) / batchSize).coerceAtLeast(1)
                for (bIdx in 0 until totalBatches) {
                    val start = bIdx * batchSize
                    val end = (start + batchSize).coerceAtMost(cues.size)
                    val pct = 35 + ((bIdx * 58) / totalBatches)
                    val progMsg = "✨ AI đang dịch sang ${targetLang.uppercase()} ($end/${cues.size} câu)..."
                    _detailSubState.value = _detailSubState.value.copy(
                        percent = pct.coerceIn(35, 95),
                        statusText = progMsg
                    )
                    com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(
                        context = getApplication<Application>(),
                        title = "ChillFilm • ${movie.title}",
                        message = progMsg,
                        percent = pct.coerceIn(35, 95),
                        isActive = true
                    )
                    val translated = detailSubTranslator.translateCueBatch(
                        cues = cues.subList(start, end),
                        targetLang = targetLang,
                        mode = mode,
                        geminiApiKey = apiKey,
                        geminiModelId = cfg.geminiModelId
                    )
                    finalCues.addAll(translated)
                }
            }
            com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(
                context = getApplication<Application>(),
                title = "",
                message = "",
                percent = 100,
                isActive = false
            )

            if (CloudSyncClient.isSignedIn()) {
                _detailSubState.value = _detailSubState.value.copy(
                    percent = 97,
                    statusText = "☁️ Đang lưu phụ đề lên NKI Cloud Pool..."
                )
                CloudSyncClient.uploadCloudSubtitle(
                    rawMovieSlug = movie.id,
                    rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                    primaryLangCode = targetLang,
                    secondaryLangCode = "",
                    sourceLangCode = picked.languageCode,
                    sourcePlatform = picked.sourcePlatform,
                    isAiAudio = false,
                    isFullyTranslated = true,
                    completedAudioSlots = emptySet(),
                    sourceCues = cues,
                    primaryCues = finalCues,
                    secondaryCues = emptyList()
                )
            }

            _detailSubState.value = DetailSubPreTranslateState(
                movieId = movie.id,
                episodeId = episode.id,
                isRunning = false,
                percent = 100,
                statusText = "✅ Đã dịch xong 100% Sub ${targetLang.uppercase()} (${finalCues.size} câu) • Bấm Xem ngay!",
                isReady = true
            )
            showBanner("✅ Đã dịch xong Sub ${targetLang.uppercase()} cho ${episode.name}!")
        }
    }

    fun repairDetailEpisodeSubGaps(movie: MovieItem, episode: Episode) {
        val ctx = getApplication<Application>()
        detailSubJob?.cancel()
        detailSubJob = viewModelScope.launch {
            _detailSubState.value = DetailSubPreTranslateState(
                movieId = movie.id,
                episodeId = episode.id,
                isRunning = true,
                percent = 4,
                statusText = "🔍 Đang quét các đoạn thiếu Sub (không dịch lại phần đã có)...",
                isReady = false
            )
            CloudSyncClient.activeMovieSlug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
            val subMgr = ThaiSubtitleManager(ctx.cacheDir, File(ctx.filesDir, "chillfilm_saved_subs_v1"))
            try {
                val (totalCues, netAdded) = subMgr.repairMissingAudioSubGaps(
                    movie = movie,
                    episode = episode,
                    config = prefs.subtitleConfig.value,
                    geminiApiKey = prefs.geminiApiKey.value,
                    onProgress = { pct, msg ->
                        _detailSubState.value = DetailSubPreTranslateState(
                            movieId = movie.id,
                            episodeId = episode.id,
                            isRunning = pct in 1..99,
                            percent = pct,
                            statusText = msg,
                            isReady = pct >= 100
                        )
                        com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(
                            context = ctx,
                            title = "Dịch bù Sub • ${movie.title}",
                            message = msg,
                            percent = pct,
                            isActive = pct in 1..99
                        )
                    }
                )
                if (netAdded > 0) {
                    showBanner("🔧 Đã dịch bù thêm +$netAdded câu Sub (Tổng $totalCues câu)!")
                }
            } finally {
                com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(ctx, "", "", 100, false)
                subMgr.release()
            }
        }
    }

    /**
     * #4 Cinema AI Subtitle Polisher from Detail Screen (with #8 Foreground Service protection).
     */
    fun polishDetailEpisodeSubtitles(movie: MovieItem, episode: Episode) {
        val ctx = getApplication<Application>()
        detailSubJob?.cancel()
        detailSubJob = viewModelScope.launch {
            _detailSubState.value = DetailSubPreTranslateState(
                movieId = movie.id,
                episodeId = episode.id,
                isRunning = true,
                percent = 5,
                statusText = "✨ Đang chuốt lời điện ảnh & đồng nhất xưng hô toàn phim...",
                isReady = false
            )
            CloudSyncClient.activeMovieSlug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
            val subMgr = ThaiSubtitleManager(ctx.cacheDir, File(ctx.filesDir, "chillfilm_saved_subs_v1"))
            try {
                val count = subMgr.polishSubtitlesWithAi(
                    movie = movie,
                    episode = episode,
                    config = prefs.subtitleConfig.value,
                    onProgress = { pct, msg ->
                        _detailSubState.value = DetailSubPreTranslateState(
                            movieId = movie.id,
                            episodeId = episode.id,
                            isRunning = pct in 1..99,
                            percent = pct,
                            statusText = msg,
                            isReady = pct >= 100
                        )
                        com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(
                            context = ctx,
                            title = "Chuốt lời AI • ${movie.title}",
                            message = msg,
                            percent = pct,
                            isActive = pct in 1..99
                        )
                    }
                )
                if (count > 0) {
                    showBanner("✨ Đã chuốt xong $count câu Sub điện ảnh & đồng bộ Cloud!")
                }
            } finally {
                com.phyll.filmhub.subtitle.SubtitleTranslationService.updateProgress(ctx, "", "", 100, false)
                subMgr.release()
            }
        }
    }

    fun openMovieDetail(movie: MovieItem) {
        if (_detailSubState.value.movieId != movie.id) {
            _detailSubState.value = DetailSubPreTranslateState(movieId = movie.id)
        }
        navigateTo(AppScreen.Detail(movie = movie, isResolving = movie.servers.isEmpty()))
        if (movie.servers.isEmpty()) {
            viewModelScope.launch {
                val resolved = repository.resolveMovieDetail(movie, prefs.customProviders.value)
                if (_currentScreen.value is AppScreen.Detail) {
                    _currentScreen.value = AppScreen.Detail(movie = resolved, isResolving = false)
                }
            }
        }
    }

    fun quickPlayMovie(movie: MovieItem) {
        val lastProgress = prefs.getLatestMovieWatchProgress(movie.id)
        val allEps = movie.servers.flatMap { it.episodes }
        val readyEp = if (lastProgress != null && allEps.isNotEmpty()) {
            allEps.firstOrNull { it.id == lastProgress.episodeId || it.streamUrl == lastProgress.streamUrl }
                ?: movie.firstPlayableEpisode
        } else {
            movie.firstPlayableEpisode
        }
        if (readyEp != null) {
            val resumeMs = if (lastProgress != null &&
                (readyEp.id == lastProgress.episodeId || readyEp.streamUrl == lastProgress.streamUrl)
            ) {
                (lastProgress.positionMs - 2_000L).coerceAtLeast(0L)
            } else {
                0L
            }
            startTargetAwarePlayback(movie, readyEp, resumeMs)
            return
        }
        showBanner("⏳ Đang kết nối máy chủ phim & chuẩn bị phụ đề AI...")
        viewModelScope.launch {
            val resolved = repository.resolveMovieDetail(movie, prefs.customProviders.value)
            val resolvedEps = resolved.servers.flatMap { it.episodes }
            val ep = if (lastProgress != null && resolvedEps.isNotEmpty()) {
                resolvedEps.firstOrNull { it.id == lastProgress.episodeId || it.streamUrl == lastProgress.streamUrl }
                    ?: resolved.firstPlayableEpisode
            } else {
                resolved.firstPlayableEpisode
            }
            if (ep != null) {
                val resumeMs = if (lastProgress != null &&
                    (ep.id == lastProgress.episodeId || ep.streamUrl == lastProgress.streamUrl)
                ) {
                    (lastProgress.positionMs - 2_000L).coerceAtLeast(0L)
                } else {
                    0L
                }
                startTargetAwarePlayback(resolved, ep, resumeMs)
            } else {
                navigateTo(AppScreen.Detail(movie = resolved, isResolving = false))
            }
        }
    }

    fun resumeFromWatchHistory(progress: WatchProgress) {
        if (progress.movieId.startsWith("yt_")) {
            val ytId = progress.movieId.removePrefix("yt_")
            showBanner("⏳ Đang kết nối lại luồng YouTube 1080p...")
            viewModelScope.launch {
                val freshYt = repository.resolveDirectOrYouTubeStream(progress.title, "yt:$ytId")
                val ep = freshYt.firstPlayableEpisode
                if (ep != null) {
                    startTargetAwarePlayback(freshYt, ep, progress.positionMs)
                }
            }
            return
        }
        val targetEpKey = CloudSyncClient.normalizeEpisodeKey(progress.episodeName.ifBlank { progress.episodeId })
        val cached = repository.getCachedMovie(progress.movieId)
        if (cached != null) {
            val ep = cached.servers.flatMap { it.episodes }
                .firstOrNull {
                    it.id == progress.episodeId ||
                        CloudSyncClient.normalizeEpisodeKey(it.name.ifBlank { it.id }) == targetEpKey ||
                        (progress.streamUrl.isNotBlank() && it.streamUrl == progress.streamUrl)
                }
                ?: cached.firstPlayableEpisode
            if (ep != null) {
                startTargetAwarePlayback(cached, ep, progress.positionMs)
                return
            }
        }
        if (progress.movieId.startsWith("modern_")) {
            val slug = CloudSyncClient.normalizeMovieSlug(progress.movieId)
            showBanner("☁️ Loading synced movie details...")
            viewModelScope.launch {
                val stub = MovieItem(
                    id = "modern_$slug",
                    title = progress.title,
                    posterUrl = progress.posterUrl,
                    backdropUrl = progress.posterUrl,
                    detailLookupId = slug
                )
                val resolved = repository.resolveMovieDetail(stub, prefs.customProviders.value)
                val eps = resolved.servers.flatMap { it.episodes }
                val matchedEp = eps.firstOrNull {
                    CloudSyncClient.normalizeEpisodeKey(it.name.ifBlank { it.id }) == targetEpKey ||
                        (progress.streamUrl.isNotBlank() && it.streamUrl == progress.streamUrl)
                } ?: resolved.firstPlayableEpisode
                if (matchedEp != null) {
                    startTargetAwarePlayback(resolved, matchedEp, progress.positionMs)
                } else if (progress.streamUrl.isNotBlank()) {
                    val adHoc = repository.registerAdHocStream(progress.title, progress.streamUrl)
                    adHoc.firstPlayableEpisode?.let { ep ->
                        startTargetAwarePlayback(adHoc, ep, progress.positionMs)
                    }
                }
            }
            return
        }
        val adHoc = repository.registerAdHocStream(progress.title, progress.streamUrl)
        adHoc.firstPlayableEpisode?.let { ep ->
            startTargetAwarePlayback(adHoc, ep, progress.positionMs)
        }
    }

    fun startPlayback(movie: MovieItem, episode: Episode, startPositionMs: Long = 0L) {
        val effectiveStartMs = if (startPositionMs > 0L) {
            startPositionMs
        } else {
            prefs.getEpisodeResumePositionMs(movie.id, episode.id, episode.streamUrl, episode.name)
        }
        _activePlaybackSession.value = ActivePlaybackSession(
            movie = movie,
            episode = episode,
            startPositionMs = effectiveStartMs
        )
        _isPlayerMinimized.value = false
        // If Host / Co-Controller switches movie or episode while inside Watch Party 2.0, sync the whole room live!
        val party = _watchPartyState.value
        if (party.isInRoom && party.canControl) {
            val mMeta = movieToPartyMeta(movie)
            val eMeta = episodeToPartyMeta(episode)
            val nextKey = "${mMeta.slug}|${eMeta.linkM3u8}"
            if (nextKey != partyLastMovieKey && eMeta.linkM3u8.isNotBlank()) {
                partyLastMovieKey = nextKey
                viewModelScope.launch {
                    CloudSyncClient.partyControl(
                        roomCode = party.roomCode,
                        hostToken = party.hostToken,
                        memberId = party.memberId,
                        nickname = party.nickname,
                        isPlaying = true,
                        positionSec = effectiveStartMs / 1000.0,
                        movie = mMeta,
                        episode = eMeta
                    ).onSuccess { res ->
                        partyLastStateUpdatedAt = res.stateUpdatedAt
                    }
                }
            }
        }
    }

    fun minimizePlayer() {
        if (_activePlaybackSession.value != null) {
            _isPlayerMinimized.value = true
            flushPendingCloudProgress()
        }
    }

    fun restoreMinimizedPlayer() {
        if (_activePlaybackSession.value != null) {
            _isPlayerMinimized.value = false
        }
    }

    fun closePlayerSession() {
        flushPendingCloudProgress()
        if (_watchPartyState.value.isInRoom) {
            leaveWatchParty(silent = true)
        }
        _activePlaybackSession.value = null
        _isPlayerMinimized.value = false
        _playerTelemetry.value = CompanionPlayerTelemetry(isActive = false)
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.setAppLanguage(language)
        refreshCatalog()
    }

    fun toggleMyList(movie: MovieItem) {
        val (added, cloudItem, removedKeys) = prefs.toggleMyListWithRemovedKeys(movie)
        showBanner(if (added) "❤️ Đã lưu \"${movie.title}\" vào Yêu thích" else "🗑️ Đã xóa \"${movie.title}\" khỏi Yêu thích")
        if (CloudSyncClient.isSignedIn()) {
            viewModelScope.launch(Dispatchers.IO) {
                if (added) {
                    CloudSyncClient.pushMyListUpdates(added = listOf(cloudItem))
                } else if (removedKeys.isNotEmpty()) {
                    CloudSyncClient.pushMyListUpdates(removedKeys = removedKeys)
                }
            }
        }
    }

    fun removeMyListItem(movie: MovieItem) {
        val removedKeys = prefs.removeMyListItem(movie)
        showBanner("🗑️ Đã xóa \"${movie.title}\" khỏi Yêu thích")
        if (CloudSyncClient.isSignedIn() && removedKeys.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                CloudSyncClient.pushMyListUpdates(removedKeys = removedKeys)
            }
        }
    }

    /* ==================== PILLAR 3: OFFLINE VIDEO + AI SUBTITLE DOWNLOADS ==================== */
    fun openDownloads() {
        OfflineDownloadManager.ensureInitialized(getApplication())
        navigateTo(AppScreen.Downloads)
    }

    fun downloadEpisodeOffline(movie: MovieItem, episode: Episode) {
        val ctx = getApplication<Application>()
        if (episode.streamUrl.isBlank()) {
            showBanner("⚠️ Không tìm thấy luồng phát cho tập này")
            return
        }
        val langCode = prefs.subtitleConfig.value.resolvedPrimaryLanguage().code
        OfflineDownloadManager.enqueueEpisodeDownload(
            context = ctx,
            movie = movie,
            episode = episode,
            subtitleLangCode = langCode
        )
        showBanner("⬇️ Đang tải ngầm \"${movie.title} • ${episode.name}\" + Phụ đề ${langCode.uppercase()}!")
    }

    fun pauseOfflineDownload(id: String) {
        OfflineDownloadManager.pauseDownload(getApplication(), id)
    }

    fun resumeOfflineDownload(id: String) {
        OfflineDownloadManager.resumeDownload(getApplication(), id)
    }

    fun deleteOfflineDownload(id: String) {
        OfflineDownloadManager.removeDownload(getApplication(), id)
        showBanner("🗑️ Đã xóa tập phim tải về khỏi bộ nhớ máy")
    }

    fun playOfflineDownload(record: OfflineEpisodeRecord) {
        val (movie, episode) = record.toPlayablePair()
        val resumeMs = prefs.getEpisodeResumePositionMs(movie.id, episode.id, episode.streamUrl, episode.name)
        startPlayback(movie, episode, resumeMs)
    }

    fun exportEpisodeSrt(movie: MovieItem, episode: Episode) {
        val ctx = getApplication<Application>()
        val langCode = prefs.subtitleConfig.value.resolvedPrimaryLanguage().code
        viewModelScope.launch(Dispatchers.IO) {
            val subMgr = ThaiSubtitleManager(ctx.cacheDir, File(ctx.filesDir, "chillfilm_saved_subs_v1"))
            var cues = subMgr.loadEpisodeSubCache(movie, episode, langCode)?.primaryCues.orEmpty()
            if (cues.size < 3) {
                cues = OfflineDownloadManager.loadOfflineSubtitleCues(ctx, movie, episode)?.second.orEmpty()
            }
            if (cues.size < 3 && CloudSyncClient.isSignedIn()) {
                cues = CloudSyncClient.fetchCloudSubtitle(
                    rawMovieSlug = movie.detailLookupId ?: movie.id,
                    rawEpisodeNameOrId = episode.name.ifBlank { episode.id },
                    langCode = langCode
                )?.primaryCues.orEmpty()
            }
            if (cues.size < 3) {
                withContext(Dispatchers.Main) {
                    showBanner("⚠️ Chưa có phụ đề ${langCode.uppercase()} cho tập này — hãy dịch Sub AI trước!")
                }
                return@launch
            }
            val slug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
            val epKey = CloudSyncClient.normalizeEpisodeKey(episode.name.ifBlank { episode.id })
            val res = OfflineDownloadManager.exportSrtToPublicDownloads(ctx, "$slug-$epKey-$langCode", cues)
            withContext(Dispatchers.Main) {
                res.onSuccess { path ->
                    showBanner("📄 Đã xuất phụ đề .SRT (${cues.size} câu) → $path")
                }.onFailure { err ->
                    showBanner("⚠️ Lỗi xuất .SRT: ${err.message.orEmpty()}")
                }
            }
        }
    }

    fun exportOfflineRecordSrt(record: OfflineEpisodeRecord) {
        val (movie, episode) = record.toPlayablePair()
        exportEpisodeSrt(movie, episode)
    }

    /* ==================== PILLAR 4: WATCH PARTY 2.0 ENGINE ==================== */
    private fun defaultPartyNick(): String {
        return prefs.cloudSession.value?.displayName?.takeIf { it.isNotBlank() }
            ?: prefs.cloudSession.value?.username?.takeIf { it.isNotBlank() }
            ?: if (isTvHardware) "Smart TV" else "ChillFan"
    }

    private fun movieToPartyMeta(movie: MovieItem): WatchPartyMovieMeta {
        val c = prefs.toCloudMyListItem(movie)
        return WatchPartyMovieMeta(
            slug = c.slug,
            source = c.source,
            sourceId = c.sourceId,
            name = movie.title,
            poster = movie.posterUrl
        )
    }

    private fun episodeToPartyMeta(ep: Episode): WatchPartyEpisodeMeta {
        return WatchPartyEpisodeMeta(
            name = ep.name.ifBlank { "Full" },
            slug = CloudSyncClient.normalizeEpisodeKey(ep.name.ifBlank { ep.id }),
            linkM3u8 = ep.streamUrl
        )
    }

    fun openWatchPartyHub(initialTab: Int = 0, movie: MovieItem? = null, episode: Episode? = null) {
        val active = _activePlaybackSession.value
        val pickedMovie = movie ?: active?.movie
        val pickedEp = episode ?: active?.episode ?: pickedMovie?.firstPlayableEpisode
        _watchPartyState.value = _watchPartyState.value.copy(
            showHubDialog = true,
            hubInitialTab = initialTab,
            needPasscodePrompt = false,
            errorText = "",
            createPickedMovie = pickedMovie,
            createPickedEpisode = pickedEp,
            nickname = _watchPartyState.value.nickname.ifBlank { defaultPartyNick() }
        )
        if (initialTab == 0) refreshPartyLobby()
    }

    fun dismissWatchPartyHub() {
        _watchPartyState.value = _watchPartyState.value.copy(showHubDialog = false, errorText = "", quickSearchResults = emptyList())
    }

    fun refreshPartyLobby() {
        viewModelScope.launch {
            _watchPartyState.value = _watchPartyState.value.copy(lobbyLoading = true)
            CloudSyncClient.partyListLobby()
                .onSuccess { rooms ->
                    _watchPartyState.value = _watchPartyState.value.copy(lobbyRooms = rooms, lobbyLoading = false)
                }
                .onFailure {
                    _watchPartyState.value = _watchPartyState.value.copy(lobbyLoading = false)
                }
        }
    }

    fun partyQuickSearch(keyword: String) {
        viewModelScope.launch {
            _watchPartyState.value = _watchPartyState.value.copy(quickSearching = true)
            val results = CloudSyncClient.partyQuickSearch(keyword)
            _watchPartyState.value = _watchPartyState.value.copy(quickSearchResults = results, quickSearching = false)
        }
    }

    fun pickMovieForPartyCreate(movie: MovieItem) {
        _watchPartyState.value = _watchPartyState.value.copy(
            createPickedMovie = movie,
            createPickedEpisode = movie.firstPlayableEpisode,
            quickSearchResults = emptyList()
        )
        if (movie.firstPlayableEpisode == null) {
            viewModelScope.launch {
                val resolved = repository.resolveMovieDetail(movie, prefs.customProviders.value)
                _watchPartyState.value = _watchPartyState.value.copy(
                    createPickedMovie = resolved,
                    createPickedEpisode = resolved.firstPlayableEpisode
                )
            }
        }
    }

    fun createWatchParty(
        roomTitle: String,
        nickname: String,
        visibility: String,
        controlMode: String,
        passcode: String,
        movie: MovieItem?,
        episode: Episode?
    ) {
        viewModelScope.launch {
            if (movie == null) {
                _watchPartyState.value = _watchPartyState.value.copy(errorText = "Hãy chọn 1 bộ phim trước khi mở phòng!")
                return@launch
            }
            _watchPartyState.value = _watchPartyState.value.copy(isBusy = true, errorText = "")
            val resolvedMovie = if (episode == null && movie.firstPlayableEpisode == null) {
                repository.resolveMovieDetail(movie, prefs.customProviders.value)
            } else {
                movie
            }
            val resolvedEp = episode ?: resolvedMovie.firstPlayableEpisode
            if (resolvedEp == null || resolvedEp.streamUrl.isBlank()) {
                _watchPartyState.value = _watchPartyState.value.copy(isBusy = false, errorText = "Phim chưa có luồng phát khả dụng")
                return@launch
            }
            val posSec = (_playerTelemetry.value.positionMs.takeIf { _activePlaybackSession.value?.episode?.streamUrl == resolvedEp.streamUrl } ?: 0L) / 1000.0
            CloudSyncClient.partyCreate(
                nickname = nickname.ifBlank { defaultPartyNick() },
                roomTitle = roomTitle.ifBlank { resolvedMovie.title },
                visibility = visibility,
                controlMode = controlMode,
                passcode = passcode,
                movie = movieToPartyMeta(resolvedMovie),
                episode = episodeToPartyMeta(resolvedEp),
                positionSec = posSec,
                isPlaying = true
            ).onSuccess { res ->
                applyPartyJoinSuccess(res, nickname.ifBlank { defaultPartyNick() })
                if (_activePlaybackSession.value?.episode?.streamUrl != resolvedEp.streamUrl) {
                    startPlayback(resolvedMovie, resolvedEp, (posSec * 1000).toLong())
                }
                showBanner("🍿 Đã mở phòng ${res.roomCode}! Chia sẻ mã hoặc QR để bạn bè vào xem.")
            }.onFailure { err ->
                _watchPartyState.value = _watchPartyState.value.copy(isBusy = false, errorText = err.message ?: "Không tạo được phòng")
            }
        }
    }

    fun joinWatchParty(code: String, nickname: String, passcode: String = "") {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length < 4) {
            _watchPartyState.value = _watchPartyState.value.copy(errorText = "Mã phòng không hợp lệ")
            return
        }
        viewModelScope.launch {
            _watchPartyState.value = _watchPartyState.value.copy(isBusy = true, errorText = "", needPasscodePrompt = false)
            val nick = nickname.ifBlank { defaultPartyNick() }
            CloudSyncClient.partyJoin(cleanCode, nick, passcode = passcode)
                .onSuccess { res ->
                    applyPartyJoinSuccess(res, nick)
                    val snap = res.party
                    val elapsedMs = if (snap.isPlaying) {
                        ((snap.serverTime - snap.stateUpdatedAt).coerceAtLeast(0L) + snap.rttMs / 2L)
                    } else 0L
                    val startMs = ((snap.positionSec * 1000.0).toLong() + elapsedMs).coerceAtLeast(0L)
                    transitionPlaybackForParty(snap.movie, snap.episode, startMs)
                    showBanner("🍿 Đã vào phòng ${res.roomCode} của ${snap.hostName}!")
                }
                .onFailure { err ->
                    val needPass = err is PartyPasscodeRequiredException
                    _watchPartyState.value = _watchPartyState.value.copy(
                        isBusy = false,
                        needPasscodePrompt = needPass,
                        prefillJoinCode = cleanCode,
                        errorText = err.message ?: "Không thể vào phòng"
                    )
                }
        }
    }

    private fun applyPartyJoinSuccess(res: com.phyll.filmhub.cloud.WatchPartyJoinOrCreateResult, nick: String) {
        val snap = res.party
        partyLastMsgId = snap.messages.maxOfOrNull { it.id } ?: 0L
        partyLastReactionTs = snap.reactions.maxOfOrNull { it.createdAt } ?: 0L
        partyLastStateUpdatedAt = snap.stateUpdatedAt
        partyLastActionAt = snap.lastActionAt
        partyLastMovieKey = "${snap.movie.slug}|${snap.episode.linkM3u8}"
        _watchPartyState.value = _watchPartyState.value.copy(
            showHubDialog = false,
            isBusy = false,
            errorText = "",
            needPasscodePrompt = false,
            roomCode = res.roomCode,
            roomTitle = snap.roomTitle,
            visibility = snap.visibility,
            controlMode = snap.controlMode,
            hasPasscode = snap.hasPasscode,
            hostToken = res.hostToken,
            hostName = snap.hostName,
            hostInactive = snap.hostInactive,
            memberId = res.memberId,
            nickname = nick,
            isHost = res.isHost,
            panelVisible = true,
            syncStatusText = "● Sync OK (${snap.rttMs}ms)",
            actionPillText = snap.lastActionText,
            actionPillTs = snap.lastActionAt,
            members = snap.members,
            queue = snap.queue,
            messages = snap.messages,
            floatingReactions = emptyList()
        )
        startPartyPollingLoop()
    }

    private fun transitionPlaybackForParty(movieMeta: WatchPartyMovieMeta, epMeta: WatchPartyEpisodeMeta, startMs: Long) {
        if (epMeta.linkM3u8.isBlank()) return
        partyLastMovieKey = "${movieMeta.slug}|${epMeta.linkM3u8}"
        val providerId = if (movieMeta.source == "phimapi") "modern_vod" else "cloud_${movieMeta.source}"
        val ep = Episode(
            id = epMeta.slug.ifBlank { epMeta.name },
            name = epMeta.name.ifBlank { "Full" },
            streamUrl = epMeta.linkM3u8
        )
        val movie = MovieItem(
            id = if (movieMeta.source == "phimapi") "modern_${movieMeta.slug}" else "${providerId}_${movieMeta.sourceId}_${movieMeta.slug}",
            title = movieMeta.name,
            posterUrl = movieMeta.poster,
            backdropUrl = movieMeta.poster,
            providerId = providerId,
            servers = listOf(ServerGroup("Party", listOf(ep))),
            detailLookupId = if (movieMeta.sourceId.isNotBlank() && movieMeta.source != "phimapi") "${movieMeta.slug}|${movieMeta.sourceId}" else movieMeta.slug
        )
        startPlayback(movie, ep, startMs)
    }

    private fun startPartyPollingLoop() {
        partyPollJob?.cancel()
        partyPollJob = viewModelScope.launch {
            while (isActive && _watchPartyState.value.isInRoom) {
                delay(1_500L)
                val st = _watchPartyState.value
                if (!st.isInRoom) break
                CloudSyncClient.partyPoll(
                    roomCode = st.roomCode,
                    memberId = st.memberId,
                    nickname = st.nickname,
                    hostToken = st.hostToken,
                    sinceMsgId = partyLastMsgId,
                    sinceReactionTs = partyLastReactionTs,
                    status = partyLastReportedStatus,
                    posSec = partyLastReportedPosSec
                ).onSuccess { snap ->
                    val cur = _watchPartyState.value
                    if (!cur.isInRoom || cur.roomCode != snap.roomCode) return@onSuccess

                    val newMsgs = snap.messages.filter { it.id > partyLastMsgId }
                    if (newMsgs.isNotEmpty()) partyLastMsgId = newMsgs.maxOf { it.id }

                    val newReacts = snap.reactions.filter { it.createdAt > partyLastReactionTs }
                    if (newReacts.isNotEmpty()) partyLastReactionTs = newReacts.maxOf { it.createdAt }

                    val nowLocal = System.currentTimeMillis()
                    val countdownLeadMs = snap.stateUpdatedAt - snap.serverTime
                    val countdownTarget = if (countdownLeadMs > 300L) nowLocal + countdownLeadMs - (snap.rttMs / 2L) else 0L

                    val incomingMovieKey = "${snap.movie.slug}|${snap.episode.linkM3u8}"
                    val movieChanged = incomingMovieKey != partyLastMovieKey && snap.episode.linkM3u8.isNotBlank()
                    if (movieChanged) {
                        val elapsed = if (snap.isPlaying) (snap.serverTime - snap.stateUpdatedAt).coerceAtLeast(0L) else 0L
                        transitionPlaybackForParty(snap.movie, snap.episode, ((snap.positionSec * 1000.0).toLong() + elapsed).coerceAtLeast(0L))
                    } else if (!cur.isHost || snap.stateUpdatedAt != partyLastStateUpdatedAt) {
                        // Compute RTT-compensated target position for 3-tier Smooth Soft-Sync on ExoPlayer
                        val elapsedMs = if (snap.isPlaying && snap.serverTime >= snap.stateUpdatedAt) {
                            (((snap.serverTime - snap.stateUpdatedAt) + snap.rttMs / 2L) * snap.playbackRate).toLong()
                        } else 0L
                        val targetMs = ((snap.positionSec * 1000.0).toLong() + elapsedMs).coerceAtLeast(0L)
                        val stateChanged = snap.stateUpdatedAt != partyLastStateUpdatedAt
                        partyLastStateUpdatedAt = snap.stateUpdatedAt
                        if (!cur.isHost || stateChanged) {
                            _watchPartySyncCommand.value = WatchPartySyncCommand(
                                id = System.nanoTime(),
                                targetPositionMs = targetMs,
                                shouldPlay = snap.isPlaying,
                                playbackRate = snap.playbackRate,
                                forceHardSeek = stateChanged,
                                countdownStartAtLocalMs = countdownTarget
                            )
                        }
                    }

                    val showNewAction = snap.lastActionAt > partyLastActionAt && snap.lastActionText.isNotBlank()
                    if (showNewAction) partyLastActionAt = snap.lastActionAt

                    _watchPartyState.value = cur.copy(
                        roomTitle = snap.roomTitle,
                        visibility = snap.visibility,
                        controlMode = snap.controlMode,
                        hasPasscode = snap.hasPasscode,
                        hostName = snap.hostName,
                        hostInactive = snap.hostInactive,
                        members = snap.members,
                        queue = snap.queue,
                        messages = (cur.messages + newMsgs).takeLast(120),
                        floatingReactions = (cur.floatingReactions.filter { nowLocal - it.createdAt < 4000L } + newReacts).takeLast(12),
                        countdownTargetLocalMs = if (countdownTarget > nowLocal) countdownTarget else cur.countdownTargetLocalMs,
                        actionPillText = if (showNewAction) snap.lastActionText else cur.actionPillText,
                        actionPillTs = if (showNewAction) snap.lastActionAt else cur.actionPillTs,
                        syncStatusText = "● Sync OK (${snap.rttMs}ms)"
                    )
                }
            }
        }
    }

    fun consumeWatchPartySyncCommand(id: Long) {
        if (_watchPartySyncCommand.value?.id == id) {
            _watchPartySyncCommand.value = null
        }
    }

    fun updatePartySyncBadge(text: String) {
        if (_watchPartyState.value.isInRoom) {
            _watchPartyState.value = _watchPartyState.value.copy(syncStatusText = text)
        }
    }

    fun reportLocalPlayerToParty(isPlaying: Boolean, posMs: Long, isBuffering: Boolean, pushControl: Boolean = false) {
        val st = _watchPartyState.value
        if (!st.isInRoom) return
        partyLastReportedPosSec = (posMs / 1000L).toInt().coerceAtLeast(0)
        partyLastReportedStatus = when {
            isBuffering -> "buffering"
            isPlaying -> "playing"
            else -> "paused"
        }
        if (pushControl && st.canControl) {
            viewModelScope.launch {
                CloudSyncClient.partyControl(
                    roomCode = st.roomCode,
                    hostToken = st.hostToken,
                    memberId = st.memberId,
                    nickname = st.nickname,
                    isPlaying = isPlaying,
                    positionSec = posMs / 1000.0
                ).onSuccess { res ->
                    partyLastStateUpdatedAt = res.stateUpdatedAt
                }
            }
        }
    }

    fun togglePartyPanel() {
        _watchPartyState.value = _watchPartyState.value.copy(panelVisible = !_watchPartyState.value.panelVisible)
    }

    fun forcePartyResync() {
        partyLastStateUpdatedAt = 0L
        showBanner("🔄 Đang đồng bộ lại mốc phát...")
    }

    fun sendPartyChat(text: String) {
        val st = _watchPartyState.value
        if (!st.isInRoom || text.isBlank()) return
        viewModelScope.launch {
            CloudSyncClient.partySendChat(st.roomCode, st.nickname, text, st.hostToken).onSuccess { msg ->
                if (msg != null && msg.id > partyLastMsgId) {
                    partyLastMsgId = msg.id
                    _watchPartyState.value = _watchPartyState.value.copy(
                        messages = (_watchPartyState.value.messages + msg).takeLast(120)
                    )
                }
            }
        }
    }

    fun sendPartyReaction(emoji: String) {
        val st = _watchPartyState.value
        if (!st.isInRoom) return
        val localReact = com.phyll.filmhub.cloud.WatchPartyReaction(
            id = "loc_${System.nanoTime()}",
            emoji = emoji,
            senderName = st.nickname,
            createdAt = System.currentTimeMillis()
        )
        _watchPartyState.value = st.copy(floatingReactions = (st.floatingReactions + localReact).takeLast(12))
        viewModelScope.launch {
            CloudSyncClient.partyReact(st.roomCode, st.memberId, st.nickname, emoji)
        }
    }

    fun startPartyCountdown() {
        val st = _watchPartyState.value
        if (!st.isInRoom || !st.canControl) return
        viewModelScope.launch {
            CloudSyncClient.partyControl(
                roomCode = st.roomCode,
                hostToken = st.hostToken,
                memberId = st.memberId,
                nickname = st.nickname,
                isPlaying = true,
                positionSec = partyLastReportedPosSec.toDouble(),
                countdown = true
            ).onSuccess { res ->
                partyLastStateUpdatedAt = res.stateUpdatedAt
                val lead = (res.stateUpdatedAt - res.serverTime).coerceAtLeast(3000L)
                val targetLocal = System.currentTimeMillis() + lead
                _watchPartyState.value = _watchPartyState.value.copy(
                    countdownTargetLocalMs = targetLocal,
                    actionPillText = res.lastActionText.ifBlank { "⏱️ Đếm ngược 3-2-1 bắt đầu cùng lúc!" },
                    actionPillTs = System.currentTimeMillis()
                )
                _watchPartySyncCommand.value = WatchPartySyncCommand(
                    id = System.nanoTime(),
                    targetPositionMs = (res.positionSec * 1000.0).toLong(),
                    shouldPlay = true,
                    forceHardSeek = true,
                    countdownStartAtLocalMs = targetLocal
                )
            }
        }
    }

    fun togglePartyControlMode() {
        val st = _watchPartyState.value
        if (!st.isInRoom || !st.isHost) return
        val next = if (st.controlMode == "EVERYONE") "HOST_ONLY" else "EVERYONE"
        viewModelScope.launch {
            CloudSyncClient.partyUpdateSettings(st.roomCode, st.hostToken, controlMode = next).onSuccess {
                _watchPartyState.value = _watchPartyState.value.copy(controlMode = next)
                showBanner(if (next == "EVERYONE") "🤝 Đã bật: Ai cũng điều khiển được" else "👑 Chỉ Chủ phòng điều khiển")
            }
        }
    }

    fun togglePartyVisibility() {
        val st = _watchPartyState.value
        if (!st.isInRoom || !st.isHost) return
        val next = if (st.visibility == "PUBLIC") "PRIVATE" else "PUBLIC"
        viewModelScope.launch {
            CloudSyncClient.partyUpdateSettings(st.roomCode, st.hostToken, visibility = next).onSuccess {
                _watchPartyState.value = _watchPartyState.value.copy(visibility = next)
                showBanner(if (next == "PUBLIC") "🌐 Phòng đã mở trên Sảnh Công Khai" else "🔒 Phòng chuyển sang Riêng tư")
            }
        }
    }

    fun claimPartyHost() {
        val st = _watchPartyState.value
        if (!st.isInRoom || st.isHost) return
        viewModelScope.launch {
            CloudSyncClient.partyClaimHost(st.roomCode, st.memberId, st.nickname).onSuccess { newToken ->
                _watchPartyState.value = _watchPartyState.value.copy(
                    isHost = true,
                    hostToken = newToken,
                    hostInactive = false,
                    hostName = st.nickname
                )
                showBanner("👑 Bạn đã tiếp quản quyền Chủ phòng!")
            }.onFailure { err ->
                showBanner("⚠️ ${err.message.orEmpty()}")
            }
        }
    }

    fun partyAddMovieToQueue(movie: MovieItem) {
        val st = _watchPartyState.value
        if (!st.isInRoom) return
        viewModelScope.launch {
            _watchPartyState.value = _watchPartyState.value.copy(quickSearchResults = emptyList())
            val resolved = if (movie.firstPlayableEpisode == null) {
                repository.resolveMovieDetail(movie, prefs.customProviders.value)
            } else movie
            val ep = resolved.firstPlayableEpisode ?: return@launch
            CloudSyncClient.partyQueueAdd(
                roomCode = st.roomCode,
                memberId = st.memberId,
                nickname = st.nickname,
                movie = movieToPartyMeta(resolved),
                episode = episodeToPartyMeta(ep)
            ).onSuccess { q ->
                _watchPartyState.value = _watchPartyState.value.copy(queue = q)
                showBanner("➕ Đã thêm \"${resolved.title}\" vào Hàng chờ!")
            }
        }
    }

    fun partyVoteQueue(itemId: String) {
        val st = _watchPartyState.value
        if (!st.isInRoom) return
        viewModelScope.launch {
            CloudSyncClient.partyQueueVote(st.roomCode, st.memberId, itemId).onSuccess { q ->
                _watchPartyState.value = _watchPartyState.value.copy(queue = q)
            }
        }
    }

    fun partyPlayQueueItem(itemId: String) {
        val st = _watchPartyState.value
        if (!st.isInRoom || !st.canControl) return
        viewModelScope.launch {
            CloudSyncClient.partyQueuePlay(st.roomCode, st.hostToken, st.memberId, st.nickname, itemId).onSuccess { (m, e) ->
                transitionPlaybackForParty(m, e, 0L)
            }
        }
    }

    fun partyRemoveQueueItem(itemId: String) {
        val st = _watchPartyState.value
        if (!st.isInRoom) return
        viewModelScope.launch {
            CloudSyncClient.partyQueueRemove(st.roomCode, st.hostToken, st.memberId, itemId).onSuccess { q ->
                _watchPartyState.value = _watchPartyState.value.copy(queue = q)
            }
        }
    }

    fun leaveWatchParty(silent: Boolean = false) {
        val st = _watchPartyState.value
        partyPollJob?.cancel()
        partyPollJob = null
        if (st.isInRoom) {
            viewModelScope.launch(Dispatchers.IO) {
                CloudSyncClient.partyLeave(st.roomCode, st.memberId)
            }
        }
        _watchPartyState.value = WatchPartyUiState(nickname = st.nickname)
        _watchPartySyncCommand.value = null
        if (!silent) showBanner("🚪 Đã rời phòng Xem chung")
    }

    fun startTargetAwarePlayback(movie: MovieItem, episode: Episode, startPositionMs: Long = 0L) {
        val effectiveStartMs = if (startPositionMs > 0L) {
            startPositionMs
        } else {
            prefs.getEpisodeResumePositionMs(movie.id, episode.id, episode.streamUrl, episode.name)
        }
        val selectedIp = prefs.selectedTvIp.value
        if (!isTvHardware &&
            prefs.controlTargetMode.value == ControlTargetMode.REMOTE_TV &&
            selectedIp != null
        ) {
            playMovieOnSelectedTv(movie, episode, effectiveStartMs)
        } else {
            startPlayback(movie, episode, effectiveStartMs)
        }
    }

    fun openTvManager() {
        navigateTo(AppScreen.TvManager)
        if (prefs.managedTvDevices.value.isEmpty() && !_isScanningTvs.value) {
            scanLanForTvs(silent = false)
        }
    }

    fun setControlTargetMode(mode: ControlTargetMode) {
        prefs.setControlTargetMode(mode)
        if (mode == ControlTargetMode.REMOTE_TV && prefs.selectedTvIp.value == null) {
            showBanner("📡 Hãy chọn hoặc quét tìm TV trong Wi-Fi để điều khiển!")
            openTvManager()
        } else {
            showBanner("Đã chuyển chế độ: ${mode.displayName}")
            if (mode == ControlTargetMode.REMOTE_TV) {
                syncCredentialsToSelectedTvIfOnline(silent = true)
            }
        }
    }

    fun toggleControlTargetMode() {
        val next = if (prefs.controlTargetMode.value == ControlTargetMode.LOCAL_DEVICE) {
            ControlTargetMode.REMOTE_TV
        } else {
            ControlTargetMode.LOCAL_DEVICE
        }
        setControlTargetMode(next)
    }

    fun playDirectStreamUrl(title: String, streamUrl: String, externalSubUrl: String = "") {
        val trimmedUrl = streamUrl.trim()
        if (trimmedUrl.isEmpty()) return
        viewModelScope.launch {
            if (trimmedUrl.contains("youtube.com", ignoreCase = true) ||
                trimmedUrl.contains("youtu.be", ignoreCase = true) ||
                trimmedUrl.startsWith("yt:", ignoreCase = true)
            ) {
                showBanner("⏳ Đang trích xuất luồng YouTube 1080p & Phụ đề...")
            }
            val baseMovie = repository.resolveDirectOrYouTubeStream(title, trimmedUrl)
            val firstEp = baseMovie.firstPlayableEpisode ?: return@launch
            val epWithSub = if (externalSubUrl.isNotBlank()) {
                val customRef = ExternalSubtitleRef(
                    id = "direct_sub_${System.currentTimeMillis()}",
                    label = "Sub ngoài (URL)",
                    languageCode = if (externalSubUrl.contains("th", ignoreCase = true)) "th" else "vi",
                    urlOrContent = externalSubUrl.trim(),
                    isRawContent = false,
                    sourcePlatform = "Direct URL"
                )
                firstEp.copy(subtitles = listOf(customRef) + firstEp.subtitles)
            } else {
                firstEp
            }
            startTargetAwarePlayback(baseMovie, epWithSub, 0L)
        }
    }

    fun openSettingsSources() {
        navigateTo(AppScreen.SettingsSources)
    }

    fun navigateHome() {
        screenBackStack.clear()
        _currentScreen.value = AppScreen.Home
    }

    fun toggleDeviceUiMode() {
        val next = if (prefs.deviceUiMode.value == DeviceUiMode.MOBILE) {
            DeviceUiMode.TV_BOX
        } else {
            DeviceUiMode.MOBILE
        }
        prefs.setDeviceUiMode(next)
        val label = if (next == DeviceUiMode.MOBILE) "📱 Giao diện Điện Thoại" else "📺 Giao diện TV"
        showBanner("Đã chuyển sang: $label")
    }

    fun setQrDialogVisible(visible: Boolean) {
        // QR pairing code is ONLY for TV boxes so phones can scan it; on phones, open Settings instead!
        if (visible && !isTvHardware) {
            _showQrCompanionDialog.value = false
            openSettingsSources()
            return
        }
        if (visible && _companionLanUrl.value == null) {
            _companionLanUrl.value = companionServer.start()
        } else if (visible) {
            _companionLanUrl.value = companionServer.getLanUrl()
        }
        _showQrCompanionDialog.value = visible
    }

    fun navigateBack(): Boolean {
        if (_showQrCompanionDialog.value) {
            _showQrCompanionDialog.value = false
            return true
        }
        if (_activePlaybackSession.value != null && !_isPlayerMinimized.value) {
            _isPlayerMinimized.value = true
            return true
        }
        if (screenBackStack.isEmpty()) return false
        _currentScreen.value = screenBackStack.removeLast()
        return true
    }

    private fun navigateTo(next: AppScreen) {
        val current = _currentScreen.value
        if (current != next) {
            // Avoid stacking duplicate Player -> Player when switching episodes
            if (!(current is AppScreen.Player && next is AppScreen.Player)) {
                screenBackStack.addLast(current)
            }
            _currentScreen.value = next
        }
    }

    fun updateSubtitleStyle(transform: (SubtitleStyleConfig) -> SubtitleStyleConfig) {
        prefs.updateSubtitleConfig(transform)
        if (!isTvHardware) {
            val ip = prefs.selectedTvIp.value
            if (ip != null && prefs.controlTargetMode.value == ControlTargetMode.REMOTE_TV) {
                lastRemoteSubStyleEditMs = System.currentTimeMillis()
                viewModelScope.launch {
                    remoteTvClient.syncCredentialsAndConfigToTv(
                        tvIp = ip,
                        apiKey = prefs.geminiApiKey.value,
                        styleConfig = prefs.subtitleConfig.value,
                        silent = true
                    )
                }
            }
        }
    }

    fun setDeviceUiMode(mode: DeviceUiMode) {
        prefs.setDeviceUiMode(mode)
    }

    fun saveWatchProgress(
        movie: MovieItem,
        episode: Episode,
        positionMs: Long,
        durationMs: Long,
        immediateCommit: Boolean = false
    ) {
        if (positionMs < 2_000L) return
        val progress = WatchProgress(
            movieId = movie.id,
            title = movie.title,
            posterUrl = movie.posterUrl,
            countryCode = movie.country.code,
            streamUrl = episode.streamUrl,
            episodeId = episode.id,
            episodeName = episode.name,
            positionMs = positionMs,
            durationMs = durationMs
        )
        prefs.saveWatchProgress(progress, immediateCommit = immediateCommit)
        if (CloudSyncClient.isSignedIn()) {
            val slug = CloudSyncClient.normalizeMovieSlug(movie.detailLookupId ?: movie.id)
            if (slug != "unknown" && slug != "direct" && slug != "local") {
                val epKey = CloudSyncClient.normalizeEpisodeKey(episode.name.ifBlank { episode.id })
                synchronized(pendingCloudProgress) {
                    pendingCloudProgress["$slug::$epKey"] = progress.copy(movieId = "modern_$slug")
                }
                val now = System.currentTimeMillis()
                if (immediateCommit || now - lastCloudPushMs >= 15_000L) {
                    flushPendingCloudProgress()
                }
            }
        }
    }

    fun addCustomVodProvider(
        name: String,
        url: String,
        type: VodProviderType,
        country: CountryCategory = CountryCategory.GLOBAL
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val provider = VodProviderConfig(
            id = "prov_${UUID.randomUUID().toString().take(8)}",
            name = name.trim().ifBlank { "Nguồn VOD Mới" },
            baseUrl = cleanUrl,
            type = type,
            defaultCountry = country,
            enabled = true
        )
        prefs.addCustomProvider(provider)
        showBanner("Đã thêm nguồn phim: ${provider.name}")
        refreshCatalog()
    }

    fun consumeIncomingCompanionSubtitle() {
        _incomingCompanionSubtitle.value = null
    }

    fun clearCacheForTvBox() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = getApplication<Application>().cacheDir
            val freedBytes = calculateDirSize(cacheDir)
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            val freedMb = "%.1f".format(freedBytes / (1024f * 1024f))
            withContext(Dispatchers.Main) {
                showBanner("Đã dọn sạch $freedMb MB bộ nhớ đệm trên thiết bị!")
            }
        }
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun showBanner(message: String) {
        _bannerMessage.value = message
    }

    private fun buildCompanionStateJson(): JSONObject {
        val isPlayerExpanded = _activePlaybackSession.value != null && !_isPlayerMinimized.value
        val screenName = if (isPlayerExpanded) {
            "PLAYER"
        } else {
            when (_currentScreen.value) {
                is AppScreen.Home -> "HOME"
                is AppScreen.Detail -> "DETAIL"
                is AppScreen.Player -> "PLAYER"
                is AppScreen.Downloads -> "DOWNLOADS"
                is AppScreen.SettingsSources -> "SETTINGS"
                is AppScreen.TvManager -> "TV_MANAGER"
            }
        }
        val subCfg = prefs.subtitleConfig.value
        val subStyleJson = JSONObject()
            .put("fontSizeSp", subCfg.fontSizeSp.toDouble())
            .put("secondaryFontSizeSp", subCfg.secondaryFontSizeSp.toDouble())
            .put("primaryColor", subCfg.primaryColor.name)
            .put("primaryColorHex", subCfg.primaryColor.hexCss)
            .put("secondaryColor", subCfg.secondaryColor.name)
            .put("secondaryColorHex", subCfg.secondaryColor.hexCss)
            .put("linkPrimarySecondaryStyle", subCfg.linkPrimarySecondaryStyle)
            .put("edgeStyle", subCfg.edgeStyle.name)
            .put("boxOpacity", subCfg.boxOpacity.toDouble())
            .put("bottomOffsetDp", subCfg.bottomOffsetDp.toDouble())
            .put("hardsubMaskEnabled", subCfg.hardsubMaskEnabled)
            .put("dualSubEnabled", subCfg.dualSubEnabled)

        return JSONObject()
            .put("ok", true)
            .put("screen", screenName)
            .put("selectedCountry", _selectedCountry.value.code)
            .put("deviceUiMode", prefs.deviceUiMode.value.name)
            .put("hasGeminiKey", prefs.geminiApiKey.value.isNotBlank())
            .put("geminiModel", subCfg.geminiModelId)
            .put("showQrDialog", _showQrCompanionDialog.value)
            .put("banner", _bannerMessage.value.orEmpty())
            .put("subtitleStyle", subStyleJson)
            .put("player", _playerTelemetry.value.toJson())
    }

    private fun buildDeviceInfoJson(): JSONObject {
        val pkgInfo = runCatching {
            val ctx = getApplication<Application>()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()
        return JSONObject()
            .put("ok", true)
            .put("app", "PhyllFilmHub")
            .put("version", pkgInfo?.versionName ?: "2.8.0")
            .put("hardwareModel", Build.MODEL.orEmpty())
            .put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("isTvHardware", isTvHardware)
    }

    private fun buildCompanionCatalogJson(countryCode: String?, searchQuery: String?): JSONObject {
        val secArray = JSONArray()
        for (sec in _sections.value) {
            val moviesArr = JSONArray()
            for (m in sec.movies) {
                moviesArr.put(movieItemToJson(m, includeEpisodes = false))
            }
            secArray.put(
                JSONObject()
                    .put("id", sec.id)
                    .put("title", sec.title)
                    .put("subtitle", sec.subtitle)
                    .put("movies", moviesArr)
            )
        }
        val searchArr = JSONArray()
        for (m in _searchResults.value) {
            searchArr.put(movieItemToJson(m, includeEpisodes = false))
        }
        return JSONObject()
            .put("ok", true)
            .put("selectedCountry", _selectedCountry.value.code)
            .put("requestedCountry", countryCode.orEmpty())
            .put("searchQuery", searchQuery ?: _searchQuery.value)
            .put("isLoading", _isLoadingCatalog.value || _isSearching.value)
            .put("sections", secArray)
            .put("searchResults", searchArr)
    }

    private suspend fun resolveMovieJsonForCompanion(movieId: String): JSONObject? {
        val existing = findMovieInMemory(movieId) ?: repository.getCachedMovie(movieId) ?: return null
        val resolved = if (existing.servers.isEmpty()) {
            repository.resolveMovieDetail(existing, prefs.customProviders.value)
        } else {
            existing
        }
        return movieItemToJson(resolved, includeEpisodes = true)
    }

    private fun findMovieInMemory(movieId: String): MovieItem? {
        _activePlaybackSession.value?.movie?.takeIf { it.id == movieId }?.let { return it }
        val cur = _currentScreen.value
        if (cur is AppScreen.Detail && cur.movie.id == movieId) return cur.movie
        if (cur is AppScreen.Player && cur.movie.id == movieId) return cur.movie
        _searchResults.value.firstOrNull { it.id == movieId }?.let { return it }
        for (sec in _sections.value) {
            sec.movies.firstOrNull { it.id == movieId }?.let { return it }
        }
        return repository.getCachedMovie(movieId)
    }

    private fun movieItemToJson(m: MovieItem, includeEpisodes: Boolean): JSONObject {
        val obj = JSONObject()
            .put("id", m.id)
            .put("title", m.title)
            .put("originalTitle", m.originalTitle)
            .put("year", m.year)
            .put("countryCode", m.country.code)
            .put("countryName", m.country.displayName)
            .put("countryFlag", m.country.flagEmoji)
            .put("genre", m.genre)
            .put("quality", m.quality)
            .put("posterUrl", m.posterUrl)
            .put("backdropUrl", m.backdropUrl)
            .put("description", m.description)
            .put("providerName", m.providerName)
            .put("hasNativeThaiSub", m.hasNativeThaiSub)

        if (includeEpisodes) {
            val epsArr = JSONArray()
            m.servers.flatMap { it.episodes }.forEach { ep ->
                epsArr.put(
                    JSONObject()
                        .put("id", ep.id)
                        .put("name", ep.name)
                        .put("streamUrl", ep.streamUrl)
                )
            }
            obj.put("episodes", epsArr)
        }
        return obj
    }

    private fun handleCompanionEvent(event: CompanionEvent) {
        when (event) {
            is CompanionEvent.RemoteSearch -> {
                _showQrCompanionDialog.value = false
                screenBackStack.clear()
                _currentScreen.value = AppScreen.Home
                executeSearch(event.query)
                showBanner("📱 Đã nhận tìm kiếm từ điện thoại: \"${event.query}\"")
            }
            is CompanionEvent.SelectCountry -> {
                _showQrCompanionDialog.value = false
                val target = CountryCategory.entries.firstOrNull {
                    it.code.equals(event.countryCode, ignoreCase = true)
                } ?: CountryCategory.ALL
                val isPlayerActive = _activePlaybackSession.value != null && !_isPlayerMinimized.value
                if (_currentScreen.value !is AppScreen.Home && !isPlayerActive) {
                    navigateHome()
                }
                selectCountry(target)
                if (!isPlayerActive) {
                    showBanner("📱 ${target.flagEmoji} ${target.displayName}")
                }
            }
            is CompanionEvent.OpenMovieOnTv -> {
                _showQrCompanionDialog.value = false
                val movie = findMovieInMemory(event.movieId) ?: return
                if (event.quickPlay) {
                    quickPlayMovie(movie)
                    showBanner("📱 Đang mở phát phim từ ĐT: ${movie.title}")
                } else {
                    openMovieDetail(movie)
                    showBanner("📱 Đã mở chi tiết phim từ ĐT: ${movie.title}")
                }
            }
            is CompanionEvent.PlayEpisodeOnTv -> {
                _showQrCompanionDialog.value = false
                val baseMovie = findMovieInMemory(event.movieId) ?: return
                viewModelScope.launch {
                    val resolved = if (baseMovie.servers.isEmpty()) {
                        repository.resolveMovieDetail(baseMovie, prefs.customProviders.value)
                    } else {
                        baseMovie
                    }
                    val allEps = resolved.servers.flatMap { it.episodes }
                    val chosenEp = allEps.firstOrNull { it.id == event.episodeId } ?: resolved.firstPlayableEpisode
                    if (chosenEp != null) {
                        startPlayback(resolved, chosenEp, 0L)
                        showBanner("📱 Đang phát ${resolved.title} (${chosenEp.name}) từ ĐT")
                    }
                }
            }
            is CompanionEvent.PlayerCommand -> {
                _showQrCompanionDialog.value = false
                _remotePlayerCommand.value = event.command
            }
            is CompanionEvent.NavigateAction -> {
                when (event.target.lowercase()) {
                    "back" -> navigateBack()
                    "home" -> {
                        _showQrCompanionDialog.value = false
                        navigateHome()
                    }
                    "settings" -> {
                        _showQrCompanionDialog.value = false
                        openSettingsSources()
                    }
                    "toggle_ui_mode" -> toggleDeviceUiMode()
                    "close_qr" -> _showQrCompanionDialog.value = false
                }
            }
            is CompanionEvent.PushDirectStream -> {
                _showQrCompanionDialog.value = false
                playDirectStreamUrl(event.title, event.streamUrl)
                showBanner("📱 Đang phát luồng gửi từ điện thoại: ${event.title}")
            }
            is CompanionEvent.PushSubtitleContent -> {
                _showQrCompanionDialog.value = false
                val ref = ExternalSubtitleRef(
                    id = "phone_${System.currentTimeMillis()}",
                    label = "📱 ${event.fileName}",
                    languageCode = event.languageCode,
                    urlOrContent = event.decodedContent,
                    isRawContent = true
                )
                _incomingCompanionSubtitle.value = ref
                showBanner("📱 Đã nhận file phụ đề từ điện thoại: ${event.fileName}")
            }
            is CompanionEvent.AddVodProvider -> {
                val pType = runCatching { VodProviderType.valueOf(event.providerType) }
                    .getOrDefault(VodProviderType.STANDARD_CMS_JSON)
                addCustomVodProvider(event.name, event.url, pType)
            }
            is CompanionEvent.SetGeminiKey -> {
                _showQrCompanionDialog.value = false
                val prevKey = prefs.geminiApiKey.value
                setSessionGeminiCredentials(
                    apiKey = event.apiKey,
                    modelId = event.modelId,
                    silent = event.silent && prevKey == event.apiKey
                )
            }
            is CompanionEvent.PlayMoviePayload -> {
                _showQrCompanionDialog.value = false
                val country = CountryCategory.fromSlugOrName(event.countryCode)
                val episode = Episode(
                    id = event.episodeId,
                    name = event.episodeName,
                    streamUrl = event.streamUrl,
                    audioStreamUrl = event.audioStreamUrl,
                    fallbackStreamUrl = event.fallbackStreamUrl,
                    subtitles = event.subtitles
                )
                val cachedMovie = findMovieInMemory(event.movieId)
                val resolvedMovie = if (cachedMovie != null && cachedMovie.servers.isNotEmpty()) {
                    cachedMovie
                } else {
                    MovieItem(
                        id = event.movieId,
                        title = event.movieTitle,
                        originalTitle = event.originalTitle,
                        year = event.year,
                        country = country,
                        genre = event.genre,
                        posterUrl = event.posterUrl,
                        backdropUrl = event.backdropUrl,
                        description = event.description,
                        providerName = event.providerName,
                        servers = listOf(ServerGroup("Phát từ ĐT", listOf(episode)))
                    )
                }
                startPlayback(resolvedMovie, episode, event.startPositionMs)
                showBanner("📱 Đang phát trên TV: ${resolvedMovie.title} (${episode.name})")
            }
            is CompanionEvent.WipeGeminiKey -> {
                wipeGeminiKeyFromRam(silent = false)
            }
            is CompanionEvent.UpdateSubtitleStyle -> {
                if (event.resetDefault) {
                    prefs.resetSubtitleConfigToThaiDefault()
                    showBanner("📱 Đã khôi phục cỡ chữ, màu chữ & khung Sub gọn chuẩn!")
                    return
                }
                prefs.updateSubtitleConfig { cfg ->
                    val targetMode = event.target.uppercase()
                    var next = cfg
                    event.linkPrimarySecondary?.let { next = next.copy(linkPrimarySecondaryStyle = it) }
                    if (event.linkPrimarySecondary == null) {
                        when (targetMode) {
                            "ALL" -> next = next.copy(linkPrimarySecondaryStyle = true)
                            "PRIMARY", "SECONDARY" -> next = next.copy(linkPrimarySecondaryStyle = false)
                        }
                    }

                    event.fontSizeSp?.let { sz ->
                        next = when (targetMode) {
                            "SECONDARY" -> next.copy(secondaryFontSizeSp = sz.coerceIn(10f, 48f))
                            "ALL" -> next.copy(
                                fontSizeSp = sz.coerceIn(12f, 52f),
                                secondaryFontSizeSp = (event.secondaryFontSizeSp ?: (sz * 0.80f)).coerceIn(10f, 48f)
                            )
                            else -> next.copy(fontSizeSp = sz.coerceIn(12f, 52f))
                        }
                    }
                    if (event.secondaryFontSizeSp != null && event.fontSizeSp == null) {
                        next = next.copy(secondaryFontSizeSp = event.secondaryFontSizeSp.coerceIn(10f, 48f))
                    }
                    event.sizeDeltaSp?.let { delta ->
                        next = when (targetMode) {
                            "SECONDARY" -> next.copy(
                                secondaryFontSizeSp = (next.secondaryFontSizeSp + delta).coerceIn(10f, 48f)
                            )
                            "ALL" -> next.copy(
                                fontSizeSp = (next.fontSizeSp + delta).coerceIn(12f, 52f),
                                secondaryFontSizeSp = (next.secondaryFontSizeSp + delta).coerceIn(10f, 48f)
                            )
                            else -> next.copy(
                                fontSizeSp = (next.fontSizeSp + delta).coerceIn(12f, 52f)
                            )
                        }
                    }
                    event.colorName?.let { cName ->
                        val chosen = SubtitleColorChoice.entries.firstOrNull {
                            it.name.equals(cName, ignoreCase = true)
                        }
                        if (chosen != null) {
                            next = when (targetMode) {
                                "SECONDARY" -> next.copy(secondaryColor = chosen)
                                "ALL" -> next.copy(primaryColor = chosen, secondaryColor = chosen)
                                else -> next.copy(primaryColor = chosen)
                            }
                        }
                    }
                    event.primaryColorName?.let { cName ->
                        SubtitleColorChoice.entries.firstOrNull { it.name.equals(cName, ignoreCase = true) }
                            ?.let { next = next.copy(primaryColor = it) }
                    }
                    event.secondaryColorName?.let { cName ->
                        SubtitleColorChoice.entries.firstOrNull { it.name.equals(cName, ignoreCase = true) }
                            ?.let { next = next.copy(secondaryColor = it) }
                    }
                    event.edgeStyleName?.let { eName ->
                        SubtitleEdgeStyle.entries.firstOrNull { it.name.equals(eName, ignoreCase = true) }
                            ?.let { next = next.copy(edgeStyle = it) }
                    }
                    event.boxOpacity?.let { op ->
                        next = next.copy(boxOpacity = op.coerceIn(0.25f, 0.96f))
                    }
                    event.bottomOffsetDp?.let { off ->
                        next = next.copy(bottomOffsetDp = off.coerceIn(4f, 180f))
                    }
                    event.hardsubMaskEnabled?.let { m ->
                        next = next.copy(hardsubMaskEnabled = m)
                    }
                    event.dualSubEnabled?.let { d ->
                        next = next.copy(dualSubEnabled = d)
                    }
                    next
                }
            }
        }
    }

    // ============================================================================================
    // STANDALONE ANDROID PHONE CONTROLLER: Multi-TV Manager, ADB Install/Uninstall & Remote Sync
    // ============================================================================================

    fun scanLanForTvs(silent: Boolean = false) {
        if (_isScanningTvs.value) return
        viewModelScope.launch {
            _isScanningTvs.value = true
            val found = remoteTvClient.discoverLanTvs(prefs.managedTvDevices.value)
            prefs.mergeDiscoveredTvDevices(found)
            if (prefs.selectedTvIp.value == null) {
                val firstOnline = found.firstOrNull { it.companionOnline } ?: found.firstOrNull()
                if (firstOnline != null) {
                    prefs.selectManagedTvIp(firstOnline.ip)
                }
            }
            _isScanningTvs.value = false
            startPollingSelectedTv()
            if (!silent) {
                showBanner(
                    if (found.isNotEmpty()) "✅ Đã tìm thấy ${found.size} TV trên mạng Wi-Fi!"
                    else "⚠️ Chưa thấy TV nào mở cổng 5555 (ADB) hoặc 8899. Hãy thêm IP thủ công hoặc bật Gỡ lỗi ADB trên TV."
                )
            }
        }
    }

    fun addOrProbeTvByIp(rawIp: String, nickname: String = "") {
        val ip = rawIp.trim()
        if (ip.isEmpty()) return
        viewModelScope.launch {
            showBanner("🔍 Đang kết nối thử tới TV $ip...")
            val probed = remoteTvClient.probeSingleTv(ip)
            val withName = if (nickname.isNotBlank()) probed.copy(name = nickname.trim()) else probed
            prefs.upsertManagedTvDevice(withName)
            selectManagedTv(withName.ip)
            val status = when {
                withName.companionOnline -> "Đang mở Phyll Cinema (${withName.appVersion})"
                withName.adbOnline -> "Đã kết nối ADB (:5555) — sẵn sàng Cài/Mở App!"
                else -> "Đã lưu IP (TV đang tắt hoặc chưa mở cổng 5555/8899)"
            }
            showBanner("📺 ${withName.displayTitle}: $status")
        }
    }

    fun selectManagedTv(ip: String) {
        prefs.selectManagedTvIp(ip)
        startPollingSelectedTv()
        syncCredentialsToSelectedTvIfOnline(silent = false)
    }

    fun renameManagedTv(ip: String, newName: String) {
        prefs.renameManagedTvDevice(ip, newName)
    }

    fun removeManagedTv(ip: String) {
        prefs.removeManagedTvDevice(ip)
        if (prefs.selectedTvIp.value == null) {
            _remoteTvState.value = RemoteTvLiveState()
        } else {
            startPollingSelectedTv()
        }
    }

    fun syncCredentialsToSelectedTvIfOnline(silent: Boolean = false) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch {
            val ok = remoteTvClient.syncCredentialsAndConfigToTv(
                tvIp = ip,
                apiKey = prefs.geminiApiKey.value,
                styleConfig = prefs.subtitleConfig.value,
                silent = true
            )
            if (ok) {
                if (!silent) showBanner("🔐 Đã đồng bộ API Key & Cấu hình sang TV $ip!")
                val refreshed = remoteTvClient.probeSingleTv(ip)
                prefs.upsertManagedTvDevice(refreshed)
            } else if (!silent) {
                showBanner("⚠️ Chưa kết nối được cổng 8899 trên TV $ip (hãy bấm Mở App TV trước).")
            }
        }
    }

    fun installAppOnTv(ip: String) {
        if (_adbTaskProgress.value.isRunning) {
            showBanner("⏳ Đang có tác vụ cài/gỡ khác đang chạy...")
            return
        }
        val apkPath = getApplication<Application>().applicationInfo.sourceDir
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adbClient.pushAndInstallApk(
                    host = ip,
                    apkFile = File(apkPath),
                    onProgress = { pct, msg ->
                        _adbTaskProgress.value = AdbTaskProgress(
                            isRunning = true,
                            targetIp = ip,
                            operationName = "Cài đặt APK lên TV",
                            progressPercent = pct,
                            statusMessage = msg
                        )
                    }
                )
                delay(2_400L)
                val refreshed = remoteTvClient.probeSingleTv(ip)
                prefs.upsertManagedTvDevice(refreshed)
                prefs.selectManagedTvIp(ip)
                startPollingSelectedTv()
                remoteTvClient.syncCredentialsAndConfigToTv(
                    tvIp = ip,
                    apiKey = prefs.geminiApiKey.value,
                    styleConfig = prefs.subtitleConfig.value,
                    silent = true
                )
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = ip,
                    operationName = "Hoàn tất",
                    progressPercent = 100,
                    statusMessage = "✅ Đã cài đặt xong Phyll Cinema lên TV & tự nạp API Key!"
                )
                withContext(Dispatchers.Main) {
                    showBanner("✅ Đã cài & kết nối xong TV $ip!")
                }
            } catch (e: Exception) {
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = ip,
                    operationName = "Lỗi cài đặt",
                    progressPercent = 0,
                    statusMessage = e.message ?: "Lỗi kết nối ADB",
                    isError = true
                )
                withContext(Dispatchers.Main) {
                    showBanner("❌ Cài App TV thất bại: ${e.message}")
                }
            }
        }
    }

    fun uninstallAppFromTv(ip: String) {
        if (_adbTaskProgress.value.isRunning) {
            showBanner("⏳ Đang có tác vụ cài/gỡ khác đang chạy...")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adbClient.uninstallPackage(
                    host = ip,
                    onProgress = { pct, msg ->
                        _adbTaskProgress.value = AdbTaskProgress(
                            isRunning = true,
                            targetIp = ip,
                            operationName = "Gỡ App khỏi TV",
                            progressPercent = pct,
                            statusMessage = msg
                        )
                    }
                )
                val prev = prefs.managedTvDevices.value.firstOrNull { it.ip == ip }
                if (prev != null) {
                    prefs.upsertManagedTvDevice(
                        prev.copy(
                            companionOnline = false,
                            appInstalled = false,
                            appVersion = "",
                            nowPlayingTitle = "",
                            hasGeminiKeyOnTv = false
                        )
                    )
                }
                if (prefs.selectedTvIp.value == ip) {
                    _remoteTvState.value = RemoteTvLiveState(tvIp = ip, online = false)
                }
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = ip,
                    operationName = "Đã gỡ cài đặt",
                    progressPercent = 100,
                    statusMessage = "🗑 Đã gỡ ứng dụng khỏi TV $ip!"
                )
                withContext(Dispatchers.Main) {
                    showBanner("🗑 Đã gỡ Phyll Cinema khỏi TV $ip!")
                }
            } catch (e: Exception) {
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = ip,
                    operationName = "Lỗi gỡ App",
                    progressPercent = 0,
                    statusMessage = e.message ?: "Không gỡ được App",
                    isError = true
                )
                withContext(Dispatchers.Main) {
                    showBanner("❌ Gỡ App thất bại: ${e.message}")
                }
            }
        }
    }

    fun launchAppOnTv(ip: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    showBanner("🚀 Đang đánh thức & mở Phyll Cinema trên TV $ip...")
                }
                adbClient.launchAppOnTv(ip)
                delay(2_200L)
                val refreshed = remoteTvClient.probeSingleTv(ip)
                prefs.upsertManagedTvDevice(refreshed)
                selectManagedTv(ip)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("⚠️ Không mở được App qua ADB: ${e.message}")
                }
            }
        }
    }

    fun dismissAdbProgress() {
        if (!_adbTaskProgress.value.isRunning) {
            _adbTaskProgress.value = AdbTaskProgress()
        }
    }

    fun playMovieOnSelectedTv(movie: MovieItem, episode: Episode, startPositionMs: Long = 0L) {
        val ip = prefs.selectedTvIp.value
        if (ip == null) {
            showBanner("📡 Chưa chọn TV. Hãy chọn hoặc quét TV trước!")
            openTvManager()
            return
        }
        viewModelScope.launch {
            showBanner("📺 Đang gửi \"${movie.title}\" (${episode.name}) sang TV...")
            // Always push saved API key & subtitle language to TV BEFORE opening movie so TV never asks for key!
            if (prefs.geminiApiKey.value.isNotBlank()) {
                runCatching {
                    remoteTvClient.syncCredentialsAndConfigToTv(
                        ip, prefs.geminiApiKey.value, prefs.subtitleConfig.value, silent = true
                    )
                }
            }
            var ok = remoteTvClient.pushMovieAndPlayOnTv(ip, movie, episode, startPositionMs)
            if (!ok) {
                // Auto-wake TV app via ADB if it was closed on the TV box!
                val woke = runCatching {
                    withContext(Dispatchers.IO) { adbClient.launchAppOnTv(ip) }
                    delay(2_300L)
                    true
                }.getOrDefault(false)
                if (woke) {
                    remoteTvClient.syncCredentialsAndConfigToTv(
                        ip, prefs.geminiApiKey.value, prefs.subtitleConfig.value, silent = true
                    )
                    ok = remoteTvClient.pushMovieAndPlayOnTv(ip, movie, episode, startPositionMs)
                }
            }
            if (ok) {
                showBanner("▶ Đã phát trên TV: ${movie.title} (${episode.name})")
            } else {
                showBanner("⚠️ Không gửi được lệnh sang TV $ip. Hãy kiểm tra kết nối Wi-Fi TV!")
            }
        }
    }

    fun sendRemoteTouchKey(key: String) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch { remoteTvClient.sendTouchKey(ip, key) }
    }

    fun sendRemoteTap(nx: Float, ny: Float) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch { remoteTvClient.sendTap(ip, nx, ny) }
    }

    fun sendRemoteSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch { remoteTvClient.sendSwipe(ip, x1, y1, x2, y2) }
    }

    fun sendRemotePlayerCommand(cmd: String, value: Long = 0L, second: Long = 0L) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch { remoteTvClient.sendPlayerCommand(ip, cmd, value, second) }
    }

    fun sendRemoteAction(payload: JSONObject) {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch { remoteTvClient.sendAction(ip, payload) }
    }

    fun pushDirectStreamToTv(title: String, url: String) {
        val ip = prefs.selectedTvIp.value ?: return
        if (url.isBlank()) return
        viewModelScope.launch {
            val ok = remoteTvClient.pushDirectStream(ip, title.ifBlank { "Phim từ ĐT" }, url.trim())
            if (ok) showBanner("📺 Đã gửi luồng phát sang TV!")
            else showBanner("⚠️ Không kết nối được với TV $ip")
        }
    }

    fun pushSubtitleBytesToTv(fileName: String, rawBytes: ByteArray, lang: String = "th") {
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch {
            val ok = remoteTvClient.pushSubtitleFileBytes(ip, fileName, rawBytes, lang)
            if (ok) showBanner("💬 Đã nạp file phụ đề \"$fileName\" lên TV!")
            else showBanner("⚠️ Không gửi được file sub lên TV $ip")
        }
    }

    fun updateRemoteTvSubtitleStyle(
        stylePayload: JSONObject,
        localTransform: ((SubtitleStyleConfig) -> SubtitleStyleConfig)? = null
    ) {
        lastRemoteSubStyleEditMs = System.currentTimeMillis()
        if (localTransform != null) {
            prefs.updateSubtitleConfig(localTransform)
        }
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch {
            remoteTvClient.sendAction(ip, stylePayload.put("type", "sub_style"))
        }
    }

    fun resetRemoteTvSubtitleStyleDefault() {
        lastRemoteSubStyleEditMs = System.currentTimeMillis()
        prefs.resetSubtitleConfigToThaiDefault()
        val ip = prefs.selectedTvIp.value ?: return
        viewModelScope.launch {
            remoteTvClient.sendAction(ip, JSONObject().put("type", "sub_style").put("resetDefault", true))
            showBanner("↺ Đã khôi phục phụ đề chuẩn trên TV & ĐT!")
        }
    }

    fun startTvScreenStream(width: Int = 960, quality: Int = 68) {
        tvScreenStreamJob?.cancel()
        if (prefs.selectedTvIp.value.isNullOrBlank()) return
        tvScreenStreamJob = viewModelScope.launch {
            while (isActive) {
                val curIp = prefs.selectedTvIp.value ?: break
                val bytes = remoteTvClient.fetchScreenFrame(curIp, width = width, quality = quality)
                if (bytes != null && bytes.isNotEmpty()) {
                    _tvScreenFrameBytes.value = bytes
                }
                delay(110L)
            }
        }
    }

    fun stopTvScreenStream() {
        tvScreenStreamJob?.cancel()
        tvScreenStreamJob = null
    }

    private fun startPollingSelectedTv() {
        tvPollJob?.cancel()
        if (isTvHardware) return
        if (prefs.selectedTvIp.value.isNullOrBlank()) return
        tvPollJob = viewModelScope.launch {
            while (isActive) {
                val curIp = prefs.selectedTvIp.value ?: break
                val st = remoteTvClient.fetchLiveState(curIp)
                if (st != null) {
                    _remoteTvState.value = st
                    // Auto-sync saved phone Gemini API key whenever TV connects without a key!
                    val phoneKey = prefs.geminiApiKey.value
                    if (!st.hasGeminiKey && phoneKey.isNotBlank()) {
                        remoteTvClient.syncCredentialsAndConfigToTv(
                            tvIp = curIp,
                            apiKey = phoneKey,
                            styleConfig = prefs.subtitleConfig.value,
                            silent = true
                        )
                    }
                    // Mirror TV's live subtitle style when Phone user isn't actively dragging sliders
                    if (System.currentTimeMillis() - lastRemoteSubStyleEditMs > 2_500L) {
                        val existing = prefs.managedTvDevices.value.firstOrNull { it.ip == curIp }
                        if (existing != null) {
                            prefs.upsertManagedTvDevice(
                                existing.copy(
                                    companionOnline = true,
                                    appInstalled = true,
                                    activeScreen = st.screen,
                                    nowPlayingTitle = if (st.playerActive) st.movieTitle else "",
                                    hasGeminiKeyOnTv = st.hasGeminiKey,
                                    lastSeenMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } else {
                    _remoteTvState.value = _remoteTvState.value.copy(tvIp = curIp, online = false)
                }
                delay(1_200L)
            }
        }
    }

    // ============================================================================================
    // IN-APP GITHUB AUTO-UPDATER (Settings -> Kiểm tra cập nhật & Push sang TV)
    // ============================================================================================

    fun checkForAppUpdates() {
        val current = appUpdater.getInstalledVersion(getApplication())
        _appUpdateState.value = _appUpdateState.value.copy(
            currentVersion = current,
            isChecking = true,
            statusMessage = null,
            errorMessage = null
        )
        viewModelScope.launch {
            val res = appUpdater.checkForUpdates()
            res.onSuccess { remote ->
                val hasNew = AppUpdater.isUpdateAvailable(current, remote)
                _appUpdateState.value = _appUpdateState.value.copy(
                    currentVersion = current,
                    latestRelease = remote,
                    hasUpdate = hasNew,
                    isChecking = false,
                    statusMessage = if (hasNew) {
                        "🚀 Có bản mới v${remote.versionName} (Build ${remote.versionCode}) — Cài đè giữ nguyên mọi dữ liệu!"
                    } else {
                        "✅ Bạn đang dùng phiên bản mới nhất (${current.versionName} • Build ${current.versionCode})!"
                    },
                    errorMessage = null
                )
            }.onFailure { err ->
                _appUpdateState.value = _appUpdateState.value.copy(
                    isChecking = false,
                    errorMessage = err.message ?: "Không thể kết nối GitHub Releases để kiểm tra cập nhật."
                )
            }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val release = _appUpdateState.value.latestRelease
        val url = release?.downloadUrl ?: AppUpdater.FALLBACK_APK_URL
        val ctx = getApplication<Application>()

        if (_appUpdateState.value.isDownloading) return
        _appUpdateState.value = _appUpdateState.value.copy(
            isDownloading = true,
            downloadPercent = 0,
            errorMessage = null,
            needsInstallPermission = false,
            statusMessage = "Đang tải bản cập nhật ChillFilm APK..."
        )

        viewModelScope.launch {
            val result = appUpdater.downloadApkToCache(ctx, url) { received, total, pct ->
                _appUpdateState.value = _appUpdateState.value.copy(
                    isDownloading = true,
                    downloadedBytes = received,
                    totalBytes = total,
                    downloadPercent = pct,
                    statusMessage = "Đang tải APK cập nhật: $pct%"
                )
            }
            result.onSuccess { apkFile ->
                _appUpdateState.value = _appUpdateState.value.copy(
                    isDownloading = false,
                    downloadPercent = 100,
                    downloadedApkPath = apkFile.absolutePath,
                    needsInstallPermission = !appUpdater.canRequestPackageInstalls(ctx),
                    statusMessage = if (appUpdater.canRequestPackageInstalls(ctx)) {
                        "Đã tải xong! Đang mở trình cài đặt cập nhật (giữ nguyên toàn bộ dữ liệu)..."
                    } else {
                        "Đã tải xong! Vui lòng cấp quyền Cài đặt ứng dụng để cập nhật ngay."
                    }
                )
                if (appUpdater.canRequestPackageInstalls(ctx)) {
                    markExternalOverlayLaunching()
                    runCatching { appUpdater.launchApkInstaller(ctx, apkFile) }
                        .onFailure { err ->
                            clearExternalOverlayLaunching()
                            _appUpdateState.value = _appUpdateState.value.copy(
                                errorMessage = "Không mở được trình cài đặt: ${err.message}"
                            )
                        }
                }
            }.onFailure { err ->
                _appUpdateState.value = _appUpdateState.value.copy(
                    isDownloading = false,
                    errorMessage = err.message ?: "Tải APK thất bại."
                )
            }
        }
    }

    fun openInstallPermissionAndRetry() {
        val ctx = getApplication<Application>()
        val path = _appUpdateState.value.downloadedApkPath
        if (appUpdater.canRequestPackageInstalls(ctx) && path != null && File(path).exists()) {
            markExternalOverlayLaunching()
            _appUpdateState.value = _appUpdateState.value.copy(needsInstallPermission = false)
            runCatching { appUpdater.launchApkInstaller(ctx, File(path)) }
        } else {
            markExternalOverlayLaunching()
            runCatching { appUpdater.launchUnknownSourcesPermissionSettings(ctx) }
        }
    }

    fun pushDownloadedUpdateToSelectedTv() {
        val tvIp = prefs.selectedTvIp.value ?: run {
            showBanner("📡 Chưa chọn TV. Hãy chọn TV tại mục Quản Lý TV trước!")
            return
        }
        val path = _appUpdateState.value.downloadedApkPath
        val apkFile = if (path != null && File(path).exists()) File(path)
        else File(getApplication<Application>().applicationInfo.sourceDir)

        if (_adbTaskProgress.value.isRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                adbClient.pushAndInstallApk(
                    host = tvIp,
                    apkFile = apkFile,
                    onProgress = { pct, msg ->
                        _adbTaskProgress.value = AdbTaskProgress(
                            isRunning = true,
                            targetIp = tvIp,
                            operationName = "Cập nhật APK cho TV",
                            progressPercent = pct,
                            statusMessage = msg
                        )
                    }
                )
                delay(2_200L)
                val refreshed = remoteTvClient.probeSingleTv(tvIp)
                prefs.upsertManagedTvDevice(refreshed)
                syncCredentialsToSelectedTvIfOnline(silent = true)
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = tvIp,
                    operationName = "Cập nhật TV xong",
                    progressPercent = 100,
                    statusMessage = "✅ TV $tvIp đã lên phiên bản mới & giữ nguyên mọi dữ liệu!"
                )
                withContext(Dispatchers.Main) {
                    showBanner("✅ Đã cập nhật TV $tvIp lên bản mới!")
                }
            } catch (e: Exception) {
                _adbTaskProgress.value = AdbTaskProgress(
                    isRunning = false,
                    targetIp = tvIp,
                    operationName = "Lỗi cập nhật TV",
                    progressPercent = 0,
                    statusMessage = e.message ?: "Không kết nối được ADB TV",
                    isError = true
                )
            }
        }
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { calculateDirSize(it) } ?: 0L
    }

    override fun onCleared() {
        partyPollJob?.cancel()
        tvPollJob?.cancel()
        tvScreenStreamJob?.cancel()
        companionServer.stop()
        super.onCleared()
    }
}
