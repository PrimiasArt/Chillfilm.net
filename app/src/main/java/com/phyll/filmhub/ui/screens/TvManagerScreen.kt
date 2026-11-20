package com.phyll.filmhub.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.model.AdbTaskProgress
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.RemoteTvLiveState
import com.phyll.filmhub.model.SecondarySubLang
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleFontChoice
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.LiveRed
import com.phyll.filmhub.ui.theme.SuccessGreen
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvManagerScreen(
    managedTvs: List<ManagedTvDevice>,
    selectedTvIp: String?,
    controlTargetMode: ControlTargetMode,
    isScanningTvs: Boolean,
    adbProgress: AdbTaskProgress,
    remoteState: RemoteTvLiveState,
    tvScreenFrameBytes: ByteArray?,
    savedGeminiKey: String,
    discoveredGeminiModels: List<String>,
    localSubtitleConfig: SubtitleStyleConfig,
    onScanLan: () -> Unit,
    onAddManualTvIp: (ip: String, nickname: String) -> Unit,
    onSelectTv: (ip: String) -> Unit,
    onRenameTv: (ip: String, newName: String) -> Unit,
    onRemoveTv: (ip: String) -> Unit,
    onInstallAppToTv: (ip: String) -> Unit,
    onUninstallAppFromTv: (ip: String) -> Unit,
    onLaunchAppOnTv: (ip: String) -> Unit,
    onDismissAdbProgress: () -> Unit,
    onSetControlTargetMode: (ControlTargetMode) -> Unit,
    onSaveGeminiKey: (key: String, modelId: String) -> Unit,
    onSyncKeyToTvNow: () -> Unit,
    onWipeGeminiKey: () -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onSendTouchKey: (String) -> Unit,
    onSendTap: (Float, Float) -> Unit,
    onSendSwipe: (Float, Float, Float, Float) -> Unit,
    onSendPlayerCmd: (cmd: String, value: Long) -> Unit,
    onSendSearchToTv: (String) -> Unit,
    onPushDirectStreamToTv: (title: String, url: String) -> Unit,
    onPushSubtitleFileToTv: (fileName: String, bytes: ByteArray, lang: String) -> Unit,
    onUpdateRemoteSubStyle: (JSONObject, ((SubtitleStyleConfig) -> SubtitleStyleConfig)?) -> Unit,
    onResetRemoteSubStyleDefault: () -> Unit,
    onStartScreenStream: (width: Int, quality: Int) -> Unit,
    onStopScreenStream: () -> Unit,
    onBeforeLaunchSystemOverlay: () -> Unit,
    onAfterSystemOverlayResult: () -> Unit,
    onGoPickMoviesForTv: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var activeTab by remember { mutableIntStateOf(0) }
    var isStreamFullscreen by remember { mutableStateOf(false) }

    val selectedTv = managedTvs.firstOrNull { it.ip == selectedTvIp }
    val effectiveSubStyle = if (remoteState.online && selectedTvIp != null) {
        remoteState.subtitleStyle
    } else {
        localSubtitleConfig
    }

    var renameTargetDevice by remember { mutableStateOf<ManagedTvDevice?>(null) }
    var renameText by remember { mutableStateOf("") }
    var uninstallConfirmDevice by remember { mutableStateOf<ManagedTvDevice?>(null) }

    DisposableEffect(activeTab, selectedTvIp, isStreamFullscreen) {
        if (activeTab == 3 && selectedTvIp != null) {
            onStartScreenStream(if (isStreamFullscreen) 1080 else 860, if (isStreamFullscreen) 74 else 66)
        } else {
            onStopScreenStream()
        }
        onDispose {
            onStopScreenStream()
            if (isStreamFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    if (isStreamFullscreen) {
        FullscreenTvStreamOverlay(
            selectedTv = selectedTv,
            frameBytes = tvScreenFrameBytes,
            onSendTap = onSendTap,
            onSendSwipe = onSendSwipe,
            onSendTouchKey = onSendTouchKey,
            onExitFullscreen = {
                isStreamFullscreen = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaObsidian)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CinemaSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CinemaSurfaceElevated)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trang chủ", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📡 Quản Lý TV & Điều Khiển Toàn Diện",
                        color = ThaiGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            selectedTv != null && remoteState.online ->
                                "🟢 Đang kết nối: ${selectedTv.displayTitle} (${selectedTv.ip})"
                            selectedTv != null ->
                                "🟡 Đã chọn: ${selectedTv.displayTitle} • ${if (selectedTv.adbOnline) "ADB Sẵn sàng" else "Kiểm tra Wi-Fi TV"}"
                            else -> "Chưa chọn TV • Chọn TV hoặc chuyển sang Xem trực tiếp trên ĐT"
                        },
                        color = if (remoteState.online) SuccessGreen else TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Mode Toggle Pill (Phone Direct vs Remote TV)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan.copy(alpha = 0.2f)
                        else ThaiGold.copy(alpha = 0.18f)
                    )
                    .border(
                        1.dp,
                        if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan else ThaiGold,
                        RoundedCornerShape(99.dp)
                    )
                    .clickable {
                        val next = if (controlTargetMode == ControlTargetMode.REMOTE_TV) {
                            ControlTargetMode.LOCAL_DEVICE
                        } else {
                            ControlTargetMode.REMOTE_TV
                        }
                        onSetControlTargetMode(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = controlTargetMode.shortBadge,
                    color = if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan else ThaiGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 4 Hub Category Tabs
        val tabs = listOf(
            0 to "📡 TV & Cài App",
            1 to "🎮 Điều Khiển",
            2 to "💬 Chỉnh Sub",
            3 to "📺 Stream TV"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CinemaSurface)
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEach { (idx, label) ->
                val isSelected = activeTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ThaiGold else CinemaSurfaceElevated)
                        .clickable { activeTab = idx }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CinemaObsidian else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ADB Live Progress Banner (Install / Uninstall / Launch)
        if (adbProgress.isRunning || adbProgress.statusMessage.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (adbProgress.isError) LiveRed.copy(alpha = 0.16f)
                        else Color(0xFF064E3B)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${adbProgress.operationName} (${adbProgress.targetIp}): ${adbProgress.progressPercent}%",
                        color = if (adbProgress.isError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!adbProgress.isRunning) {
                        Text(
                            text = "✕ Đóng",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onDismissAdbProgress)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = adbProgress.statusMessage,
                    color = TextPrimary,
                    fontSize = 11.sp
                )
                if (adbProgress.isRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { adbProgress.progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = ElectricCyan,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Tab Body
        when (activeTab) {
            0 -> DevicesAndApiTab(
                managedTvs = managedTvs,
                selectedTvIp = selectedTvIp,
                isScanningTvs = isScanningTvs,
                savedGeminiKey = savedGeminiKey,
                discoveredGeminiModels = discoveredGeminiModels,
                selectedModelId = localSubtitleConfig.geminiModelId,
                onScanLan = onScanLan,
                onAddManualTvIp = onAddManualTvIp,
                onSelectTv = { ip ->
                    onSelectTv(ip)
                    onSetControlTargetMode(ControlTargetMode.REMOTE_TV)
                },
                onRequestRename = { dev ->
                    renameTargetDevice = dev
                    renameText = dev.name
                },
                onRemoveTv = onRemoveTv,
                onInstallAppToTv = onInstallAppToTv,
                onRequestUninstall = { dev -> uninstallConfirmDevice = dev },
                onLaunchAppOnTv = onLaunchAppOnTv,
                onSaveGeminiKey = onSaveGeminiKey,
                onSyncKeyToTvNow = onSyncKeyToTvNow,
                onWipeGeminiKey = onWipeGeminiKey,
                onRefreshGeminiModels = onRefreshGeminiModels
            )

            1 -> RemoteControlTab(
                selectedTv = selectedTv,
                remoteState = remoteState,
                onSelectDeviceTab = { activeTab = 0 },
                onOpenSubTab = { activeTab = 2 },
                onOpenStreamTab = { activeTab = 3 },
                onGoPickMoviesForTv = onGoPickMoviesForTv,
                onSendTouchKey = onSendTouchKey,
                onSendPlayerCmd = onSendPlayerCmd,
                onSendSearchToTv = onSendSearchToTv,
                onPushDirectStreamToTv = onPushDirectStreamToTv
            )

            2 -> RemoteSubtitleStudioTab(
                selectedTv = selectedTv,
                isTvOnline = remoteState.online,
                config = effectiveSubStyle,
                onPushSubtitleFileToTv = onPushSubtitleFileToTv,
                onSendPlayerCmd = onSendPlayerCmd,
                onUpdateRemoteSubStyle = onUpdateRemoteSubStyle,
                onResetRemoteSubStyleDefault = onResetRemoteSubStyleDefault,
                onBeforeLaunchSystemOverlay = onBeforeLaunchSystemOverlay,
                onAfterSystemOverlayResult = onAfterSystemOverlayResult
            )

            3 -> TvScreenStreamTab(
                selectedTv = selectedTv,
                frameBytes = tvScreenFrameBytes,
                onSelectDeviceTab = { activeTab = 0 },
                onSendTap = onSendTap,
                onSendSwipe = onSendSwipe,
                onSendTouchKey = onSendTouchKey,
                onEnterFullscreen = {
                    isStreamFullscreen = true
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            )
        }
    }

    // Rename TV Dialog
    val devToRename = renameTargetDevice
    if (devToRename != null) {
        AlertDialog(
            onDismissRequest = { renameTargetDevice = null },
            containerColor = CinemaSurface,
            titleContentColor = ThaiGold,
            title = { Text("Đổi Tên Gợi Nhớ TV (${devToRename.ip})", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("VD: TV Phòng Khách, TV Phòng Ngủ...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameTv(devToRename.ip, renameText)
                        renameTargetDevice = null
                    }
                ) {
                    Text("Lưu Tên", color = ThaiGold, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetDevice = null }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }

    // Confirm Uninstall App From TV Dialog
    val devToUninstall = uninstallConfirmDevice
    if (devToUninstall != null) {
        AlertDialog(
            onDismissRequest = { uninstallConfirmDevice = null },
            containerColor = CinemaSurface,
            titleContentColor = Color(0xFFFCA5A5),
            title = { Text("Gỡ Phyll Cinema Khỏi TV?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Ứng dụng com.phyll.filmhub sẽ được gỡ cài đặt hoàn toàn khỏi TV \"${devToUninstall.displayTitle}\" (${devToUninstall.ip}) qua kết nối ADB :5555.\n\nAPI Key trên điện thoại vẫn được giữ nguyên.",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstallAppFromTv(devToUninstall.ip)
                        uninstallConfirmDevice = null
                    }
                ) {
                    Text("Gỡ Cài Đặt Ngay", color = LiveRed, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallConfirmDevice = null }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevicesAndApiTab(
    managedTvs: List<ManagedTvDevice>,
    selectedTvIp: String?,
    isScanningTvs: Boolean,
    savedGeminiKey: String,
    discoveredGeminiModels: List<String>,
    selectedModelId: String,
    onScanLan: () -> Unit,
    onAddManualTvIp: (String, String) -> Unit,
    onSelectTv: (String) -> Unit,
    onRequestRename: (ManagedTvDevice) -> Unit,
    onRemoveTv: (String) -> Unit,
    onInstallAppToTv: (String) -> Unit,
    onRequestUninstall: (ManagedTvDevice) -> Unit,
    onLaunchAppOnTv: (String) -> Unit,
    onSaveGeminiKey: (String, String) -> Unit,
    onSyncKeyToTvNow: () -> Unit,
    onWipeGeminiKey: () -> Unit,
    onRefreshGeminiModels: () -> Unit
) {
    var apiKeyInput by remember(savedGeminiKey) { mutableStateOf(savedGeminiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var customModelInput by remember(selectedModelId) { mutableStateOf(selectedModelId) }
    var manualIpInput by remember { mutableStateOf("") }
    var manualNameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Persistent Android Phone Gemini API Key Vault
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaSurface)
                .border(1.dp, ThaiGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = ThaiGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔐 Kho Lưu API Key Tại App Android",
                        color = ThaiGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (savedGeminiKey.isNotBlank()) Color(0xFF064E3B) else Color(0xFF334155))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (savedGeminiKey.isNotBlank()) "🟢 ĐÃ LƯU CỐ ĐỊNH" else "Chưa nhập Key",
                        color = if (savedGeminiKey.isNotBlank()) Color(0xFF6EE7B7) else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "API Key được lưu cố định trên máy Android này (không mất khi thoát app) và TỰ ĐỘNG đồng bộ sang TV bất kỳ khi bạn chọn TV hoặc xem trực tiếp trên ĐT.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("Gemini API Key (AIza...)") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (showApiKey) "ẨN" else "HIỆN",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showApiKey = !showApiKey }
                            .padding(horizontal = 8.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThaiGold,
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThaiGold)
                        .clickable { onSaveGeminiKey(apiKeyInput, customModelInput) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💾 Lưu Key Cố Định", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (selectedTvIp != null && savedGeminiKey.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricCyan.copy(alpha = 0.2f))
                            .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                            .clickable(onClick = onSyncKeyToTvNow)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚀 Đồng Bộ TV Ngay", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (savedGeminiKey.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LiveRed.copy(alpha = 0.22f))
                            .border(1.dp, LiveRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable {
                                apiKeyInput = ""
                                onWipeGeminiKey()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Xóa", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Gemini Model Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Model AI đang dùng: $selectedModelId", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "🔄 Tải Model Thực",
                    color = ThaiGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onRefreshGeminiModels)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                discoveredGeminiModels.take(8).forEach { modelId ->
                    val selected = modelId == selectedModelId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) ElectricCyan else CinemaSurfaceElevated)
                            .clickable {
                                customModelInput = modelId
                                onSaveGeminiKey(apiKeyInput, modelId)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = modelId,
                            color = if (selected) CinemaObsidian else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Card 2: Multi-TV Manager & Wi-Fi ADB Install / Uninstall
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaSurface)
                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📺 Thiết Bị TV Trong Nhà (${managedTvs.size})",
                    color = ElectricCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan)
                        .clickable(enabled = !isScanningTvs, onClick = onScanLan)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanningTvs) {
                            CircularProgressIndicator(
                                color = CinemaObsidian,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isScanningTvs) "Đang quét..." else "Quét Wi-Fi LAN",
                            color = CinemaObsidian,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Add manual TV IP row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualIpInput,
                    onValueChange = { manualIpInput = it },
                    placeholder = { Text("IP TV (VD: 192.168.102.14)", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = manualNameInput,
                    onValueChange = { manualNameInput = it },
                    placeholder = { Text("Tên phòng", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThaiGold)
                        .clickable {
                            onAddManualTvIp(manualIpInput, manualNameInput)
                            manualIpInput = ""
                            manualNameInput = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Thêm TV", tint = CinemaObsidian, modifier = Modifier.size(20.dp))
                }
            }

            if (managedTvs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bấm \"Quét Wi-Fi LAN\" để tự tìm các TV mở ADB (:5555) hoặc TV đã cài Phyll Cinema (:8899), hoặc gõ IP TV vào ô trên.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                managedTvs.forEach { dev ->
                    val isSelected = dev.ip == selectedTvIp
                    ManagedTvCard(
                        device = dev,
                        isSelected = isSelected,
                        onSelect = { onSelectTv(dev.ip) },
                        onRename = { onRequestRename(dev) },
                        onInstall = { onInstallAppToTv(dev.ip) },
                        onLaunch = { onLaunchAppOnTv(dev.ip) },
                        onUninstall = { onRequestUninstall(dev) },
                        onRemove = { onRemoveTv(dev.ip) }
                    )
                }
            }

            Text(
                text = "💡 Mẹo Cài/Gỡ từ xa: Trên TV Box hoặc Android TV, bật \"Gỡ lỗi USB / Gỡ lỗi qua mạng (ADB)\" trong Cài đặt Nhà phát triển. Khi cài lần đầu, TV có thể hỏi \"Cho phép gỡ lỗi\" -> bấm OK trên TV 1 lần duy nhất.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagedTvCard(
    device: ManagedTvDevice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF102033) else CinemaSurfaceElevated)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ElectricCyan else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isSelected) ElectricCyan else ThaiGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.displayTitle,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Đổi tên",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable(onClick = onRename)
                        )
                    }
                    Text(
                        text = "IP: ${device.ip}" + if (device.appVersion.isNotBlank()) " • v${device.appVersion}" else "",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(ElectricCyan)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("✓ ĐANG CHỌN", color = CinemaObsidian, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // Live Status Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                text = when {
                    device.adbOnline && device.adbNeedsAuth -> "🔑 ADB :5555 (Bấm Cho Phép trên TV)"
                    device.adbOnline -> "● ADB :5555 Sẵn sàng"
                    else -> "○ ADB Offline"
                },
                bg = when {
                    device.adbOnline && device.adbNeedsAuth -> Color(0xFF33260C)
                    device.adbOnline -> Color(0xFF064E3B)
                    else -> Color(0xFF1E293B)
                },
                fg = when {
                    device.adbOnline && device.adbNeedsAuth -> ThaiGold
                    device.adbOnline -> Color(0xFF6EE7B7)
                    else -> TextSecondary
                }
            )
            StatusPill(
                text = when {
                    device.companionOnline -> "● App TV Đang Mở (${device.activeScreen})"
                    device.appInstalled -> "● Đã Cài App (Đang Tắt)"
                    else -> "○ Chưa Cài App Trên TV"
                },
                bg = when {
                    device.companionOnline -> Color(0xFF0E3746)
                    device.appInstalled -> Color(0xFF33260C)
                    else -> Color(0xFF281921)
                },
                fg = when {
                    device.companionOnline -> ElectricCyan
                    device.appInstalled -> ThaiGold
                    else -> Color(0xFFFCA5A5)
                }
            )
            if (device.hasGeminiKeyOnTv) {
                StatusPill("🔐 TV Đã Nhận Key AI", Color(0xFF064E3B), Color(0xFF6EE7B7))
            }
            if (device.nowPlayingTitle.isNotBlank()) {
                StatusPill("▶ ${device.nowPlayingTitle}", Color(0xFF2B2108), ThaiGold)
            }
        }

        // Actions Row: Select, Install/Update APK, Launch App, Uninstall App, Remove
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isSelected) {
                ActionChipButton(
                    icon = Icons.Default.CheckCircle,
                    label = "Chọn Kết Nối TV Này",
                    bg = ElectricCyan,
                    fg = CinemaObsidian,
                    onClick = onSelect
                )
            }
            if (device.adbOnline) {
                ActionChipButton(
                    icon = Icons.Default.InstallMobile,
                    label = if (device.appInstalled) "Cập Nhật APK Lên TV" else "Cài App Lên TV",
                    bg = ThaiGold,
                    fg = CinemaObsidian,
                    onClick = onInstall
                )
                ActionChipButton(
                    icon = Icons.Default.RocketLaunch,
                    label = if (device.companionOnline) "Nạp Lại App TV" else "Mở App TV",
                    bg = CinemaSurface,
                    fg = ElectricCyan,
                    onClick = onLaunch
                )
                if (device.appInstalled) {
                    ActionChipButton(
                        icon = Icons.Default.DeleteForever,
                        label = "Gỡ App Khỏi TV",
                        bg = LiveRed.copy(alpha = 0.18f),
                        fg = Color(0xFFFCA5A5),
                        onClick = onUninstall
                    )
                }
            }
            ActionChipButton(
                icon = Icons.Default.Refresh,
                label = "Xóa khỏi DS",
                bg = CinemaSurface,
                fg = TextSecondary,
                onClick = onRemove
            )
        }
    }
}

@Composable
private fun RemoteControlTab(
    selectedTv: ManagedTvDevice?,
    remoteState: RemoteTvLiveState,
    onSelectDeviceTab: () -> Unit,
    onOpenSubTab: () -> Unit,
    onOpenStreamTab: () -> Unit,
    onGoPickMoviesForTv: () -> Unit,
    onSendTouchKey: (String) -> Unit,
    onSendPlayerCmd: (String, Long) -> Unit,
    onSendSearchToTv: (String) -> Unit,
    onPushDirectStreamToTv: (String, String) -> Unit
) {
    var searchTvText by remember { mutableStateOf("") }
    var directUrlTitle by remember { mutableStateOf("") }
    var directUrlLink by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedTv == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CinemaSurface)
                    .clickable(onClick = onSelectDeviceTab)
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠️ Chưa chọn TV nào. Chạm để chọn hoặc cài App lên TV tại Tab 📡 TV & Cài App.",
                    color = ThaiGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        // Now Playing / Live TV Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaSurface)
                .border(1.dp, ThaiGold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (remoteState.playerActive) "🎬 Đang phát trên ${selectedTv.displayTitle}"
                    else "📺 Màn hình TV: ${remoteState.screen}",
                    color = ThaiGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (remoteState.online) "🟢 Online" else "🔴 Offline",
                    color = if (remoteState.online) SuccessGreen else LiveRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (remoteState.playerActive) {
                Text(
                    text = "${remoteState.movieTitle} • ${remoteState.episodeName}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (remoteState.statusBadge.isNotBlank()) {
                    Text(text = remoteState.statusBadge, color = ElectricCyan, fontSize = 11.sp)
                }

                // Live subtitle text currently showing on TV
                if (remoteState.primaryThaiText.isNotBlank() || remoteState.secondaryText.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (remoteState.primaryThaiText.isNotBlank()) {
                            Text(remoteState.primaryThaiText, color = ThaiGold, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        if (remoteState.secondaryText.isNotBlank()) {
                            Text(remoteState.secondaryText, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                // Seekbar slider
                val dur = remoteState.durationMs.coerceAtLeast(1L)
                var draggingRatio by remember { mutableStateOf<Float?>(null) }
                val currentRatio = draggingRatio ?: (remoteState.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                Slider(
                    value = currentRatio,
                    onValueChange = { draggingRatio = it },
                    onValueChangeFinished = {
                        val r = draggingRatio ?: return@Slider
                        draggingRatio = null
                        onSendPlayerCmd("seek_to", (r * dur).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ThaiGold,
                        activeTrackColor = ThaiGold,
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDurationMs((currentRatio * dur).toLong()), color = TextSecondary, fontSize = 11.sp)
                    Text("Lệch Sub: ${remoteState.syncOffsetMs}ms • ${"%.2fx".format(remoteState.subSpeedScale)}", color = ElectricCyan, fontSize = 11.sp)
                    Text(formatDurationMs(dur), color = TextSecondary, fontSize = 11.sp)
                }

                // Transport buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallRemoteButton("-30s", Modifier.weight(1f)) { onSendPlayerCmd("seek_delta", -30_000L) }
                    SmallRemoteButton("-10s", Modifier.weight(1f)) { onSendPlayerCmd("seek_delta", -10_000L) }
                    SmallRemoteButton(
                        text = if (remoteState.isPlaying) "⏸ Dừng" else "▶ Phát",
                        modifier = Modifier.weight(1.4f),
                        bg = ThaiGold,
                        fg = CinemaObsidian
                    ) { onSendPlayerCmd("toggle_play", 0L) }
                    SmallRemoteButton("+10s", Modifier.weight(1f)) { onSendPlayerCmd("seek_delta", 10_000L) }
                    SmallRemoteButton("+30s", Modifier.weight(1f)) { onSendPlayerCmd("seek_delta", 30_000L) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallRemoteButton("💬 Chỉnh Sub Studio", Modifier.weight(1f), bg = ElectricCyan, fg = CinemaObsidian, onClick = onOpenSubTab)
                    SmallRemoteButton("🚀 Khớp 3s TV", Modifier.weight(1f)) { onSendPlayerCmd("open_quick_snap", 0L) }
                    SmallRemoteButton("🤖 AI Tạo Sub", Modifier.weight(1f)) { onSendPlayerCmd("trigger_ai_sub", 0L) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ThaiGold)
                            .clickable(onClick = onGoPickMoviesForTv)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎬 Chọn Phim Phát Lên TV", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricCyan.copy(alpha = 0.2f))
                            .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenStreamTab)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📺 Xem Màn Hình TV", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Full Virtual D-Pad Remote
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaSurface)
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🎛 Remote Điều Khiển TV Ảo", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DPadKeyBox("⬅ Quay lại", Modifier.width(105.dp)) { onSendTouchKey("BACK") }
                DPadKeyBox("▲ LÊN", Modifier.width(90.dp), bg = Color(0xFF273449)) { onSendTouchKey("UP") }
                DPadKeyBox("🏠 Trang chủ", Modifier.width(105.dp)) { onSendTouchKey("HOME") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DPadKeyBox("◄ TRÁI", Modifier.width(95.dp), bg = Color(0xFF273449)) { onSendTouchKey("LEFT") }
                DPadKeyBox("OK / CHỌN", Modifier.width(100.dp), bg = ThaiGold, fg = CinemaObsidian) { onSendTouchKey("OK") }
                DPadKeyBox("► PHẢI", Modifier.width(95.dp), bg = Color(0xFF273449)) { onSendTouchKey("RIGHT") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DPadKeyBox("🔉 Âm lượng -", Modifier.width(105.dp)) { onSendTouchKey("VOL_DOWN") }
                DPadKeyBox("▼ XUỐNG", Modifier.width(90.dp), bg = Color(0xFF273449)) { onSendTouchKey("DOWN") }
                DPadKeyBox("🔊 Âm lượng +", Modifier.width(105.dp)) { onSendTouchKey("VOL_UP") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallRemoteButton("⚙ Cài đặt TV", Modifier.weight(1f)) { onSendTouchKey("SETTINGS") }
                SmallRemoteButton("🔄 Đổi Giao diện ĐT↔TV", Modifier.weight(1.3f)) { onSendTouchKey("TOGGLE_UI") }
                SmallRemoteButton("💬 Mở Bảng Sub TV", Modifier.weight(1.2f)) { onSendPlayerCmd("open_sub_drawer", 0L) }
            }
        }

        // Quick Search & Direct Stream Push to TV
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CinemaSurface)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🔍 Gõ Tìm Phim & Bắn Link Trực Tiếp Lên TV", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchTvText,
                    onValueChange = { searchTvText = it },
                    placeholder = { Text("Tên phim tiếng Việt / Thái / Anh...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan)
                        .clickable {
                            if (searchTvText.isNotBlank()) onSendSearchToTv(searchTvText)
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text("Tìm TV", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            OutlinedTextField(
                value = directUrlTitle,
                onValueChange = { directUrlTitle = it },
                placeholder = { Text("Tên hiển thị (VD: Phim Thái Tập 1)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThaiGold,
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = directUrlLink,
                    onValueChange = { directUrlLink = it },
                    placeholder = { Text("https://youtu.be/... hoặc .m3u8 / .mp4") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThaiGold,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThaiGold)
                        .clickable {
                            if (directUrlLink.isNotBlank()) {
                                onPushDirectStreamToTv(directUrlTitle, directUrlLink)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text("Phát TV", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteSubtitleStudioTab(
    selectedTv: ManagedTvDevice?,
    isTvOnline: Boolean,
    config: SubtitleStyleConfig,
    onPushSubtitleFileToTv: (String, ByteArray, String) -> Unit,
    onSendPlayerCmd: (String, Long) -> Unit,
    onUpdateRemoteSubStyle: (JSONObject, ((SubtitleStyleConfig) -> SubtitleStyleConfig)?) -> Unit,
    onResetRemoteSubStyleDefault: () -> Unit,
    onBeforeLaunchSystemOverlay: () -> Unit,
    onAfterSystemOverlayResult: () -> Unit
) {
    val context = LocalContext.current
    var pickLang by remember { mutableStateOf("th") }

    val subFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onAfterSystemOverlayResult()
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "phone_sub.srt"
                onPushSubtitleFileToTv(fileName, bytes, pickLang)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (selectedTv != null && isTvOnline) {
                "🟢 Đang điều chỉnh phụ đề trực tiếp trên TV: ${selectedTv.displayTitle}"
            } else {
                "🟡 Chưa kết nối TV online — thay đổi sẽ lưu sẵn trên ĐT và tự đồng bộ khi TV bật"
            },
            color = if (isTvOnline) SuccessGreen else ThaiGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        LiveSubtitlePreviewBanner(config = config)

        // 1. ALL (Primary + Secondary simultaneously)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CinemaSurface)
                .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🔗 1. Chỉnh Tất Cả (Sub Chính + Sub Phụ)", color = Color(0xFF6EE7B7), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${config.fontSizeSp.toInt()}sp / ${config.secondaryFontSizeSp.toInt()}sp", color = ThaiGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallRemoteButton("A- Tất Cả (-2sp)", Modifier.weight(1f)) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "ALL").put("sizeDeltaSp", -2.0),
                        { cfg ->
                            cfg.copy(
                                linkPrimarySecondaryStyle = true,
                                fontSizeSp = (cfg.fontSizeSp - 2f).coerceIn(12f, 52f),
                                secondaryFontSizeSp = (cfg.secondaryFontSizeSp - 2f).coerceIn(10f, 48f)
                            )
                        }
                    )
                }
                SmallRemoteButton("A+ Tất Cả (+2sp)", Modifier.weight(1f), bg = ThaiGold, fg = CinemaObsidian) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "ALL").put("sizeDeltaSp", 2.0),
                        { cfg ->
                            cfg.copy(
                                linkPrimarySecondaryStyle = true,
                                fontSizeSp = (cfg.fontSizeSp + 2f).coerceIn(12f, 52f),
                                secondaryFontSizeSp = (cfg.secondaryFontSizeSp + 2f).coerceIn(10f, 48f)
                            )
                        }
                    )
                }
                SmallRemoteButton("↺ Mặc Định", Modifier.weight(0.9f), bg = LiveRed.copy(alpha = 0.2f), fg = Color(0xFFFCA5A5), onClick = onResetRemoteSubStyleDefault)
            }
            SubColorSwatchesRow(selected = config.primaryColor) { chosen ->
                onUpdateRemoteSubStyle(
                    JSONObject().put("target", "ALL").put("color", chosen.name),
                    { cfg -> cfg.copy(linkPrimarySecondaryStyle = true, primaryColor = chosen, secondaryColor = chosen) }
                )
            }
        }

        // 2. PRIMARY (Thai)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CinemaSurface)
                .border(1.dp, ThaiGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🇹🇭 2. Chỉnh Riêng Sub Chính (Dòng 1)", color = ThaiGold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${config.fontSizeSp.toInt()}sp", color = ThaiGold, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallRemoteButton("-2sp", Modifier.width(64.dp)) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "PRIMARY").put("sizeDeltaSp", -2.0),
                        { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, fontSizeSp = (cfg.fontSizeSp - 2f).coerceIn(12f, 52f)) }
                    )
                }
                Slider(
                    value = config.fontSizeSp,
                    onValueChange = { sz ->
                        onUpdateRemoteSubStyle(
                            JSONObject().put("target", "PRIMARY").put("fontSizeSp", sz.toDouble()),
                            { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, fontSizeSp = sz) }
                        )
                    },
                    valueRange = 12f..48f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ThaiGold, activeTrackColor = ThaiGold)
                )
                SmallRemoteButton("+2sp", Modifier.width(64.dp), bg = ThaiGold, fg = CinemaObsidian) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "PRIMARY").put("sizeDeltaSp", 2.0),
                        { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, fontSizeSp = (cfg.fontSizeSp + 2f).coerceIn(12f, 52f)) }
                    )
                }
            }
            SubColorSwatchesRow(selected = config.primaryColor) { chosen ->
                onUpdateRemoteSubStyle(
                    JSONObject().put("target", "PRIMARY").put("color", chosen.name),
                    { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, primaryColor = chosen) }
                )
            }
        }

        // 3. SECONDARY (Vi/En)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CinemaSurface)
                .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🇻🇳 3. Chỉnh Riêng Sub Phụ (Dòng 2)", color = ElectricCyan, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (config.dualSubEnabled) ElectricCyan else CinemaSurfaceElevated)
                        .clickable {
                            val nextDual = !config.dualSubEnabled
                            onUpdateRemoteSubStyle(
                                JSONObject().put("target", "SECONDARY").put("dualSubEnabled", nextDual),
                                { cfg -> cfg.copy(dualSubEnabled = nextDual) }
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (config.dualSubEnabled) "Sub Phụ: BẬT" else "Sub Phụ: TẮT",
                        color = if (config.dualSubEnabled) CinemaObsidian else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallRemoteButton("-2sp", Modifier.width(64.dp)) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "SECONDARY").put("sizeDeltaSp", -2.0),
                        { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, secondaryFontSizeSp = (cfg.secondaryFontSizeSp - 2f).coerceIn(10f, 44f)) }
                    )
                }
                Slider(
                    value = config.secondaryFontSizeSp,
                    onValueChange = { sz ->
                        onUpdateRemoteSubStyle(
                            JSONObject().put("target", "SECONDARY").put("secondaryFontSizeSp", sz.toDouble()),
                            { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, secondaryFontSizeSp = sz) }
                        )
                    },
                    valueRange = 10f..44f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                )
                SmallRemoteButton("+2sp", Modifier.width(64.dp), bg = ElectricCyan, fg = CinemaObsidian) {
                    onUpdateRemoteSubStyle(
                        JSONObject().put("target", "SECONDARY").put("sizeDeltaSp", 2.0),
                        { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, secondaryFontSizeSp = (cfg.secondaryFontSizeSp + 2f).coerceIn(10f, 44f)) }
                    )
                }
            }
            SubColorSwatchesRow(selected = config.secondaryColor) { chosen ->
                onUpdateRemoteSubStyle(
                    JSONObject().put("target", "SECONDARY").put("color", chosen.name),
                    { cfg -> cfg.copy(linkPrimarySecondaryStyle = false, secondaryColor = chosen) }
                )
            }
        }

        // 4. Sync, Frame, & Push Local .SRT File from Phone to TV
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CinemaSurface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⏱ 4. Khớp Giờ, Khung Nền & Bắn File Sub .SRT Từ Điện Thoại", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SmallRemoteButton("-2s", Modifier.weight(1f)) { onSendPlayerCmd("nudge_sub", -2000L) }
                SmallRemoteButton("-0.5s", Modifier.weight(1f)) { onSendPlayerCmd("nudge_sub", -500L) }
                SmallRemoteButton("0.0s", Modifier.weight(0.9f)) { onSendPlayerCmd("reset_sync", 0L) }
                SmallRemoteButton("+0.5s", Modifier.weight(1f)) { onSendPlayerCmd("nudge_sub", 500L) }
                SmallRemoteButton("+2s", Modifier.weight(1f)) { onSendPlayerCmd("nudge_sub", 2000L) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SubtitleEdgeStyle.entries.forEach { edge ->
                    val label = when (edge) {
                        SubtitleEdgeStyle.BACKGROUND_BOX -> "Khung Mờ"
                        SubtitleEdgeStyle.OUTLINE -> "Viền Đen"
                        SubtitleEdgeStyle.DROP_SHADOW -> "Đổ Bóng"
                        SubtitleEdgeStyle.NONE -> "Trong Suốt"
                    }
                    SmallRemoteButton(
                        text = label,
                        modifier = Modifier.weight(1f),
                        bg = if (config.edgeStyle == edge) ThaiGold else CinemaSurfaceElevated,
                        fg = if (config.edgeStyle == edge) CinemaObsidian else TextPrimary
                    ) {
                        onUpdateRemoteSubStyle(
                            JSONObject().put("edgeStyle", edge.name),
                            { cfg -> cfg.copy(edgeStyle = edge) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallRemoteButton("⬇ Hạ Sub", Modifier.weight(1f)) {
                    val nextOff = (config.bottomOffsetDp - 6f).coerceIn(4f, 180f)
                    onUpdateRemoteSubStyle(
                        JSONObject().put("bottomOffsetDp", nextOff.toDouble()),
                        { cfg -> cfg.copy(bottomOffsetDp = nextOff) }
                    )
                }
                SmallRemoteButton("⬆ Nâng Sub", Modifier.weight(1f)) {
                    val nextOff = (config.bottomOffsetDp + 6f).coerceIn(4f, 180f)
                    onUpdateRemoteSubStyle(
                        JSONObject().put("bottomOffsetDp", nextOff.toDouble()),
                        { cfg -> cfg.copy(bottomOffsetDp = nextOff) }
                    )
                }
                SmallRemoteButton("🎯 AI Tự Khớp", Modifier.weight(1.2f), bg = ElectricCyan, fg = CinemaObsidian) {
                    onSendPlayerCmd("auto_sync", 0L)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan.copy(alpha = 0.2f))
                        .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                        .clickable {
                            onBeforeLaunchSystemOverlay()
                            subFilePicker.launch(arrayOf("*/*"))
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chọn File Sub (.SRT/.ASS) Bắn Lên TV", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CinemaSurfaceElevated)
                        .clickable { pickLang = if (pickLang == "th") "vi" else "th" }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (pickLang == "th") "Sub: ไทย" else "Sub: Việt→ไทย",
                        color = ThaiGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TvScreenStreamTab(
    selectedTv: ManagedTvDevice?,
    frameBytes: ByteArray?,
    onSelectDeviceTab: () -> Unit,
    onSendTap: (Float, Float) -> Unit,
    onSendSwipe: (Float, Float, Float, Float) -> Unit,
    onSendTouchKey: (String) -> Unit,
    onEnterFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedTv == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CinemaSurface)
                    .clickable(onClick = onSelectDeviceTab)
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Chạm để chọn TV muốn stream màn hình về điện thoại.", color = ThaiGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📺 Màn hình TV trực tiếp (Chạm & Vuốt điều khiển)",
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(ElectricCyan)
                    .clickable(onClick = onEnterFullscreen)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, tint = CinemaObsidian, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xoay Ngang Full", color = CinemaObsidian, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        TvTouchViewport(
            frameBytes = frameBytes,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .background(Color.Black),
            onSendTap = onSendTap,
            onSendSwipe = onSendSwipe
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallRemoteButton("⬅ Back", Modifier.weight(1f)) { onSendTouchKey("BACK") }
            SmallRemoteButton("◄ Trái", Modifier.weight(1f)) { onSendTouchKey("LEFT") }
            SmallRemoteButton("OK", Modifier.weight(1f), bg = ThaiGold, fg = CinemaObsidian) { onSendTouchKey("OK") }
            SmallRemoteButton("► Phải", Modifier.weight(1f)) { onSendTouchKey("RIGHT") }
            SmallRemoteButton("🏠 Home", Modifier.weight(1f)) { onSendTouchKey("HOME") }
        }
    }
}

@Composable
private fun FullscreenTvStreamOverlay(
    selectedTv: ManagedTvDevice?,
    frameBytes: ByteArray?,
    onSendTap: (Float, Float) -> Unit,
    onSendSwipe: (Float, Float, Float, Float) -> Unit,
    onSendTouchKey: (String) -> Unit,
    onExitFullscreen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TvTouchViewport(
            frameBytes = frameBytes,
            modifier = Modifier.fillMaxSize(),
            onSendTap = onSendTap,
            onSendSwipe = onSendSwipe
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, ThaiGold, RoundedCornerShape(99.dp))
                .clickable(onClick = onExitFullscreen)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FullscreenExit, contentDescription = null, tint = ThaiGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Thoát Full (${selectedTv?.name.orEmpty()})",
                color = ThaiGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallRemoteButton("⬅", Modifier.width(46.dp)) { onSendTouchKey("BACK") }
            SmallRemoteButton("◄", Modifier.width(46.dp)) { onSendTouchKey("LEFT") }
            SmallRemoteButton("▲", Modifier.width(46.dp)) { onSendTouchKey("UP") }
            SmallRemoteButton("OK", Modifier.width(52.dp), bg = ThaiGold, fg = CinemaObsidian) { onSendTouchKey("OK") }
            SmallRemoteButton("▼", Modifier.width(46.dp)) { onSendTouchKey("DOWN") }
            SmallRemoteButton("►", Modifier.width(46.dp)) { onSendTouchKey("RIGHT") }
            SmallRemoteButton("⏯", Modifier.width(52.dp), bg = ElectricCyan, fg = CinemaObsidian) { onSendTouchKey("PLAY_PAUSE") }
        }
    }
}

@Composable
private fun TvTouchViewport(
    frameBytes: ByteArray?,
    modifier: Modifier = Modifier,
    onSendTap: (Float, Float) -> Unit,
    onSendSwipe: (Float, Float, Float, Float) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmap = remember(frameBytes) {
        if (frameBytes != null && frameBytes.isNotEmpty()) {
            runCatching { BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)?.asImageBitmap() }.getOrNull()
        } else null
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                detectTapGestures { offset ->
                    if (boxSize.width > 0 && boxSize.height > 0) {
                        val nx = (offset.x / boxSize.width.toFloat()).coerceIn(0f, 1f)
                        val ny = (offset.y / boxSize.height.toFloat()).coerceIn(0f, 1f)
                        onSendTap(nx, ny)
                    }
                }
            }
            .pointerInput(boxSize) {
                var startX = 0f
                var startY = 0f
                var curX = 0f
                var curY = 0f
                detectDragGestures(
                    onDragStart = { start ->
                        startX = start.x
                        startY = start.y
                        curX = start.x
                        curY = start.y
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        curX += dragAmount.x
                        curY += dragAmount.y
                    },
                    onDragEnd = {
                        if (boxSize.width > 0 && boxSize.height > 0) {
                            onSendSwipe(
                                (startX / boxSize.width).coerceIn(0f, 1f),
                                (startY / boxSize.height).coerceIn(0f, 1f),
                                (curX / boxSize.width).coerceIn(0f, 1f),
                                (curY / boxSize.height).coerceIn(0f, 1f)
                            )
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Màn hình TV trực tiếp",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Đang kết nối luồng màn hình TV...",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubColorSwatchesRow(
    selected: SubtitleColorChoice,
    onPick: (SubtitleColorChoice) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubtitleColorChoice.entries.forEach { choice ->
            val active = choice == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (active) Color(0xFF283548) else CinemaSurfaceElevated)
                    .border(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) ThaiGold else Color(0xFF334155),
                        shape = RoundedCornerShape(99.dp)
                    )
                    .clickable { onPick(choice) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(choice.argb))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(choice.displayName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SmallRemoteButton(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = CinemaSurfaceElevated,
    fg: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DPadKeyBox(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = CinemaSurfaceElevated,
    fg: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1_000L).coerceAtLeast(0L)
    val h = totalSec / 3_600L
    val m = (totalSec % 3_600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
