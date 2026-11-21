package com.phyll.filmhub.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.phyll.filmhub.R
import com.phyll.filmhub.model.DeviceUiMode
import com.phyll.filmhub.model.SubtitleFontChoice

// ============================================================================================
// Netflix-Inspired Cinema Palette — Pitch Obsidian (#141414) x Signature Emerald Green (#00E676)
// Replaces Netflix Red (#E50914) 1-for-1 with vivid NKI Cinema Green while keeping pure dark contrast
// ============================================================================================
val CinemaObsidian = Color(0xFF141414)
val CinemaSurface = Color(0xFF181818)
val CinemaSurfaceElevated = Color(0xFF262626)
val CinemaGlass = Color(0xD9141414)
val CinemaGlassBorder = Color(0x26FFFFFF)

val EmeraldPrimary = Color(0xFF00C853)
val EmeraldNeon = Color(0xFF00E676)
val VioletPrimary = Color(0xFF00A844)
val VioletGlow = Color(0xFF00E676)
val VioletSoft = Color(0xFF46D369)
val AmberBoost = Color(0xFFFBBF24)

// Unified palette aliases across all screens — 100% cohesive Netflix-Green cinema system
val ThaiGold = EmeraldNeon
val ElectricCyan = VioletSoft

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)
val LiveRed = Color(0xFFE50914)
val SuccessGreen = EmeraldNeon

val BrandEmeraldVioletBrush = Brush.horizontalGradient(
    listOf(EmeraldPrimary, EmeraldNeon)
)

val BrandNeonGlassBorderBrush = Brush.horizontalGradient(
    listOf(EmeraldNeon.copy(alpha = 0.72f), Color(0x44FFFFFF))
)

val AmbientCinemaBackdropBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF0E1410),
        CinemaObsidian,
        Color(0xFF0B0B0B)
    )
)

val SarabunFontFamily = FontFamily(
    Font(R.font.sarabun_regular, FontWeight.Normal),
    Font(R.font.sarabun_semibold, FontWeight.SemiBold),
    Font(R.font.sarabun_semibold, FontWeight.Bold)
)

val KanitFontFamily = FontFamily(
    Font(R.font.kanit_medium, FontWeight.Normal),
    Font(R.font.kanit_medium, FontWeight.SemiBold),
    Font(R.font.kanit_medium, FontWeight.Bold)
)

val PromptFontFamily = FontFamily(
    Font(R.font.prompt_medium, FontWeight.Normal),
    Font(R.font.prompt_medium, FontWeight.SemiBold),
    Font(R.font.prompt_medium, FontWeight.Bold)
)

fun resolveSubtitleFontFamily(choice: SubtitleFontChoice): FontFamily = when (choice) {
    SubtitleFontChoice.SARABUN -> SarabunFontFamily
    SubtitleFontChoice.KANIT -> KanitFontFamily
    SubtitleFontChoice.PROMPT -> PromptFontFamily
    SubtitleFontChoice.SYSTEM -> FontFamily.SansSerif
}

private val ChillFilmColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF031710),
    secondary = VioletPrimary,
    onSecondary = Color(0xFFF8FAFC),
    tertiary = EmeraldNeon,
    background = CinemaObsidian,
    onBackground = TextPrimary,
    surface = CinemaSurface,
    onSurface = TextPrimary,
    surfaceVariant = CinemaSurfaceElevated,
    onSurfaceVariant = TextSecondary
)

val LocalIsTvMode = staticCompositionLocalOf { false }

fun isDeviceTelevision(context: Context, config: Configuration, overrideMode: DeviceUiMode): Boolean {
    return when (overrideMode) {
        DeviceUiMode.TV_BOX -> true
        DeviceUiMode.MOBILE -> false
        DeviceUiMode.AUTO -> {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
            val hasTelevision = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                context.packageManager.hasSystemFeature("android.hardware.type.television")
            val hasLeanback = context.packageManager.hasSystemFeature("android.software.leanback")
            val noTouch = !context.packageManager.hasSystemFeature("android.hardware.touchscreen")
            isTvUiMode || hasTelevision || hasLeanback || noTouch
        }
    }
}

@Composable
fun PhyllFilmHubTheme(
    deviceUiMode: DeviceUiMode = DeviceUiMode.AUTO,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = isDeviceTelevision(context, configuration, deviceUiMode)

    CompositionLocalProvider(LocalIsTvMode provides isTv) {
        MaterialTheme(
            colorScheme = ChillFilmColorScheme,
            content = content
        )
    }
}
