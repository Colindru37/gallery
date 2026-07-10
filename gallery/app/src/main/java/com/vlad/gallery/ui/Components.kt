package com.vlad.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vlad.gallery.data.FolderNode
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.ui.theme.FavoriteRed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- formatting ----------

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else "%.1f %s".format(value, units[unit])
}

fun itemDate(item: MediaItem): LocalDate =
    Instant.ofEpochMilli(item.dateTaken).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val fmt = if (date.year == today.year)
                DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
            else
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
            date.format(fmt)
        }
    }
}

fun formatFullDate(item: MediaItem): String {
    val zdt = Instant.ofEpochMilli(item.dateTaken).atZone(ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
}

// ---------- pinch to change grid density ----------

fun Modifier.pinchGridColumns(onStep: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            var acc = 1f
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    acc *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    if (acc > 1.35f) { onStep(-1); acc = 1f }
                    if (acc < 0.74f) { onStep(+1); acc = 1f }
                }
            } while (event.changes.any { it.pressed })
        }
    }

// ---------- grid ----------

data class TimelineSection(val label: String, val items: List<MediaItem>)

fun buildSections(items: List<MediaItem>): List<TimelineSection> {
    val out = ArrayList<TimelineSection>()
    var currentLabel: String? = null
    var bucket = ArrayList<MediaItem>()
    for (item in items) {
        val label = dayLabel(itemDate(item))
        if (label != currentLabel) {
            if (bucket.isNotEmpty()) out.add(TimelineSection(currentLabel!!, bucket))
            currentLabel = label
            bucket = ArrayList()
        }
        bucket.add(item)
    }
    if (bucket.isNotEmpty()) out.add(TimelineSection(currentLabel!!, bucket))
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCell(
    item: MediaItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .size(384)
                .crossfade(false)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayCircle, null,
                    tint = Color.White, modifier = Modifier.size(12.dp)
                )
                Text(
                    " " + formatDuration(item.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (item.favorite && !selectionMode) {
            Icon(
                Icons.Filled.Favorite, null,
                tint = FavoriteRed,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(13.dp),
            )
        }
        if (selectionMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) Color.Black.copy(alpha = 0.35f) else Color.Transparent
                    )
            )
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }
}

/**
 * The main media grid: optional date section headers, multi-select on
 * long-press, pinch to change density.
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    selected: Set<Long>,
    withHeaders: Boolean,
    gridState: LazyGridState,
    onToggleSelect: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    onPinch: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selectionMode = selected.isNotEmpty()
    val sections = if (withHeaders) buildSections(items) else null

    var gridModifier = modifier.fillMaxSize()
    if (onPinch != null) gridModifier = gridModifier.pinchGridColumns(onPinch)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = gridModifier,
    ) {
        if (sections != null) {
            var index = 0
            for (section in sections) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${section.label}") {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 8.dp),
                    )
                }
                val base = index
                val sectionItems = section.items
                items(sectionItems.size, key = { sectionItems[it].id }) { i ->
                    val item = sectionItems[i]
                    MediaCell(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in selected,
                        onClick = {
                            if (selectionMode) onToggleSelect(item.id) else onOpen(base + i)
                        },
                        onLongClick = { onToggleSelect(item.id) },
                    )
                }
                index += sectionItems.size
            }
        } else {
            items(items.size, key = { items[it].id }) { i ->
                val item = items[i]
                MediaCell(
                    item = item,
                    selectionMode = selectionMode,
                    selected = item.id in selected,
                    onClick = { if (selectionMode) onToggleSelect(item.id) else onOpen(i) },
                    onLongClick = { onToggleSelect(item.id) },
                )
            }
        }
    }
}

// ---------- album card ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    node: FolderNode,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick?.invoke() })
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val cover = node.cover
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(cover.uri).size(512).build(),
                    contentDescription = node.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (node.children.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Folder, null,
                        tint = Color.White, modifier = Modifier.size(12.dp)
                    )
                    Text(
                        " ${node.children.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            node.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Text(
            "${node.totalCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

// ---------- dialogs ----------

/** Pick a destination folder (or create a new album) for move/copy. */
@Composable
fun FolderPickerDialog(
    root: FolderNode,
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newAlbum by remember { mutableStateOf("") }
    val folders = remember(root) {
        val out = ArrayList<FolderNode>()
        fun walk(node: FolderNode) {
            if (node.path.isNotEmpty()) out.add(node)
            node.children.values.forEach { walk(it) }
        }
        walk(root)
        out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = newAlbum,
                    onValueChange = { newAlbum = it },
                    label = { Text("New album (under Pictures)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(top = 8.dp)
                ) {
                    items(folders, key = { it.path }) { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickableNoLong { onPick(node.path) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Folder, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "  " + node.path.trimEnd('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = newAlbum.trim().replace('/', ' ')
                    if (name.isNotEmpty()) onPick("Pictures/$name/")
                },
                enabled = newAlbum.isNotBlank(),
            ) { Text("Create + use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoLong(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick))

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name); onDismiss() },
                enabled = name.isNotBlank() && name != currentName,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
