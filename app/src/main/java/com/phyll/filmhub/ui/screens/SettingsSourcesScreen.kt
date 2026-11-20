package com.phyll.filmhub.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.model.DeviceUiMode
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.model.VodProviderConfig
import com.phyll.filmhub.model.VodProviderType
import com.phyll.filmhub.ui.theme.AmbientCinemaBackdropBrush
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
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
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    hasSelectedTv: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {},
    onOpenInstallPermission: () -> Unit = {},
    onPushUpdateToTv: () -> Unit = {},
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

    var directTitle by remember { mutableStateOf("") }
    var directStreamUrl by remember { mutableStateOf("") }
    var directSubUrl by remember { mutableStateOf("") }

    var provName by remember { mutableStateOf("") }
    var provUrl by remember { mutableStateOf("") }
    var provType by remember { mutableStateOf(VodProviderType.STANDARD_CMS_JSON) }

    var geminiInput by remember { mutableStateOf("") }
    var customModelInput by remember(subtitleConfig.geminiModelId) {
        mutableStateOf(subtitleConfig.geminiModelId)
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
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video USB"
            onPlayDirectUrl(fileName, uri.toString(), "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmbientCinemaBackdropBrush)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 36.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = onBack
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Trang Chủ", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "⚙️ Nguồn Phim, USB & Cấu Hình Sub Thái",
                    color = ThaiGold,
                    fontSize = if (isTv) 21.sp else 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(
                modifier = Modifier
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderColor = ElectricCyan,
                        onClick = onOpenQrCompanion
                    )
                    .background(CinemaSurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Nhập nhanh qua QR Điện Thoại", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Section 0: App Version & GitHub Releases One-Click Auto Updater (Persistent Keystore = No Data Loss!)
        SectionBox(
            title = "0. Phiên Bản ChillFilm & Kiểm Tra Cập Nhật (GitHub Build)",
            subtitle = "Ứng dụng sử dụng chữ ký cố định: cập nhật đè bản mới giữ nguyên API Key, danh sách TV, nguồn phim & lịch sử xem."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phiên bản hiện tại: v${appUpdateState.currentVersion.versionName} (Build ${appUpdateState.currentVersion.versionCode})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Repo: github.com/PrimiasArt/Chillfilm.net",
                        color = ElectricCyan,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = EmeraldPrimary.copy(alpha = 0.6f),
                            enabled = !appUpdateState.isChecking && !appUpdateState.isDownloading,
                            onClick = onCheckForUpdates
                        )
                        .background(BrandEmeraldVioletBrush)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appUpdateState.isChecking) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Kiểm tra cập nhật",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (appUpdateState.isChecking) "Đang kiểm tra..." else "Kiểm tra cập nhật",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            appUpdateState.statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(10.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VioletGlow.copy(alpha = 0.12f))
                        .border(1.dp, VioletGlow.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Phát hiện bản mới v${remote.versionName} (Build ${remote.versionCode})",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        if (remote.assetSizeBytes > 0) {
                            Text(
                                text = "%.1f MB".format(remote.assetSizeBytes / (1024f * 1024f)),
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (remote.releaseNotes.isNotBlank()) {
                        Text(
                            text = remote.releaseNotes.take(260),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
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
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = EmeraldNeon,
                                    enabled = !appUpdateState.isDownloading,
                                    onClick = onDownloadAndInstallUpdate
                                )
                                .background(EmeraldPrimary)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (appUpdateState.isDownloading) {
                                    "Đang tải ${appUpdateState.downloadPercent}%..."
                                } else {
                                    "Tải & Cài Đặt Ngay (1 Chạm)"
                                },
                                color = CinemaObsidian,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (appUpdateState.needsInstallPermission) {
                            Row(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(99.dp),
                                        focusedBorderColor = Color.White,
                                        onClick = onOpenInstallPermission
                                    )
                                    .background(VioletGlow)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔓 Cấp quyền cài APK",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (hasSelectedTv && appUpdateState.downloadedApkPath != null) {
                            Row(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(99.dp),
                                        focusedBorderColor = Color.White,
                                        onClick = onPushUpdateToTv
                                    )
                                    .background(CinemaSurfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tv, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Cập nhật lên TV đang chọn",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (appUpdateState.isDownloading) {
                        LinearProgressIndicator(
                            progress = { (appUpdateState.downloadPercent.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp)),
                            color = EmeraldNeon,
                            trackColor = Color.White.copy(alpha = 0.14f)
                        )
                    }
                }
            }
        }

        // Section 1: Play from USB on TV360 Box or Direct Stream URL (.m3u8 / .mp4 / .mkv)
        SectionBox(
            title = "1. Phát Phim Từ Cổng USB (TV360 Box) Hoặc Link Trực Tiếp (.M3U8 / .MP4 / .MKV)",
            subtitle = "Hỗ trợ chọn thêm file Sub Thái .SRT/.ASS ngay trong trình phát"
        ) {
            Row(
                modifier = Modifier
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(10.dp),
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = ElectricCyan,
                        onClick = {
                            onBeforeLaunchSystemOverlay()
                            usbVideoPicker.launch(arrayOf("video/*", "*/*"))
                        }
                    )
                    .background(ElectricCyan)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mở File Phim Từ USB / Bộ Nhớ Máy",
                    color = CinemaObsidian,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = directStreamUrl,
                onValueChange = { directStreamUrl = it },
                singleLine = true,
                placeholder = { Text("Dán link YouTube (youtu.be / youtube.com) hoặc .m3u8 / .mp4...", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                    .background(ThaiGold)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Phát Ngay Với Sub Thái", color = CinemaObsidian, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }

        // Section 2: Multi-Source VOD Manager
        SectionBox(
            title = "2. Quản Lý Nguồn Phim Mở (Tích hợp sẵn + Thêm API Phim Mới)",
            subtitle = "App đã tích hợp sẵn: Phyll Showcase + Archive Cinema (Mỹ, Anh, Trung, Hàn, Thái) + Kênh TV Quốc Tế. Bạn có thể cắm thêm bất kỳ API JSON VOD CMS hoặc M3U nào."
        ) {
            OutlinedTextField(
                value = provName,
                onValueChange = { provName = it },
                singleLine = true,
                placeholder = { Text("Tên nguồn hiển thị (VD: Kho Phim Châu Á HD)", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = provUrl,
                onValueChange = { provUrl = it },
                singleLine = true,
                placeholder = { Text("Base URL API (https://...) hoặc link danh sách .m3u / .json", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VodProviderType.entries.forEach { t ->
                    val selected = provType == t
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBorderColor = if (selected) ThaiGold else Color(0xFF374151),
                                onClick = { provType = t }
                            )
                            .background(if (selected) ThaiGold else CinemaSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = t.displayName,
                            color = if (selected) CinemaObsidian else TextPrimary,
                            fontSize = 12.sp,
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
                    .background(ThaiGold)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Thêm Nguồn Phim", color = CinemaObsidian, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }

            if (customProviders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Các nguồn đã thêm:", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                customProviders.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CinemaSurfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = p.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${p.type.displayName} • ${p.baseUrl}", color = TextSecondary, fontSize = 11.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = { onToggleProvider(p.id) }
                                    )
                                    .background(if (p.enabled) ElectricCyan else Color(0xFF374151))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (p.enabled) "Đang Bật" else "Đang Tắt",
                                    color = if (p.enabled) CinemaObsidian else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(8.dp),
                                        focusedBorderColor = LiveRed,
                                        onClick = { onRemoveProvider(p.id) }
                                    )
                                    .background(LiveRed.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = LiveRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section 3: AI Thai Translation & Multimodal Audio Sub Engine (RAM-Only Ephemeral Security)
        SectionBox(
            title = "3. Gemini AI Đa Mô Hình Dịch & Nghe Phim Tạo Sub ไทย (Bảo Mật Chỉ Lưu RAM)",
            subtitle = "API Key KHÔNG BAO GIỜ ghi xuống bộ nhớ TV — chỉ nạp tạm trên RAM qua mã QR mỗi phiên xem, tự động xóa sạch ngay khi thoát App."
        ) {
            val hasKey = geminiApiKey.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasKey) Color(0xFF064E3B) else CinemaSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (hasKey) {
                        "🔐 TRẠNG THÁI RAM: Đã nạp Gemini Key tạm thời • Model đang dùng: ${subtitleConfig.geminiModelId} • Thoát App tự xóa"
                    } else {
                        "🔓 TRẠNG THÁI RAM: Trống (0 byte trên ổ cứng) • Quét QR từ Điện Thoại để nạp Key tạm"
                    },
                    color = if (hasKey) Color(0xFF6EE7B7) else ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TranslationEngineMode.entries.forEach { mode ->
                    val selected = subtitleConfig.translationEngine == mode
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBorderColor = if (selected) ThaiGold else Color(0xFF374151),
                                onClick = { onUpdateSubtitleConfig { it.copy(translationEngine = mode) } }
                            )
                            .background(if (selected) ThaiGold else CinemaSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (selected) CinemaObsidian else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBorderColor = if (subtitleConfig.autoGenerateAudioSubWhenMissing) ElectricCyan else Color(0xFF374151),
                            onClick = {
                                onUpdateSubtitleConfig {
                                    it.copy(autoGenerateAudioSubWhenMissing = !it.autoGenerateAudioSubWhenMissing)
                                }
                            }
                        )
                        .background(if (subtitleConfig.autoGenerateAudioSubWhenMissing) ElectricCyan else CinemaSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (subtitleConfig.autoGenerateAudioSubWhenMissing) {
                            "🎙️ Tự Nghe Audio Tạo Sub ไทย Khi Kho Sub Trống: BẬT"
                        } else {
                            "🎙️ Tự Nghe Audio Tạo Sub ไทย Khi Kho Sub Trống: TẮT"
                        },
                        color = if (subtitleConfig.autoGenerateAudioSubWhenMissing) CinemaObsidian else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(8.dp),
                            unfocusedBorderColor = if (promptQrOnStartup) ElectricCyan else Color(0xFF374151),
                            onClick = { onTogglePromptQrOnStartup(!promptQrOnStartup) }
                        )
                        .background(if (promptQrOnStartup) ElectricCyan else CinemaSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (promptQrOnStartup) {
                            "📱 Hiện QR Nạp Key Mỗi Khi Mở App: BẬT"
                        } else {
                            "📱 Hiện QR Nạp Key Mỗi Khi Mở App: TẮT"
                        },
                        color = if (promptQrOnStartup) CinemaObsidian else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chọn Model Gemini Dịch & Tạo Sub (Hỗ trợ Gemini 3 / 3.1 / 2.5 hoặc tự nhập mã bất kỳ):",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            val modelOptions = remember(discoveredGeminiModels, subtitleConfig.geminiModelId) {
                val merged = LinkedHashSet<String>()
                if (subtitleConfig.geminiModelId.isNotBlank()) merged.add(subtitleConfig.geminiModelId)
                discoveredGeminiModels.forEach { merged.add(it) }
                merged.toList()
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modelOptions.forEach { modelId ->
                    val selected = subtitleConfig.geminiModelId == modelId
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBorderColor = if (selected) ThaiGold else Color(0xFF374151),
                                onClick = {
                                    customModelInput = modelId
                                    onSelectGeminiModel(modelId)
                                }
                            )
                            .background(if (selected) ThaiGold else CinemaSurfaceElevated)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = modelId,
                            color = if (selected) CinemaObsidian else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(8.dp),
                            focusedBorderColor = ElectricCyan,
                            onClick = onRefreshGeminiModels
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "🔄 Tải DS Model Từ Key",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    singleLine = true,
                    placeholder = { Text("Mã Model tùy chọn (VD: gemini-3-flash-preview)", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = geminiInput,
                    onValueChange = { geminiInput = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("Nạp tạm Key vào RAM (Khuyên dùng quét QR)", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = textFieldColors()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = ThaiGold,
                            onClick = onOpenQrCompanion
                        )
                        .background(ThaiGold)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "📱 Mở QR Quét Key Từ Điện Thoại",
                        color = CinemaObsidian,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                onSaveSessionGeminiKey(geminiInput, customModelInput)
                                geminiInput = ""
                            }
                        )
                        .background(ElectricCyan)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "🔐 Áp Dụng Model / Nạp Tạm RAM",
                        color = CinemaObsidian,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
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
                            .background(LiveRed.copy(alpha = 0.22f))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "🧹 Xóa Key Khỏi RAM Ngay",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Section 4: TV360 Box vs Mobile UI Mode & 8GB ROM Cache Cleaner
        SectionBox(
            title = "4. Tối Ưu Cho TV360 Box & Chế Độ Giao Diện",
            subtitle = "Chuyển đổi giao diện Remote D-Pad / Cảm ứng và giải phóng bộ nhớ trong 8GB của TV360 Box."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceUiMode.entries.forEach { mode ->
                    val selected = deviceUiMode == mode
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(8.dp),
                                unfocusedBorderColor = if (selected) ThaiGold else Color(0xFF374151),
                                onClick = { onSetDeviceUiMode(mode) }
                            )
                            .background(if (selected) ThaiGold else CinemaSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (selected) CinemaObsidian else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = ElectricCyan,
                            onClick = onClearCache
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Dọn Sạch Bộ Nhớ Đệm (Tối ưu TV360 8GB)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = LiveRed,
                            onClick = onClearWatchHistory
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = LiveRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Xóa Lịch Sử Xem Dở", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
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
            .border(1.dp, CinemaGlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(text = title, color = ThaiGold, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ThaiGold,
    unfocusedBorderColor = Color(0xFF323D52),
    focusedContainerColor = CinemaObsidian,
    unfocusedContainerColor = CinemaObsidian,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
