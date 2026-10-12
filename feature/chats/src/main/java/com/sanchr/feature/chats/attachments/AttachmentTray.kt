package com.sanchr.feature.chats.attachments

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWarning
import com.sanchr.core.designsystem.theme.SanchrWhite
import kotlinx.coroutines.delay

/** The round actions along the bottom of the tray, in iOS's order. */
enum class AttachmentPill(
    val title: String,
    val icon: ImageVector,
) {
    CAMERA("Camera", Icons.Filled.PhotoCamera),
    PHOTOS("Photos", Icons.Filled.PhotoLibrary),
    VIDEO("Video", Icons.Filled.Videocam),

    // "GIF" on iOS, where the same panel also searches GIFs. Android has
    // stickers only, so it is named for what it opens.
    STICKERS("Stickers", Icons.Filled.AutoAwesome),
    FILE("File", Icons.AutoMirrored.Filled.InsertDriveFile),
    CONTACT("Contact", Icons.Filled.AccountCircle),
    LOCATION("Location", Icons.Filled.LocationOn),

    // Not a pill on iOS, which offers view-once from the review screen. Here
    // it is its own picker, and this menu was the only way to it.
    VIEW_ONCE("View once", Icons.Filled.LocalFireDepartment),
}

/**
 * The panel under the composer that the plus opens, as iOS's
 * `AttachmentPickerView`: the device's recent photos in a strip, and a row of
 * round actions beneath it.
 *
 * A tap on a photo picks it; a long press starts a selection of several, sent
 * in the order they were tapped. This replaces a dropdown of seven text rows,
 * which offered no photos at all and sent every pick through the system file
 * browser.
 *
 * @param onMediaChosen the photos and videos picked from the strip, in order.
 */
@Composable
fun AttachmentTray(
    onPill: (AttachmentPill) -> Unit,
    onMediaChosen: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source = remember { RecentMediaSource(context.applicationContext) }
    var access by remember { mutableStateOf(source.access()) }
    var reload by remember { mutableIntStateOf(0) }
    val askForAccess =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            access = source.access()
            // On a limited library the set can change with the grant unchanged.
            reload++
        }
    // Asked once, when the tray is first opened without access, as iOS asks.
    // A refusal is then explained in the strip rather than asked about again.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (access == MediaAccess.DENIED && !asked) {
            asked = true
            askForAccess.launch(RecentMediaSource.permissions())
        }
    }
    // Back from Settings, where the answer may have changed.
    LifecycleResumeEffect(Unit) {
        access = source.access()
        onPauseOrDispose {}
    }
    // Null while the library is being read.
    var recents by remember { mutableStateOf<List<RecentMedia>?>(null) }
    LaunchedEffect(access, reload) { recents = source.recents() }
    var selection by remember { mutableStateOf(TraySelection()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MS)
            message = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selection.active) {
            SelectionBar(
                selection = selection,
                onCancel = { selection = TraySelection() },
                onSend = {
                    val byId = recents.orEmpty().associateBy { it.id }
                    val chosen = selection.ids.mapNotNull { byId[it]?.uri }
                    selection = TraySelection()
                    if (chosen.isNotEmpty()) onMediaChosen(chosen)
                },
            )
        }
        message?.let {
            Text(
                text = it,
                style = IosType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT), contentAlignment = Alignment.Center) {
            val reason = trayEmptyReason(access, recents)
            if (reason != null) {
                EmptyStrip(reason = reason, onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) })
            } else {
                RecentsStrip(
                    recents = recents.orEmpty(),
                    selection = selection,
                    source = source,
                    onTap = { media ->
                        if (selection.active) {
                            val toggle = selection.toggled(media.id)
                            selection = toggle.selection
                            if (toggle.refused) message = "You can send up to ${TraySelection.LIMIT} photos at once"
                        } else {
                            onMediaChosen(listOf(media.uri))
                        }
                    },
                    onLongPress = { media -> if (!selection.active) selection = selection.startedWith(media.id) },
                )
            }
        }
        if (access == MediaAccess.LIMITED) {
            // Android, like iOS, lets a chosen handful of photos be shared and
            // expects a way to revise the choice.
            TextButton(
                onClick = { askForAccess.launch(RecentMediaSource.permissions()) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Select more photos…", style = IosType.caption) }
        }
        PillRow(onPill = onPill)
    }
}

private fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Cancel, the count, and Send, for a selection of several. */
@Composable
private fun SelectionBar(
    selection: TraySelection,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val count = selection.ids.size
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onCancel) { Text("Cancel", style = IosType.caption) }
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = IosType.caption,
            // At the ceiling the count is the only warning there is.
            color = if (selection.atLimit) SanchrWarning else MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onSend, enabled = count > 0) {
            Text("Send", style = IosType.caption, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Said where the strip would be when it has nothing in it, so a refused
 * library, an empty one and one still loading do not all look like blank
 * space.
 */
@Composable
private fun EmptyStrip(
    reason: TrayEmptyReason,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (reason == TrayEmptyReason.LOADING) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text =
                when (reason) {
                    TrayEmptyReason.LOADING -> "Loading photos…"
                    TrayEmptyReason.DENIED -> "Sanchr doesn't have access to your photos."
                    TrayEmptyReason.NO_PHOTOS -> "No recent photos."
                },
            style = IosType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (reason == TrayEmptyReason.DENIED) {
            TextButton(onClick = onOpenSettings) { Text("Open Settings", style = IosType.caption) }
        }
    }
}

@Composable
private fun RecentsStrip(
    recents: List<RecentMedia>,
    selection: TraySelection,
    source: RecentMediaSource,
    onTap: (RecentMedia) -> Unit,
    onLongPress: (RecentMedia) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(items = recents, key = { it.id }) { media ->
            RecentTile(
                media = media,
                position = recents.indexOf(media) + 1,
                total = recents.size,
                selecting = selection.active,
                selectedAt = selection.indexOf(media.id),
                source = source,
                onTap = { onTap(media) },
                onLongPress = { onLongPress(media) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTile(
    media: RecentMedia,
    position: Int,
    total: Int,
    selecting: Boolean,
    selectedAt: Int?,
    source: RecentMediaSource,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val sidePx = with(LocalDensity.current) { TILE_SIZE.roundToPx() }
    var thumbnail by remember(media.id) { mutableStateOf(source.cachedThumbnail(media)) }
    LaunchedEffect(media.id) { if (thumbnail == null) thumbnail = source.thumbnail(media, sidePx) }
    Box(
        modifier =
            Modifier
                .size(TILE_SIZE)
                .clip(RoundedCornerShape(12.dp))
                .background(SanchrTheme.surfaces.surface)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = "Select several",
                    onLongClick = onLongPress,
                    onClick = onTap,
                ).semantics {
                    // "Video, 0:12, 3 of 40" rather than the same word forty times.
                    contentDescription =
                        listOfNotNull(
                            if (media.isVideo) "Video" else "Photo",
                            trayDurationLabel(media.durationMs).takeIf { media.isVideo },
                            "$position of $total",
                        ).joinToString(", ")
                    if (selecting) {
                        stateDescription = selectedAt?.let { "Selected, number ${it + 1}" } ?: "Not selected"
                    }
                },
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (media.isVideo) VideoMarks(durationMs = media.durationMs)
        if (selecting) SelectionBadge(selectedAt = selectedAt, modifier = Modifier.padding(6.dp))
    }
}

/** A shade along the bottom, so the play mark and the length read over any picture. */
@Composable
private fun VideoMarks(durationMs: Long) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(VIDEO_SHADE_START to Color.Transparent, 1f to Color.Black.copy(alpha = SHADE_ALPHA))),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = trayDurationLabel(durationMs),
                style = IosType.micro,
                fontWeight = FontWeight.SemiBold,
                color = SanchrWhite,
            )
            Box(
                modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha = SHADE_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SanchrWhite, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Numbered rather than ticked: the send order is the tap order, so the badge
 * has to show it.
 */
@Composable
private fun SelectionBadge(
    selectedAt: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(22.dp)
                .background(if (selectedAt != null) SanchrIndigo500 else Color.Black.copy(alpha = UNPICKED_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selectedAt != null) {
            Text(text = "${selectedAt + 1}", style = IosType.captionSmall, fontWeight = FontWeight.Bold, color = SanchrWhite)
        }
    }
}

@Composable
private fun PillRow(onPill: (AttachmentPill) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PILLS_HEIGHT)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentPill.entries.forEach { pill ->
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = pill.title) { onPill(pill) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp).background(SanchrTheme.surfaces.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = pill.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(text = pill.title, style = IosType.captionSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The tray's least height, which is the height iOS frames it at. */
val ATTACHMENT_TRAY_HEIGHT = 280.dp

// iOS: a 124pt strip of 112pt tiles over a 96pt row of pills.
private val STRIP_HEIGHT = 124.dp
private val TILE_SIZE = 112.dp
private val PILLS_HEIGHT = 96.dp
private const val MESSAGE_MS = 4000L
private const val VIDEO_SHADE_START = 0.55f
private const val SHADE_ALPHA = 0.55f
private const val UNPICKED_ALPHA = 0.3f
