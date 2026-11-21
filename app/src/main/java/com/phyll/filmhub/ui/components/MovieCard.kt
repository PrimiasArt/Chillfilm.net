package com.phyll.filmhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.phyll.filmhub.model.ContentType
import com.phyll.filmhub.model.MovieItem
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
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
import com.phyll.filmhub.ui.theme.tvFocusableClickable

@Composable
fun MovieCard(
    movie: MovieItem,
    progressFraction: Float = 0f,
    rankNumber: Int? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardWidth = when {
        isTv || screenWidthDp >= 840 -> 162.dp
        screenWidthDp >= 520 -> 136.dp
        else -> 114.dp
    }
    val posterHeight = when {
        isTv || screenWidthDp >= 840 -> 236.dp
        screenWidthDp >= 520 -> 196.dp
        else -> 166.dp
    }
    // Crisp Netflix-style tight poster corners
    val cardShape = RoundedCornerShape(8.dp)

    Row(verticalAlignment = Alignment.Bottom) {
        // Netflix Iconic Top 10 Rank Number beside poster
        if (rankNumber != null && rankNumber in 1..10) {
            Text(
                text = rankNumber.toString(),
                color = EmeraldNeon.copy(alpha = 0.88f),
                fontSize = if (isTv) 68.sp else 54.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp,
                modifier = Modifier.padding(end = 2.dp, bottom = 18.dp)
            )
        }

        Column(
            modifier = Modifier
                .width(cardWidth)
                .tvFocusableClickable(
                    shape = cardShape,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0x18FFFFFF),
                    borderWidth = if (isTv) 2.dp else 1.dp,
                    focusedScale = 1.06f,
                    onFocused = onFocused,
                    onClick = onClick
                )
                .background(CinemaSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(posterHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(CinemaSurfaceElevated, Color(0xFF0E0E0E))
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = movie.title.take(24),
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                if (movie.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = movie.posterUrl,
                        contentDescription = movie.title,
                        contentScale = if (movie.contentType == ContentType.LIVE_CHANNEL) {
                            ContentScale.Fit
                        } else {
                            ContentScale.Crop
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Clean Netflix bottom gradient scrim
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(62.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x99141414),
                                    Color(0xF0141414)
                                )
                            )
                        )
                )

                // Signature Netflix-Green "NKI" Top-Left Corner Emblem (replaces Netflix red N!)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC0A0A0A))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "NKI",
                        color = EmeraldNeon,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }

                // Subtle Top-Right LIVE / AI SUB pill
                val isLive = movie.contentType == ContentType.LIVE_CHANNEL
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLive) LiveRed else Color(0xCC141414))
                        .border(
                            width = 0.6.dp,
                            color = if (isLive) Color.Transparent else EmeraldPrimary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "AI",
                        color = if (isLive) Color.White else EmeraldNeon,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }

                // Bottom-left Quality / Episode tag
                if (movie.quality.isNotBlank()) {
                    val cleanQuality = movie.quality
                        .replace("Sub ไทย Gốc", "FHD", ignoreCase = true)
                        .replace("AI Sub ไทย", "AI", ignoreCase = true)
                        .replace("Sub ไทย", "HD", ignoreCase = true)
                        .trim()
                        .take(10)
                        .ifBlank { "HD" }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 6.dp, vertical = if (progressFraction > 0.01f) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xCC0A0A0A))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = cleanQuality.uppercase(),
                            color = Color(0xFFE5E5E5),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Authentic Netflix-style Watch Progress Bar (Signature Green!)
                if (progressFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                                .height(3.5.dp)
                                .background(EmeraldNeon)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurface)
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            ) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = listOfNotNull(
                        movie.year.takeIf { it.isNotBlank() },
                        movie.providerName.takeIf { it.isNotBlank() && it != "KKPhim" }
                    ).joinToString(" • ").ifBlank { "HD • AI Sub" },
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
