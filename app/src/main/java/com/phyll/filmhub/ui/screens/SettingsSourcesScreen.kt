package com.phyll.filmhub.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.R
import com.phyll.filmhub.cloud.AdminVaultSnapshot
import com.phyll.filmhub.cloud.CloudHealthInfo
import com.phyll.filmhub.cloud.CloudSession
import com.phyll.filmhub.model.DeviceUiMode
import com.phyll.filmhub.model.SubtitleLanguageCatalog
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.model.VodProviderConfig
import com.phyll.filmhub.model.VodProviderType
import com.phyll.filmhub.ui.i18n.AppLanguage
import com.phyll.filmhub.ui.i18n.LocalAppStrings
import com.phyll.filmhub.ui.theme.AmbientCinemaBackdropBrush
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
import com.phyll.filmhub.ui.theme.BrandNeonGlassBorderBrush
import com.phyll.filmhub.ui.theme.CinemaGlass
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.LiveRed
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.VioletPrimary
import com.phyll.filmhub.ui.theme.VioletSoft
import com.phyll.filmhub.ui.theme.tvFocusableClickable
import com.phyll.filmhub.updater.AppUpdateUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSourcesScreen(
    customProviders: List<VodProviderConfig>,
    subtitleConfig: SubtitleStyleConfig,
    geminiApiKey: String,
    discoveredGeminiModels: List<String>,
    promptQrOnStartup: Boolean,
    deviceUiMode: DeviceUiMode,
    currentAppLanguage: AppLanguage = AppLanguage.EN,
    onSelectAppLanguage: (AppLanguage) -> Unit = {},
    cloudSession: CloudSession? = null,
    cloudHealth: CloudHealthInfo = CloudHealthInfo(),
    cloudSyncBusy: Boolean = false,
    adminVaultSnapshot: AdminVaultSnapshot? = null,
    onCloudLogin: (String, String) -> Unit = { _, _ -> },
    onCloudRegister: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onCloudUpdateProfile: (String?, String?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onCloudLogout: () -> Unit = {},
    onCloudSyncNow: () -> Unit = {},
    onLoadAdminVault: () -> Unit = {},
    onAdminSaveServerKey: (String) -> Unit = {},
    onAdminSavePolicy: (String, String) -> Unit = { _, _ -> },
    onAdminCreateInvite: ((String) -> Unit) -> Unit = {},
    onAdminRevokeInvite: (String) -> Unit = {},
    onAdminToggleMemberStatus: (String, String) -> Unit = { _, _ -> },
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    hasSelectedTv: Boolean = false,
    isTvHardware: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {},
    onOpenInstallPermission: () -> Unit = {},
    onPushUpdateToTv: () -> Unit = {},
    onOpenTvManager: () -> Unit = {},
    onAddProvider: (String, String, VodProviderType) -> Unit,
    onToggleProvider: (String) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onPlayDirectUrl: (String, String, String) -> Unit,
    onSaveSessionGeminiKey: (String, String) -> Unit,
    onWipeSessionGeminiKey: () -> Unit,
    onSelectGeminiModel: (String) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onTogglePromptQrOnStartup: (Boolean) -> Unit,
    onUpdateSubtitleConfig: ((SubtitleStyleConfig) -> SubtitleStyleConfig) -> Unit,
    onSetDeviceUiMode: (DeviceUiMode) -> Unit,
    onClearCache: () -> Unit,
    onClearWatchHistory: () -> Unit,
    onOpenQrCompanion: () -> Unit,
    onBeforeLaunchSystemOverlay: () -> Unit = {},
    onAfterSystemOverlayResult: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isTv = LocalIsTvMode.current
    val s = LocalAppStrings.current

    var directTitle by remember { mutableStateOf("") }
    var directStreamUrl by remember { mutableStateOf("") }
    var directSubUrl by remember { mutableStateOf("") }

    var provName by remember { mutableStateOf("") }
    var provUrl by remember { mutableStateOf("") }
    var provType by remember { mutableStateOf(VodProviderType.STANDARD_CMS_JSON) }

    var geminiInput by remember { mutableStateOf("") }
    var customLangInput by remember { mutableStateOf("") }
    var customModelInput by remember(subtitleConfig.geminiModelId) {
        mutableStateOf(subtitleConfig.geminiModelId)
    }

    // NKI Cloud Sync & Admin Vault UI state
    var cloudIsRegisterMode by remember { mutableStateOf(false) }
    var cloudUserInput by remember { mutableStateOf("") }
    var cloudPassInput by remember { mutableStateOf("") }
    var cloudConfirmPassInput by remember { mutableStateOf("") }
    var cloudDisplayNameInput by remember { mutableStateOf("") }
    var cloudInviteInput by remember { mutableStateOf("") }
    var cloudFormError by remember { mutableStateOf("") }
    var showProfileManager by remember { mutableStateOf(false) }
    var profileNameInput by remember(cloudSession?.displayName) { mutableStateOf(cloudSession?.displayName.orEmpty()) }
    var profileCurPassInput by remember { mutableStateOf("") }
    var profileNewPassInput by remember { mutableStateOf("") }
    var profileConfirmPassInput by remember { mutableStateOf("") }
    var profileFormError by remember { mutableStateOf("") }
    var showAdminVault by remember { mutableStateOf(false) }
    var adminNewKeyInput by remember { mutableStateOf("") }

    fun flushPendingKeyIfTyped() {
        val pending = geminiInput.trim()
        if (pending.length >= 20) {
            onSaveSessionGeminiKey(pending, customModelInput)
            geminiInput = ""
        }
    }

    val usbVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onAfterSystemOverlayResult()
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
            onPlayDirectUrl(fileName, uri.toString(), "")
        }
    }

    val latestFlush by rememberUpdatedState(::flushPendingKeyIfTyped)
    DisposableEffect(Unit) {
        onDispose { latestFlush() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AmbientCinemaBackdropBrush)
    ) {
        val isCompact = maxWidth < 600.dp && !isTv
        val horizPad = if (isCompact) 14.dp else 32.dp
        val primaryLang = subtitleConfig.resolvedPrimaryLanguage()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizPad, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Clean Responsive Header with NKI x ChillFilm Brand
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                    flushPendingKeyIfTyped()
                                    onBack()
                                }
                            )
                            .background(CinemaSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_chillfilm_nki),
                        contentDescription = "ChillFilm by NKI",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = s.settingsTitle,
                            color = TextPrimary,
                            fontSize = if (isCompact) 16.sp else 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "NKI Ecosystem • AI Cinema",
                            color = EmeraldNeon,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // QR Companion pairing is ONLY for TV boxes so phones can scan the TV screen!
                if (isTvHardware) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .tvFocusableClickable(
                                shape = CircleShape,
                                focusedBorderColor = VioletGlow,
                                unfocusedBorderColor = CinemaGlassBorder,
                                onClick = onOpenQrCompanion
                            )
                            .background(CinemaSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = "QR Companion", tint = VioletSoft, modifier = Modifier.size(19.dp))
                    }
                }
            }

            // 1a. NKI Cloud Auto-Sync & Centralized Secure AI Gateway
            SectionBox(
                title = "☁️ NKI Cloud Sync & Centralized AI",
                subtitle = when {
                    cloudSession != null && cloudSession.serverAiReady ->
                        "🟢 Connected (@${cloudSession.username}) • Centralized Gemini AI Active • Auto-syncing Web ↔ Android"
                    cloudSession != null ->
                        "🟡 Connected (@${cloudSession.username}) • Auto-syncing history & subtitles"
                    !cloudHealth.hasAdmin && cloudHealth.ok ->
                        "👑 First account created becomes ADMIN and locks registration to Invite-Only"
                    else ->
                        "Sign in once to auto-sync watch history, AI subtitles & use server Gemini AI without entering an API Key"
                }
            ) {
                if (cloudSession == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false to "Sign In", true to "Create Account").forEach { (isReg, label) ->
                                val selected = cloudIsRegisterMode == isReg
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(8.dp),
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = if (selected) EmeraldNeon else CinemaGlassBorder,
                                            onClick = { cloudIsRegisterMode = isReg }
                                        )
                                        .background(if (selected) EmeraldPrimary.copy(alpha = 0.25f) else CinemaSurfaceElevated)
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) EmeraldNeon else TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = cloudUserInput,
                            onValueChange = { cloudUserInput = it; cloudFormError = "" },
                            label = { Text("Tên đăng nhập (vd: admin hoặc email)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = CinemaGlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (cloudIsRegisterMode) {
                            OutlinedTextField(
                                value = cloudDisplayNameInput,
                                onValueChange = { cloudDisplayNameInput = it; cloudFormError = "" },
                                label = { Text("Tên hiển thị công khai / Biệt danh (KHÔNG nhập mật khẩu)", fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                        OutlinedTextField(
                            value = cloudPassInput,
                            onValueChange = { cloudPassInput = it; cloudFormError = "" },
                            label = { Text("Mật khẩu (tối thiểu 6 ký tự)", fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = CinemaGlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (cloudIsRegisterMode) {
                            OutlinedTextField(
                                value = cloudConfirmPassInput,
                                onValueChange = { cloudConfirmPassInput = it; cloudFormError = "" },
                                label = { Text("Xác nhận mật khẩu", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            if (cloudHealth.hasAdmin && cloudHealth.registrationMode == "INVITE_ONLY") {
                                OutlinedTextField(
                                    value = cloudInviteInput,
                                    onValueChange = { cloudInviteInput = it.uppercase() },
                                    label = { Text("Mã mời Admin (NKI-XXXX-XXXX)", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }
                        if (cloudFormError.isNotBlank()) {
                            Text(text = cloudFormError, color = LiveRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = EmeraldPrimary,
                                    onClick = {
                                        if (!cloudSyncBusy) {
                                            if (cloudIsRegisterMode) {
                                                when {
                                                    cloudPassInput.length < 6 -> cloudFormError = "Mật khẩu phải ít nhất 6 ký tự"
                                                    cloudPassInput != cloudConfirmPassInput -> cloudFormError = "Mật khẩu xác nhận không khớp!"
                                                    cloudDisplayNameInput.trim().isNotBlank() && cloudDisplayNameInput.trim() == cloudPassInput ->
                                                        cloudFormError = "⚠️ Biệt danh không được trùng mật khẩu!"
                                                    else -> {
                                                        cloudFormError = ""
                                                        onCloudRegister(cloudUserInput, cloudPassInput, cloudDisplayNameInput, cloudInviteInput)
                                                        cloudPassInput = ""
                                                        cloudConfirmPassInput = ""
                                                    }
                                                }
                                            } else {
                                                cloudFormError = ""
                                                onCloudLogin(cloudUserInput, cloudPassInput)
                                                cloudPassInput = ""
                                            }
                                        }
                                    }
                                )
                                .background(EmeraldPrimary)
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    cloudSyncBusy -> "⏳ Đang kết nối NKI Cloud..."
                                    cloudIsRegisterMode -> "✨ Tạo tài khoản NKI & Đồng bộ"
                                    else -> "☁️ Đăng nhập & Đồng bộ"
                                },
                                color = CinemaObsidian,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val quotaStr = if (cloudSession.role == "ADMIN" || cloudSession.dailyAiLimit <= 0) {
                            "${cloudSession.todayAiRequests} / ∞ AI today"
                        } else {
                            "${cloudSession.todayAiRequests} / ${cloudSession.dailyAiLimit} AI today"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${cloudSession.displayName} (@${cloudSession.username}) • ${cloudSession.role}",
                                    color = EmeraldNeon,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = quotaStr,
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(8.dp),
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = CinemaGlassBorder,
                                            onClick = onCloudSyncNow
                                        )
                                        .background(CinemaSurfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (cloudSyncBusy) "⏳" else "🔄 Sync",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(8.dp),
                                            focusedBorderColor = LiveRed,
                                            unfocusedBorderColor = CinemaGlassBorder,
                                            onClick = onCloudLogout
                                        )
                                        .background(CinemaSurfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text("Đăng xuất", color = LiveRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Profile & Security Manager (Visible to ALL signed-in users: Rename, Change Password, Revoke other devices)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = { showProfileManager = !showProfileManager }
                                )
                                .background(CinemaSurfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = if (showProfileManager) "👤 Ẩn Quản lý Hồ sơ & Bảo mật ▲" else "👤 Quản lý Hồ sơ & Bảo mật (Đổi tên • Đổi mật khẩu) ▼",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showProfileManager) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CinemaObsidian.copy(alpha = 0.72f))
                                    .border(1.dp, CinemaGlassBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tài khoản: @${cloudSession.username} • Vai trò: ${cloudSession.role}",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                                OutlinedTextField(
                                    value = profileNameInput,
                                    onValueChange = { profileNameInput = it; profileFormError = "" },
                                    label = { Text("Tên hiển thị công khai (Biệt danh)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(8.dp),
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = EmeraldPrimary,
                                            onClick = {
                                                val clean = profileNameInput.trim()
                                                if (clean.isBlank()) {
                                                    profileFormError = "Vui lòng nhập biệt danh mới"
                                                } else {
                                                    profileFormError = ""
                                                    onCloudUpdateProfile(clean, null, null, false)
                                                }
                                            }
                                        )
                                        .background(EmeraldPrimary)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("💾 Lưu tên hiển thị mới", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                OutlinedTextField(
                                    value = profileCurPassInput,
                                    onValueChange = { profileCurPassInput = it; profileFormError = "" },
                                    label = { Text("Mật khẩu hiện tại", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                OutlinedTextField(
                                    value = profileNewPassInput,
                                    onValueChange = { profileNewPassInput = it; profileFormError = "" },
                                    label = { Text("Mật khẩu mới (tối thiểu 6 ký tự)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                OutlinedTextField(
                                    value = profileConfirmPassInput,
                                    onValueChange = { profileConfirmPassInput = it; profileFormError = "" },
                                    label = { Text("Nhập lại mật khẩu mới", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = CinemaGlassBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                if (profileFormError.isNotBlank()) {
                                    Text(text = profileFormError, color = LiveRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvFocusableClickable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = EmeraldNeon,
                                                unfocusedBorderColor = EmeraldPrimary,
                                                onClick = {
                                                    when {
                                                        profileCurPassInput.isBlank() -> profileFormError = "Nhập mật khẩu hiện tại"
                                                        profileNewPassInput.length < 6 -> profileFormError = "Mật khẩu mới tối thiểu 6 ký tự"
                                                        profileNewPassInput != profileConfirmPassInput -> profileFormError = "Mật khẩu xác nhận không khớp!"
                                                        else -> {
                                                            profileFormError = ""
                                                            onCloudUpdateProfile(null, profileCurPassInput, profileNewPassInput, false)
                                                            profileCurPassInput = ""
                                                            profileNewPassInput = ""
                                                            profileConfirmPassInput = ""
                                                        }
                                                    }
                                                }
                                            )
                                            .background(EmeraldPrimary)
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔑 Đổi mật khẩu", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .tvFocusableClickable(
                                                shape = RoundedCornerShape(8.dp),
                                                focusedBorderColor = LiveRed,
                                                unfocusedBorderColor = CinemaGlassBorder,
                                                onClick = { onCloudUpdateProfile(null, null, null, true) }
                                            )
                                            .background(CinemaSurfaceElevated)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🛡️ Đăng xuất máy khác", color = LiveRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (cloudSession.role == "ADMIN") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(8.dp),
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = EmeraldPrimary.copy(alpha = 0.5f),
                                        onClick = {
                                            showAdminVault = !showAdminVault
                                            if (showAdminVault) onLoadAdminVault()
                                        }
                                    )
                                    .background(EmeraldPrimary.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = if (showAdminVault) "🔐 Hide NKI Admin Vault & Invite Manager ▲" else "🔐 Open NKI Admin Vault & Invite Manager ▼",
                                    color = EmeraldNeon,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (showAdminVault) {
                                val snap = adminVaultSnapshot
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CinemaObsidian.copy(alpha = 0.72f))
                                        .border(1.dp, CinemaGlassBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (snap != null) {
                                            "Env Key: ${if (snap.hasEnvKey) "✅" else "❌"} • Vault: ${if (snap.hasVaultKey) snap.maskedVaultKey else "Env"} • Cloud Subs: ${snap.cachedSubtitlesCount}"
                                        } else {
                                            "Loading Admin Vault..."
                                        },
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                    OutlinedTextField(
                                        value = adminNewKeyInput,
                                        onValueChange = { adminNewKeyInput = it },
                                        label = { Text("Rotate Server Gemini API Key (AES-256-GCM)", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldNeon,
                                            unfocusedBorderColor = CinemaGlassBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .tvFocusableClickable(
                                                    shape = RoundedCornerShape(8.dp),
                                                    focusedBorderColor = EmeraldNeon,
                                                    unfocusedBorderColor = EmeraldPrimary,
                                                    onClick = {
                                                        if (adminNewKeyInput.isNotBlank()) {
                                                            onAdminSaveServerKey(adminNewKeyInput)
                                                            adminNewKeyInput = ""
                                                        }
                                                    }
                                                )
                                                .background(EmeraldPrimary)
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🔐 Save Server Key", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .tvFocusableClickable(
                                                    shape = RoundedCornerShape(8.dp),
                                                    focusedBorderColor = EmeraldNeon,
                                                    unfocusedBorderColor = CinemaGlassBorder,
                                                    onClick = {
                                                        onAdminCreateInvite { code ->
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                            cm?.setPrimaryClip(ClipData.newPlainText("NKI Invite", code))
                                                        }
                                                    }
                                                )
                                                .background(CinemaSurfaceElevated)
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🎟 + New Invite", color = EmeraldNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (snap != null) {
                                        val nextRegMode = if (snap.registrationMode == "INVITE_ONLY") "OPEN" else "INVITE_ONLY"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Global Default Model: ${snap.defaultModel}",
                                                color = EmeraldNeon,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .tvFocusableClickable(
                                                        shape = RoundedCornerShape(6.dp),
                                                        focusedBorderColor = EmeraldNeon,
                                                        unfocusedBorderColor = CinemaGlassBorder,
                                                        onClick = onRefreshGeminiModels
                                                    )
                                                    .background(CinemaSurfaceElevated)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("🔄 Load Models", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            discoveredGeminiModels.take(12).forEach { mId ->
                                                val isGlobal = snap.defaultModel == mId
                                                Box(
                                                    modifier = Modifier
                                                        .tvFocusableClickable(
                                                            shape = RoundedCornerShape(99.dp),
                                                            focusedBorderColor = EmeraldNeon,
                                                            unfocusedBorderColor = if (isGlobal) EmeraldNeon else CinemaGlassBorder,
                                                            onClick = { onAdminSavePolicy(mId, snap.registrationMode) }
                                                        )
                                                        .background(if (isGlobal) EmeraldPrimary else CinemaSurfaceElevated)
                                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = mId,
                                                        color = if (isGlobal) CinemaObsidian else TextPrimary,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .tvFocusableClickable(
                                                    shape = RoundedCornerShape(8.dp),
                                                    focusedBorderColor = EmeraldNeon,
                                                    unfocusedBorderColor = CinemaGlassBorder,
                                                    onClick = { onAdminSavePolicy(snap.defaultModel, nextRegMode) }
                                                )
                                                .background(CinemaSurfaceElevated)
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "Sign-up Policy: ${if (snap.registrationMode == "INVITE_ONLY") "🔒 Invite-Only (Tap to Open)" else "🌐 Open (Tap to Lock Invite-Only)"}",
                                                color = TextPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        snap.invites.take(6).forEach { inv ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${inv.code} (${inv.usedCount}/${inv.maxUses})${if (!inv.active) " [Revoked]" else ""}",
                                                    color = if (inv.active) EmeraldNeon else TextSecondary,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .tvFocusableClickable(
                                                                shape = RoundedCornerShape(6.dp),
                                                                focusedBorderColor = EmeraldNeon,
                                                                unfocusedBorderColor = CinemaGlassBorder,
                                                                onClick = {
                                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                                    cm?.setPrimaryClip(ClipData.newPlainText("NKI Invite", inv.code))
                                                                }
                                                            )
                                                            .background(CinemaSurfaceElevated)
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("Copy", color = TextPrimary, fontSize = 11.sp)
                                                    }
                                                    if (inv.active) {
                                                        Box(
                                                            modifier = Modifier
                                                                .tvFocusableClickable(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    focusedBorderColor = LiveRed,
                                                                    unfocusedBorderColor = CinemaGlassBorder,
                                                                    onClick = { onAdminRevokeInvite(inv.code) }
                                                                )
                                                                .background(CinemaSurfaceElevated)
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("Revoke", color = LiveRed, fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        snap.users.filter { it.role != "ADMIN" }.take(8).forEach { member ->
                                            val nextStatus = if (member.status == "ACTIVE") "REVOKED" else "ACTIVE"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "@${member.username} (${member.status} • ${member.todayRequests}/${member.dailyAiLimit})",
                                                    color = TextPrimary,
                                                    fontSize = 11.5.sp
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .tvFocusableClickable(
                                                            shape = RoundedCornerShape(6.dp),
                                                            focusedBorderColor = EmeraldNeon,
                                                            unfocusedBorderColor = CinemaGlassBorder,
                                                            onClick = { onAdminToggleMemberStatus(member.id, nextStatus) }
                                                        )
                                                        .background(CinemaSurfaceElevated)
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (member.status == "ACTIVE") "Revoke" else "Restore",
                                                        color = if (member.status == "ACTIVE") LiveRed else EmeraldNeon,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1b. App Interface Language Switcher (Default: English • EN / VI / TH / ZH / KO)
            SectionBox(
                title = "🌐 ${s.uiLanguageSectionTitle}",
                subtitle = s.uiLanguageSectionDesc
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val active = lang == currentAppLanguage
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = if (active) EmeraldNeon else CinemaGlassBorder,
                                    onClick = { onSelectAppLanguage(lang) }
                                )
                                .background(
                                    if (active) EmeraldPrimary.copy(alpha = 0.24f) else CinemaSurfaceElevated
                                )
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${lang.shortBadge} • ${lang.nativeName}",
                                color = if (active) EmeraldNeon else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 2. App Version & GitHub Releases Auto-Updater
            SectionBox(
                title = "Phiên Bản & Cập Nhật Tự Động",
                subtitle = "Chữ ký cố định NKI/ChillFilm — cập nhật bản mới giữ nguyên API Key, ngôn ngữ phụ đề & lịch sử xem."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "v${appUpdateState.currentVersion.versionName} (Build ${appUpdateState.currentVersion.versionCode})",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Web App & APK: PrimiasArt/Chillfilm.net",
                                color = VioletSoft,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = EmeraldNeon,
                                    enabled = !appUpdateState.isChecking && !appUpdateState.isDownloading,
                                    onClick = onCheckForUpdates
                                )
                                .background(BrandEmeraldVioletBrush)
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appUpdateState.isChecking) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (appUpdateState.isChecking) "Đang kiểm..." else "Kiểm tra",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    appUpdateState.statusMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldPrimary.copy(alpha = 0.14f))
                                .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = msg, color = EmeraldNeon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    appUpdateState.errorMessage?.let { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LiveRed.copy(alpha = 0.14f))
                                .border(1.dp, LiveRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = err, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                        }
                    }

                    val remote = appUpdateState.latestRelease
                    if (remote != null && appUpdateState.hasUpdate) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VioletGlow.copy(alpha = 0.12f))
                                .border(1.dp, VioletGlow.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SystemUpdate, null, tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bản mới v${remote.versionName} (Build ${remote.versionCode})",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(99.dp),
                                            enabled = !appUpdateState.isDownloading,
                                            onClick = onDownloadAndInstallUpdate
                                        )
                                        .background(EmeraldPrimary)
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Download, null, tint = CinemaObsidian, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (appUpdateState.isDownloading) "Đang tải ${appUpdateState.downloadPercent}%..." else "Tải & Cài Đặt",
                                        color = CinemaObsidian,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                if (appUpdateState.needsInstallPermission) {
                                    Box(
                                        modifier = Modifier
                                            .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = onOpenInstallPermission)
                                            .background(VioletPrimary)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Cấp quyền cài APK", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (hasSelectedTv && appUpdateState.downloadedApkPath != null) {
                                    Box(
                                        modifier = Modifier
                                            .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = onPushUpdateToTv)
                                            .background(CinemaSurfaceElevated)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Cập nhật lên TV", color = VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (appUpdateState.isDownloading) {
                                LinearProgressIndicator(
                                    progress = { appUpdateState.downloadPercent.coerceIn(0, 100) / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                                    color = EmeraldNeon,
                                    trackColor = Color.White.copy(alpha = 0.14f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Universal Multi-Language AI Subtitles & Gemini API Key
            SectionBox(
                title = "Phụ Đề AI Đa Ngôn Ngữ & Gemini Key",
                subtitle = "Hỗ trợ dịch và nghe phim tạo phụ đề sang mọi ngôn ngữ AI hỗ trợ. Đang chọn: ${primaryLang.displayName}."
            ) {
                val hasKey = geminiApiKey.isNotBlank()
                val hasCloudAi = cloudSession?.serverAiReady == true
                val maskedKey = remember(geminiApiKey) {
                    val k = geminiApiKey.trim()
                    when {
                        k.length >= 10 -> "${k.take(6)}••••${k.takeLast(4)}"
                        k.isNotEmpty() -> "••••"
                        else -> ""
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasKey || hasCloudAi) EmeraldPrimary.copy(alpha = 0.15f) else CinemaSurfaceElevated)
                        .border(1.dp, if (hasKey || hasCloudAi) EmeraldNeon.copy(alpha = 0.45f) else CinemaGlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when {
                            hasCloudAi -> "☁️ Đang dùng Gemini API tập trung trên NKI Cloud • Model: ${subtitleConfig.geminiModelId}"
                            hasKey -> "🔐 Đã lưu cố định ($maskedKey) • Model: ${subtitleConfig.geminiModelId}"
                            else -> "🔓 Chưa đăng nhập NKI Cloud / chưa nhập Key • Đang dùng Neural AI miễn phí"
                        },
                        color = if (hasKey || hasCloudAi) EmeraldNeon else VioletSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Ngôn ngữ phụ đề chính mặc định:",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubtitleLanguageCatalog.PRESETS.forEach { lang ->
                        val selected = SubtitleLanguageCatalog.isTrackMatchingLanguage(primaryLang.code, lang.code)
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    unfocusedBorderColor = if (selected) EmeraldNeon else CinemaGlassBorder,
                                    onClick = {
                                        onUpdateSubtitleConfig {
                                            it.copy(preferredPrimaryLang = lang.code, autoTranslateToThai = true)
                                        }
                                    }
                                )
                                .background(if (selected) EmeraldPrimary else CinemaSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${lang.shortBadge} • ${lang.displayName.substringBefore(" (")}",
                                color = if (selected) CinemaObsidian else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customLangInput,
                        onValueChange = { customLangInput = it },
                        singleLine = true,
                        placeholder = { Text("Hoặc nhập bất kỳ ngôn ngữ nào (VD: Dutch, Polish...)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors()
                    )
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    val clean = customLangInput.trim()
                                    if (clean.isNotEmpty()) {
                                        onUpdateSubtitleConfig { it.copy(preferredPrimaryLang = clean, autoTranslateToThai = true) }
                                        customLangInput = ""
                                    }
                                }
                            )
                            .background(EmeraldPrimary)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text("Áp dụng", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TranslationEngineMode.entries.forEach { mode ->
                        val selected = subtitleConfig.translationEngine == mode
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    unfocusedBorderColor = if (selected) EmeraldNeon else CinemaGlassBorder,
                                    onClick = { onUpdateSubtitleConfig { it.copy(translationEngine = mode) } }
                                )
                                .background(if (selected) EmeraldPrimary else CinemaSurfaceElevated)
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (selected) CinemaObsidian else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val modelOptions = remember(discoveredGeminiModels, subtitleConfig.geminiModelId) {
                    val merged = LinkedHashSet<String>()
                    if (subtitleConfig.geminiModelId.isNotBlank()) merged.add(subtitleConfig.geminiModelId)
                    discoveredGeminiModels.forEach { merged.add(it) }
                    merged.toList()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelOptions.forEach { modelId ->
                        val selected = subtitleConfig.geminiModelId == modelId
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    unfocusedBorderColor = if (selected) VioletGlow else CinemaGlassBorder,
                                    onClick = {
                                        customModelInput = modelId
                                        if (geminiInput.isNotBlank()) {
                                            onSaveSessionGeminiKey(geminiInput, modelId)
                                            geminiInput = ""
                                        } else {
                                            onSelectGeminiModel(modelId)
                                        }
                                    }
                                )
                                .background(if (selected) VioletPrimary else CinemaSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = modelId,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = onRefreshGeminiModels)
                            .background(EmeraldPrimary.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🔄 Tải Model thực tế từ API", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Responsive: stacked on mobile phones, side-by-side on wide/TV!
                val keyPlaceholder = if (hasKey) {
                    "Đã lưu Key ($maskedKey) — chỉ dán lại nếu muốn đổi Key mới"
                } else {
                    "Dán Google Gemini API Key (AIza...)..."
                }
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = geminiInput,
                            onValueChange = { geminiInput = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text(keyPlaceholder, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            singleLine = true,
                            placeholder = { Text("Mã Model (VD: gemini-3-flash-preview)", fontSize = 12.5.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors()
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = geminiInput,
                            onValueChange = { geminiInput = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text(keyPlaceholder, fontSize = 12.5.sp) },
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            singleLine = true,
                            placeholder = { Text("Mã Model (VD: gemini-3-flash-preview)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                onClick = {
                                    onSaveSessionGeminiKey(geminiInput, customModelInput)
                                    geminiInput = ""
                                }
                            )
                            .background(BrandEmeraldVioletBrush)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "💾 Lưu Key & Model",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp
                        )
                    }

                    if (hasKey) {
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderColor = LiveRed,
                                    onClick = {
                                        geminiInput = ""
                                        onWipeSessionGeminiKey()
                                    }
                                )
                                .background(LiveRed.copy(alpha = 0.2f))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Xóa Key", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }

            // 4. Optional Secondary Feature: Stream & Manage TV
            if (!isTvHardware) {
                SectionBox(
                    title = "Stream & Quản Lý TV (Tính Năng Phụ)",
                    subtitle = "Tùy chọn quét mạng Wi-Fi, cài/gỡ ứng dụng lên Android TV/TV360 Box và phát phim từ điện thoại lên màn hình lớn."
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(14.dp),
                                focusedBorderColor = VioletGlow,
                                unfocusedBorderColor = VioletPrimary.copy(alpha = 0.5f),
                                onClick = onOpenTvManager
                            )
                            .background(VioletPrimary.copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Cast, null, tint = VioletSoft, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Mở Trung Tâm Kết Nối TV", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (hasSelectedTv) "Đã ghép nối TV trên mạng LAN" else "Cài APK qua Wi-Fi, điều khiển & truyền hình",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(Icons.Default.Tv, null, tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 5. Play Local File / USB or Direct Stream URL
            SectionBox(
                title = "Phát File Video Từ Máy Hoặc Link Trực Tiếp",
                subtitle = "Hỗ trợ mở file MP4/MKV trong máy, link YouTube hoặc luồng HLS (.m3u8) kèm phụ đề AI đa ngôn ngữ."
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                onBeforeLaunchSystemOverlay()
                                usbVideoPicker.launch(arrayOf("video/*", "*/*"))
                            }
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, null, tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mở Video Từ Bộ Nhớ / USB", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = directStreamUrl,
                    onValueChange = { directStreamUrl = it },
                    singleLine = true,
                    placeholder = { Text("Dán link YouTube hoặc .m3u8 / .mp4...", fontSize = 12.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = directTitle,
                            onValueChange = { directTitle = it },
                            singleLine = true,
                            placeholder = { Text("Tên phim (Tùy chọn)", fontSize = 12.5.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = directSubUrl,
                            onValueChange = { directSubUrl = it },
                            singleLine = true,
                            placeholder = { Text("Link Sub .srt/.vtt (Tùy chọn)", fontSize = 12.5.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors()
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = directTitle,
                            onValueChange = { directTitle = it },
                            singleLine = true,
                            placeholder = { Text("Tên phim (Tùy chọn)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = directSubUrl,
                            onValueChange = { directSubUrl = it },
                            singleLine = true,
                            placeholder = { Text("Link Sub .srt/.vtt (Tùy chọn)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                if (directStreamUrl.isNotBlank()) {
                                    onPlayDirectUrl(
                                        directTitle.ifBlank { "Luồng Trực Tiếp" },
                                        directStreamUrl,
                                        directSubUrl
                                    )
                                }
                            }
                        )
                        .background(EmeraldPrimary)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = CinemaObsidian, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phát Ngay Với AI Sub", color = CinemaObsidian, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }

            // 6. Custom VOD Sources
            SectionBox(
                title = "Nguồn Phim Mở Tùy Chỉnh",
                subtitle = "Cắm thêm bất kỳ API JSON VOD CMS hoặc danh sách M3U/M3U8 nào."
            ) {
                OutlinedTextField(
                    value = provName,
                    onValueChange = { provName = it },
                    singleLine = true,
                    placeholder = { Text("Tên nguồn hiển thị", fontSize = 12.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = provUrl,
                    onValueChange = { provUrl = it },
                    singleLine = true,
                    placeholder = { Text("URL API (https://...) hoặc link .m3u / .json", fontSize = 12.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VodProviderType.entries.forEach { t ->
                        val selected = provType == t
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = { provType = t }
                                )
                                .background(if (selected) EmeraldPrimary else CinemaSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = t.displayName.substringBefore(" ("),
                                color = if (selected) CinemaObsidian else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                if (provUrl.isNotBlank()) {
                                    onAddProvider(provName, provUrl, provType)
                                    provName = ""
                                    provUrl = ""
                                }
                            }
                        )
                        .background(EmeraldPrimary)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = CinemaObsidian, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Thêm Nguồn", color = CinemaObsidian, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                }

                if (customProviders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    customProviders.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CinemaSurfaceElevated)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.baseUrl, color = TextSecondary, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(shape = RoundedCornerShape(8.dp), onClick = { onToggleProvider(p.id) })
                                        .background(if (p.enabled) EmeraldPrimary else Color(0xFF374151))
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(if (p.enabled) "Bật" else "Tắt", color = if (p.enabled) CinemaObsidian else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(shape = RoundedCornerShape(8.dp), onClick = { onRemoveProvider(p.id) })
                                        .background(LiveRed.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = LiveRed, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 7. Display Mode & Cache Maintenance
            SectionBox(
                title = "Giao Diện & Bộ Nhớ Đệm",
                subtitle = "Tự động tối ưu theo màn hình điện thoại, máy tính bảng hoặc Smart TV."
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceUiMode.entries.forEach { mode ->
                        val selected = deviceUiMode == mode
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = { onSetDeviceUiMode(mode) })
                                .background(if (selected) EmeraldPrimary else CinemaSurfaceElevated)
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = mode.displayName.substringBefore(" ("),
                                color = if (selected) CinemaObsidian else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .tvFocusableClickable(shape = RoundedCornerShape(10.dp), onClick = onClearCache)
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CleaningServices, null, tint = EmeraldNeon, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dọn Bộ Nhớ Đệm", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier
                            .tvFocusableClickable(shape = RoundedCornerShape(10.dp), onClick = onClearWatchHistory)
                            .background(CinemaSurfaceElevated)
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, tint = LiveRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa Lịch Sử Xem", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionBox(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CinemaGlass)
            .border(1.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(text = title, color = EmeraldNeon, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = subtitle, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldNeon,
    unfocusedBorderColor = CinemaGlassBorder,
    focusedContainerColor = CinemaSurface,
    unfocusedContainerColor = CinemaSurface,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
