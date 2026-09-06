package com.sanchr.feature.chats.media.editor

import androidx.compose.ui.geometry.Rect

/**
 * Crop geometry, in coordinates normalised to the image's own bounds.
 *
 * Normalised rather than pixels so the rectangle survives a rotation and
 * means the same thing at preview size and at full resolution — the overlay
 * drags in view coordinates, the render crops in image pixels, and only this
 * one representation passes between them.
 *
 * Kept apart from [ImageEditorState] because none of it needs a Bitmap: the
 * arithmetic is where the mistakes are, and this way it is tested on the JVM
 * without a Robolectric shadow standing in for the framework.
 *
 * Mirrors iOS `ImageEditorState`'s crop helpers.
 */
object ImageEditorCrop {
    /** The whole image: the identity crop, and what "no crop" compares against. */
    val FULL = Rect(0f, 0f, 1f, 1f)

    /** Smallest crop the handles may produce, as a fraction of each side. */
    const val MIN_SIDE = 0.05f

    /**
     * The largest centred rectangle with aspect ratio [targetAspect] that
     * fits an image whose aspect ratio is [imageAspect].
     *
     * Both are width ÷ height. A target wider than the image is limited by
     * width and leaves bars top and bottom; a taller one is limited by height.
     */
    fun forAspect(
        targetAspect: Float,
        imageAspect: Float,
    ): Rect {
        require(targetAspect > 0f && imageAspect > 0f) {
            "aspect ratios must be positive, got target=$targetAspect image=$imageAspect"
        }
        return if (targetAspect > imageAspect) {
            val height = imageAspect / targetAspect
            Rect(0f, (1f - height) / 2f, 1f, (1f + height) / 2f)
        } else {
            val width = targetAspect / imageAspect
            Rect((1f - width) / 2f, 0f, (1f + width) / 2f, 1f)
        }
    }

    /**
     * [rect] clamped back inside the image and kept at least [MIN_SIDE] wide
     * and tall.
     *
     * A drag can otherwise invert an edge past its opposite or push the
     * rectangle off the image, and a zero-area crop reaches the renderer as a
     * request for a zero-pixel bitmap.
     */
    fun clamp(rect: Rect): Rect {
        val left = rect.left.coerceIn(0f, 1f - MIN_SIDE)
        val top = rect.top.coerceIn(0f, 1f - MIN_SIDE)
        val right = rect.right.coerceIn(left + MIN_SIDE, 1f)
        val bottom = rect.bottom.coerceIn(top + MIN_SIDE, 1f)
        return Rect(left, top, right, bottom)
    }

    /**
     * [rect] as it sits after the image is rotated 90° clockwise.
     *
     * Rotating with a crop already drawn would otherwise silently keep the
     * old rectangle and crop a different part of the picture than the one the
     * user framed.
     */
    fun rotatedRight(rect: Rect): Rect =
        Rect(
            left = 1f - rect.bottom,
            top = rect.left,
            right = 1f - rect.top,
            bottom = rect.right,
        )

    /** [rect] as it sits after the image is mirrored horizontally. */
    fun flippedHorizontally(rect: Rect): Rect =
        Rect(
            left = 1f - rect.right,
            top = rect.top,
            right = 1f - rect.left,
            bottom = rect.bottom,
        )
}
