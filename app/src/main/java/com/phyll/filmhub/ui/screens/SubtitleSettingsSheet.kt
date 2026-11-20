package com.phyll.filmhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.model.SecondarySubLang
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleFontChoice
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.SubtitleTrackOption
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import com.phyll.filmhub.ui.theme.tvFocusableClickable
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleSettingsSheet(
    config: SubtitleStyleConfig,
    availableTracks: List<SubtitleTrackOption>,
    selectedTrackId: String?,
    defaultSearchQuery: String,
    discoveredModels: List<String>,
    hasGeminiKeyInRam: Boolean,
    isAutoSyncing: Boolean = false,
    preferGeminiAudioForSeries: Boolean = false,
    totalSeriesEpisodes: Int = 1,
    nearbyCuesForSnap: List<SnapCueCandidate> = emptyList(),
    onSelectTrack: (SubtitleTrackOption) -> Unit,
    onSearchOnlineSub: (String) -> Unit,
    onSelectGeminiModel: (String) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onTriggerGeminiAudioSubNow: () -> Unit,
    onTogglePreferGeminiAudioForSeries: () -> Unit = {},
    onAutoSyncNow: () -> Unit = {},
    onOpenFastSnapModal: () -> Unit = {},
    onCycleNextSubRelease: () -> Unit = {},
    onSnapCueToCurrentTime: (SnapCueCandidate) -> Unit = {},
    onAdjustSyncManual: (Long, Float?) -> Unit = { _, _ -> },
    onUpdateConfig: ((SubtitleStyleConfig) -> SubtitleStyleConfig) -> Unit,
    onResetDefault: () -> Unit,
    onPickLocalSubFile: () -> Unit,
    onLoadSubFromUrl: (String) -> Unit,
    onOpenQrUpload: () -> Unit,
    onClose: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val firstRowFocusRequester = remember { FocusRequester() }
    var activeTab by remember { mutableIntStateOf(0) }
    var showUrlInput by remember { mutableStateOf(false) }
    var showOnlineSearchInput by remember { mutableStateOf(false) }
    var showCustomModelInput by remember { mutableStateOf(false) }
    var customSubUrl by remember { mutableStateOf("") }
    var customModelText by remember(config.geminiModelId) { mutableStateOf(config.geminiModelId) }
    var onlineSearchText by remember(defaultSearchQuery) { mutableStateOf(defaultSearchQuery) }

    // Always request focus on the first interactive control row when opened
    // so the physical TV Remote D-Pad works on the very first press even after a phone touch event!
    LaunchedEffect(Unit) {
        delay(40L)
        runCatching { firstRowFocusRequester.requestFocus() }
    }

    val colors = SubtitleColorChoice.entries

    fun cycleColor(current: SubtitleColorChoice, delta: Int): SubtitleColorChoice {
        val idx = colors.indexOf(current).coerceAtLeast(0)
        val nextIdx = (idx + delta + colors.size) % colors.size
        return colors[nextIdx]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (isTv) 0.58f else 0.92f)
                .background(CinemaObsidian.copy(alpha = 0.97f))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                .pointerInput(Unit) {
                    // Consume taps inside the drawer so tapping inside does not close it
                    detectTapGestures { }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💬 Cài Đặt Phụ Đề Nhanh (Remote ◄/► & Cảm Ứng)",
                        color = ThaiGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Dùng phím Lên/Xuống (▲/▼) chọn dòng • Trái/Phải (◄/►) hoặc chạm - / + để chỉnh ngay",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = ThaiGold,
                            unfocusedBorderColor = Color(0xFF475569),
                            onClick = onClose
                        )
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Đóng", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 2. Live Real-Time 2-Line Subtitle Preview Box right inside the drawer
            LiveSubtitlePreviewBanner(config = config)

            // 3. 4 Clean Category Tabs (1D Row)
            val tabs = listOf(
                0 to "🎨 Chữ & Màu",
                1 to "⏱ Khớp Giờ",
                2 to "📦 Khung & Vị Trí",
                3 to "🤖 Nguồn & AI"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEach { (tabIdx, tabTitle) ->
                    val isSelected = activeTab == tabIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = if (isSelected) ThaiGold else Color(0xFF334155),
                                onLeftKey = {
                                    activeTab = (activeTab - 1 + tabs.size) % tabs.size
                                },
                                onRightKey = {
                                    activeTab = (activeTab + 1) % tabs.size
                                },
                                onClick = { activeTab = tabIdx }
                            )
                            .background(if (isSelected) ThaiGold else CinemaSurfaceElevated)
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabTitle,
                            color = if (isSelected) CinemaObsidian else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 4. Scrollable 1D Control Rows per Tab
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // TAB 0: SIMULTANEOUS "ALL", "PRIMARY (THAI)", AND "SECONDARY (VI/EN)" CONTROLS
                        SectionHeaderLabel("🔗 CHỈNH NHANH TẤT CẢ (CẢ SUB CHÍNH + SUB PHỤ CÙNG LÚC)")

                        SubControlStepperRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            title = "🔗 Cỡ chữ Tất Cả (Chính + Phụ)",
                            subtitle = "Bấm ◄ / ► trên Remote hoặc chạm - / + để tăng giảm cả 2 dòng cùng lúc",
                            valueText = "Chính ${config.fontSizeSp.toInt()}sp • Phụ ${config.secondaryFontSizeSp.toInt()}sp",
                            leftButtonLabel = "-2sp",
                            rightButtonLabel = "+2sp",
                            accentColor = Color(0xFF10B981),
                            onStepLeft = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = true,
                                        fontSizeSp = (cfg.fontSizeSp - 2f).coerceIn(12f, 52f),
                                        secondaryFontSizeSp = (cfg.secondaryFontSizeSp - 2f).coerceIn(10f, 48f)
                                    )
                                }
                            },
                            onStepRight = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = true,
                                        fontSizeSp = (cfg.fontSizeSp + 2f).coerceIn(12f, 52f),
                                        secondaryFontSizeSp = (cfg.secondaryFontSizeSp + 2f).coerceIn(10f, 48f)
                                    )
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "🔗 Màu chữ Tất Cả (Chính + Phụ)",
                            subtitle = "Đổi màu đồng bộ cho cả dòng Tiếng Thái và dòng phụ",
                            valueText = config.primaryColor.displayName,
                            valueDotColor = Color(config.primaryColor.argb),
                            leftButtonLabel = "◄ Màu",
                            rightButtonLabel = "Màu ►",
                            accentColor = Color(0xFF10B981),
                            onStepLeft = {
                                val nextCol = cycleColor(config.primaryColor, -1)
                                onUpdateConfig {
                                    it.copy(
                                        linkPrimarySecondaryStyle = true,
                                        primaryColor = nextCol,
                                        secondaryColor = nextCol
                                    )
                                }
                            },
                            onStepRight = {
                                val nextCol = cycleColor(config.primaryColor, +1)
                                onUpdateConfig {
                                    it.copy(
                                        linkPrimarySecondaryStyle = true,
                                        primaryColor = nextCol,
                                        secondaryColor = nextCol
                                    )
                                }
                            }
                        )

                        SectionHeaderLabel("🇹🇭 CHỈNH RIÊNG SUB CHÍNH (DÒNG 1 - TIẾNG THÁI)")

                        SubControlStepperRow(
                            title = "🇹🇭 Cỡ chữ Sub Chính (ไทย)",
                            subtitle = "Chỉnh riêng kích thước dòng Tiếng Thái (12sp – 52sp)",
                            valueText = "${config.fontSizeSp.toInt()} sp",
                            leftButtonLabel = "-2sp",
                            rightButtonLabel = "+2sp",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = false,
                                        fontSizeSp = (cfg.fontSizeSp - 2f).coerceIn(12f, 52f)
                                    )
                                }
                            },
                            onStepRight = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = false,
                                        fontSizeSp = (cfg.fontSizeSp + 2f).coerceIn(12f, 52f)
                                    )
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "🎨 Màu chữ Sub Chính (ไทย)",
                            subtitle = "Bấm ◄ / ► trên Remote hoặc chọn nhanh ô màu bên dưới",
                            valueText = config.primaryColor.displayName,
                            valueDotColor = Color(config.primaryColor.argb),
                            leftButtonLabel = "◄ Trước",
                            rightButtonLabel = "Sau ►",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                val nextCol = cycleColor(config.primaryColor, -1)
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, primaryColor = nextCol)
                                }
                            },
                            onStepRight = {
                                val nextCol = cycleColor(config.primaryColor, +1)
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, primaryColor = nextCol)
                                }
                            }
                        )

                        ColorPaletteQuickRow(
                            selectedColor = config.primaryColor,
                            onPickColor = { picked ->
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, primaryColor = picked)
                                }
                            }
                        )

                        SectionHeaderLabel("🇻🇳 CHỈNH RIÊNG SUB PHỤ SONG NGỮ (DÒNG 2 - VIỆT / ANH)")

                        SubControlStepperRow(
                            title = "🈯 Hiển thị Sub Phụ (Dòng 2)",
                            subtitle = "Bật để xem 2 dòng song ngữ hoặc Tắt để chỉ hiện 1 dòng Tiếng Thái gọn gàng",
                            valueText = if (config.dualSubEnabled) "Đang BẬT (2 dòng)" else "Đang TẮT (1 dòng)",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = if (config.dualSubEnabled) "Tắt" else "Bật",
                            accentColor = ElectricCyan,
                            onStepLeft = { onUpdateConfig { it.copy(dualSubEnabled = !it.dualSubEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(dualSubEnabled = !it.dualSubEnabled) } }
                        )

                        SubControlStepperRow(
                            title = "🇻🇳 Cỡ chữ Sub Phụ (Việt / Anh)",
                            subtitle = "Chỉnh riêng kích thước dòng phụ bên dưới (10sp – 48sp)",
                            valueText = "${config.secondaryFontSizeSp.toInt()} sp",
                            leftButtonLabel = "-2sp",
                            rightButtonLabel = "+2sp",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = false,
                                        secondaryFontSizeSp = (cfg.secondaryFontSizeSp - 2f).coerceIn(10f, 48f)
                                    )
                                }
                            },
                            onStepRight = {
                                onUpdateConfig { cfg ->
                                    cfg.copy(
                                        linkPrimarySecondaryStyle = false,
                                        secondaryFontSizeSp = (cfg.secondaryFontSizeSp + 2f).coerceIn(10f, 48f)
                                    )
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "🎨 Màu chữ Sub Phụ (Việt / Anh)",
                            subtitle = "Chỉnh riêng màu sắc cho dòng phụ bên dưới",
                            valueText = config.secondaryColor.displayName,
                            valueDotColor = Color(config.secondaryColor.argb),
                            leftButtonLabel = "◄ Trước",
                            rightButtonLabel = "Sau ►",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                val nextCol = cycleColor(config.secondaryColor, -1)
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, secondaryColor = nextCol)
                                }
                            },
                            onStepRight = {
                                val nextCol = cycleColor(config.secondaryColor, +1)
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, secondaryColor = nextCol)
                                }
                            }
                        )

                        ColorPaletteQuickRow(
                            selectedColor = config.secondaryColor,
                            onPickColor = { picked ->
                                onUpdateConfig {
                                    it.copy(linkPrimarySecondaryStyle = false, secondaryColor = picked)
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "🌐 Ngôn ngữ Sub Phụ (Dòng 2)",
                            subtitle = "Chọn ngôn ngữ hiển thị ở dòng phụ bên dưới Tiếng Thái",
                            valueText = config.secondaryLang.displayName,
                            leftButtonLabel = "◄ Trước",
                            rightButtonLabel = "Sau ►",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                val langs = SecondarySubLang.entries
                                val nextIdx = (langs.indexOf(config.secondaryLang) - 1 + langs.size) % langs.size
                                onUpdateConfig { it.copy(secondaryLang = langs[nextIdx]) }
                            },
                            onStepRight = {
                                val langs = SecondarySubLang.entries
                                val nextIdx = (langs.indexOf(config.secondaryLang) + 1) % langs.size
                                onUpdateConfig { it.copy(secondaryLang = langs[nextIdx]) }
                            }
                        )

                        SubControlStepperRow(
                            title = "🅱 Chữ Đậm Rõ Nét (Bold)",
                            subtitle = "Tăng độ dày nét chữ giúp nhìn rõ từ xa trên TV",
                            valueText = if (config.boldText) "Đang BẬT" else "Đang TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = ThaiGold,
                            onStepLeft = { onUpdateConfig { it.copy(boldText = !it.boldText) } },
                            onStepRight = { onUpdateConfig { it.copy(boldText = !it.boldText) } }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.RestartAlt,
                            title = "↺ Khôi phục Mặc định Chuẩn Cinema (Chính 20sp Vàng • Phụ 15sp Trắng)",
                            subtitle = "Đặt lại cỡ chữ, màu sắc và khung nền gọn chuẩn ban đầu",
                            accentColor = ThaiGold,
                            onClick = onResetDefault
                        )
                    }

                    1 -> {
                        // TAB 1: SUBTITLE TIMING SYNC & SERIES MEMORY
                        val secStr = "%+.1fs".format(config.syncOffsetMs / 1000f)
                        val speedStr = "%.3fx".format(config.subSpeedScale)
                        val seriesNote = if (totalSeriesEpisodes > 1) " • Tự nhớ cho cả $totalSeriesEpisodes tập" else ""

                        SectionHeaderLabel("⏱ ĐỘ LỆCH HIỆN TẠI: $secStr • TỐC ĐỘ FPS: $speedStr$seriesNote")

                        FullWidthActionRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            icon = Icons.Default.Mic,
                            title = if (isAutoSyncing) {
                                "⏳ AI Đang Nghe Phim 2 Điểm & Tự Khớp..."
                            } else {
                                "🎯 AI Tự Nghe 2 Điểm Khớp Giờ + FPS (±4 Phút)"
                            },
                            subtitle = "Tự động nghe tiếng nhân vật trong phim để khớp phụ đề chính xác và lưu cho cả bộ",
                            accentColor = ElectricCyan,
                            onClick = { if (!isAutoSyncing) onAutoSyncNow() }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Search,
                            title = "⚡ Tạm Dừng & Chọn Câu Khớp 1 Chạm (Song Ngữ Thái - Việt)",
                            subtitle = "Mở danh sách câu thoại quanh cảnh hiện tại — bấm chọn đúng câu nhân vật vừa nói là khớp ngay",
                            accentColor = ThaiGold,
                            onClick = onOpenFastSnapModal
                        )

                        SubControlStepperRow(
                            title = "⏱ Tinh chỉnh Nhanh ±0.5 Giây",
                            subtitle = "Bấm ◄ (Sớm 0.5s) hoặc ► (Trễ 0.5s) trên Remote để dịch phụ đề khớp miệng nhân vật",
                            valueText = secStr,
                            leftButtonLabel = "⏪ -0.5s",
                            rightButtonLabel = "+0.5s ⏩",
                            accentColor = ElectricCyan,
                            onStepLeft = { onAdjustSyncManual(config.syncOffsetMs - 500L, null) },
                            onStepRight = { onAdjustSyncManual(config.syncOffsetMs + 500L, null) }
                        )

                        SubControlStepperRow(
                            title = "⏩ Tinh chỉnh Nhanh ±2.0 Giây",
                            subtitle = "Dịch nhanh 2 giây mỗi lần bấm ◄ / ►",
                            valueText = secStr,
                            leftButtonLabel = "⏪ -2s",
                            rightButtonLabel = "+2s ⏩",
                            accentColor = ThaiGold,
                            onStepLeft = { onAdjustSyncManual(config.syncOffsetMs - 2_000L, null) },
                            onStepRight = { onAdjustSyncManual(config.syncOffsetMs + 2_000L, null) }
                        )

                        SubControlStepperRow(
                            title = "🚀 Tinh chỉnh Nhanh ±10 Giây",
                            subtitle = "Dùng khi phụ đề lệch cả đoạn intro dài (10 giây mỗi lần bấm ◄ / ►)",
                            valueText = secStr,
                            leftButtonLabel = "⏪ -10s",
                            rightButtonLabel = "+10s ⏩",
                            accentColor = ThaiGold,
                            onStepLeft = { onAdjustSyncManual(config.syncOffsetMs - 10_000L, null) },
                            onStepRight = { onAdjustSyncManual(config.syncOffsetMs + 10_000L, null) }
                        )

                        val fpsPresets = listOf(
                            1.0f to "Chuẩn 1.000x",
                            0.95904f to "25→23.976fps (0.959x)",
                            1.04271f to "23.976→25fps (1.043x)"
                        )
                        val curFpsIdx = fpsPresets.indexOfFirst {
                            kotlin.math.abs(config.subSpeedScale - it.first) < 0.003f
                        }.let { if (it < 0) 0 else it }

                        SubControlStepperRow(
                            title = "🎞 Tốc độ Khung hình (Chống trôi Sub cuối tập)",
                            subtitle = "Khắc phục lỗi đầu tập khớp nhưng càng về cuối tập càng lệch dần",
                            valueText = fpsPresets[curFpsIdx].second,
                            leftButtonLabel = "◄ FPS",
                            rightButtonLabel = "FPS ►",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                val nextIdx = (curFpsIdx - 1 + fpsPresets.size) % fpsPresets.size
                                onAdjustSyncManual(config.syncOffsetMs, fpsPresets[nextIdx].first)
                            },
                            onStepRight = {
                                val nextIdx = (curFpsIdx + 1) % fpsPresets.size
                                onAdjustSyncManual(config.syncOffsetMs, fpsPresets[nextIdx].first)
                            }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.RestartAlt,
                            title = "↺ Đặt Lại Độ Lệch Về 0.0s (Chuẩn Mặc Định)",
                            subtitle = "Xóa độ lệch giờ và đưa tốc độ FPS về 1.000x",
                            accentColor = TextPrimary,
                            onClick = { onAdjustSyncManual(0L, 1.0f) }
                        )

                        if (availableTracks.count { !it.isEmbedded && it.externalRef != null } >= 2) {
                            FullWidthActionRow(
                                icon = Icons.Default.Refresh,
                                title = "🔄 Đổi Sang Bản Phụ Đề Khác (Release Tiếp Theo)",
                                subtitle = "Thử bản sub khác trong kho nếu bản hiện tại khác bản dựng phim",
                                accentColor = ElectricCyan,
                                onClick = onCycleNextSubRelease
                            )
                        }

                        if (totalSeriesEpisodes > 1) {
                            SubControlStepperRow(
                                title = "🎙️ Khóa Gemini Audio Cho Cả Bộ ($totalSeriesEpisodes Tập)",
                                subtitle = "Tự động dùng Gemini nghe tiếng phim tạo Sub Thái cho mọi tập tiếp theo",
                                valueText = if (preferGeminiAudioForSeries) "Đang BẬT" else "Đang TẮT",
                                leftButtonLabel = "Đổi",
                                rightButtonLabel = "Đổi",
                                accentColor = ThaiGold,
                                onStepLeft = onTogglePreferGeminiAudioForSeries,
                                onStepRight = onTogglePreferGeminiAudioForSeries
                            )
                        }
                    }

                    2 -> {
                        // TAB 2: SUBTITLE FRAME, OPACITY, POSITION, HARDSUB MASK & FONT
                        SectionHeaderLabel("📦 KHUNG NỀN CHỮ, ĐỘ MỜ & VỊ TRÍ TRÊN MÀN HÌNH")

                        val edges = SubtitleEdgeStyle.entries
                        SubControlStepperRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            title = "📦 Kiểu Khung Nền Phụ Đề",
                            subtitle = "Chọn khung đen mờ gọn gàng hoặc chỉ viền đen trong suốt",
                            valueText = config.edgeStyle.displayName,
                            leftButtonLabel = "◄ Kiểu",
                            rightButtonLabel = "Kiểu ►",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                val nextIdx = (edges.indexOf(config.edgeStyle) - 1 + edges.size) % edges.size
                                onUpdateConfig { it.copy(edgeStyle = edges[nextIdx]) }
                            },
                            onStepRight = {
                                val nextIdx = (edges.indexOf(config.edgeStyle) + 1) % edges.size
                                onUpdateConfig { it.copy(edgeStyle = edges[nextIdx]) }
                            }
                        )

                        SubControlStepperRow(
                            title = "🌑 Độ Đậm Khung Nền Đen",
                            subtitle = "Bấm ◄ để làm khung trong suốt hơn hoặc ► để làm khung đậm hơn (40% – 95%)",
                            valueText = "${(config.boxOpacity * 100).toInt()}%",
                            leftButtonLabel = "-10%",
                            rightButtonLabel = "+10%",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                onUpdateConfig {
                                    it.copy(
                                        edgeStyle = SubtitleEdgeStyle.BACKGROUND_BOX,
                                        boxOpacity = (it.boxOpacity - 0.10f).coerceIn(0.40f, 0.95f)
                                    )
                                }
                            },
                            onStepRight = {
                                onUpdateConfig {
                                    it.copy(
                                        edgeStyle = SubtitleEdgeStyle.BACKGROUND_BOX,
                                        boxOpacity = (it.boxOpacity + 0.10f).coerceIn(0.40f, 0.95f)
                                    )
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "↕ Vị Trí Phụ Đề Cao / Thấp",
                            subtitle = "Hạ thấp sát đáy màn hình hoặc nâng cao lên trên",
                            valueText = "${config.bottomOffsetDp.toInt()} dp",
                            leftButtonLabel = "Hạ thấp",
                            rightButtonLabel = "Nâng cao",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                onUpdateConfig {
                                    it.copy(bottomOffsetDp = (it.bottomOffsetDp - 6f).coerceIn(4f, 180f))
                                }
                            },
                            onStepRight = {
                                onUpdateConfig {
                                    it.copy(bottomOffsetDp = (it.bottomOffsetDp + 6f).coerceIn(4f, 180f))
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "🛡 Dải Đen Che Hardsub Dính Sẵn Trong Video",
                            subtitle = "Chỉ bật khi video gốc có chữ phụ đề cứng làm rối mắt",
                            valueText = if (config.hardsubMaskEnabled) "Đang BẬT" else "Đang TẮT (Gọn)",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = ElectricCyan,
                            onStepLeft = { onUpdateConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) } }
                        )

                        val fonts = SubtitleFontChoice.entries
                        SubControlStepperRow(
                            title = "🔤 Phông Chữ Tiếng Thái (Font)",
                            subtitle = "Đổi kiểu chữ Sarabun, Kanit, Cinema Serif hoặc Mono",
                            valueText = config.fontChoice.displayName,
                            leftButtonLabel = "◄ Font",
                            rightButtonLabel = "Font ►",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                val nextIdx = (fonts.indexOf(config.fontChoice) - 1 + fonts.size) % fonts.size
                                onUpdateConfig { it.copy(fontChoice = fonts[nextIdx]) }
                            },
                            onStepRight = {
                                val nextIdx = (fonts.indexOf(config.fontChoice) + 1) % fonts.size
                                onUpdateConfig { it.copy(fontChoice = fonts[nextIdx]) }
                            }
                        )

                        SubControlStepperRow(
                            title = "✅ Hiển Thị Phụ Đề Trên Màn Hình",
                            subtitle = "Bật hoặc tắt toàn bộ phụ đề",
                            valueText = if (config.subtitlesEnabled) "Đang BẬT" else "Đang TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = Color(0xFF10B981),
                            onStepLeft = { onUpdateConfig { it.copy(subtitlesEnabled = !it.subtitlesEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(subtitlesEnabled = !it.subtitlesEnabled) } }
                        )
                    }

                    3 -> {
                        // TAB 3: SUBTITLE TRACKS, GEMINI AUDIO SUB, AI TRANSLATION & ONLINE SEARCH
                        SectionHeaderLabel("🤖 NGUỒN PHỤ ĐỀ (${availableTracks.size} BẢN) & AI DỊCH TIẾNG THÁI")

                        FullWidthActionRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            icon = Icons.Default.Mic,
                            title = if (hasGeminiKeyInRam) {
                                "✨ Dùng Gemini (${config.geminiModelId}) Nghe Phim Tạo Sub ไทย Ngay"
                            } else {
                                "🔐 Quét QR Nạp Gemini Key Để AI Nghe Phim Tạo Sub ไทย"
                            },
                            subtitle = "AI nghe trực tiếp âm thanh gốc trong phim và tạo phụ đề Tiếng Thái khớp 100%",
                            accentColor = ThaiGold,
                            onClick = {
                                if (hasGeminiKeyInRam) onTriggerGeminiAudioSubNow() else onOpenQrUpload()
                            }
                        )

                        if (availableTracks.isNotEmpty()) {
                            val currentIdx = availableTracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
                            val currentTrack = availableTracks[currentIdx]
                            SubControlStepperRow(
                                title = "🎞 Bản Phụ Đề Đang Dùng (${currentIdx + 1}/${availableTracks.size})",
                                subtitle = "Bấm ◄ / ► để đổi nhanh giữa các bản phụ đề đã tải",
                                valueText = currentTrack.label,
                                leftButtonLabel = "◄ Bản trước",
                                rightButtonLabel = "Bản sau ►",
                                accentColor = ElectricCyan,
                                onStepLeft = {
                                    val prev = availableTracks[(currentIdx - 1 + availableTracks.size) % availableTracks.size]
                                    onSelectTrack(prev)
                                },
                                onStepRight = {
                                    val next = availableTracks[(currentIdx + 1) % availableTracks.size]
                                    onSelectTrack(next)
                                }
                            )
                        }

                        val modelChoices = remember(discoveredModels, config.geminiModelId) {
                            val set = LinkedHashSet<String>()
                            if (config.geminiModelId.isNotBlank()) set.add(config.geminiModelId)
                            discoveredModels.forEach { set.add(it) }
                            if (set.isEmpty()) set.add("gemini-2.5-flash")
                            set.toList()
                        }

                        SubControlStepperRow(
                            title = "🧠 Model Gemini AI Đang Chọn",
                            subtitle = if (hasGeminiKeyInRam) "🔐 Key RAM sẵn sàng • Bấm ◄ / ► để đổi Model" else "🔓 Chưa nạp Gemini Key trong RAM",
                            valueText = config.geminiModelId,
                            leftButtonLabel = "◄ Model",
                            rightButtonLabel = "Model ►",
                            accentColor = ElectricCyan,
                            onStepLeft = {
                                val cur = modelChoices.indexOf(config.geminiModelId).coerceAtLeast(0)
                                val nextModel = modelChoices[(cur - 1 + modelChoices.size) % modelChoices.size]
                                onSelectGeminiModel(nextModel)
                                onUpdateConfig {
                                    it.copy(geminiModelId = nextModel, translationEngine = TranslationEngineMode.GEMINI_AI)
                                }
                            },
                            onStepRight = {
                                val cur = modelChoices.indexOf(config.geminiModelId).coerceAtLeast(0)
                                val nextModel = modelChoices[(cur + 1) % modelChoices.size]
                                onSelectGeminiModel(nextModel)
                                onUpdateConfig {
                                    it.copy(geminiModelId = nextModel, translationEngine = TranslationEngineMode.GEMINI_AI)
                                }
                            }
                        )

                        SubControlStepperRow(
                            title = "⚡ Bộ Máy Dịch Sang Tiếng Thái",
                            subtitle = "Chọn giữa Neural AI siêu tốc hoặc Gemini AI ngữ cảnh sâu",
                            valueText = if (config.translationEngine == TranslationEngineMode.FAST_FREE) {
                                "⚡ Neural AI Multi-Batch"
                            } else {
                                "✨ Gemini AI (${config.geminiModelId})"
                            },
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = ThaiGold,
                            onStepLeft = {
                                val nextMode = if (config.translationEngine == TranslationEngineMode.FAST_FREE) {
                                    TranslationEngineMode.GEMINI_AI
                                } else {
                                    TranslationEngineMode.FAST_FREE
                                }
                                onUpdateConfig { it.copy(translationEngine = nextMode) }
                            },
                            onStepRight = {
                                val nextMode = if (config.translationEngine == TranslationEngineMode.FAST_FREE) {
                                    TranslationEngineMode.GEMINI_AI
                                } else {
                                    TranslationEngineMode.FAST_FREE
                                }
                                onUpdateConfig { it.copy(translationEngine = nextMode) }
                            }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.QrCode2,
                            title = "📱 Mở QR Kết Nối Điện Thoại (Nạp Key & Điều Khiển Sub)",
                            subtitle = "Quét mã QR để chỉnh phụ đề trực quan trên màn hình điện thoại",
                            accentColor = ElectricCyan,
                            onClick = onOpenQrUpload
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Search,
                            title = "🔍 Tìm Phụ Đề Online Theo Tên Phim / Mã IMDb",
                            subtitle = "Tìm trên OpenSubtitles & SubDL rồi tự dịch sang Tiếng Thái",
                            accentColor = ThaiGold,
                            onClick = { showOnlineSearchInput = !showOnlineSearchInput }
                        )

                        if (showOnlineSearchInput) {
                            OutlinedTextField(
                                value = onlineSearchText,
                                onValueChange = { onlineSearchText = it },
                                singleLine = true,
                                placeholder = { Text("Tên phim tiếng Anh hoặc mã IMDb (VD: tt15239678)", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ThaiGold,
                                    unfocusedBorderColor = Color(0xFF374151),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            FullWidthActionRow(
                                icon = Icons.Default.Search,
                                title = "✅ Bắt Đầu Tìm & Tải Phụ Đề Ngay",
                                subtitle = "Tìm với từ khóa: $onlineSearchText",
                                accentColor = ElectricCyan,
                                onClick = {
                                    if (onlineSearchText.isNotBlank()) {
                                        onSearchOnlineSub(onlineSearchText.trim())
                                        showOnlineSearchInput = false
                                    }
                                }
                            )
                        }

                        FullWidthActionRow(
                            icon = Icons.Default.FolderOpen,
                            title = "📂 Chọn File Phụ Đề (.SRT / .ASS) Từ USB / Bộ Nhớ Máy",
                            subtitle = "Nạp file phụ đề có sẵn trong thiết bị",
                            accentColor = TextPrimary,
                            onClick = onPickLocalSubFile
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Link,
                            title = "🔗 Nhập Link File Phụ Đề .SRT Trực Tiếp",
                            subtitle = "Dán URL file phụ đề từ web",
                            accentColor = TextPrimary,
                            onClick = { showUrlInput = !showUrlInput }
                        )

                        if (showUrlInput) {
                            OutlinedTextField(
                                value = customSubUrl,
                                onValueChange = { customSubUrl = it },
                                singleLine = true,
                                placeholder = { Text("https://.../sub_thai.srt", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ThaiGold,
                                    unfocusedBorderColor = Color(0xFF374151),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            FullWidthActionRow(
                                icon = Icons.Default.Link,
                                title = "✅ Tải & Áp Dụng Link Phụ Đề",
                                subtitle = customSubUrl.ifBlank { "Nhập URL ở trên" },
                                accentColor = ThaiGold,
                                onClick = {
                                    if (customSubUrl.isNotBlank()) {
                                        onLoadSubFromUrl(customSubUrl.trim())
                                        showUrlInput = false
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun LiveSubtitlePreviewBanner(config: SubtitleStyleConfig) {
    val bgAlpha = if (config.edgeStyle == SubtitleEdgeStyle.BACKGROUND_BOX) {
        config.boxOpacity.coerceIn(0.35f, 0.95f)
    } else {
        0.0f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = bgAlpha))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🇹🇭 ตัวอย่างซับไทย (${config.fontSizeSp.toInt()}sp • ${config.primaryColor.displayName})",
                color = Color(config.primaryColor.argb),
                fontSize = config.fontSizeSp.coerceIn(13f, 28f).sp,
                fontWeight = if (config.boldText) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (config.dualSubEnabled) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🇻🇳 Phụ đề phụ song ngữ (${config.secondaryFontSizeSp.toInt()}sp • ${config.secondaryColor.displayName})",
                    color = Color(config.secondaryColor.argb),
                    fontSize = config.secondaryFontSizeSp.coerceIn(11f, 22f).sp,
                    fontWeight = if (config.boldText) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderLabel(text: String) {
    Text(
        text = text,
        color = ThaiGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SubControlStepperRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    valueText: String,
    valueDotColor: Color? = null,
    leftButtonLabel: String,
    rightButtonLabel: String,
    accentColor: Color = ThaiGold,
    onStepLeft: () -> Unit,
    onStepRight: () -> Unit
) {
    val currentOnStepLeft by rememberUpdatedState(onStepLeft)
    val currentOnStepRight by rememberUpdatedState(onStepRight)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = ThaiGold,
                unfocusedBorderColor = Color(0xFF2D3748),
                focusedScale = 1.01f,
                onLeftKey = { currentOnStepLeft() },
                onRightKey = { currentOnStepRight() },
                onClick = { currentOnStepRight() }
            )
            .background(CinemaSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left (-) touch button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceElevated)
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { currentOnStepLeft() }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = leftButtonLabel,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Current Value Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (valueDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(valueDotColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = valueText,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }

            // Right (+) touch button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceElevated)
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { currentOnStepRight() }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rightButtonLabel,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ColorPaletteQuickRow(
    selectedColor: SubtitleColorChoice,
    onPickColor: (SubtitleColorChoice) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SubtitleColorChoice.entries.forEach { col ->
            val isSelected = col == selectedColor
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF1E293B) else CinemaSurfaceElevated)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) ThaiGold else Color(0xFF334155),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .pointerInput(col) {
                        detectTapGestures { onPickColor(col) }
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(col.argb))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = col.displayName.substringBefore(" "),
                    color = if (isSelected) ThaiGold else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FullWidthActionRow(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = ElectricCyan,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = ThaiGold,
                unfocusedBorderColor = accentColor.copy(alpha = 0.55f),
                focusedScale = 1.01f,
                onClick = onClick
            )
            .background(CinemaSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
