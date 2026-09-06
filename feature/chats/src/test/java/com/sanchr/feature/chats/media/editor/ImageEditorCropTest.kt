package com.sanchr.feature.chats.media.editor

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageEditorCropTest {
    private fun assertRect(
        expected: Rect,
        actual: Rect,
    ) {
        val tolerance = 1e-4f
        assertTrue(
            kotlin.math.abs(expected.left - actual.left) < tolerance &&
                kotlin.math.abs(expected.top - actual.top) < tolerance &&
                kotlin.math.abs(expected.right - actual.right) < tolerance &&
                kotlin.math.abs(expected.bottom - actual.bottom) < tolerance,
            "expected $expected but was $actual",
        )
    }

    @Test
    fun `a square crop of a landscape image is limited by height and centred`() {
        // 2:1 image, 1:1 crop — half the width, full height, equal bars either side.
        assertRect(Rect(0.25f, 0f, 0.75f, 1f), ImageEditorCrop.forAspect(targetAspect = 1f, imageAspect = 2f))
    }

    @Test
    fun `a square crop of a portrait image is limited by width and centred`() {
        assertRect(Rect(0f, 0.25f, 1f, 0.75f), ImageEditorCrop.forAspect(targetAspect = 1f, imageAspect = 0.5f))
    }

    @Test
    fun `a crop matching the image aspect is the whole image`() {
        assertRect(ImageEditorCrop.FULL, ImageEditorCrop.forAspect(targetAspect = 1.5f, imageAspect = 1.5f))
    }

    @Test
    fun `every aspect stays inside the image`() {
        val aspects = listOf(1f, 16f / 9f, 4f / 3f, 0.4f, 3f)
        val images = listOf(0.3f, 1f, 2.4f)
        for (target in aspects) {
            for (image in images) {
                val rect = ImageEditorCrop.forAspect(target, image)
                assertTrue(rect.left >= -1e-4f && rect.top >= -1e-4f, "$target on $image escaped: $rect")
                assertTrue(rect.right <= 1f + 1e-4f && rect.bottom <= 1f + 1e-4f, "$target on $image escaped: $rect")
            }
        }
    }

    @Test
    fun `a drag that inverts an edge is held at the minimum size rather than flipped`() {
        // Right dragged left past the left edge: without clamping this is a
        // negative-width rect, which reaches the renderer as a zero-pixel crop.
        val clamped = ImageEditorCrop.clamp(Rect(0.5f, 0.5f, 0.1f, 0.1f))
        assertTrue(clamped.width >= ImageEditorCrop.MIN_SIDE - 1e-4f, "width collapsed: $clamped")
        assertTrue(clamped.height >= ImageEditorCrop.MIN_SIDE - 1e-4f, "height collapsed: $clamped")
    }

    @Test
    fun `a drag beyond the image is pulled back inside it`() {
        val clamped = ImageEditorCrop.clamp(Rect(-0.4f, -0.2f, 1.7f, 1.3f))
        assertRect(ImageEditorCrop.FULL, clamped)
    }

    @Test
    fun `rotating four times returns the original crop`() {
        val original = Rect(0.1f, 0.2f, 0.6f, 0.9f)
        var rect = original
        repeat(4) { rect = ImageEditorCrop.rotatedRight(rect) }
        assertRect(original, rect)
    }

    @Test
    fun `rotating right moves a top-left crop to the top-right`() {
        // The user framed the top-left corner; after a clockwise turn that
        // same content sits top-right. Keeping the rectangle untouched would
        // crop a different part of the picture than the one they framed.
        val rotated = ImageEditorCrop.rotatedRight(Rect(0f, 0f, 0.5f, 0.5f))
        assertRect(Rect(0.5f, 0f, 1f, 0.5f), rotated)
    }

    @Test
    fun `flipping twice returns the original crop`() {
        val original = Rect(0.1f, 0.2f, 0.6f, 0.9f)
        assertRect(original, ImageEditorCrop.flippedHorizontally(ImageEditorCrop.flippedHorizontally(original)))
    }

    @Test
    fun `flipping mirrors horizontally and leaves the vertical edges alone`() {
        val flipped = ImageEditorCrop.flippedHorizontally(Rect(0.1f, 0.25f, 0.4f, 0.75f))
        assertRect(Rect(0.6f, 0.25f, 0.9f, 0.75f), flipped)
    }

    @Test
    fun `the full crop is the identity under both transforms`() {
        assertEquals(ImageEditorCrop.FULL, ImageEditorCrop.flippedHorizontally(ImageEditorCrop.FULL))
        assertEquals(ImageEditorCrop.FULL, ImageEditorCrop.rotatedRight(ImageEditorCrop.FULL))
    }
}
