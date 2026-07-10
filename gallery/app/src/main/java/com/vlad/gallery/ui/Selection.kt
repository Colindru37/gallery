package com.vlad.gallery.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.LocalActions
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.ui.theme.FavoriteRed

/**
 * Bottom action bar shown while items are selected.
 * [source] is the list the selection ids refer to.
 */
@Composable
fun SelectionBar(vm: GalleryViewModel, source: List<MediaItem>) {
    val actions = LocalActions.current
    val items = vm.selectedItems(source)
    if (items.isEmpty()) return

    var showMove by remember { mutableStateOf(false) }
    var showCopy by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val allFavorite = items.all { it.favorite }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.clearSelection() }) {
                Icon(Icons.Outlined.Close, "Cancel selection")
            }
            Text(
                "${items.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                IconButton(onClick = { vm.selected = source.map { it.id }.toSet() }) {
                    Icon(Icons.Outlined.SelectAll, "Select all")
                }
                IconButton(onClick = { actions.share(items) }) {
                    Icon(Icons.Outlined.Share, "Share")
                }
                IconButton(onClick = { actions.setFavorite(items, !allFavorite) }) {
                    if (allFavorite)
                        Icon(Icons.Filled.Favorite, "Unfavorite", tint = FavoriteRed)
                    else
                        Icon(Icons.Outlined.FavoriteBorder, "Favorite")
                }
                IconButton(onClick = { showMove = true }) {
                    Icon(Icons.Outlined.DriveFileMove, "Move to album")
                }
                IconButton(onClick = { showCopy = true }) {
                    Icon(Icons.Outlined.ContentCopy, "Copy to album")
                }
                IconButton(onClick = { confirmTrash = true }) {
                    Icon(Icons.Outlined.Delete, "Move to trash")
                }
            }
        }
    }

    if (showMove) {
        FolderPickerDialog(
            root = vm.albumTree,
            title = "Move ${items.size} item(s) to",
            onPick = { path ->
                showMove = false
                actions.move(items, path)
                vm.clearSelection()
            },
            onDismiss = { showMove = false },
        )
    }
    if (showCopy) {
        FolderPickerDialog(
            root = vm.albumTree,
            title = "Copy ${items.size} item(s) to",
            onPick = { path ->
                showCopy = false
                actions.copy(items, path)
                vm.clearSelection()
            },
            onDismiss = { showCopy = false },
        )
    }
    if (confirmTrash) {
        ConfirmDialog(
            title = "Move to trash?",
            text = "${items.size} item(s) will be moved to the trash. Items in the trash are deleted after 30 days.",
            confirmLabel = "Move to trash",
            onConfirm = { actions.trash(items) },
            onDismiss = { confirmTrash = false },
        )
    }
}
