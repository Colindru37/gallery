package com.vlad.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.data.FolderNode
import com.vlad.gallery.data.ViewerSession
import com.vlad.gallery.encodePath

@Composable
fun AlbumsScreen(nav: NavController, vm: GalleryViewModel) {
    val root = vm.albumTree

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Albums",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { nav.navigate("search") }) {
                Icon(Icons.Outlined.Search, "Search")
            }
        }

        if (root.children.isEmpty()) {
            EmptyState("No albums")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
            ) {
                val nodes = root.children.values.toList()
                items(nodes.size, key = { nodes[it].path }) { i ->
                    val node = nodes[i]
                    AlbumCard(
                        node = node,
                        onClick = { nav.navigate("folder/${encodePath(node.path)}") },
                    )
                }
            }
        }
    }
}

/** One folder: its sub-albums on top, then its own media grid. */
@Composable
fun FolderScreen(nav: NavController, vm: GalleryViewModel, path: String) {
    val node: FolderNode? = vm.albumTree.find(path)
    val gridState = rememberLazyGridState()
    var menuOpen by remember { mutableStateOf(false) }

    if (node == null) {
        // Folder disappeared (deleted/hidden) — bounce back.
        EmptyState("Album not found")
        return
    }
    val ownItems = node.items

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${node.totalCount} items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Hide this album") },
                        leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                        onClick = {
                            menuOpen = false
                            vm.hidePath(node.path)
                            nav.popBackStack()
                        },
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(vm.gridColumns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .pinchGridColumns { step -> vm.setColumns(vm.gridColumns + step) },
            ) {
                if (node.children.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "sub-title") {
                        Text(
                            "Albums",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    val subs = node.children.values.toList()
                    // Sub-albums rendered as a nested 2-col strip inside full-width rows
                    val rows = subs.chunked(2)
                    for ((rowIndex, row) in rows.withIndex()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "subrow-$rowIndex") {
                            Row(Modifier.fillMaxWidth()) {
                                for (sub in row) {
                                    Box(Modifier.weight(1f)) {
                                        AlbumCard(
                                            node = sub,
                                            onClick = {
                                                nav.navigate("folder/${encodePath(sub.path)}")
                                            },
                                        )
                                    }
                                }
                                if (row.size == 1) Box(Modifier.weight(1f)) {}
                            }
                        }
                    }
                    if (ownItems.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "own-title") {
                            Text(
                                "In this album",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
                val selectionMode = vm.selected.isNotEmpty()
                items(ownItems.size, key = { ownItems[it].id }) { i ->
                    val item = ownItems[i]
                    MediaCell(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in vm.selected,
                        onClick = {
                            if (selectionMode) vm.toggleSelect(item.id)
                            else {
                                ViewerSession.items = ownItems
                                ViewerSession.startIndex = i
                                ViewerSession.fromTrash = false
                                nav.navigate("viewer")
                            }
                        },
                        onLongClick = { vm.toggleSelect(item.id) },
                    )
                }
            }
        }

        if (vm.selected.isNotEmpty()) SelectionBar(vm, ownItems)
    }
}
