package com.phyll.filmhub.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.phyll.filmhub.ui.theme.AmberBoost
import com.phyll.filmhub.ui.theme.BrandEmeraldVioletBrush
import com.phyll.filmhub.ui.theme.BrandNeonGlassBorderBrush
import com.phyll.filmhub.ui.theme.CinemaGlass
import com.phyll.filmhub.ui.theme.CinemaGlassBorder
import com.phyll.filmhub.ui.theme.CinemaObsidian
import com.phyll.filmhub.ui.theme.EmeraldNeon
import com.phyll.filmhub.ui.theme.EmeraldPrimary
import com.phyll.filmhub.ui.theme.TextPrimary
import com.phyll.filmhub.ui.theme.TextSecondary
import com.phyll.filmhub.ui.theme.VioletGlow
import com.phyll.filmhub.ui.theme.tvFocusableClickable

enum class ChillAspectRatioMode(
    val displayName: String,
    val resizeMode: Int,
    val enforcedRatio: Float? = null
) {
    @OptIn(UnstableApi::class)
    FIT("Vừa màn hình (Fit)", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    @OptIn(UnstableApi::class)
    FILL("Tràn khung (Fill)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    @OptIn(UnstableApi::class)
    STRETCH("Kéo giãn toàn màn", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    @OptIn(UnstableApi::class)
    WIDE_16_9("16:9 Cinema", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, enforcedRatio = 16f / 9f),
    @OptIn(UnstableApi::class)
    CLASSIC_4_3("4:3 Cổ điển", AspectRatioFrameLayout.RESIZE_MODE_FIT, enforcedRatio = 4f / 3f)
}

enum class ChillEqualizerPreset(val displayName: String) {
    FLAT("Chuẩn (Flat)"),
    BASS_BOOST("Tăng Bass (Bass Boost)"),
    VOCAL("Giọng nói rõ (Vocal)"),
    MOVIE("Rạp chiếu phim (Movie)")
}

enum class ChillPlayerMenu {
    NONE, MORE, ASPECT, SPEED, EQUALIZER, SLEEP
}

enum class ChillGestureFeedback {
    NONE, VOLUME, BRIGHTNESS, SEEK
}

/**
 * Native Android Audio DSP engine inspired by PrimiasArt/Chill-Player:
 * Combines system stream volume (0%..100%) with Android `LoudnessEnhancer` (101%..200% boost)
 * and 5-band hardware `Equalizer` presets.
 */
class ChillAudioEffectsController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var attachedSessionId: Int = -1

    fun attachToSession(sessionId: Int, currentVolumeScale: Float, preset: ChillEqualizerPreset) {
        if (sessionId <= 0) return
        if (attachedSessionId != sessionId) {
            releaseEffects()
            attachedSessionId = sessionId
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
            }
            runCatching {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
            }
        }
        applyVolumeScale(currentVolumeScale)
        applyEqualizerPreset(preset)
    }

    fun getCurrentNormalizedSystemVolume(): Float {
        val am = audioManager ?: return 1.0f
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
    }

    fun applyVolumeScale(scale: Float) {
        val clamped = scale.coerceIn(0f, 2.0f)
        val am = audioManager
        if (am != null) {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetStreamVol = (clamped.coerceAtMost(1.0f) * maxVol).toInt().coerceIn(0, maxVol)
            runCatching {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
            }
        }
        val extraBoostFraction = (clamped - 1.0f).coerceIn(0f, 1.0f)
        val targetGainMb = (extraBoostFraction * 1_400f).toInt()
        runCatching {
            loudnessEnhancer?.setTargetGain(targetGainMb)
            loudnessEnhancer?.enabled = targetGainMb > 0
        }
    }

    fun applyEqualizerPreset(preset: ChillEqualizerPreset) {
        val eq = equalizer ?: return
        runCatching {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            for (b in 0 until bands) {
                val frac = b.toFloat() / (bands - 1).coerceAtLeast(1).toFloat()
                val desiredMb = when (preset) {
                    ChillEqualizerPreset.FLAT -> 0
                    ChillEqualizerPreset.BASS_BOOST -> if (frac < 0.35f) 750 else if (frac < 0.65f) 150 else 0
                    ChillEqualizerPreset.VOCAL -> if (frac in 0.25f..0.75f) 650 else -250
                    ChillEqualizerPreset.MOVIE -> if (frac < 0.3f || frac > 0.7f) 550 else 250
                }
                val level = desiredMb.coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(b.toShort(), level)
            }
        }
    }

    fun releaseEffects() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { equalizer?.release() }
        loudnessEnhancer = null
        equalizer = null
        attachedSessionId = -1
    }
}

@Composable
fun BoxScope.ChillGestureAndLockOverlay(
    isLocked: Boolean,
    showControls: Boolean,
    lockHintVisible: Boolean,
    gestureFeedback: ChillGestureFeedback,
    gesturePercent: Int,
    seekOffsetSecs: Int,
    targetSeekPreviewMs: Long,
    durationMs: Long,
    doubleTapSide: Int, // -1 left, +1 right, 0 none
    onToggleLock: () -> Unit,
    formatTimeFn: (Long) -> String
) {
    // 1. Left-Edge Screen Lock Floating Capsule (MX / Chill-Player Signature)
    AnimatedVisibility(
        visible = isLocked || showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .tvFocusableClickable(
                    shape = CircleShape,
                    focusedBorderColor = if (isLocked) AmberBoost else EmeraldNeon,
                    unfocusedBorderColor = if (isLocked) AmberBoost else CinemaGlassBorder,
                    onClick = onToggleLock
                )
                .background(if (isLocked) AmberBoost else CinemaGlass),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isLocked) "Mở khóa" else "Khóa màn hình",
                tint = if (isLocked) CinemaObsidian else TextPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }

    // 2. Screen Lock Hint Capsule Toast
    AnimatedVisibility(
        visible = lockHintVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(CinemaGlass)
                .border(1.2.dp, AmberBoost.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = AmberBoost, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Màn hình đang khóa • Chạm ổ khóa bên trái để mở khóa",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // 3. Volume / 200% Audio Boost Floating Bar (Right Edge)
    if (gestureFeedback == ChillGestureFeedback.VOLUME) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CinemaGlass)
                .border(1.dp, CinemaGlassBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isBoost = gesturePercent > 100
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = if (isBoost) AmberBoost else EmeraldNeon,
                modifier = Modifier.size(22.dp)
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(110.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                val fill = (gesturePercent / 200f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fill)
                        .clip(CircleShape)
                        .background(if (isBoost) AmberBoost else EmeraldNeon)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$gesturePercent%",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (isBoost) {
                    Text(
                        text = "BOOST",
                        color = AmberBoost,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    // 4. Brightness Floating Bar (Left Edge)
    if (gestureFeedback == ChillGestureFeedback.BRIGHTNESS) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 74.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CinemaGlass)
                .border(1.dp, CinemaGlassBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.WbSunny, contentDescription = null, tint = VioletGlow, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(110.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                val fill = (gesturePercent / 100f).coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fill)
                        .clip(CircleShape)
                        .background(VioletGlow)
                )
            }
            Text(
                text = "$gesturePercent%",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    // 5. Horizontal Scrub Seek Capsule HUD
    if (gestureFeedback == ChillGestureFeedback.SEEK) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(24.dp))
                .background(CinemaGlass)
                .border(1.2.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (seekOffsetSecs >= 0) "+${seekOffsetSecs}s ⏩" else "⏪ ${seekOffsetSecs}s",
                color = EmeraldNeon,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatTimeFn(targetSeekPreviewMs)} / ${formatTimeFn(durationMs)}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // 6. Double-Tap 10s Ripple Badge
    if (doubleTapSide == -1) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 76.dp)
                .clip(CircleShape)
                .background(CinemaGlass)
                .border(1.dp, EmeraldNeon.copy(alpha = 0.7f), CircleShape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Replay10, contentDescription = null, tint = EmeraldNeon, modifier = Modifier.size(28.dp))
            Text("-10s", color = EmeraldNeon, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    } else if (doubleTapSide == 1) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 36.dp)
                .clip(CircleShape)
                .background(CinemaGlass)
                .border(1.dp, VioletGlow.copy(alpha = 0.7f), CircleShape)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Forward10, contentDescription = null, tint = VioletGlow, modifier = Modifier.size(28.dp))
            Text("+10s", color = VioletGlow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun BoxScope.ChillPlayerMenuOverlay(
    activeMenu: ChillPlayerMenu,
    aspectRatio: ChillAspectRatioMode,
    playbackSpeed: Float,
    equalizerPreset: ChillEqualizerPreset,
    volumeScale: Float,
    sleepTimerMinutes: Int?,
    onNavigateMenu: (ChillPlayerMenu) -> Unit,
    onSelectAspectRatio: (ChillAspectRatioMode) -> Unit,
    onSelectPlaybackSpeed: (Float) -> Unit,
    onSelectEqualizer: (ChillEqualizerPreset) -> Unit,
    onChangeVolumeScale: (Float) -> Unit,
    onSelectSleepTimer: (Int?) -> Unit
) {
    if (activeMenu == ChillPlayerMenu.NONE) return

    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 68.dp, end = 20.dp)
            .widthIn(min = 240.dp, max = 300.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xEB0E111E))
            .border(1.2.dp, BrandNeonGlassBorderBrush, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeMenu == ChillPlayerMenu.SPEED ||
                    activeMenu == ChillPlayerMenu.EQUALIZER ||
                    activeMenu == ChillPlayerMenu.SLEEP
                ) {
                    Box(
                        modifier = Modifier
                            .tvFocusableClickable(shape = CircleShape, onClick = { onNavigateMenu(ChillPlayerMenu.MORE) })
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Quay lại", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val title = when (activeMenu) {
                    ChillPlayerMenu.MORE -> "Tùy Chọn Chill-Player"
                    ChillPlayerMenu.ASPECT -> "Tỉ Lệ Khung Hình"
                    ChillPlayerMenu.SPEED -> "Tốc Độ Phát"
                    ChillPlayerMenu.EQUALIZER -> "Equalizer & Boost 200%"
                    ChillPlayerMenu.SLEEP -> "Hẹn Giờ Tắt Phim"
                    ChillPlayerMenu.NONE -> ""
                }
                Text(text = title, color = EmeraldNeon, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                modifier = Modifier
                    .tvFocusableClickable(shape = CircleShape, onClick = { onNavigateMenu(ChillPlayerMenu.NONE) })
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(17.dp))
            }
        }

        when (activeMenu) {
            ChillPlayerMenu.MORE -> {
                MenuChoiceRow(
                    icon = { Icon(Icons.Default.Speed, null, tint = EmeraldNeon, modifier = Modifier.size(16.dp)) },
                    label = "Tốc độ phát",
                    value = "${playbackSpeed}x",
                    onClick = { onNavigateMenu(ChillPlayerMenu.SPEED) }
                )
                MenuChoiceRow(
                    icon = { Icon(Icons.Default.Equalizer, null, tint = VioletGlow, modifier = Modifier.size(16.dp)) },
                    label = "Equalizer & Boost ${(volumeScale * 100).toInt()}%",
                    value = equalizerPreset.name,
                    onClick = { onNavigateMenu(ChillPlayerMenu.EQUALIZER) }
                )
                MenuChoiceRow(
                    icon = { Icon(Icons.Default.AspectRatio, null, tint = EmeraldNeon, modifier = Modifier.size(16.dp)) },
                    label = "Tỉ lệ khung hình",
                    value = aspectRatio.displayName.substringBefore(" "),
                    onClick = { onNavigateMenu(ChillPlayerMenu.ASPECT) }
                )
                MenuChoiceRow(
                    icon = { Icon(Icons.Default.NightlightRound, null, tint = VioletGlow, modifier = Modifier.size(16.dp)) },
                    label = "Hẹn giờ tắt",
                    value = sleepTimerMinutes?.let { "${it}p" } ?: "Tắt",
                    onClick = { onNavigateMenu(ChillPlayerMenu.SLEEP) }
                )
            }

            ChillPlayerMenu.ASPECT -> {
                ChillAspectRatioMode.entries.forEach { mode ->
                    val selected = aspectRatio == mode
                    SelectableGlassOptionRow(
                        label = mode.displayName,
                        selected = selected,
                        onClick = {
                            onSelectAspectRatio(mode)
                            onNavigateMenu(ChillPlayerMenu.NONE)
                        }
                    )
                }
            }

            ChillPlayerMenu.SPEED -> {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                    SelectableGlassOptionRow(
                        label = "${rate}x" + if (rate == 1.0f) " (Chuẩn)" else "",
                        selected = kotlin.math.abs(playbackSpeed - rate) < 0.05f,
                        onClick = {
                            onSelectPlaybackSpeed(rate)
                            onNavigateMenu(ChillPlayerMenu.NONE)
                        }
                    )
                }
            }

            ChillPlayerMenu.EQUALIZER -> {
                ChillEqualizerPreset.entries.forEach { eq ->
                    SelectableGlassOptionRow(
                        label = eq.displayName,
                        selected = equalizerPreset == eq,
                        onClick = { onSelectEqualizer(eq) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Âm lượng / Audio Boost:", color = TextSecondary, fontSize = 11.sp)
                    val pct = (volumeScale * 100).toInt()
                    Text(
                        text = "$pct%",
                        color = if (pct > 100) AmberBoost else EmeraldNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Slider(
                    value = volumeScale,
                    onValueChange = onChangeVolumeScale,
                    valueRange = 0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = if (volumeScale > 1.0f) AmberBoost else EmeraldNeon,
                        activeTrackColor = if (volumeScale > 1.0f) AmberBoost else EmeraldPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.16f)
                    )
                )
            }

            ChillPlayerMenu.SLEEP -> {
                listOf<Int?>(null, 15, 30, 45, 60).forEach { mins ->
                    SelectableGlassOptionRow(
                        label = mins?.let { "Tắt sau $it phút" } ?: "Tắt hẹn giờ",
                        selected = sleepTimerMinutes == mins,
                        onClick = {
                            onSelectSleepTimer(mins)
                            onNavigateMenu(ChillPlayerMenu.NONE)
                        }
                    )
                }
            }

            ChillPlayerMenu.NONE -> Unit
        }
    }
}

@Composable
private fun MenuChoiceRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = EmeraldNeon,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                onClick = onClick
            )
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = value, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectableGlassOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableClickable(
                shape = RoundedCornerShape(12.dp),
                focusedBorderColor = Color.White,
                unfocusedBorderColor = if (selected) EmeraldNeon else Color.White.copy(alpha = 0.08f),
                onClick = onClick
            )
            .background(
                brush = if (selected) BrandEmeraldVioletBrush else Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
        )
        if (selected) {
            Text(text = "✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}
