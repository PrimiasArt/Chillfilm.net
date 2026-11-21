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
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isTv = LocalIsTvMode.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardWidth = when {
        isTv || screenWidthDp >= 840 -> 166.dp
        screenWidthDp >= 520 -> 142.dp
        else -> 122.dp
    }
    val posterHeight = when {
        isTv || screenWidthDp >= 840 -> 232.dp
        screenWidthDp >= 520 -> 198.dp
        else -> 172.dp
    }
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .tvFocusableClickable(
                shape = cardShape,
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = CinemaGlassBorder,
                borderWidth = 1.5.dp,
                focusedScale = 1.04f,
                onFocused = onFocused,
                onClick = onClick
            )
            .background(CinemaGlass)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(CinemaSurfaceElevated, Color(0xFF090C12))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
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

            // Smooth cinema bottom vignette
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x9906080D),
                                Color(0xEE06080D)
                            )
                        )
                    )
            )

            // Single clean top-right AI SUB / LIVE badge
            val isLive = movie.contentType == ContentType.LIVE_CHANNEL
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isLive) LiveRed
                        else Color(0xD9101526)
                    )
                    .border(
                        width = 0.7.dp,
                        color = if (isLive) Color.Transparent else EmeraldPrimary.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isLive) "LIVE" else "AI SUB",
                    color = if (isLive) Color.White else EmeraldNeon,
                    fontSize = 8.5.sp,
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
                        .padding(horizontal = 7.dp, vertical = if (progressFraction > 0.01f) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xB806080D))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = cleanQuality.uppercase(),
                        color = TextPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Watch progress bar
            if (progressFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                            .height(3.dp)
                            .background(BrandEmeraldVioletBrush)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CinemaSurface)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = movie.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    movie.year.takeIf { it.isNotBlank() },
                    movie.country.displayName.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
