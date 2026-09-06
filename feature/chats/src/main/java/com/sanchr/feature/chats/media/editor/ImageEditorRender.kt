package com.sanchr.feature.chats.media.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.core.graphics.createBitmap

/**
 * The bitmap half of the editor: the operations that actually produce pixels.
 *
 * Strokes are flattened into the bitmap before a rotate or a flip rather than
 * being transformed alongside it. Carrying stroke coordinates through a
 * rotation means a second coordinate space to keep in step, and iOS avoids
 * that by discarding the drawing outright — losing the user's work. Baking
 * them in first keeps the drawing and leaves only one space to reason about.
 */
object ImageEditorRender {
    /** [source] turned 90° clockwise. */
    fun rotateRight(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /** [source] mirrored left-to-right. */
    fun flipHorizontal(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * [source] with [strokes] painted on, at full image resolution.
     *
     * [canvasSize] is the size the image was displayed at when the strokes
     * were drawn; stroke coordinates are normalised against it so a line drawn
     * on a 300 pt preview lands in the same place on a 4000 px photo.
     */
    fun withStrokes(
        source: Bitmap,
        strokes: List<ImageEditorStroke>,
        canvasSize: androidx.compose.ui.geometry.Size,
    ): Bitmap {
        if (strokes.isEmpty()) return source
        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return source

        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val canvas = Canvas(output)
        val scaleX = output.width / canvasSize.width
        val scaleY = output.height / canvasSize.height

        for (stroke in strokes) {
            drawStroke(canvas, stroke, scaleX, scaleY)
        }
        return output
    }

    private fun drawStroke(
        canvas: Canvas,
        stroke: ImageEditorStroke,
        scaleX: Float,
        scaleY: Float,
    ) {
        if (stroke.points.isEmpty()) return
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                // Scale the nib with the image, or a stroke that looked right
                // on the preview is a hairline on a large photo.
                strokeWidth = stroke.widthDp * ((scaleX + scaleY) / 2f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        // A tap is a single point, which a Path with no line segment draws as
        // nothing at all — give it a dot instead.
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            canvas.drawPoint(point.x * scaleX, point.y * scaleY, paint)
            return
        }
        val path =
            Path().apply {
                val first = stroke.points.first()
                moveTo(first.x * scaleX, first.y * scaleY)
                for (point in stroke.points.drop(1)) {
                    lineTo(point.x * scaleX, point.y * scaleY)
                }
            }
        canvas.drawPath(path, paint)
    }

    /**
     * [source] reduced to [crop], which is normalised to the bitmap's bounds.
     *
     * Returns [source] untouched for the identity crop so an untouched photo
     * is sent as the file that was picked rather than a re-encoded copy.
     */
    fun cropped(
        source: Bitmap,
        crop: Rect,
    ): Bitmap {
        if (crop == ImageEditorCrop.FULL) return source
        val safe = ImageEditorCrop.clamp(crop)
        val left = (safe.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (safe.top * source.height).toInt().coerceIn(0, source.height - 1)
        val width = (safe.width * source.width).toInt().coerceIn(1, source.width - left)
        val height = (safe.height * source.height).toInt().coerceIn(1, source.height - top)
        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /** An empty bitmap of the given size, for tests and placeholders. */
    internal fun blank(
        width: Int,
        height: Int,
    ): Bitmap = createBitmap(width, height)
}
