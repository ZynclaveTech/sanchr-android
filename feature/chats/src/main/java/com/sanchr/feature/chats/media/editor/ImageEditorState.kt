package com.sanchr.feature.chats.media.editor

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** One freehand line, in the coordinate space of the displayed image. */
data class ImageEditorStroke(
    val color: Int,
    val widthDp: Float,
    val points: List<Offset>,
)

/** The editor's three tools. Only one is active at a time. */
enum class ImageEditorTool { DRAW, CROP, ROTATE }

/** The fixed crop ratios offered alongside a free drag. */
enum class ImageEditorAspect(
    val label: String,
    val ratio: Float?,
) {
    FREE("Free", null),
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    WIDESCREEN("16:9", 16f / 9f),
    FOUR_THREE("4:3", 4f / 3f),
}

/**
 * Everything the image editor is currently doing.
 *
 * Rotation and flip are applied to the bitmap straight away rather than
 * accumulated as a transform, so drawing and cropping always work in the
 * orientation on screen and there is no deferred matrix to reconcile at
 * render time. Mirrors iOS `ImageEditorState`, which takes the same approach
 * for the same reason.
 *
 * Where it deliberately differs: iOS discards the drawing on a rotate or a
 * flip. Here the strokes are baked into the bitmap first, so a turn after
 * drawing keeps the drawing, and the crop rectangle is carried through the
 * same transform instead of being reset — the user framed something, and a
 * rotate should not quietly re-frame it.
 */
class ImageEditorState(
    initial: Bitmap,
) {
    /** The working bitmap, already rotated and flipped as the user asked. */
    var image by mutableStateOf(initial)
        private set

    var tool by mutableStateOf<ImageEditorTool?>(null)

    var aspect by mutableStateOf(ImageEditorAspect.FREE)
        private set

    /** The crop, normalised to [image]'s bounds. */
    var crop by mutableStateOf(ImageEditorCrop.FULL)

    /** Committed strokes, in the displayed image's coordinate space. */
    val strokes = mutableStateListOf<ImageEditorStroke>()

    /** True once anything would change the bytes that get sent. */
    val isEdited: Boolean
        get() = hasTransformed || strokes.isNotEmpty() || crop != ImageEditorCrop.FULL

    private var hasTransformed = false

    fun rotateRight(canvasSize: Size) {
        bakeStrokes(canvasSize)
        image = ImageEditorRender.rotateRight(image)
        crop = ImageEditorCrop.rotatedRight(crop)
        // The ratio was chosen against the old orientation; the rotated
        // rectangle no longer matches it, and claiming otherwise would leave
        // the chip highlighted while the crop says something else.
        aspect = ImageEditorAspect.FREE
        hasTransformed = true
    }

    fun flipHorizontal(canvasSize: Size) {
        bakeStrokes(canvasSize)
        image = ImageEditorRender.flipHorizontal(image)
        crop = ImageEditorCrop.flippedHorizontally(crop)
        hasTransformed = true
    }

    fun applyAspect(aspect: ImageEditorAspect) {
        this.aspect = aspect
        crop =
            when (val ratio = aspect.ratio) {
                null -> ImageEditorCrop.FULL
                else -> ImageEditorCrop.forAspect(ratio, image.width.toFloat() / image.height.toFloat())
            }
    }

    fun addStroke(stroke: ImageEditorStroke) {
        if (stroke.points.isEmpty()) return
        strokes.add(stroke)
    }

    /** Removes the last stroke, or clears the crop once there are none left. */
    fun undo() {
        when {
            strokes.isNotEmpty() -> strokes.removeAt(strokes.lastIndex)
            crop != ImageEditorCrop.FULL -> {
                crop = ImageEditorCrop.FULL
                aspect = ImageEditorAspect.FREE
            }
        }
    }

    val canUndo: Boolean
        get() = strokes.isNotEmpty() || crop != ImageEditorCrop.FULL

    /** The finished image: strokes flattened, then cropped. */
    fun render(canvasSize: Size): Bitmap = ImageEditorRender.cropped(ImageEditorRender.withStrokes(image, strokes, canvasSize), crop)

    private fun bakeStrokes(canvasSize: Size) {
        if (strokes.isEmpty()) return
        image = ImageEditorRender.withStrokes(image, strokes, canvasSize)
        strokes.clear()
    }
}

/** The normalised crop expressed against a displayed rectangle of [size]. */
fun Rect.scaledTo(size: Size): Rect =
    Rect(
        left = left * size.width,
        top = top * size.height,
        right = right * size.width,
        bottom = bottom * size.height,
    )
