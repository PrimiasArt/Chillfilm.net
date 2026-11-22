package com.phyll.filmhub.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.R
import com.phyll.filmhub.data.CatalogSection
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.CountryCategory
import com.phyll.filmhub.model.ManagedTvDevice
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.WatchProgress
import com.phyll.filmhub.ui.components.MovieCard
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
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.VioletPrimary
import com.phyll.filmhub.ui.theme.VioletSoft
import com.phyll.filmhub.ui.theme.tvFocusableClickable

@Composable
fun HomeScreen(
    selectedCountry: CountryCategory,
    sections: List<CatalogSection>,
    watchHistory: List<WatchProgress>,
    myList: List<MovieItem> = emptyList(),
    isLoading: Boolean,
    searchQuery: String,
    searchResults: List<MovieItem>,
    isSearching: Boolean,
    aiVibeSummary: String = "",
    hasSessionGeminiKey: Boolean,
    activeGeminiModel: String,
    companionLanUrl: String? = null,
    isTvHardware: Boolean = true,
    controlTargetMode: ControlTargetMode = ControlTargetMode.LOCAL_DEVICE,
    selectedTvDevice: ManagedTvDevice? = null,
    isRemoteTvOnline: Boolean = false,
    currentAppLanguage: AppLanguage = AppLanguage.EN,
    onSelectAppLanguage: (AppLanguage) -> Unit = {},
    onSelectCountry: (CountryCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    onExecuteAiVibeSearch: (String) -> Unit = onExecuteSearch,
    onMovieClick: (MovieItem) -> Unit,
    onQuickPlayMovie: (MovieItem) -> Unit,
    onResumeHistory: (WatchProgress) -> Unit,
    onRemoveContinueWatching: (String) -> Unit = {},
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
    val s = LocalAppStrings.current
    val focusManager = LocalFocusManager.current
    var showSearchBar by remember { mutableStateOf(searchQuery.isNotBlank() || isTv) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val heroCandidates = remember(sections) {
        sections.firstOrNull()?.movies?.take(6).orEmpty()
    }
    var spotlightMovie by remember(selectedCountry, heroCandidates) {
        mutableStateOf(heroCandidates.firstOrNull())
    }
    if (spotlightMovie == null && heroCandidates.isNotEmpty()) {
        spotlightMovie = heroCandidates.firstOrNull()
    }
    LaunchedEffect(heroCandidates, isTv, searchQuery) {
        if (!isTv && heroCandidates.size > 1 && searchQuery.isBlank()) {
            var idx = 0
            while (true) {
                kotlinx.coroutines.delay(8_000L)
                idx = (idx + 1) % heroCandidates.size
                spotlightMovie = heroCandidates[idx]
            }
        }
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
                showSearchBar = true
                onExecuteSearch(spoken)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AmbientCinemaBackdropBrush)
    ) {
        val isWide = maxWidth >= 600.dp || isTv
        val horizPad = if (isWide) 24.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Minimalist, Overflow-Proof NKI x ChillFilm Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizPad, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Lockup: Authentic NKI x ChillFilm Icon + Wordmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_chillfilm_nki),
                        contentDescription = "ChillFilm by NKI",
                        modifier = Modifier
                            .size(if (isWide) 38.dp else 34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CHILLFILM",
                                color = EmeraldNeon,
                                fontSize = if (isWide) 20.sp else 17.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.6.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EmeraldPrimary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "NKI",
                                    color = Color(0xFF0A0A0A),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.7.sp
                                )
                            }
                        }
                        if (isWide && !companionLanUrl.isNullOrBlank()) {
                            Text(
                                text = "NKI Ecosystem • $companionLanUrl",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right Action Cluster: Compact Language Switcher + Search + Cast + Refresh (zero overflow!)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick 5-Language UI Switcher Pill (EN / VI / TH / ZH / KO)
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(99.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = if (showLanguagePicker) EmeraldNeon else CinemaGlassBorder,
                                onClick = { showLanguagePicker = !showLanguagePicker }
                            )
                            .background(if (showLanguagePicker) EmeraldPrimary.copy(alpha = 0.22f) else CinemaGlass)
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = s.languageLabel,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = currentAppLanguage.shortBadge,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }

                    HeaderGlassIconBtn(
                        icon = Icons.Default.Search,
                        contentDesc = s.searchAction,
                        tint = if (showSearchBar || searchQuery.isNotBlank()) EmeraldNeon else TextPrimary,
                        active = showSearchBar || searchQuery.isNotBlank(),
                        onClick = {
                            if (showSearchBar && searchQuery.isNotBlank()) {
                                onSearchQueryChange("")
                            }
                            showSearchBar = !showSearchBar
                        }
                    )

                    // Optional Secondary TV Cast / Stream Button
                    HeaderGlassIconBtn(
                        icon = if (isTvHardware) Icons.Default.Tv else Icons.Default.Cast,
                        contentDesc = s.castTvDesc,
                        tint = if (isRemoteTvOnline) EmeraldNeon else VioletSoft,
                        showDot = isRemoteTvOnline,
                        onClick = {
                            if (isTvHardware) onOpenQrCompanion() else onOpenTvManager()
                        }
                    )

                    HeaderGlassIconBtn(
                        icon = Icons.Default.Refresh,
                        contentDesc = s.refreshDesc,
                        tint = TextSecondary,
                        onClick = onRefresh
                    )
                }
            }

            // 1b. Quick 5-Language Interface Switcher Strip (EN - VI - TH - ZH - KO)
            AnimatedVisibility(
                visible = showLanguagePicker,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizPad, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    items(AppLanguage.entries) { lang ->
                        val active = lang == currentAppLanguage
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(99.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = if (active) EmeraldNeon else CinemaGlassBorder,
                                    onClick = {
                                        onSelectAppLanguage(lang)
                                        showLanguagePicker = false
                                    }
                                )
                                .background(
                                    if (active) BrandEmeraldVioletBrush
                                    else Brush.horizontalGradient(listOf(CinemaSurfaceElevated, CinemaSurfaceElevated))
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "${lang.shortBadge} • ${lang.nativeName}",
                                color = if (active) Color.White else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 2. Expandable Sleek Glass Search Bar (only takes vertical space when opened!)
            AnimatedVisibility(
                visible = showSearchBar || searchQuery.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizPad)
                        .padding(bottom = 8.dp),
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
                                text = s.searchPlaceholder,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = EmeraldNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                if (searchQuery.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .tvFocusableClickable(shape = CircleShape) {
                                                onSearchQueryChange("")
                                            }
                                            .padding(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Xóa",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .tvFocusableClickable(
                                            shape = CircleShape,
                                            focusedBorderColor = VioletGlow,
                                            onClick = {
                                                runCatching {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(
                                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                        )
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên phim...")
                                                    }
                                                    onBeforeLaunchSystemOverlay()
                                                    voiceLauncher.launch(intent)
                                                }.onFailure {
                                                    onAfterSystemOverlayResult()
                                                    onVoiceError()
                                                }
                                            }
                                        )
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Tìm bằng giọng nói",
                                        tint = VioletSoft,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            onExecuteSearch(searchQuery)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = CinemaGlassBorder,
                            focusedContainerColor = CinemaSurface,
                            unfocusedContainerColor = CinemaSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(14.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = EmeraldNeon.copy(alpha = 0.6f),
                                onClick = {
                                    focusManager.clearFocus()
                                    onExecuteSearch(searchQuery)
                                }
                            )
                            .background(BrandEmeraldVioletBrush)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.searchAction,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // ✨ AI Vibe Natural-Language Search Button (Touch & TV D-Pad)
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(14.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = VioletSoft.copy(alpha = 0.65f),
                                onClick = {
                                    focusManager.clearFocus()
                                    onExecuteAiVibeSearch(searchQuery)
                                }
                            )
                            .background(VioletPrimary.copy(alpha = 0.34f))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✨ AI",
                            color = EmeraldNeon,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // 3. Clean Horizontally Scrollable Category Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizPad, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CountryCategory.entries) { country ->
                    val isSelected = country == selectedCountry && searchQuery.isBlank()
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(99.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = if (isSelected) EmeraldNeon.copy(alpha = 0.7f) else CinemaGlassBorder,
                                focusedScale = 1.04f,
                                onClick = { onSelectCountry(country) }
                            )
                            .background(
                                brush = if (isSelected) BrandEmeraldVioletBrush
                                else Brush.horizontalGradient(listOf(CinemaGlass, CinemaGlass))
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = s.countryName(country),
                            color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Main Scrollable Catalog OR Responsive Search Results Grid
            if (searchQuery.isNotBlank()) {
                SearchResultsBody(
                    query = searchQuery,
                    isSearching = isSearching,
                    aiVibeSummary = aiVibeSummary,
                    results = searchResults,
                    horizPad = horizPad,
                    onClearSearch = { onSearchQueryChange("") },
                    onMovieClick = onMovieClick
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    spotlightMovie?.let { hero ->
                        item(key = "hero_banner") {
                            HeroSpotlightBanner(
                                movie = hero,
                                isWide = isWide,
                                horizPad = horizPad,
                                onPlayClick = { onQuickPlayMovie(hero) },
                                onDetailClick = { onMovieClick(hero) }
                            )
                        }
                    }

                    if (watchHistory.isNotEmpty() && selectedCountry == CountryCategory.ALL) {
                        item(key = "continue_watching") {
                            ContinueWatchingRow(
                                history = watchHistory,
                                horizPad = horizPad,
                                onResume = onResumeHistory,
                                onRemove = onRemoveContinueWatching
                            )
                        }
                    }

                    if (myList.isNotEmpty() && selectedCountry == CountryCategory.ALL) {
                        item(key = "my_list_row") {
                            CatalogSectionRow(
                                section = CatalogSection(
                                    id = "my_list_cloud",
                                    title = "❤️ My List",
                                    subtitle = "Saved for later • Synced across Web, Android & TV",
                                    movies = myList
                                ),
                                horizPad = horizPad,
                                showTopTenRanks = false,
                                onMovieFocused = { focused -> spotlightMovie = focused },
                                onMovieClick = onMovieClick
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
                                    CircularProgressIndicator(color = EmeraldNeon)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = s.loadingCatalog,
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(sections, key = { _, sec -> sec.id }) { idx, section ->
                        CatalogSectionRow(
                            section = section,
                            horizPad = horizPad,
                            showTopTenRanks = (idx == 0 && selectedCountry == CountryCategory.ALL),
                            onMovieFocused = { focused -> spotlightMovie = focused },
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }
        }

        // 5. Sleek Floating 4-Tab Spatial Glass Bottom Navigation Bar (100% Equal-Weight, Zero Clipping!)
        FloatingBottomNavBar(
            selectedCountry = selectedCountry,
            isSearchActive = searchQuery.isNotBlank(),
            hasGeminiKey = hasSessionGeminiKey,
            isWide = isWide,
            onGoDiscover = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.ALL)
            },
            onGoSeries = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.LIVE_TV)
            },
            onGoYouTube = {
                onSearchQueryChange("")
                onSelectCountry(CountryCategory.YOUTUBE)
            },
            onOpenSettings = onOpenSettingsSources,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun HeaderGlassIconBtn(
    icon: ImageVector,
    contentDesc: String,
    tint: Color,
    active: Boolean = false,
    showDot: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .tvFocusableClickable(
                shape = CircleShape,
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = if (active) EmeraldNeon.copy(alpha = 0.65f) else CinemaGlassBorder,
                onClick = onClick
            )
            .background(if (active) EmeraldPrimary.copy(alpha = 0.18f) else CinemaGlass),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(EmeraldNeon)
            )
        }
    }
}

@Composable
private fun FloatingBottomNavBar(
    selectedCountry: CountryCategory,
    isSearchActive: Boolean,
    hasGeminiKey: Boolean,
    isWide: Boolean,
    onGoDiscover: () -> Unit,
    onGoSeries: () -> Unit,
    onGoYouTube: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isWide) 64.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF5121212))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab(
                icon = Icons.Default.Home,
                label = s.navDiscover,
                active = !isSearchActive && selectedCountry != CountryCategory.LIVE_TV && selectedCountry != CountryCategory.YOUTUBE,
                accentColor = EmeraldNeon,
                modifier = Modifier.weight(1f),
                onClick = onGoDiscover
            )
            BottomNavTab(
                icon = Icons.Default.Movie,
                label = s.navSeries,
                active = !isSearchActive && selectedCountry == CountryCategory.LIVE_TV,
                accentColor = EmeraldNeon,
                modifier = Modifier.weight(1f),
                onClick = onGoSeries
            )
            BottomNavTab(
                icon = Icons.Default.SmartDisplay,
                label = s.navYouTube,
                active = !isSearchActive && selectedCountry == CountryCategory.YOUTUBE,
                accentColor = VioletSoft,
                modifier = Modifier.weight(1f),
                onClick = onGoYouTube
            )
            BottomNavTab(
                icon = Icons.Default.Settings,
                label = s.navSettings,
                active = false,
                accentColor = if (hasGeminiKey) EmeraldNeon else VioletSoft,
                showBadgeDot = hasGeminiKey,
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun BottomNavTab(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    showBadgeDot: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .tvFocusableClickable(
                shape = RoundedCornerShape(18.dp),
                focusedBorderColor = accentColor,
                unfocusedBorderColor = if (active) accentColor.copy(alpha = 0.35f) else Color.Transparent,
                focusedScale = 1.03f,
                onClick = onClick
            )
            .background(if (active) accentColor.copy(alpha = 0.16f) else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) accentColor else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            if (showBadgeDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(EmeraldNeon)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) TextPrimary else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeroSpotlightBanner(
    movie: MovieItem,
    isWide: Boolean,
    horizPad: androidx.compose.ui.unit.Dp,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val s = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizPad)
            .height(if (isWide) 308.dp else 284.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
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

        // Authentic Netflix bottom & side obsidian fade into #141414
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x22141414),
                            Color(0x88141414),
                            Color(0xF5141414)
                        )
                    )
                )
        )
        if (isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF0141414),
                                Color(0x99141414),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isWide) 24.dp else 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Netflix-Green "NKI SERIES / AI CINEMA" eyebrow tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldPrimary)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "NKI • ${s.aiSubMultiBadge}",
                        color = Color(0xFF0A0A0A),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                Text(
                    text = listOfNotNull(
                        "98% Match",
                        s.countryName(movie.country).takeIf { it.isNotBlank() },
                        movie.year.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    color = EmeraldNeon,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = movie.title,
                color = Color.White,
                fontSize = if (isWide) 28.sp else 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = if (isWide) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isWide && movie.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = movie.description,
                    color = Color(0xFFE5E5E5),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.74f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Authentic Netflix Button Pair: Crisp Pure White "▶ Play" + Translucent Gray "ⓘ Details"
            Row(
                modifier = if (isWide) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = (if (isWide) Modifier else Modifier.weight(1.25f))
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(6.dp),
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = Color.Transparent,
                            onClick = onPlayClick
                        )
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = s.watchNow,
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.5.sp,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = (if (isWide) Modifier else Modifier.weight(1f))
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(6.dp),
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            onClick = onDetailClick
                        )
                        .background(Color(0xB84A4A4D))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = s.detailsBtn,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    history: List<WatchProgress>,
    horizPad: androidx.compose.ui.unit.Dp,
    onResume: (WatchProgress) -> Unit,
    onRemove: (String) -> Unit = {}
) {
    val s = LocalAppStrings.current
    Column {
        Row(
            modifier = Modifier.padding(horizontal = horizPad, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandEmeraldVioletBrush)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = s.continueWatching,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    providerName = s.resumeLabel
                )
                Box {
                    MovieCard(
                        movie = pseudoMovie,
                        progressFraction = item.progressFraction,
                        onClick = { onResume(item) }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .tvFocusableClickable(
                                shape = CircleShape,
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = CinemaGlassBorder,
                                onClick = { onRemove(item.movieId) }
                            )
                            .background(Color(0xCC0A0A0E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Xóa khỏi Tiếp tục xem",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionRow(
    section: CatalogSection,
    horizPad: androidx.compose.ui.unit.Dp,
    showTopTenRanks: Boolean = false,
    onMovieFocused: (MovieItem) -> Unit,
    onMovieClick: (MovieItem) -> Unit
) {
    val s = LocalAppStrings.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (showTopTenRanks) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EmeraldPrimary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TOP 10",
                            color = Color(0xFF0A0A0A),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = section.title,
                    color = TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${section.movies.size} ${s.moviesCountSuffix}",
                color = EmeraldNeon,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(section.movies, key = { _, m -> m.id }) { idx, movie ->
                MovieCard(
                    movie = movie,
                    rankNumber = if (showTopTenRanks && idx < 10) idx + 1 else null,
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
    aiVibeSummary: String = "",
    results: List<MovieItem>,
    horizPad: androidx.compose.ui.unit.Dp,
    onClearSearch: () -> Unit,
    onMovieClick: (MovieItem) -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizPad)
    ) {
        if (aiVibeSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VioletPrimary.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldNeon.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = aiVibeSummary,
                    color = EmeraldNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${s.searchResultsPrefix} \"$query\" (${results.size})",
                color = EmeraldNeon,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .tvFocusableClickable(shape = RoundedCornerShape(99.dp), onClick = onClearSearch)
                    .background(CinemaSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = s.closeAction, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldNeon)
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = s.noSearchResults,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 126.dp),
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results, key = { it.id }) { movie ->
                    MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}
