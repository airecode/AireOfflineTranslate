package com.example.myapplication.translate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Which part of the rectangle a drag grabbed. */
private enum class Grip { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Smallest the box may be shrunk to, as a fraction of each axis. */
private const val MIN_SIDE = 0.12f

/** How close, in fractions of the shorter axis, a touch must land to count as grabbing a corner. */
private const val CORNER_GRAB = 0.09f

/**
 * A draggable, resizable selection rectangle drawn over a camera preview.
 *
 * [crop] is in normalised coordinates — 0..1 across the composable — so it can be handed straight
 * to the image pipeline without knowing anything about the view's pixel size or the capture
 * resolution. That only holds because the preview it sits on shows the whole captured frame and
 * nothing else; see the aspect-ratio note in CameraOcrDialog.
 *
 * Drag a corner to resize, anywhere inside to move.
 */
@Composable
fun CropOverlay(
    crop: Rect,
    onCropChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var grip by remember { mutableStateOf(Grip.NONE) }

    // The gesture detector is set up once and would otherwise close over the rectangle as it was
    // then, so every drag would start from the original position.
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)

    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                if (boxSize.width == 0 || boxSize.height == 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { position ->
                        grip = gripAt(
                            Offset(position.x / boxSize.width, position.y / boxSize.height),
                            currentCrop,
                        )
                    },
                    onDragEnd = { grip = Grip.NONE },
                    onDragCancel = { grip = Grip.NONE },
                ) { change, drag ->
                    if (grip == Grip.NONE) return@detectDragGestures
                    change.consume()
                    currentOnChange(
                        currentCrop.dragged(
                            grip = grip,
                            dx = drag.x / boxSize.width,
                            dy = drag.y / boxSize.height,
                        )
                    )
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val l = crop.left * size.width
            val t = crop.top * size.height
            val r = crop.right * size.width
            val b = crop.bottom * size.height

            // Four bands rather than a cut-out path: the same result, without depending on
            // even-odd fill behaviour that is easy to get subtly wrong.
            val scrim = Color.Black.copy(alpha = 0.55f)
            drawRect(scrim, size = Size(size.width, t))
            drawRect(scrim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
            drawRect(scrim, topLeft = Offset(0f, t), size = Size(l, b - t))
            drawRect(scrim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))

            drawRect(
                color = Color.White,
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                style = Stroke(width = 2.dp.toPx()),
            )

            // Corner brackets, which read as "grab here" in a way a plain thin outline does not.
            val arm = minOf(r - l, b - t) * 0.22f
            val thickness = 4.dp.toPx()
            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(Color.White, Offset(x, y), Offset(x + dx * arm, y), thickness)
                drawLine(Color.White, Offset(x, y), Offset(x, y + dy * arm), thickness)
            }
            bracket(l, t, 1f, 1f)
            bracket(r, t, -1f, 1f)
            bracket(l, b, 1f, -1f)
            bracket(r, b, -1f, -1f)
        }
    }
}

private fun gripAt(point: Offset, crop: Rect): Grip {
    fun near(x: Float, y: Float) =
        abs(point.x - x) < CORNER_GRAB && abs(point.y - y) < CORNER_GRAB

    return when {
        near(crop.left, crop.top) -> Grip.TOP_LEFT
        near(crop.right, crop.top) -> Grip.TOP_RIGHT
        near(crop.left, crop.bottom) -> Grip.BOTTOM_LEFT
        near(crop.right, crop.bottom) -> Grip.BOTTOM_RIGHT
        point.x in crop.left..crop.right && point.y in crop.top..crop.bottom -> Grip.MOVE
        else -> Grip.NONE
    }
}

/**
 * Applies one drag step.
 *
 * Resizing clamps each edge against the opposite one so the rectangle can never be dragged
 * inside-out, and moving translates without resizing — dragging the box to the screen edge slides
 * it there rather than squashing it.
 */
private fun Rect.dragged(grip: Grip, dx: Float, dy: Float): Rect = when (grip) {
    Grip.NONE -> this

    Grip.MOVE -> {
        val shiftX = dx.coerceIn(-left, 1f - right)
        val shiftY = dy.coerceIn(-top, 1f - bottom)
        Rect(left + shiftX, top + shiftY, right + shiftX, bottom + shiftY)
    }

    Grip.TOP_LEFT -> Rect(
        left = (left + dx).coerceIn(0f, right - MIN_SIDE),
        top = (top + dy).coerceIn(0f, bottom - MIN_SIDE),
        right = right,
        bottom = bottom,
    )

    Grip.TOP_RIGHT -> Rect(
        left = left,
        top = (top + dy).coerceIn(0f, bottom - MIN_SIDE),
        right = (right + dx).coerceIn(left + MIN_SIDE, 1f),
        bottom = bottom,
    )

    Grip.BOTTOM_LEFT -> Rect(
        left = (left + dx).coerceIn(0f, right - MIN_SIDE),
        top = top,
        right = right,
        bottom = (bottom + dy).coerceIn(top + MIN_SIDE, 1f),
    )

    Grip.BOTTOM_RIGHT -> Rect(
        left = left,
        top = top,
        right = (right + dx).coerceIn(left + MIN_SIDE, 1f),
        bottom = (bottom + dy).coerceIn(top + MIN_SIDE, 1f),
    )
}
