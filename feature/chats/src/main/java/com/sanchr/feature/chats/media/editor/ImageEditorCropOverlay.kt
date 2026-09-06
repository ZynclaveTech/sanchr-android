package com.sanchr.feature.chats.media.editor

import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The crop rectangle drawn over the image, with draggable corners.
 *
 * Works in the displayed image's pixels and reports back in normalised
 * coordinates, so the caller never has to know the view size. Mirrors iOS
 * `ImageEditorCropOverlay`.
 */
@Composable
fun ImageEditorCropOverlay(
    crop: Rect,
    imageSize: Size,
    onCropChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A finger is far larger than a drawn corner, so the hit target is set by
    // touch size rather than by how big the handle looks.
    val handleTouchRadius = 48f
    var dragging by remember { mutableStateOf<CropHandle?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(imageSize) {
                    if (imageSize.width <= 0f || imageSize.height <= 0f) return@pointerInput
                    detectDragGestures(
                        onDragStart = { position ->
                            dragging = CropHandle.nearest(position, crop.scaledTo(imageSize), handleTouchRadius)
                        },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ ->
                        change.consume()
                        val handle = dragging ?: return@detectDragGestures
                        val moved = handle.applied(crop.scaledTo(imageSize), change.position)
                        onCropChange(
                            ImageEditorCrop.clamp(
                                Rect(
                                    left = moved.left / imageSize.width,
                                    top = moved.top / imageSize.height,
                                    right = moved.right / imageSize.width,
                                    bottom = moved.bottom / imageSize.height,
                                ),
                            ),
                        )
                    }
                },
    ) {
        DrawCanvas(modifier = Modifier.fillMaxSize()) {
            val rect = crop.scaledTo(Size(size.width, size.height))

            // Dim what is being cut away, so the framing reads at a glance.
            val shade = Color.Black.copy(alpha = 0.55f)
            drawRect(shade, size = Size(size.width, rect.top))
            drawRect(shade, topLeft = Offset(0f, rect.bottom), size = Size(size.width, size.height - rect.bottom))
            drawRect(shade, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
            drawRect(
                shade,
                topLeft = Offset(rect.right, rect.top),
                size = Size(size.width - rect.right, rect.height),
            )

            drawRect(
                color = Color.White,
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
                style = Stroke(width = 2.dp.toPx()),
            )

            // Thirds, the standard framing guide.
            val thirdWidth = rect.width / 3f
            val thirdHeight = rect.height / 3f
            val guide = Color.White.copy(alpha = 0.35f)
            for (step in 1..2) {
                drawLine(
                    guide,
                    Offset(rect.left + thirdWidth * step, rect.top),
                    Offset(rect.left + thirdWidth * step, rect.bottom),
                )
                drawLine(
                    guide,
                    Offset(rect.left, rect.top + thirdHeight * step),
                    Offset(rect.right, rect.top + thirdHeight * step),
                )
            }

            val handleLength = 20.dp.toPx()
            val handleWidth = 3.dp.toPx()
            for (corner in CropHandle.entries) {
                val point = corner.position(rect)
                val horizontal = if (corner.isLeft) handleLength else -handleLength
                val vertical = if (corner.isTop) handleLength else -handleLength
                drawLine(Color.White, point, Offset(point.x + horizontal, point.y), strokeWidth = handleWidth)
                drawLine(Color.White, point, Offset(point.x, point.y + vertical), strokeWidth = handleWidth)
            }
        }
    }
}

/** The four corners a drag can grab. */
enum class CropHandle(
    val isLeft: Boolean,
    val isTop: Boolean,
) {
    TOP_LEFT(isLeft = true, isTop = true),
    TOP_RIGHT(isLeft = false, isTop = true),
    BOTTOM_LEFT(isLeft = true, isTop = false),
    BOTTOM_RIGHT(isLeft = false, isTop = false),
    ;

    fun position(rect: Rect): Offset =
        Offset(
            x = if (isLeft) rect.left else rect.right,
            y = if (isTop) rect.top else rect.bottom,
        )

    /** [rect] with this corner moved to [point]. */
    fun applied(
        rect: Rect,
        point: Offset,
    ): Rect =
        Rect(
            left = if (isLeft) point.x else rect.left,
            top = if (isTop) point.y else rect.top,
            right = if (isLeft) rect.right else point.x,
            bottom = if (isTop) rect.bottom else point.y,
        )

    companion object {
        /**
         * The corner within [radius] of [point], or null.
         *
         * Nearest rather than first match: on a small crop the touch targets
         * overlap, and picking the first in declaration order would grab the
         * top-left corner whenever two were in range.
         */
        fun nearest(
            point: Offset,
            rect: Rect,
            radius: Float,
        ): CropHandle? =
            entries
                .map { it to (it.position(rect) - point).getDistance() }
                .filter { it.second <= radius }
                .minByOrNull { it.second }
                ?.first
    }
}
