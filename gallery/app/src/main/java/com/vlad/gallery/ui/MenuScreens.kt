package com.vlad.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.LocalActions
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.data.ViewerSession
import com.vlad.gallery.ui.theme.FavoriteRed

@Composable
fun MenuScreen(nav: NavController, vm: GalleryViewModel) {
    val totalSize = remember(vm.visibleItems) { vm.visibleItems.sumOf { it.size } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "More",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        Text(
            "${vm.visibleItems.size} items · ${formatSize(totalSize)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        MenuRow(Icons.Filled.Favorite, "Favourites", "${vm.favorites.size}", FavoriteRed) {
            nav.navigate("favorites")
        }
        MenuRow(Icons.Outlined.Movie, "Videos", "${vm.videos.size}") {
            nav.navigate("videos")
        }
        MenuRow(Icons.Outlined.Delete, "Trash", "${vm.trashedItems.size}") {
            nav.navigate("trash")
        }
        MenuRow(Icons.Outlined.VisibilityOff, "Hidden albums", "${vm.hiddenPaths.size}") {
            nav.navigate("hidden")
        }
        MenuRow(Icons.Outlined.Settings, "Settings", "") {
            nav.navigate("settings")
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    badge: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            Text(
                badge,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Simple grid page used by Favourites / Videos. */
@Composable
fun FilteredGridScreen(
    nav: NavController,
    vm: GalleryViewModel,
    title: String,
    items: List<MediaItem>,
    emptyText: String,
) {
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) EmptyState(emptyText)
            else MediaGrid(
                items = items,
                columns = vm.gridColumns,
                selected = vm.selected,
                withHeaders = true,
                gridState = gridState,
                onToggleSelect = vm::toggleSelect,
                onOpen = { index ->
                    ViewerSession.items = items
                    ViewerSession.startIndex = index
                    ViewerSession.fromTrash = false
                    nav.navigate("viewer")
                },
                onPinch = { step -> vm.setColumns(vm.gridColumns + step) },
            )
        }
        if (vm.selected.isNotEmpty()) SelectionBar(vm, items)
    }
}

@Composable
fun FavoritesScreen(nav: NavController, vm: GalleryViewModel) =
    FilteredGridScreen(nav, vm, "Favourites", vm.favorites, "Nothing favourited yet")

@Composable
fun VideosScreen(nav: NavController, vm: GalleryViewModel) =
    FilteredGridScreen(nav, vm, "Videos", vm.videos, "No videos")

@Composable
fun TrashScreen(nav: NavController, vm: GalleryViewModel) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val items = vm.trashedItems
    val gridState = rememberLazyGridState()
    val canManage = MediaStore.canManageMedia(context)
    var confirmEmpty by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                "Trash",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { actions.restore(items) }) { Text("Restore all") }
                TextButton(onClick = { confirmEmpty = true }) {
                    Text("Empty", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (!canManage) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "To see items trashed by other apps and manage the trash without confirmation dialogs, grant Gallery the \"Media management\" special access.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    ) { Text("Open settings") }
                }
            }
        }

        Text(
            "Items in the trash are permanently deleted by the system after 30 days.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) EmptyState("Trash is empty")
            else MediaGrid(
                items = items,
                columns = vm.gridColumns,
                selected = emptySet(),
                withHeaders = false,
                gridState = gridState,
                onToggleSelect = {},
                onOpen = { index ->
                    ViewerSession.items = items
                    ViewerSession.startIndex = index
                    ViewerSession.fromTrash = true
                    nav.navigate("viewer")
                },
            )
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty trash?",
            text = "All ${items.size} item(s) in the trash will be permanently deleted.",
            confirmLabel = "Delete all",
            onConfirm = { actions.deleteForever(items) },
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
fun HiddenScreen(nav: NavController, vm: GalleryViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                "Hidden albums",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (vm.hiddenPaths.isEmpty()) {
            EmptyState("No hidden albums. Hide one from its ⋮ menu.")
        } else {
            LazyColumn {
                items(vm.hiddenPaths.sorted()) { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.VisibilityOff, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            path.trimEnd('/'),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        IconButton(onClick = { vm.unhidePath(path) }) {
                            Icon(Icons.Outlined.Visibility, "Unhide")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController, vm: GalleryViewModel) {
    val context = LocalContext.current
    val canManage = MediaStore.canManageMedia(context)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Outlined.GridView, null, modifier = Modifier.size(20.dp))
                Text(
                    "Grid columns: ${vm.gridColumns}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Slider(
                value = vm.gridColumns.toFloat(),
                onValueChange = { vm.setColumns(it.toInt()) },
                valueRange = 2f..6f,
                steps = 3,
            )
            Text(
                "Tip: pinch on any grid to change this on the fly.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Text("Media management access", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (canManage)
                    "Granted — move, rename, trash and favourite work without confirmation dialogs."
                else
                    "Not granted — Android will show a confirmation dialog for every change to files owned by other apps. Granting this makes the app feel like a real system gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!canManage) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 10.dp),
                ) { Text("Grant in settings") }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Gallery 0.1 — built for the Nothing Phone 2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
