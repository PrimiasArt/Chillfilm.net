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
// 2026 Spatial Cinema Palette — Emerald Green x Royal Violet over Midnight Glass
// ============================================================================================
val CinemaObsidian = Color(0xFF06070D)
val CinemaSurface = Color(0xFF0F1220)
val CinemaSurfaceElevated = Color(0xFF171B30)
val CinemaGlass = Color(0xBF121628)
val CinemaGlassBorder = Color(0x26FFFFFF)

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldNeon = Color(0xFF34D399)
val VioletPrimary = Color(0xFF8B5CF6)
val VioletGlow = Color(0xFFA855F7)
val VioletSoft = Color(0xFFC084FC)
val AmberBoost = Color(0xFFFBBF24)

// Unified palette aliases across screens so every highlight balances Emerald & Violet
val ThaiGold = EmeraldPrimary
val ElectricCyan = VioletSoft

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFA1A7B8)
val LiveRed = Color(0xFFEF4444)
val SuccessGreen = EmeraldNeon

val BrandEmeraldVioletBrush = Brush.horizontalGradient(
    listOf(EmeraldPrimary, VioletPrimary)
)

val BrandNeonGlassBorderBrush = Brush.horizontalGradient(
    listOf(EmeraldNeon.copy(alpha = 0.65f), VioletGlow.copy(alpha = 0.65f))
)

val AmbientCinemaBackdropBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF091418),
        CinemaObsidian,
        Color(0xFF100B1D)
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
