package com.sanchr.feature.chats.media.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
// Native graphics, because the pixel assertions below are the point: under
// Robolectric's default shadows Canvas.drawPath is a no-op, so a renderer
// that painted nothing at all would pass.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageEditorStateTest {
    private val canvas = Size(100f, 200f)

    private fun bitmap(
        width: Int = 100,
        height: Int = 200,
    ): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun stroke() =
        ImageEditorStroke(
            color = Color.RED,
            widthDp = 4f,
            points = listOf(Offset(10f, 10f), Offset(50f, 60f)),
        )

    @Test
    fun `an untouched image reports no edit`() {
        assertFalse(ImageEditorState(bitmap()).isEdited)
    }

    @Test
    fun `rotating swaps the dimensions`() {
        val state = ImageEditorState(bitmap(width = 100, height = 200))
        state.rotateRight(canvas)
        assertEquals(200, state.image.width)
        assertEquals(100, state.image.height)
        assertTrue(state.isEdited)
    }

    @Test
    fun `flipping keeps the dimensions and counts as an edit`() {
        val state = ImageEditorState(bitmap())
        state.flipHorizontal(canvas)
        assertEquals(100, state.image.width)
        assertEquals(200, state.image.height)
        assertTrue(state.isEdited)
    }

    /**
     * iOS throws the drawing away here. Losing work the user did because they
     * then straightened the photo is the kind of thing nobody reports as a
     * bug, so it is worth a test rather than a comment.
     */
    @Test
    fun `rotating keeps a drawing by baking it into the image`() {
        val state = ImageEditorState(bitmap())
        state.addStroke(stroke())
        state.rotateRight(canvas)
        assertTrue(state.strokes.isEmpty(), "strokes should be flattened, not carried")
        assertTrue(state.isEdited)
    }

    @Test
    fun `rotating carries the crop rather than resetting it`() {
        val state = ImageEditorState(bitmap())
        state.crop = Rect(0f, 0f, 0.5f, 0.5f)
        state.rotateRight(canvas)
        assertEquals(Rect(0.5f, 0f, 1f, 0.5f), state.crop)
    }

    @Test
    fun `a fixed aspect produces a centred crop for the current image`() {
        val state = ImageEditorState(bitmap(width = 200, height = 100))
        state.applyAspect(ImageEditorAspect.SQUARE)
        assertEquals(Rect(0.25f, 0f, 0.75f, 1f), state.crop)
        assertTrue(state.isEdited)
    }

    @Test
    fun `free and original clear the crop`() {
        val state = ImageEditorState(bitmap())
        state.applyAspect(ImageEditorAspect.SQUARE)
        state.applyAspect(ImageEditorAspect.FREE)
        assertEquals(ImageEditorCrop.FULL, state.crop)
    }

    /**
     * The chip and the rectangle must not disagree: a rotated 1:1 crop is no
     * longer 1:1, so leaving the chip lit would state something untrue.
     */
    @Test
    fun `rotating releases the chosen aspect`() {
        val state = ImageEditorState(bitmap(width = 200, height = 100))
        state.applyAspect(ImageEditorAspect.SQUARE)
        state.rotateRight(canvas)
        assertEquals(ImageEditorAspect.FREE, state.aspect)
    }

    @Test
    fun `undo removes strokes newest first, then the crop`() {
        val state = ImageEditorState(bitmap())
        state.crop = Rect(0f, 0f, 0.5f, 0.5f)
        state.addStroke(stroke())
        state.addStroke(stroke())

        state.undo()
        assertEquals(1, state.strokes.size)
        state.undo()
        assertTrue(state.strokes.isEmpty())
        assertEquals(Rect(0f, 0f, 0.5f, 0.5f), state.crop, "the crop outlives the strokes")

        state.undo()
        assertEquals(ImageEditorCrop.FULL, state.crop)
        assertFalse(state.canUndo)
    }

    @Test
    fun `an empty stroke is not recorded`() {
        val state = ImageEditorState(bitmap())
        state.addStroke(ImageEditorStroke(color = Color.RED, widthDp = 4f, points = emptyList()))
        assertTrue(state.strokes.isEmpty())
        assertFalse(state.isEdited)
    }

    @Test
    fun `rendering an untouched image returns it unchanged`() {
        val state = ImageEditorState(bitmap())
        val rendered = state.render(canvas)
        assertEquals(100, rendered.width)
        assertEquals(200, rendered.height)
    }

    @Test
    fun `rendering applies the crop in pixels`() {
        val state = ImageEditorState(bitmap(width = 100, height = 200))
        state.crop = Rect(0f, 0f, 0.5f, 0.5f)
        val rendered = state.render(canvas)
        assertEquals(50, rendered.width)
        assertEquals(100, rendered.height)
    }

    /** A collapsed crop would otherwise ask for a zero-pixel bitmap. */
    @Test
    fun `rendering a degenerate crop still produces a usable bitmap`() {
        val state = ImageEditorState(bitmap())
        state.crop = Rect(0.5f, 0.5f, 0.5f, 0.5f)
        val rendered = state.render(canvas)
        assertTrue(rendered.width >= 1 && rendered.height >= 1, "${rendered.width}x${rendered.height}")
    }

    @Test
    fun `a stroke drawn on the preview lands on the full-resolution image`() {
        // Canvas is a quarter of the bitmap's size, so a stroke has to be
        // scaled up or it draws in the wrong quarter of the photo.
        val source = bitmap(width = 400, height = 800)
        val strokes = listOf(ImageEditorStroke(Color.RED, 8f, listOf(Offset(0f, 0f), Offset(100f, 200f))))
        val painted = ImageEditorRender.withStrokes(source, strokes, Size(100f, 200f))
        assertEquals(400, painted.width)
        assertEquals(800, painted.height)
        assertTrue(painted.getPixel(200, 400) != 0, "the stroke did not reach the middle of the image")
    }

    @Test
    fun `a single tap draws a dot rather than nothing`() {
        val source = bitmap(width = 100, height = 100)
        val strokes = listOf(ImageEditorStroke(Color.RED, 10f, listOf(Offset(50f, 50f))))
        val painted = ImageEditorRender.withStrokes(source, strokes, Size(100f, 100f))
        assertTrue(painted.getPixel(50, 50) != 0, "a tap left no mark")
    }

    @Test
    fun `an unmeasured canvas leaves the image alone rather than dividing by zero`() {
        val source = bitmap()
        val painted = ImageEditorRender.withStrokes(source, listOf(stroke()), Size.Zero)
        assertEquals(source, painted)
    }
}
