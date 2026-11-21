package com.phyll.filmhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.model.SnapCueCandidate
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleFontChoice
import com.phyll.filmhub.model.SubtitleLanguageCatalog
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.model.SubtitleTrackOption
import com.phyll.filmhub.model.TranslationEngineMode
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
import com.phyll.filmhub.ui.theme.BrandNeonGlassBorderBrush
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.VioletPrimary
import com.phyll.filmhub.ui.theme.VioletSoft
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
    isTvHardware: Boolean = false,
    isAutoSyncing: Boolean = false,
    preferGeminiAudioForSeries: Boolean = false,
    totalSeriesEpisodes: Int = 1,
    nearbyCuesForSnap: List<SnapCueCandidate> = emptyList(),
    onSelectTrack: (SubtitleTrackOption) -> Unit,
    onSearchOnlineSub: (String) -> Unit,
    onSelectGeminiModel: (String) -> Unit,
    onSaveGeminiKeyInline: (String, String) -> Unit = { _, _ -> },
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
    var showInlineKeyInput by remember { mutableStateOf(false) }
    var inlineKeyText by remember { mutableStateOf("") }
    var customSubUrl by remember { mutableStateOf("") }
    var customPrimaryLangInput by remember { mutableStateOf("") }
    var customSecondaryLangInput by remember { mutableStateOf("") }
    var onlineSearchText by remember(defaultSearchQuery) { mutableStateOf(defaultSearchQuery) }

    LaunchedEffect(Unit) {
        delay(40L)
        runCatching { firstRowFocusRequester.requestFocus() }
    }

    val colors = SubtitleColorChoice.entries
    val primaryLang = config.resolvedPrimaryLanguage()
    val secondaryLangCode = config.resolvedSecondaryLangCode()

    fun cycleColor(current: SubtitleColorChoice, delta: Int): SubtitleColorChoice {
        val idx = colors.indexOf(current).coerceAtLeast(0)
        val nextIdx = (idx + delta + colors.size) % colors.size
        return colors[nextIdx]
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            }
    ) {
        val isPortraitMobile = !isTv && maxWidth < 600.dp
        val sheetShape = if (isPortraitMobile) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
        }
        val sheetModifier = if (isPortraitMobile) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
        } else {
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (isTv) 0.56f else 0.58f)
        }

        Column(
            modifier = sheetModifier
                .clip(sheetShape)
                .background(CinemaObsidian.copy(alpha = 0.98f))
                .border(1.dp, BrandNeonGlassBorderBrush, sheetShape)
                .then(if (isPortraitMobile) Modifier.navigationBarsPadding() else Modifier.statusBarsPadding().navigationBarsPadding().displayCutoutPadding())
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Drag handle pill on mobile bottom sheet
            if (isPortraitMobile) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }

            // 1. Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phụ Đề AI Đa Ngôn Ngữ",
                        color = EmeraldNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chính: ${primaryLang.displayName} • Phụ: ${config.resolvedSecondaryDisplayName()}",
                        color = VioletSoft,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .tvFocusableClickable(
                            shape = CircleShape,
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = CinemaGlassBorder,
                            onClick = onClose
                        )
                        .background(CinemaSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // 2. Live Preview Banner in User's Chosen Languages
            LiveSubtitlePreviewBanner(config = config)

            // 3. 4 Compact Equal-Weight Category Tabs
            val tabs = listOf(
                0 to "🌐 Ngôn ngữ",
                1 to "🎨 Chữ & Màu",
                2 to "⏱ Khớp giờ",
                3 to "📦 Khung"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tabs.forEach { (tabIdx, tabTitle) ->
                    val isSelected = activeTab == tabIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = if (isSelected) EmeraldNeon else CinemaGlassBorder,
                                onLeftKey = { activeTab = (activeTab - 1 + tabs.size) % tabs.size },
                                onRightKey = { activeTab = (activeTab + 1) % tabs.size },
                                onClick = { activeTab = tabIdx }
                            )
                            .background(if (isSelected) EmeraldPrimary else CinemaSurfaceElevated)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabTitle,
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

            // 4. Scrollable Responsive Tab Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        // TAB 0: UNIVERSAL MULTI-LANGUAGE AI SELECTOR & SOURCES
                        SubControlStepperRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            title = "Hiển thị Phụ Đề",
                            subtitle = "Bật hoặc tắt phụ đề trên màn hình",
                            valueText = if (config.subtitlesEnabled) "BẬT" else "TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = EmeraldNeon,
                            onStepLeft = { onUpdateConfig { it.copy(subtitlesEnabled = !it.subtitlesEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(subtitlesEnabled = !it.subtitlesEnabled) } }
                        )

                        SectionHeaderLabel("NGÔN NGỮ SUB CHÍNH (DÒNG 1 • AI DỊCH MỌI NGÔN NGỮ)")

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubtitleLanguageCatalog.PRESETS.forEach { lang ->
                                val selected = SubtitleLanguageCatalog.isTrackMatchingLanguage(primaryLang.code, lang.code)
                                LanguageChip(
                                    badge = lang.shortBadge,
                                    label = lang.displayName.substringBefore(" ("),
                                    selected = selected,
                                    accent = EmeraldPrimary,
                                    onClick = {
                                        onUpdateConfig {
                                            it.copy(
                                                preferredPrimaryLang = lang.code,
                                                autoTranslateToThai = true
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // Custom Any-Language AI Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customPrimaryLangInput,
                                onValueChange = { customPrimaryLangInput = it },
                                singleLine = true,
                                placeholder = {
                                    Text("Ngôn ngữ khác bất kỳ (VD: Tiếng Hà Lan, Turkish...)", fontSize = 11.5.sp)
                                },
                                modifier = Modifier.weight(1f),
                                colors = sheetTextFieldColors()
                            )
                            Box(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = {
                                            val clean = customPrimaryLangInput.trim()
                                            if (clean.isNotEmpty()) {
                                                onUpdateConfig {
                                                    it.copy(preferredPrimaryLang = clean, autoTranslateToThai = true)
                                                }
                                                customPrimaryLangInput = ""
                                            }
                                        }
                                    )
                                    .background(EmeraldPrimary)
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Text("Chọn", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        SectionHeaderLabel("SONG NGỮ — SUB PHỤ (DÒNG 2 • TÙY CHỌN)")

                        SubControlStepperRow(
                            title = "Bật Phụ Đề Song Ngữ (2 Dòng)",
                            subtitle = "Hiển thị đồng thời 2 ngôn ngữ (Chính + Phụ)",
                            valueText = if (config.dualSubEnabled) "BẬT" else "TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = VioletSoft,
                            onStepLeft = { onUpdateConfig { it.copy(dualSubEnabled = !it.dualSubEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(dualSubEnabled = !it.dualSubEnabled) } }
                        )

                        if (config.dualSubEnabled) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LanguageChip(
                                    badge = "GỐC",
                                    label = "Ngôn ngữ gốc",
                                    selected = secondaryLangCode == "orig",
                                    accent = VioletPrimary,
                                    onClick = { onUpdateConfig { it.copy(secondaryLangCode = "orig") } }
                                )
                                SubtitleLanguageCatalog.PRESETS.take(12).forEach { lang ->
                                    val selected = SubtitleLanguageCatalog.isTrackMatchingLanguage(secondaryLangCode, lang.code)
                                    LanguageChip(
                                        badge = lang.shortBadge,
                                        label = lang.displayName.substringBefore(" ("),
                                        selected = selected,
                                        accent = VioletPrimary,
                                        onClick = { onUpdateConfig { it.copy(secondaryLangCode = lang.code) } }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customSecondaryLangInput,
                                    onValueChange = { customSecondaryLangInput = it },
                                    singleLine = true,
                                    placeholder = { Text("Ngôn ngữ Sub phụ khác...", fontSize = 11.5.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = sheetTextFieldColors()
                                )
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(10.dp),
                                            onClick = {
                                                val clean = customSecondaryLangInput.trim()
                                                if (clean.isNotEmpty()) {
                                                    onUpdateConfig { it.copy(secondaryLangCode = clean) }
                                                    customSecondaryLangInput = ""
                                                }
                                            }
                                        )
                                        .background(VioletPrimary)
                                        .padding(horizontal = 12.dp, vertical = 14.dp)
                                ) {
                                    Text("Chọn", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        SectionHeaderLabel("NGUỒN SUB (${availableTracks.size} BẢN) & AI GEMINI")

                        FullWidthActionRow(
                            icon = Icons.Default.Mic,
                            title = if (hasGeminiKeyInRam) {
                                "🎙️ Gemini (${config.geminiModelId}) Nghe Phim Tạo Sub ${primaryLang.shortBadge}"
                            } else if (isTvHardware) {
                                "🔐 Quét QR Nạp Gemini Key (AI Nghe Phim Tạo Sub ${primaryLang.shortBadge})"
                            } else {
                                "🔐 Nhập Gemini Key (Lưu 1 Lần) Để AI Nghe Phim Tạo Sub ${primaryLang.shortBadge}"
                            },
                            subtitle = "AI nghe trực tiếp lời thoại và dịch tức thì sang ${primaryLang.displayName}",
                            accentColor = EmeraldNeon,
                            onClick = {
                                when {
                                    hasGeminiKeyInRam -> onTriggerGeminiAudioSubNow()
                                    isTvHardware -> onOpenQrUpload()
                                    else -> showInlineKeyInput = !showInlineKeyInput
                                }
                            }
                        )

                        if (showInlineKeyInput && !isTvHardware) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inlineKeyText,
                                    onValueChange = { inlineKeyText = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    placeholder = { Text("Dán Gemini API Key (AIza...)...", fontSize = 11.5.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = sheetTextFieldColors()
                                )
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = RoundedCornerShape(10.dp),
                                            onClick = {
                                                val clean = inlineKeyText.trim()
                                                if (clean.isNotEmpty()) {
                                                    onSaveGeminiKeyInline(clean, config.geminiModelId)
                                                    inlineKeyText = ""
                                                    showInlineKeyInput = false
                                                }
                                            }
                                        )
                                        .background(EmeraldPrimary)
                                        .padding(horizontal = 12.dp, vertical = 14.dp)
                                ) {
                                    Text("Lưu", color = CinemaObsidian, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        if (availableTracks.isNotEmpty()) {
                            val currentIdx = availableTracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
                            val currentTrack = availableTracks[currentIdx]
                            SubControlStepperRow(
                                title = "Bản Phụ Đề Đang Dùng (${currentIdx + 1}/${availableTracks.size})",
                                subtitle = "Đổi qua bản sub khác đã tải về",
                                valueText = currentTrack.label.take(26),
                                leftButtonLabel = "◄",
                                rightButtonLabel = "►",
                                accentColor = VioletSoft,
                                onStepLeft = {
                                    onSelectTrack(availableTracks[(currentIdx - 1 + availableTracks.size) % availableTracks.size])
                                },
                                onStepRight = {
                                    onSelectTrack(availableTracks[(currentIdx + 1) % availableTracks.size])
                                }
                            )
                        }

                        SubControlStepperRow(
                            title = "Bộ Máy Dịch AI",
                            subtitle = "Neural AI miễn phí hoặc Gemini AI chất lượng cao",
                            valueText = if (config.translationEngine == TranslationEngineMode.FAST_FREE) "Neural AI" else "Gemini AI",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = EmeraldNeon,
                            onStepLeft = {
                                val next = if (config.translationEngine == TranslationEngineMode.FAST_FREE) {
                                    TranslationEngineMode.GEMINI_AI
                                } else TranslationEngineMode.FAST_FREE
                                onUpdateConfig { it.copy(translationEngine = next) }
                            },
                            onStepRight = {
                                val next = if (config.translationEngine == TranslationEngineMode.FAST_FREE) {
                                    TranslationEngineMode.GEMINI_AI
                                } else TranslationEngineMode.FAST_FREE
                                onUpdateConfig { it.copy(translationEngine = next) }
                            }
                        )

                        val modelChoices = remember(discoveredModels, config.geminiModelId) {
                            val set = LinkedHashSet<String>()
                            if (config.geminiModelId.isNotBlank()) set.add(config.geminiModelId)
                            discoveredModels.forEach { set.add(it) }
                            if (set.isEmpty()) set.add("gemini-3-flash-preview")
                            set.toList()
                        }
                        SubControlStepperRow(
                            title = "Model Gemini AI",
                            subtitle = if (hasGeminiKeyInRam) "Key sẵn sàng • Chạm ◄/► đổi Model" else "Chưa nạp Gemini Key",
                            valueText = config.geminiModelId.take(22),
                            leftButtonLabel = "◄",
                            rightButtonLabel = "►",
                            accentColor = VioletSoft,
                            onStepLeft = {
                                val cur = modelChoices.indexOf(config.geminiModelId).coerceAtLeast(0)
                                val nextModel = modelChoices[(cur - 1 + modelChoices.size) % modelChoices.size]
                                onSelectGeminiModel(nextModel)
                                onUpdateConfig { it.copy(geminiModelId = nextModel, translationEngine = TranslationEngineMode.GEMINI_AI) }
                            },
                            onStepRight = {
                                val cur = modelChoices.indexOf(config.geminiModelId).coerceAtLeast(0)
                                val nextModel = modelChoices[(cur + 1) % modelChoices.size]
                                onSelectGeminiModel(nextModel)
                                onUpdateConfig { it.copy(geminiModelId = nextModel, translationEngine = TranslationEngineMode.GEMINI_AI) }
                            }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Search,
                            title = "Tìm Phụ Đề Online Theo Tên / IMDb",
                            subtitle = "Tìm OpenSubtitles / YIFY rồi tự dịch sang ${primaryLang.displayName}",
                            accentColor = EmeraldNeon,
                            onClick = { showOnlineSearchInput = !showOnlineSearchInput }
                        )

                        if (showOnlineSearchInput) {
                            OutlinedTextField(
                                value = onlineSearchText,
                                onValueChange = { onlineSearchText = it },
                                singleLine = true,
                                placeholder = { Text("Tên phim tiếng Anh hoặc tt15239678", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = sheetTextFieldColors()
                            )
                            FullWidthActionRow(
                                icon = Icons.Default.Search,
                                title = "Bắt Đầu Tìm & Tải Sub",
                                subtitle = onlineSearchText,
                                accentColor = VioletSoft,
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
                            title = "Chọn File Sub (.SRT / .ASS / .VTT) Từ Máy",
                            subtitle = "Nạp file phụ đề có sẵn trên thiết bị",
                            accentColor = TextPrimary,
                            onClick = onPickLocalSubFile
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Link,
                            title = "Nhập Link File Phụ Đề .SRT",
                            subtitle = "Dán URL trực tiếp",
                            accentColor = TextPrimary,
                            onClick = { showUrlInput = !showUrlInput }
                        )

                        if (showUrlInput) {
                            OutlinedTextField(
                                value = customSubUrl,
                                onValueChange = { customSubUrl = it },
                                singleLine = true,
                                placeholder = { Text("https://.../subtitle.srt", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = sheetTextFieldColors()
                            )
                            FullWidthActionRow(
                                icon = Icons.Default.Link,
                                title = "Tải & Áp Dụng Link Phụ Đề",
                                subtitle = customSubUrl.ifBlank { "Nhập URL ở trên" },
                                accentColor = EmeraldNeon,
                                onClick = {
                                    if (customSubUrl.isNotBlank()) {
                                        onLoadSubFromUrl(customSubUrl.trim())
                                        showUrlInput = false
                                    }
                                }
                            )
                        }
                    }

                    1 -> {
                        // TAB 1: FONT SIZE & COLOR (ALL / PRIMARY / SECONDARY)
                        SectionHeaderLabel("CHỈNH ĐỒNG BỘ TẤT CẢ (CHÍNH + PHỤ)")

                        SubControlStepperRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            title = "Cỡ chữ Tất Cả",
                            subtitle = "Tăng giảm đồng thời cả 2 dòng phụ đề",
                            valueText = "${config.fontSizeSp.toInt()} / ${config.secondaryFontSizeSp.toInt()}sp",
                            leftButtonLabel = "-2",
                            rightButtonLabel = "+2",
                            accentColor = EmeraldNeon,
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

                        SectionHeaderLabel("SUB CHÍNH (${primaryLang.displayName.uppercase()})")

                        SubControlStepperRow(
                            title = "Cỡ chữ Sub Chính",
                            subtitle = "12sp – 52sp",
                            valueText = "${config.fontSizeSp.toInt()} sp",
                            leftButtonLabel = "-2",
                            rightButtonLabel = "+2",
                            accentColor = EmeraldNeon,
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

                        ColorPaletteQuickRow(
                            selectedColor = config.primaryColor,
                            onPickColor = { picked ->
                                onUpdateConfig { it.copy(linkPrimarySecondaryStyle = false, primaryColor = picked) }
                            }
                        )

                        SectionHeaderLabel("SUB PHỤ (${config.resolvedSecondaryDisplayName().uppercase()})")

                        SubControlStepperRow(
                            title = "Cỡ chữ Sub Phụ",
                            subtitle = "10sp – 48sp",
                            valueText = "${config.secondaryFontSizeSp.toInt()} sp",
                            leftButtonLabel = "-2",
                            rightButtonLabel = "+2",
                            accentColor = VioletSoft,
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

                        ColorPaletteQuickRow(
                            selectedColor = config.secondaryColor,
                            onPickColor = { picked ->
                                onUpdateConfig { it.copy(linkPrimarySecondaryStyle = false, secondaryColor = picked) }
                            }
                        )

                        SubControlStepperRow(
                            title = "Chữ Đậm Rõ Nét (Bold)",
                            subtitle = "Dễ đọc hơn trên nền phim sáng",
                            valueText = if (config.boldText) "BẬT" else "TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = EmeraldNeon,
                            onStepLeft = { onUpdateConfig { it.copy(boldText = !it.boldText) } },
                            onStepRight = { onUpdateConfig { it.copy(boldText = !it.boldText) } }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.RestartAlt,
                            title = "Khôi phục Mặc định Cinema",
                            subtitle = "Đặt lại cỡ chữ, màu sắc & khung gọn chuẩn",
                            accentColor = EmeraldNeon,
                            onClick = onResetDefault
                        )
                    }

                    2 -> {
                        // TAB 2: TIMING SYNC & SERIES MEMORY
                        val secStr = "%+.1fs".format(config.syncOffsetMs / 1000f)
                        val speedStr = "%.3fx".format(config.subSpeedScale)
                        SectionHeaderLabel("ĐỘ LỆCH: $secStr • FPS: $speedStr")

                        FullWidthActionRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            icon = Icons.Default.Mic,
                            title = if (isAutoSyncing) "AI Đang Nghe & Tự Khớp..." else "🎯 AI Tự Nghe 2 Điểm Khớp Giờ + FPS",
                            subtitle = "Nghe trực tiếp âm thanh phim để khớp chuẩn & nhớ cho cả bộ",
                            accentColor = EmeraldNeon,
                            onClick = { if (!isAutoSyncing) onAutoSyncNow() }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.Search,
                            title = "⚡ Tạm Dừng & Chọn Câu Khớp 1 Chạm",
                            subtitle = "Chạm đúng câu nhân vật vừa nói là khớp ngay",
                            accentColor = VioletSoft,
                            onClick = onOpenFastSnapModal
                        )

                        SubControlStepperRow(
                            title = "Tinh chỉnh ±0.5 Giây",
                            subtitle = "Dịch sớm hơn (-) hoặc trễ hơn (+)",
                            valueText = secStr,
                            leftButtonLabel = "-0.5s",
                            rightButtonLabel = "+0.5s",
                            accentColor = EmeraldNeon,
                            onStepLeft = { onAdjustSyncManual(config.syncOffsetMs - 500L, null) },
                            onStepRight = { onAdjustSyncManual(config.syncOffsetMs + 500L, null) }
                        )

                        SubControlStepperRow(
                            title = "Tinh chỉnh ±2.0 Giây",
                            subtitle = "Dịch nhanh 2 giây",
                            valueText = secStr,
                            leftButtonLabel = "-2s",
                            rightButtonLabel = "+2s",
                            accentColor = VioletSoft,
                            onStepLeft = { onAdjustSyncManual(config.syncOffsetMs - 2_000L, null) },
                            onStepRight = { onAdjustSyncManual(config.syncOffsetMs + 2_000L, null) }
                        )

                        val fpsPresets = listOf(
                            1.0f to "1.000x",
                            0.95904f to "0.959x",
                            1.04271f to "1.043x"
                        )
                        val curFpsIdx = fpsPresets.indexOfFirst {
                            kotlin.math.abs(config.subSpeedScale - it.first) < 0.003f
                        }.coerceAtLeast(0)
                        SubControlStepperRow(
                            title = "Chống Trôi Sub Cuối Tập (FPS)",
                            subtitle = "Khắc phục lệch dần 23.976 ↔ 25fps",
                            valueText = fpsPresets[curFpsIdx].second,
                            leftButtonLabel = "◄",
                            rightButtonLabel = "►",
                            accentColor = VioletSoft,
                            onStepLeft = {
                                onAdjustSyncManual(config.syncOffsetMs, fpsPresets[(curFpsIdx - 1 + fpsPresets.size) % fpsPresets.size].first)
                            },
                            onStepRight = {
                                onAdjustSyncManual(config.syncOffsetMs, fpsPresets[(curFpsIdx + 1) % fpsPresets.size].first)
                            }
                        )

                        FullWidthActionRow(
                            icon = Icons.Default.RestartAlt,
                            title = "Đặt Lại Về 0.0s",
                            subtitle = "Reset offset & FPS về mặc định",
                            accentColor = TextPrimary,
                            onClick = { onAdjustSyncManual(0L, 1.0f) }
                        )

                        if (availableTracks.count { !it.isEmbedded && it.externalRef != null } >= 2) {
                            FullWidthActionRow(
                                icon = Icons.Default.Refresh,
                                title = "Đổi Sang Bản Phụ Đề Khác",
                                subtitle = "Thử bản sub khác trong kho",
                                accentColor = EmeraldNeon,
                                onClick = onCycleNextSubRelease
                            )
                        }

                        if (totalSeriesEpisodes > 1) {
                            SubControlStepperRow(
                                title = "Khóa Gemini Audio Cho Cả Bộ ($totalSeriesEpisodes Tập)",
                                subtitle = "Dùng AI nghe tiếng phim cho mọi tập sau",
                                valueText = if (preferGeminiAudioForSeries) "BẬT" else "TẮT",
                                leftButtonLabel = "Đổi",
                                rightButtonLabel = "Đổi",
                                accentColor = EmeraldNeon,
                                onStepLeft = onTogglePreferGeminiAudioForSeries,
                                onStepRight = onTogglePreferGeminiAudioForSeries
                            )
                        }
                    }

                    3 -> {
                        // TAB 3: FRAME, OPACITY, POSITION, FONT
                        val edges = SubtitleEdgeStyle.entries
                        SubControlStepperRow(
                            modifier = Modifier.focusRequester(firstRowFocusRequester),
                            title = "Kiểu Khung Nền",
                            subtitle = "Khung mờ gọn, viền đen hoặc đổ bóng",
                            valueText = config.edgeStyle.displayName.take(18),
                            leftButtonLabel = "◄",
                            rightButtonLabel = "►",
                            accentColor = EmeraldNeon,
                            onStepLeft = {
                                onUpdateConfig { it.copy(edgeStyle = edges[(edges.indexOf(config.edgeStyle) - 1 + edges.size) % edges.size]) }
                            },
                            onStepRight = {
                                onUpdateConfig { it.copy(edgeStyle = edges[(edges.indexOf(config.edgeStyle) + 1) % edges.size]) }
                            }
                        )

                        SubControlStepperRow(
                            title = "Độ Đậm Khung Nền",
                            subtitle = "40% – 95%",
                            valueText = "${(config.boxOpacity * 100).toInt()}%",
                            leftButtonLabel = "-10%",
                            rightButtonLabel = "+10%",
                            accentColor = VioletSoft,
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
                            title = "Vị Trí Cao / Thấp",
                            subtitle = "Khoảng cách từ mép dưới màn hình",
                            valueText = "${config.bottomOffsetDp.toInt()} dp",
                            leftButtonLabel = "Thấp",
                            rightButtonLabel = "Cao",
                            accentColor = EmeraldNeon,
                            onStepLeft = {
                                onUpdateConfig { it.copy(bottomOffsetDp = (it.bottomOffsetDp - 6f).coerceIn(4f, 180f)) }
                            },
                            onStepRight = {
                                onUpdateConfig { it.copy(bottomOffsetDp = (it.bottomOffsetDp + 6f).coerceIn(4f, 180f)) }
                            }
                        )

                        SubControlStepperRow(
                            title = "Che Hardsub Gốc",
                            subtitle = "Dải đen che chữ phụ đề cứng dính trong video",
                            valueText = if (config.hardsubMaskEnabled) "BẬT" else "TẮT",
                            leftButtonLabel = "Đổi",
                            rightButtonLabel = "Đổi",
                            accentColor = VioletSoft,
                            onStepLeft = { onUpdateConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) } },
                            onStepRight = { onUpdateConfig { it.copy(hardsubMaskEnabled = !it.hardsubMaskEnabled) } }
                        )

                        val fonts = SubtitleFontChoice.entries
                        SubControlStepperRow(
                            title = "Phông Chữ (Font)",
                            subtitle = "Sarabun / Kanit / Prompt / Hệ thống",
                            valueText = config.fontChoice.displayName.substringBefore(" "),
                            leftButtonLabel = "◄",
                            rightButtonLabel = "►",
                            accentColor = EmeraldNeon,
                            onStepLeft = {
                                onUpdateConfig { it.copy(fontChoice = fonts[(fonts.indexOf(config.fontChoice) - 1 + fonts.size) % fonts.size]) }
                            },
                            onStepRight = {
                                onUpdateConfig { it.copy(fontChoice = fonts[(fonts.indexOf(config.fontChoice) + 1) % fonts.size]) }
                            }
                        )

                        if (isTvHardware) {
                            FullWidthActionRow(
                                icon = Icons.Default.QrCode2,
                                title = "QR Kết Nối Điện Thoại (Gửi Sub & Key lên TV)",
                                subtitle = "Quét QR trên cùng Wi-Fi",
                                accentColor = VioletSoft,
                                onClick = onOpenQrUpload
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LanguageChip(
    badge: String,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(99.dp),
                focusedBorderColor = Color.White,
                unfocusedBorderColor = if (selected) accent else CinemaGlassBorder,
                onClick = onClick
            )
            .background(if (selected) accent else CinemaSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = badge,
            color = if (selected) CinemaObsidian else EmeraldNeon,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = if (selected) CinemaObsidian else TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun LiveSubtitlePreviewBanner(config: SubtitleStyleConfig) {
    val bgAlpha = if (config.edgeStyle == SubtitleEdgeStyle.BACKGROUND_BOX) {
        config.boxOpacity.coerceIn(0.35f, 0.95f)
    } else {
        0.0f
    }
    val primaryLang = config.resolvedPrimaryLanguage()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E1324))
            .border(1.dp, CinemaGlassBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = bgAlpha))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = primaryLang.samplePreview,
                color = Color(config.primaryColor.argb),
                fontSize = config.fontSizeSp.coerceIn(12f, 24f).sp,
                fontWeight = if (config.boldText) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (config.dualSubEnabled) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${config.resolvedSecondaryDisplayName()} (${config.secondaryFontSizeSp.toInt()}sp)",
                    color = Color(config.secondaryColor.argb),
                    fontSize = config.secondaryFontSizeSp.coerceIn(10f, 18f).sp,
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
        color = EmeraldNeon,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
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
    accentColor: Color = EmeraldNeon,
    onStepLeft: () -> Unit,
    onStepRight: () -> Unit
) {
    val currentOnStepLeft by rememberUpdatedState(onStepLeft)
    val currentOnStepRight by rememberUpdatedState(onStepRight)
    val isCompact = LocalConfiguration.current.screenWidthDp < 540

    val containerMod = modifier
        .fillMaxWidth()
        .tvFocusableClickable(
            shape = RoundedCornerShape(12.dp),
            focusedBorderColor = EmeraldNeon,
            unfocusedBorderColor = CinemaGlassBorder,
            focusedScale = 1.01f,
            onLeftKey = { currentOnStepLeft() },
            onRightKey = { currentOnStepRight() },
            onClick = { currentOnStepRight() }
        )
        .background(CinemaSurface)
        .padding(horizontal = 12.dp, vertical = 10.dp)

    if (isCompact) {
        // Mobile-first 2-row card: ZERO squished title or overflowing stepper!
        Column(
            modifier = containerMod,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StepperButton(label = leftButtonLabel, onClick = { currentOnStepLeft() })
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CinemaObsidian)
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (valueDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StepperButton(label = rightButtonLabel, onClick = { currentOnStepRight() })
            }
        }
    } else {
        Row(
            modifier = containerMod,
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
                StepperButton(label = leftButtonLabel, onClick = { currentOnStepLeft() })
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CinemaObsidian)
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (valueDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
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
                StepperButton(label = rightButtonLabel, onClick = { currentOnStepRight() })
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CinemaSurfaceElevated)
            .border(1.dp, CinemaGlassBorder, RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteQuickRow(
    selectedColor: SubtitleColorChoice,
    onPickColor: (SubtitleColorChoice) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SubtitleColorChoice.entries.forEach { col ->
            val isSelected = col == selectedColor
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isSelected) CinemaSurfaceElevated else CinemaSurface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) EmeraldNeon else CinemaGlassBorder,
                        shape = RoundedCornerShape(99.dp)
                    )
                    .pointerInput(col) { detectTapGestures { onPickColor(col) } }
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(col.argb))
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = col.displayName.substringBefore(" "),
                    color = if (isSelected) EmeraldNeon else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1
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
    accentColor: Color = VioletSoft,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = accentColor.copy(alpha = 0.45f),
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
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun sheetTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EmeraldNeon,
    unfocusedBorderColor = CinemaGlassBorder,
    focusedContainerColor = CinemaSurface,
    unfocusedContainerColor = CinemaSurface,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
