package com.phyll.filmhub.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.model.WatchProgress
import com.phyll.filmhub.ui.i18n.LocalAppStrings
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    movie: MovieItem,
    isResolving: Boolean,
    isFavorite: Boolean,
    isTvHardware: Boolean = true,
    controlTargetMode: ControlTargetMode = ControlTargetMode.LOCAL_DEVICE,
    selectedTvName: String = "",
    savedWatchProgress: WatchProgress? = null,
    onPlayEpisode: (Episode) -> Unit,
    onPlayOnPhone: (Episode) -> Unit = onPlayEpisode,
    onPlayOnTv: (Episode) -> Unit = onPlayEpisode,
    onToggleFavorite: () -> Unit,
    onOpenQrCompanion: () -> Unit,
    onBack: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val s = LocalAppStrings.current
    val playFocusRequester = remember { FocusRequester() }
    var selectedServerIndex by remember(movie.id) { mutableIntStateOf(0) }
    var expandDescription by remember(movie.id) { mutableStateOf(false) }

    LaunchedEffect(isResolving, movie.servers.size) {
        if (!isResolving && movie.firstPlayableEpisode != null) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaObsidian)
    ) {
        val isWide = maxWidth >= 600.dp || isTv
        val horizPad = if (isWide) 32.dp else 16.dp
        val allEps = remember(movie.id, movie.servers) { movie.servers.flatMap { it.episodes } }
        val resumeEp = remember(savedWatchProgress, allEps) {
            if (savedWatchProgress != null) {
                allEps.firstOrNull {
                    it.id == savedWatchProgress.episodeId || it.streamUrl == savedWatchProgress.streamUrl
                }
            } else null
        }
        val firstEp = resumeEp ?: movie.firstPlayableEpisode
        val primaryCtaLabel = if (resumeEp != null && savedWatchProgress != null && savedWatchProgress.positionMs >= 3_000L) {
            val mins = savedWatchProgress.positionMs / 60_000L
            val secs = (savedWatchProgress.positionMs / 1000L) % 60L
            s.continueEpFmt.format(resumeEp.name, mins, secs)
        } else if (firstEp != null) {
            s.watchNowEpFmt.format(firstEp.name)
        } else {
            s.watchNow
        }

        // Full-bleed atmospheric backdrop header
        val heroUrl = movie.backdropUrl.ifBlank { movie.posterUrl }
        if (heroUrl.isNotBlank()) {
            AsyncImage(
                model = heroUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWide) 340.dp else 280.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWide) 355.dp else 295.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66141414),
                            Color(0xCC141414),
                            CinemaObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizPad, vertical = 14.dp)
        ) {
            // 1. Clean Floating Top Bar (Back on left, Favorite + Optional Secondary Cast on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = EmeraldNeon,
                            unfocusedBorderColor = CinemaGlassBorder,
                            onClick = onBack
                        )
                        .background(Color(0xCC0F1220))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = s.backBtn,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = s.backBtn, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .tvFocusableClickable(
                                shape = CircleShape,
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = CinemaGlassBorder,
                                onClick = onToggleFavorite
                            )
                            .background(Color(0xCC0F1220)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Yêu thích",
                            tint = if (isFavorite) Color(0xFFEF4444) else TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Secondary Optional Cast-to-TV / QR action
                    if (!isTvHardware && firstEp != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .tvFocusableClickable(
                                    shape = CircleShape,
                                    focusedBorderColor = VioletGlow,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = { onPlayOnTv(firstEp) }
                                )
                                .background(Color(0xCC0F1220)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Phát lên TV",
                                tint = VioletSoft,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else if (isTvHardware) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .tvFocusableClickable(
                                    shape = CircleShape,
                                    focusedBorderColor = VioletGlow,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = onOpenQrCompanion
                                )
                                .background(Color(0xCC0F1220)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "QR Companion",
                                tint = VioletSoft,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isWide) 22.dp else 18.dp))

            // 2. Responsive Hero Info Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isWide) 22.dp else 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(if (isWide) 186.dp else 108.dp)
                        .height(if (isWide) 268.dp else 158.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, CinemaGlassBorder, RoundedCornerShape(16.dp))
                        .background(CinemaSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(32.dp)
                    )
                    if (movie.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = movie.posterUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldPrimary)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = s.aiSubMultiBadge,
                                color = CinemaObsidian,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        if (movie.year.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CinemaSurfaceElevated)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = movie.year,
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = movie.title,
                        color = TextPrimary,
                        fontSize = if (isWide) 25.sp else 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (movie.originalTitle.isNotBlank() && !movie.originalTitle.equals(movie.title, ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = movie.originalTitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = listOfNotNull(
                            movie.country.displayName.takeIf { it.isNotBlank() },
                            movie.duration.takeIf { it.isNotBlank() },
                            movie.genre.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        color = VioletSoft,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isWide && movie.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = movie.description,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Clean Primary Action CTA (Direct Play on This Device FIRST; Cast to TV secondary!)
            if (isResolving) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = EmeraldNeon,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Đang chuẩn bị luồng phát & phụ đề...",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (firstEp != null) {
                if (!isWide) {
                    // Mobile portrait: Crisp Netflix Pure White Primary Play CTA + sleek gray Cast button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(playFocusRequester)
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(6.dp),
                                focusedBorderColor = EmeraldNeon,
                                unfocusedBorderColor = Color.Transparent,
                                onClick = { onPlayOnPhone(firstEp) }
                            )
                            .background(Color.White)
                            .padding(vertical = 13.dp, horizontal = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = primaryCtaLabel,
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isTvHardware && selectedTvName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(14.dp),
                                    focusedBorderColor = VioletGlow,
                                    unfocusedBorderColor = VioletPrimary.copy(alpha = 0.45f),
                                    onClick = { onPlayOnTv(firstEp) }
                                )
                                .background(VioletPrimary.copy(alpha = 0.14f))
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = null,
                                tint = VioletSoft,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Phát lên TV: $selectedTvName",
                                color = VioletSoft,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .focusRequester(playFocusRequester)
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(6.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = Color.Transparent,
                                    onClick = { onPlayOnPhone(firstEp) }
                                )
                                .background(Color.White)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = primaryCtaLabel,
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        if (!isTvHardware && selectedTvName.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(99.dp),
                                        focusedBorderColor = VioletGlow,
                                        unfocusedBorderColor = VioletPrimary.copy(alpha = 0.5f),
                                        onClick = { onPlayOnTv(firstEp) }
                                    )
                                    .background(VioletPrimary.copy(alpha = 0.16f))
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Cast, null, tint = VioletSoft, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Phát lên $selectedTvName",
                                    color = VioletSoft,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Nguồn phim này hiện chưa có luồng trực tiếp.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Mobile synopsis (expandable so episodes stay reachable without endless scrolling!)
            if (!isWide && movie.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CinemaGlass)
                        .border(1.dp, CinemaGlassBorder, RoundedCornerShape(14.dp))
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(14.dp),
                            unfocusedBorderColor = Color.Transparent,
                            onClick = { expandDescription = !expandDescription }
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = movie.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        maxLines = if (expandDescription) 20 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (movie.description.length > 130) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (expandDescription) "Thu gọn" else "Xem thêm nội dung ›",
                            color = EmeraldNeon,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Server & Episode Selector
            if (movie.servers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = "Danh Sách Tập",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (movie.servers.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        movie.servers.forEachIndexed { idx, server ->
                            val active = idx == selectedServerIndex
                            Box(
                                modifier = Modifier
                                    .tvFocusableClickable(
                                        shape = RoundedCornerShape(99.dp),
                                        focusedBorderColor = EmeraldNeon,
                                        unfocusedBorderColor = if (active) EmeraldNeon else CinemaGlassBorder,
                                        onClick = { selectedServerIndex = idx }
                                    )
                                    .background(if (active) EmeraldPrimary else CinemaSurfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = server.serverName,
                                    color = if (active) CinemaObsidian else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val currentServer = movie.servers.getOrNull(selectedServerIndex) ?: movie.servers.first()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (ep in currentServer.episodes) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 74.dp)
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(12.dp),
                                    focusedBorderColor = EmeraldNeon,
                                    unfocusedBorderColor = CinemaGlassBorder,
                                    onClick = { onPlayOnPhone(ep) }
                                )
                                .background(CinemaSurface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ep.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
