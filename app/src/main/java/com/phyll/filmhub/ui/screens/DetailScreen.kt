package com.phyll.filmhub.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.model.ControlTargetMode
import com.phyll.filmhub.model.Episode
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.CinemaSurface
import com.phyll.filmhub.ui.theme.CinemaSurfaceElevated
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
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
    onPlayEpisode: (Episode) -> Unit,
    onPlayOnPhone: (Episode) -> Unit = onPlayEpisode,
    onPlayOnTv: (Episode) -> Unit = onPlayEpisode,
    onToggleFavorite: () -> Unit,
    onOpenQrCompanion: () -> Unit,
    onBack: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val playFocusRequester = remember { FocusRequester() }
    var selectedServerIndex by remember(movie.id) { mutableIntStateOf(0) }

    LaunchedEffect(isResolving, movie.servers.size) {
        if (!isResolving && movie.firstPlayableEpisode != null) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaObsidian)
    ) {
        // Full-bleed atmospheric backdrop header
        val heroUrl = movie.backdropUrl.ifBlank { movie.posterUrl }
        if (heroUrl.isNotBlank()) {
            AsyncImage(
                model = heroUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xAD070A0F),
                            Color(0xE0070A0F),
                            CinemaObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTv) 36.dp else 18.dp, vertical = 20.dp)
        ) {
            // Top Glass Back Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = ThaiGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            onClick = onBack
                        )
                        .background(Color(0xD9111723))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Quay lại", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = movie.providerName.uppercase(),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Info Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (isTv || movie.posterUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .width(if (isTv) 196.dp else 124.dp)
                            .height(if (isTv) 280.dp else 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .background(Color(0xFF0E131D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
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
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ThaiGold)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (movie.hasNativeThaiSub) "SUB ไทย GỐC" else "AI SUB ไทย",
                                color = CinemaObsidian,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp
                            )
                        }

                        Text(
                            text = movie.country.displayName.uppercase(),
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )

                        if (movie.year.isNotBlank()) {
                            Text(text = "• ${movie.year}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = movie.title,
                        color = TextPrimary,
                        fontSize = if (isTv) 26.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (movie.originalTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = movie.originalTitle,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = listOfNotNull(
                            movie.genre.takeIf { it.isNotBlank() },
                            movie.duration.takeIf { it.isNotBlank() },
                            movie.quality.takeIf { it.isNotBlank() }
                        ).joinToString("  •  "),
                        color = ThaiGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = movie.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Action Buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val firstEp = movie.firstPlayableEpisode
            if (isResolving) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = ThaiGold,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Đang trích xuất luồng phát & phụ đề...",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            } else if (firstEp != null) {
                val primaryPlayLabel = if (!isTvHardware && controlTargetMode == ControlTargetMode.REMOTE_TV) {
                    "📺 Phát Lên ${selectedTvName.ifBlank { "TV" }} (${firstEp.name})"
                } else if (!isTvHardware) {
                    "📱 Xem Trên ĐT (${firstEp.name})"
                } else {
                    "Phát Ngay (${firstEp.name})"
                }
                Row(
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .tvFocusableClickable(
                            shape = RoundedCornerShape(99.dp),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = EmeraldNeon,
                            onClick = { onPlayEpisode(firstEp) }
                        )
                        .background(BrandEmeraldVioletBrush)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = primaryPlayLabel,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (!isTvHardware) {
                    val altOnTv = controlTargetMode != ControlTargetMode.REMOTE_TV
                    Row(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(12.dp),
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = ElectricCyan,
                                onClick = {
                                    if (altOnTv) onPlayOnTv(firstEp) else onPlayOnPhone(firstEp)
                                }
                            )
                            .background(ElectricCyan.copy(alpha = 0.2f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (altOnTv) "📺 Phát Lên ${selectedTvName.ifBlank { "TV" }}" else "📱 Xem Trên ĐT",
                            color = ElectricCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurfaceElevated)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Nguồn phim này hiện không có luồng MP4/HLS trực tiếp.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Favorite Toggle
            Row(
                modifier = Modifier
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(12.dp),
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF2B3548),
                        onClick = onToggleFavorite
                    )
                    .background(CinemaSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFEF4444) else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFavorite) "Đã Lưu Yêu Thích" else "Lưu Yêu Thích",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // QR Phone Sub Upload Helper
            Row(
                modifier = Modifier
                    .tvFocusableClickable(
                        shape = RoundedCornerShape(12.dp),
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF2B3548),
                        onClick = onOpenQrCompanion
                    )
                    .background(CinemaSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gửi Sub Thái từ Điện Thoại",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Server & Episode Selection
        if (movie.servers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Danh Sách Server & Tập Phim",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Server Tabs
            if (movie.servers.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    movie.servers.forEachIndexed { idx, server ->
                        val active = idx == selectedServerIndex
                        Box(
                            modifier = Modifier
                                .tvFocusableClickable(
                                    shape = RoundedCornerShape(8.dp),
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = if (active) ThaiGold else Color(0xFF2B3548),
                                    onClick = { selectedServerIndex = idx }
                                )
                                .background(if (active) ThaiGold else CinemaSurface)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = server.serverName,
                                color = if (active) CinemaObsidian else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            val currentServer = movie.servers.getOrNull(selectedServerIndex) ?: movie.servers.first()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (ep in currentServer.episodes) {
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(
                                shape = RoundedCornerShape(10.dp),
                                focusedBorderColor = ThaiGold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                                onClick = { onPlayEpisode(ep) }
                            )
                            .background(Color(0xFF0E131D))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = ep.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (ep.subtitles.isNotEmpty()) {
                                Text(
                                    text = "${ep.subtitles.size} track phụ đề sẵn sàng",
                                    color = ElectricCyan,
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
