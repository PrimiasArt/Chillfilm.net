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
import com.phyll.filmhub.ui.theme.ElectricCyan
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.LiveRed
import com.phyll.filmhub.ui.theme.LocalIsTvMode
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.ThaiGold
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
    val cardWidth = if (isTv) 172.dp else 148.dp
    val posterHeight = if (isTv) 236.dp else 206.dp
    val cardShape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .tvFocusableClickable(
                shape = cardShape,
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = CinemaGlassBorder,
                borderWidth = 2.dp,
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
            // Subtle luxury placeholder behind AsyncImage
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = movie.title.take(24),
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
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
                    .height(74.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x9906080D),
                                Color(0xEB06080D)
                            )
                        )
                    )
            )

            // Top Micro-Badges Row (Glass Country Pill + Crisp Sub/LIVE Badge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xBF080B11))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = movie.country.displayName,
                        color = Color(0xFFE2E8F0),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }

                val badgeBg = when {
                    movie.contentType == ContentType.LIVE_CHANNEL -> LiveRed
                    movie.hasNativeThaiSub -> ThaiGold
                    else -> VioletPrimary.copy(alpha = 0.85f)
                }
                val badgeFg = when {
                    movie.contentType == ContentType.LIVE_CHANNEL -> Color.White
                    movie.hasNativeThaiSub -> CinemaObsidian
                    else -> Color.White
                }
                val badgeText = when {
                    movie.contentType == ContentType.LIVE_CHANNEL -> "LIVE"
                    movie.hasNativeThaiSub -> "SUB ไทย"
                    else -> "AI ไทย"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .border(
                            width = 0.5.dp,
                            color = if (movie.hasNativeThaiSub) Color.Transparent else VioletGlow.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // Bottom-left Quality & Year micro-tag on the poster vignette
            if (movie.quality.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = if (progressFraction > 0.01f) 9.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = movie.quality.take(10).uppercase(),
                        color = TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Watch progress indicator bar
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    movie.providerName.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
