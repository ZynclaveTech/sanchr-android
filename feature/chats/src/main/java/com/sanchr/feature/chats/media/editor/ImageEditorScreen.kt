package com.sanchr.feature.chats.media.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrTheme

/** The colours available to the pen. */
private val PEN_COLORS =
    listOf(
        Color.White,
        Color.Black,
        Color(0xFFE53935),
        Color(0xFFFDD835),
        Color(0xFF43A047),
        Color(0xFF1E88E5),
    )

private const val PEN_WIDTH_DP = 6f

/**
 * Crop, rotate and draw on a photo before it is sent.
 *
 * Android had no editor at all, so the only way to send part of a picture was
 * to leave the app, edit it elsewhere and come back. Mirrors iOS
 * `ImageEditorView`; the tools and the crop ratios are deliberately the same,
 * so a screenshot marked up on one platform looks like one marked up on the
 * other.
 *
 * Everything happens on the bitmap in memory. Nothing is written until
 * [onDone] is called, so backing out leaves the original file untouched.
 */
@Composable
fun ImageEditorScreen(
    source: Bitmap,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit,
) {
    val state = remember(source) { ImageEditorState(source) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var penColor by remember { mutableStateOf(PEN_COLORS.first()) }
    var wetStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Discard edits", tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { state.undo() },
                enabled = state.canUndo,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (state.canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                )
            }
            IconButton(onClick = { onDone(state.render(canvasSize)) }) {
                Icon(Icons.Filled.Check, contentDescription = "Save edits", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) },
            ) {
                ComposeImage(
                    bitmap = state.image.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                // Committed strokes plus the one under the finger, so a line
                // appears as it is drawn rather than on lift.
                DrawCanvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (state.tool == ImageEditorTool.DRAW) {
                                    Modifier.pointerInput(penColor) {
                                        detectDragGestures(
                                            onDragStart = { wetStroke = listOf(it) },
                                            onDragEnd = {
                                                state.addStroke(
                                                    ImageEditorStroke(
                                                        color = penColor.toArgb(),
                                                        widthDp = PEN_WIDTH_DP,
                                                        points = wetStroke,
                                                    ),
                                                )
                                                wetStroke = emptyList()
                                            },
                                            onDragCancel = { wetStroke = emptyList() },
                                        ) { change, _ ->
                                            change.consume()
                                            wetStroke = wetStroke + change.position
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    for (stroke in state.strokes + ImageEditorStroke(penColor.toArgb(), PEN_WIDTH_DP, wetStroke)) {
                        if (stroke.points.size < 2) continue
                        for (index in 0 until stroke.points.lastIndex) {
                            drawLine(
                                color = Color(stroke.color),
                                start = stroke.points[index],
                                end = stroke.points[index + 1],
                                strokeWidth = stroke.widthDp.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }

                if (state.tool == ImageEditorTool.CROP && canvasSize != Size.Zero) {
                    ImageEditorCropOverlay(
                        crop = state.crop,
                        imageSize = canvasSize,
                        onCropChange = { state.crop = it },
                    )
                }
            }
        }

        ToolOptions(
            state = state,
            canvasSize = canvasSize,
            penColor = penColor,
            onPenColor = { penColor = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.lg, Alignment.CenterHorizontally),
        ) {
            ToolButton(Icons.Filled.Edit, "Draw", state.tool == ImageEditorTool.DRAW) {
                state.tool = if (state.tool == ImageEditorTool.DRAW) null else ImageEditorTool.DRAW
            }
            ToolButton(Icons.Filled.Crop, "Crop", state.tool == ImageEditorTool.CROP) {
                state.tool = if (state.tool == ImageEditorTool.CROP) null else ImageEditorTool.CROP
            }
            ToolButton(Icons.Filled.RotateRight, "Rotate", state.tool == ImageEditorTool.ROTATE) {
                state.tool = if (state.tool == ImageEditorTool.ROTATE) null else ImageEditorTool.ROTATE
            }
        }
    }
}

/**
 * The row of options belonging to the active tool.
 *
 * Split out of [ImageEditorScreen] because the three arms together push the
 * screen past the complexity limit, and a toolbar that changes with the tool
 * reads better on its own than nested three levels into a Column.
 */
@Composable
private fun ToolOptions(
    state: ImageEditorState,
    canvasSize: Size,
    penColor: Color,
    onPenColor: (Color) -> Unit,
) {
    when (state.tool) {
        ImageEditorTool.DRAW ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm, Alignment.CenterHorizontally),
            ) {
                for (color in PEN_COLORS) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (color == penColor) 3.dp else 1.dp,
                                    color = if (color == penColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape,
                                ).semantics { contentDescription = "Pen colour" }
                                .clickable { onPenColor(color) },
                    )
                }
            }

        ImageEditorTool.CROP ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm, Alignment.CenterHorizontally),
            ) {
                for (aspect in ImageEditorAspect.entries) {
                    Text(
                        text = aspect.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (aspect == state.aspect) MaterialTheme.colorScheme.primary else Color.White,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { state.applyAspect(aspect) }
                                .padding(horizontal = SanchrTheme.spacing.sm, vertical = 6.dp),
                    )
                }
            }

        ImageEditorTool.ROTATE ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default, Alignment.CenterHorizontally),
            ) {
                IconButton(onClick = { state.rotateRight(canvasSize) }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = "Rotate right", tint = Color.White)
                }
                IconButton(onClick = { state.flipHorizontal(canvasSize) }) {
                    Icon(Icons.Filled.Flip, contentDescription = "Flip horizontally", tint = Color.White)
                }
            }

        null -> Unit
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}
