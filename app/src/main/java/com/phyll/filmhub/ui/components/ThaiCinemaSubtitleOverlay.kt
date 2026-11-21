package com.phyll.filmhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phyll.filmhub.model.ActiveSubtitleRenderState
import com.phyll.filmhub.model.SubtitleColorChoice
import com.phyll.filmhub.model.SubtitleEdgeStyle
import com.phyll.filmhub.model.SubtitleStyleConfig
import com.phyll.filmhub.ui.theme.resolveSubtitleFontFamily

/**
 * Sleek Netflix / Apple TV+ Cinema Subtitle Renderer for Thai script (อักษรไทย):
 * - Hugs only the active spoken dialogue lines with a compact, rounded translucent dark pill
 *   (completely invisible during silent scenes unless the user explicitly turns on Hardsub Mask).
 * - Supports independent or linked customization of Primary (Sub Chính) and Secondary (Sub Phụ)
 *   font sizes and colors.
 */
@Composable
fun ThaiCinemaSubtitleOverlay(
    renderState: ActiveSubtitleRenderState,
    config: SubtitleStyleConfig,
    previewSampleWhenEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!config.subtitlesEnabled && !previewSampleWhenEmpty) return

    val primarySizeSp = config.fontSizeSp.coerceIn(10f, 52f)
    val secondarySizeSp = (if (config.secondaryFontSizeSp > 0f) config.secondaryFontSizeSp else primarySizeSp * 0.80f)
        .coerceIn(10f, 48f)

    val primaryColor = Color(config.primaryColor.argb)
    val secondaryColor = Color(config.secondaryColor.argb)

    val primaryLang = config.resolvedPrimaryLanguage()
    val primaryText = renderState.primaryThaiText
        ?: if (previewSampleWhenEmpty) primaryLang.samplePreview else null

    val secondaryText = renderState.secondaryText
        ?: if (previewSampleWhenEmpty) {
            if (config.dualSubEnabled) {
                "Sub phụ (${config.resolvedSecondaryDisplayName()}): Chỉnh cỡ chữ & màu riêng hoặc cùng lúc"
            } else {
                "Sub phụ đang tắt — bật Song ngữ để hiện dòng thứ 2"
            }
        } else null

    Box(modifier = modifier.fillMaxSize()) {
        // Optional Hardsub Cover Strip (only shown when user explicitly enables it for hardsubbed videos)
        if (config.hardsubMaskEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (config.bottomOffsetDp - 4f).coerceAtLeast(0f).dp)
                    .fillMaxWidth(0.76f)
                    .height(config.hardsubMaskHeightDp.coerceIn(36f, 140f).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF06080D).copy(alpha = config.hardsubMaskOpacity.coerceIn(0.35f, 0.96f)))
            )
        }

        if (!primaryText.isNullOrBlank() || !secondaryText.isNullOrBlank()) {
            val useUnifiedFrame = config.edgeStyle == SubtitleEdgeStyle.BACKGROUND_BOX
            val containerShape = RoundedCornerShape(8.dp)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = config.bottomOffsetDp.coerceAtLeast(4f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(
                    modifier = if (useUnifiedFrame) {
                        Modifier
                            .widthIn(max = 820.dp)
                            .clip(containerShape)
                            .background(
                                Color(0xFF080B11).copy(alpha = config.boxOpacity.coerceIn(0.25f, 0.96f))
                            )
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.12f),
                                shape = containerShape
                            )
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    } else {
                        Modifier
                            .widthIn(max = 820.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!primaryText.isNullOrBlank()) {
                        StyledCinemaSubtitleLine(
                            text = primaryText,
                            fontSizeSp = primarySizeSp,
                            textColor = primaryColor,
                            config = config,
                            strokeWidthPx = 4.5f
                        )
                    }

                    if (!secondaryText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        StyledCinemaSubtitleLine(
                            text = secondaryText,
                            fontSizeSp = secondarySizeSp,
                            textColor = secondaryColor,
                            config = config,
                            strokeWidthPx = 3.5f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyledCinemaSubtitleLine(
    text: String,
    fontSizeSp: Float,
    textColor: Color,
    config: SubtitleStyleConfig,
    strokeWidthPx: Float
) {
    val fontFamily = resolveSubtitleFontFamily(config.fontChoice)
    val weight = if (config.boldText) FontWeight.SemiBold else FontWeight.Normal
    val lineHeightSp = (fontSizeSp * 1.32f).sp

    val baseTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )

    Box(contentAlignment = Alignment.Center) {
        when (config.edgeStyle) {
            SubtitleEdgeStyle.OUTLINE -> {
                Text(
                    text = text,
                    style = baseTextStyle.copy(
                        color = Color.Black,
                        drawStyle = Stroke(
                            width = strokeWidthPx,
                            join = StrokeJoin.Round
                        )
                    )
                )
                Text(
                    text = text,
                    style = baseTextStyle.copy(color = textColor)
                )
            }
            SubtitleEdgeStyle.DROP_SHADOW -> {
                Text(
                    text = text,
                    style = baseTextStyle.copy(
                        color = textColor,
                        shadow = Shadow(
                            color = Color.Black,
                            offset = Offset(2.5f, 2.5f),
                            blurRadius = 6f
                        )
                    )
                )
            }
            SubtitleEdgeStyle.BACKGROUND_BOX -> {
                Text(
                    text = text,
                    style = baseTextStyle.copy(
                        color = textColor,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(1f, 1f),
                            blurRadius = 2f
                        )
                    )
                )
            }
            SubtitleEdgeStyle.NONE -> {
                Text(
                    text = text,
                    style = baseTextStyle.copy(color = textColor)
                )
            }
        }
    }
}
