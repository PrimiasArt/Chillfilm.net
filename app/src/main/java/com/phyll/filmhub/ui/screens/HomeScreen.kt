package com.phyll.filmhub.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.data.CatalogSection
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.WatchProgress
import com.phyll.filmhub.ui.components.MovieCard
import com.phyll.filmhub.ui.theme.AmbientCinemaBackdropBrush
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
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.VioletPrimary
import com.phyll.filmhub.ui.theme.tvFocusableClickable

@Composable
fun HomeScreen(
    selectedCountry: CountryCategory,
    sections: List<CatalogSection>,
    watchHistory: List<WatchProgress>,
    isLoading: Boolean,
    searchQuery: String,
    searchResults: List<MovieItem>,
    isSearching: Boolean,
    hasSessionGeminiKey: Boolean,
    activeGeminiModel: String,
    companionLanUrl: String? = null,
    isTvHardware: Boolean = true,
    controlTargetMode: ControlTargetMode = ControlTargetMode.LOCAL_DEVICE,
    selectedTvDevice: ManagedTvDevice? = null,
    isRemoteTvOnline: Boolean = false,
    onSelectCountry: (CountryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onMovieClick: (MovieItem) -> Unit,
    onQuickPlayMovie: (MovieItem) -> Unit,
    onResumeHistory: (WatchProgress) -> Unit,
    onToggleControlTargetMode: () -> Unit = {},
    onOpenTvManager: () -> Unit = {},
    onOpenQrCompanion: () -> Unit,
    onOpenSettingsSources: () -> Unit,
    onToggleUiMode: () -> Unit = {},
    onRefresh: () -> Unit,
    onBeforeLaunchSystemOverlay: () -> Unit = {},
    onAfterSystemOverlayResult: () -> Unit = {},
    onVoiceError: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val focusManager = LocalFocusManager.current

    var spotlightMovie by remember(sections) {
        mutableStateOf(sections.firstOrNull()?.movies?.firstOrNull())
    }
    if (spotlightMovie == null && sections.isNotEmpty()) {
        spotlightMovie = sections.firstOrNull()?.movies?.firstOrNull()
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onAfterSystemOverlayResult()
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                onExecuteSearch(spoken)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmbientCinemaBackdropBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 66.dp) // Space for the Phone-Style Bottom Navigation Bar
        ) {
            // Sleek Phone-Style Header & Search Pill
            TopBarSection(
                isTv = isTv,
                searchQuery = searchQuery,
                hasSessionGeminiKey = hasSessionGeminiKey,
                activeGeminiModel = activeGeminiModel,
                companionLanUrl = companionLanUrl,
                onSearchQueryChange = onSearchQueryChange,
                onExecuteSearch = {
                    focusManager.clearFocus()
                    onExecuteSearch(searchQuery)
                },
                onLaunchVoiceSearch = {
                    runCatching {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên phim (Tiếng Thái / Việt / Anh)...")
                        }
                        onBeforeLaunchSystemOverlay()
                        voiceLauncher.launch(intent)
                    }.onFailure {
                        onAfterSystemOverlayResult()
                        onVoiceError()
                    }
                },
                onOpenQrCompanion = onOpenQrCompanion,
                onToggleUiMode = onToggleUiMode,
                onRefresh = onRefresh
            )

            // Dual-Mode Switcher & TV Quick Status Bar on Standalone Android Phones / Tablets
            if (!isTvHardware) {
                PhoneControllerModeBanner(
                    controlTargetMode = controlTargetMode,
                    selectedTv = selectedTvDevice,
                    isRemoteTvOnline = isRemoteTvOnline,
                    hasSavedGeminiKey = hasSessionGeminiKey,
                    onToggleMode = onToggleControlTargetMode,
                    onOpenTvManager = onOpenTvManager
                )
            }

            // Luxury Minimalist Country Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = if (isTv) 24.dp else 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CountryCategory.entries) { country ->
                    val isSelected = country == selectedCountry && searchQuery.isBlank()
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(99.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = if (isSelected) VioletGlow else CinemaGlassBorder,
                                focusedScale = 1.04f,
                                onClick = { onSelectCountry(country) }
                            )
                            .background(
                                brush = if (isSelected) BrandEmeraldVioletBrush else Brush.horizontalGradient(listOf(CinemaGlass, CinemaGlass))
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = country.displayName,
                            color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 12.sp,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }

            // Main Body: Either Search Results or Home Catalog
            if (searchQuery.isNotBlank()) {
                SearchResultsBody(
                    query = searchQuery,
                    isSearching = isSearching,
                    results = searchResults,
                    onClearSearch = { onSearchQueryChange("") },
                    onMovieClick = onMovieClick
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Phone-Style Hero Spotlight Card
                    spotlightMovie?.let { hero ->
                        item(key = "hero_banner") {
                            HeroSpotlightBanner(
                                movie = hero,
                                isTv = isTv,
                                onPlayClick = { onQuickPlayMovie(hero) },
                                onDetailClick = { onMovieClick(hero) },
                                onStreamToPhoneClick = onOpenQrCompanion
                            )
                        }
                    }

                    // Continue Watching Section
                    if (watchHistory.isNotEmpty() && selectedCountry == CountryCategory.ALL) {
                        item(key = "continue_watching") {
                            ContinueWatchingRow(
                                history = watchHistory,
                                isTv = isTv,
                                onResume = onResumeHistory
                            )
                        }
                    }

                    if (isLoading && sections.isEmpty()) {
                        item(key = "loading_indicator") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = ThaiGold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Đang tải kho phim đa quốc gia...",
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    items(sections, key = { it.id }) { section ->
                        CatalogSectionRow(
                            section = section,
                            isTv = isTv,
                            onMovieFocused = { focused ->
                                spotlightMovie = focused
                            },
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }
        }

        // Sleek Phone-Like Bottom Glass Navigation Bar (Works with BOTH Touch/Phone Stream & TV Remote D-Pad!)
        MobileBottomNavigationBar(
            selectedCountry = selectedCountry,
            isSearchActive = searchQuery.isNotBlank(),
            hasGeminiKey = hasSessionGeminiKey,
            isTvHardware = isTvHardware,
            selectedTvDevice = selectedTvDevice,
            onGoHomeAll = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.ALL)
            },
            onGoThaiMovies = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.THAILAND)
            },
            onGoYouTubeMovies = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.YOUTUBE)
            },
            onOpenPhoneStream = onOpenQrCompanion,
            onOpenTvManager = onOpenTvManager,
            onOpenSettings = onOpenSettingsSources,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PhoneControllerModeBanner(
    controlTargetMode: ControlTargetMode,
    selectedTv: ManagedTvDevice?,
    isRemoteTvOnline: Boolean,
    hasSavedGeminiKey: Boolean,
    onToggleMode: () -> Unit,
    onOpenTvManager: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1522))
            .border(1.dp, ElectricCyan.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .tvFocusableClickable(
                    shape = RoundedCornerShape(10.dp),
                    focusedBorderColor = ThaiGold,
                    unfocusedBorderColor = if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan else ThaiGold,
                    onClick = onToggleMode
                )
                .background(
                    if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan.copy(alpha = 0.16f)
                    else ThaiGold.copy(alpha = 0.16f)
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            val modeTitle = if (controlTargetMode == ControlTargetMode.REMOTE_TV) {
                val tvLabel = selectedTv?.displayTitle ?: "Chưa chọn TV"
                val dot = if (isRemoteTvOnline) "🟢" else "🟡"
                "$dot Chế độ Điều Khiển TV: $tvLabel"
            } else {
                "📱 Chế độ: Xem Trực Tiếp Trên ĐT ${if (hasSavedGeminiKey) "• 🔐 AI Sẵn" else ""}"
            }
            Text(
                text = modeTitle,
                color = if (controlTargetMode == ControlTargetMode.REMOTE_TV) ElectricCyan else ThaiGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .tvFocusableClickable(
                    shape = RoundedCornerShape(10.dp),
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = ElectricCyan,
                    onClick = onOpenTvManager
                )
                .background(ElectricCyan)
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(
                text = "📡 Cài & Chọn TV",
                color = CinemaObsidian,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MobileBottomNavigationBar(
    selectedCountry: CountryCategory,
    isSearchActive: Boolean,
    hasGeminiKey: Boolean,
    isTvHardware: Boolean,
    selectedTvDevice: ManagedTvDevice?,
    onGoHomeAll: () -> Unit,
    onGoThaiMovies: () -> Unit,
    onGoYouTubeMovies: () -> Unit,
    onOpenPhoneStream: () -> Unit,
    onOpenTvManager: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTvHardware) 20.dp else 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CinemaGlass)
                .border(width = 1.dp, brush = BrandNeonGlassBorderBrush, shape = RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTabItem(
                icon = Icons.Default.Home,
                label = "Khám phá",
                active = !isSearchActive && selectedCountry == CountryCategory.ALL,
                accentColor = ThaiGold,
                onClick = onGoHomeAll
            )
            BottomNavTabItem(
                icon = Icons.Default.Movie,
                label = "Phim Thái",
                active = !isSearchActive && selectedCountry == CountryCategory.THAILAND,
                accentColor = ThaiGold,
                onClick = onGoThaiMovies
            )
            BottomNavTabItem(
                icon = Icons.Default.SmartDisplay,
                label = "YouTube",
                active = !isSearchActive && selectedCountry == CountryCategory.YOUTUBE,
                accentColor = ElectricCyan,
                onClick = onGoYouTubeMovies
            )
            if (isTvHardware) {
                BottomNavTabItem(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Stream ĐT",
                    active = false,
                    accentColor = ElectricCyan,
                    highlightPill = true,
                    onClick = onOpenPhoneStream
                )
            } else {
                BottomNavTabItem(
                    icon = Icons.Default.Tv,
                    label = if (selectedTvDevice != null) "TV & Điều Khiển" else "Quản Lý TV",
                    active = false,
                    accentColor = ElectricCyan,
                    highlightPill = true,
                    onClick = onOpenTvManager
                )
            }
            BottomNavTabItem(
                icon = Icons.Default.Settings,
                label = if (hasGeminiKey) "AI & Nguồn" else "Cài đặt",
                active = false,
                accentColor = if (hasGeminiKey) Color(0xFF10B981) else ThaiGold,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    highlightPill: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        active -> accentColor.copy(alpha = 0.16f)
        highlightPill -> ElectricCyan.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val fg = when {
        active -> accentColor
        highlightPill -> ElectricCyan
        else -> TextSecondary
    }
    val isCompactPortrait = LocalConfiguration.current.screenWidthDp < 520
    val modifierWithFocus = Modifier
        .tvFocusableClickable(
            shape = RoundedCornerShape(12.dp),
            focusedBorderColor = accentColor,
            unfocusedBorderColor = if (active || highlightPill) accentColor.copy(alpha = 0.35f) else Color.Transparent,
            focusedScale = 1.05f,
            onClick = onClick
        )
        .background(bg)

    if (isCompactPortrait) {
        Column(
            modifier = modifierWithFocus.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (active || highlightPill) TextPrimary else TextSecondary,
                fontSize = 9.sp,
                fontWeight = if (active || highlightPill) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(
            modifier = modifierWithFocus.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active || highlightPill) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (active || highlightPill) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopBarSection(
    isTv: Boolean,
    searchQuery: String,
    hasSessionGeminiKey: Boolean,
    activeGeminiModel: String,
    companionLanUrl: String?,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onLaunchVoiceSearch: () -> Unit,
    onOpenQrCompanion: () -> Unit,
    onToggleUiMode: () -> Unit,
    onRefresh: () -> Unit
) {
    val horizPad = if (isTv) 24.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizPad, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandEmeraldVioletBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CHILL",
                            color = EmeraldNeon,
                            fontSize = if (isTv) 18.sp else 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "FILM",
                            color = VioletGlow,
                            fontSize = if (isTv) 18.sp else 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(VioletPrimary.copy(alpha = 0.16f))
                                .border(0.75.dp, VioletGlow.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "2026 AI",
                                color = EmeraldNeon,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    if (!companionLanUrl.isNullOrBlank()) {
                        Text(
                            text = "Remote & Stream: $companionLanUrl",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ephemeral RAM AI Status Pill
                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = ThaiGold,
                            unfocusedBorderColor = if (hasSessionGeminiKey) Color(0xFF10B981).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f),
                            onClick = onOpenQrCompanion
                        )
                        .background(
                            if (hasSessionGeminiKey) Color(0xFF063E2E) else Color(0xFF111723)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (hasSessionGeminiKey) "AI READY • ${activeGeminiModel.take(12)}" else "Nạp Gemini AI",
                        color = if (hasSessionGeminiKey) Color(0xFF6EE7B7) else ThaiGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }

                ActionIconPill(
                    icon = Icons.Default.PhoneAndroid,
                    label = if (isTv) "Mode: TV" else "Mode: ĐT",
                    accent = ThaiGold,
                    onClick = onToggleUiMode
                )

                ActionIconPill(
                    icon = Icons.Default.QrCode2,
                    label = "Stream ĐT",
                    accent = ElectricCyan,
                    onClick = onOpenQrCompanion
                )

                ActionIconPill(
                    icon = Icons.Default.Refresh,
                    label = "",
                    accent = TextPrimary,
                    onClick = onRefresh
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sleek Glass Search Pill Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Tìm phim Thái, Hàn, Âu Mỹ hoặc dán link YouTube...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = ThaiGold, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .tvFocusableClickable(shape = CircleShape) {
                                        onSearchQueryChange("")
                                    }
                                    .padding(4.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = CircleShape,
                                    focusedBorderColor = ElectricCyan,
                                    onClick = onLaunchVoiceSearch
                                )
                                .background(CinemaSurfaceElevated)
                                .padding(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Giọng nói",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ThaiGold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color(0xFF0E131D),
                    unfocusedContainerColor = Color(0xFF0E131D),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )

            ActionIconPill(
                icon = Icons.Default.Search,
                label = "Tìm",
                accent = ThaiGold,
                onClick = onExecuteSearch
            )
        }
    }
}

@Composable
private fun ActionIconPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .tvFocusableClickable(
                shape = RoundedCornerShape(99.dp),
                focusedBorderColor = accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                onClick = onClick
            )
            .background(Color(0xFF111723))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HeroSpotlightBanner(
    movie: MovieItem,
    isTv: Boolean,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    onStreamToPhoneClick: () -> Unit
) {
    val horizPad = if (isTv) 24.dp else 14.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizPad)
            .height(if (isTv) 228.dp else 202.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.2.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(22.dp))
            .background(CinemaObsidian)
    ) {
        val heroArt = movie.backdropUrl.ifBlank { movie.posterUrl }
        if (heroArt.isNotBlank()) {
            AsyncImage(
                model = heroArt,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Multi-stop luxury cinema vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFA070A0F),
                            Color(0xE0070A0F),
                            Color(0x80070A0F),
                            Color(0x22070A0F)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x66070A0F),
                            Color(0xEE070A0F)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTv) 22.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.84f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ThaiGold)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "FEATURED • SUB ไทย",
                            color = CinemaObsidian,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = movie.country.displayName.uppercase(),
                        color = ThaiGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (movie.quality.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = movie.quality.uppercase(),
                                color = ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (movie.year.isNotBlank()) {
                        Text(
                            text = movie.year,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = if (isTv) 24.sp else 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movie.description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = EmeraldNeon,
                            onClick = onPlayClick
                        )
                        .background(BrandEmeraldVioletBrush)
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phát Phim",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = VioletGlow,
                            unfocusedBorderColor = CinemaGlassBorder,
                            onClick = onDetailClick
                        )
                        .background(CinemaGlass)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chi Tiết & Tập",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = VioletGlow,
                            unfocusedBorderColor = VioletGlow.copy(alpha = 0.50f),
                            onClick = onStreamToPhoneClick
                        )
                        .background(VioletPrimary.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Điều Khiển ĐT",
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    history: List<WatchProgress>,
    isTv: Boolean,
    onResume: (WatchProgress) -> Unit
) {
    val horizPad = if (isTv) 24.dp else 14.dp
    Column {
        Row(
            modifier = Modifier.padding(horizontal = horizPad, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ThaiGold)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tiếp Tục Xem Dở",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.movieId }) { item ->
                val mins = (item.positionMs / 60000L)
                val secs = (item.positionMs / 1000L) % 60L
                val pseudoMovie = MovieItem(
                    id = item.movieId,
                    title = item.title,
                    year = "%02d:%02d".format(mins, secs),
                    country = CountryCategory.fromSlugOrName(item.countryCode),
                    quality = item.episodeName,
                    posterUrl = item.posterUrl,
                    providerName = "Xem tiếp",
                    hasNativeThaiSub = true
                )
                MovieCard(
                    movie = pseudoMovie,
                    progressFraction = item.progressFraction,
                    onClick = { onResume(item) }
                )
            }
        }
    }
}

@Composable
private fun CatalogSectionRow(
    section: CatalogSection,
    isTv: Boolean,
    onMovieFocused: (MovieItem) -> Unit,
    onMovieClick: (MovieItem) -> Unit
) {
    val horizPad = if (isTv) 24.dp else 14.dp
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = section.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (section.subtitle.isNotBlank()) {
                    Text(
                        text = section.subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = "${section.movies.size} phim ›",
                color = ThaiGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.movies, key = { it.id }) { movie ->
                MovieCard(
                    movie = movie,
                    onFocused = { onMovieFocused(movie) },
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultsBody(
    query: String,
    isSearching: Boolean,
    results: List<MovieItem>,
    onClearSearch: () -> Unit,
    onMovieClick: (MovieItem) -> Unit
) {
    val isTv = LocalIsTvMode.current
    val horizPad = if (isTv) 24.dp else 14.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizPad)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kết quả cho \"$query\" (${results.size})",
                color = ThaiGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = onClearSearch)
                    .background(CinemaSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "✖ Đóng tìm kiếm", color = TextPrimary, fontSize = 12.sp)
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ThaiGold)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Không tìm thấy phim phù hợp. Hãy bấm Tìm hoặc thử từ khóa tiếng Anh.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val columns = if (isTv) 5 else 4
                items(results.chunked(columns)) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (m in rowItems) {
                            MovieCard(
                                movie = m,
                                onClick = { onMovieClick(m) }
                            )
                        }
                    }
                }
            }
        }
    }
}
