package com.phyll.filmhub.ui.theme

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV360 Box D-Pad & Phone Touch unified interactive modifier.
 *
 * Why this uses a single `.focusable()` + `.pointerInput(detectTapGestures)` instead of `.clickable()`:
 * - Jetpack Compose's built-in `Modifier.clickable` attaches `FocusableInNonTouchMode`, which disables
 *   D-Pad focus (`canFocus = false`) as soon as the phone companion injects a touchscreen `MotionEvent`
 *   (`InputMode.Touch`), and chaining `.focusable().clickable()` creates duplicate nested `FocusTargetNode`s.
 * - By combining a single `.focusable()` with `detectTapGestures` and `rememberUpdatedState`, every control
 *   remains 100% focusable via TV Remote D-Pad AND `FocusRequester.requestFocus()` at all times — even while
 *   simultaneously streaming & touching on the phone!
 */
fun Modifier.tvFocusableClickable(
    shape: Shape = RoundedCornerShape(12.dp),
    focusedBorderColor: Color = ThaiGold,
    unfocusedBorderColor: Color = Color.Transparent,
    borderWidth: Dp = 2.5.dp,
    focusedScale: Float = 1.03f,
    enabled: Boolean = true,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLeft by rememberUpdatedState(onLeftKey)
    val currentOnRight by rememberUpdatedState(onRightKey)
    val currentOnFocused by rememberUpdatedState(onFocused)

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "tvFocusScale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            border = BorderStroke(
                width = if (isFocused) borderWidth else 1.dp,
                color = if (isFocused) focusedBorderColor else unfocusedBorderColor
            ),
            shape = shape
        )
        .clip(shape)
        .focusProperties {
            canFocus = enabled
        }
        .onFocusChanged { state ->
            isFocused = state.isFocused
            if (state.isFocused) {
                currentOnFocused?.invoke()
            }
        }
        .onKeyEvent { event ->
            if (!enabled || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                return@onKeyEvent false
            }
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_SPACE -> {
                    currentOnClick()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val leftHandler = currentOnLeft
                    if (leftHandler != null) {
                        leftHandler()
                        true
                    } else {
                        false
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val rightHandler = currentOnRight
                    if (rightHandler != null) {
                        rightHandler()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
        .focusable(enabled = enabled, interactionSource = interactionSource)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onTap = { currentOnClick() }
            )
        }
}
