package com.vlad.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.data.ViewerSession

@Composable
fun TimelineScreen(nav: NavController, vm: GalleryViewModel) {
    val items = vm.visibleItems
    val gridState = rememberLazyGridState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pictures",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { nav.navigate("search") }) {
                Icon(Icons.Outlined.Search, "Search")
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                items.isEmpty() -> EmptyState("No photos or videos yet")
                else -> MediaGrid(
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
        }

        if (vm.selected.isNotEmpty()) SelectionBar(vm, items)
    }
}
