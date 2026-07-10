package com.vlad.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.data.ViewerSession
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SearchFilter { ALL, IMAGES, VIDEOS, FAVORITES }

@Composable
fun SearchScreen(nav: NavController, vm: GalleryViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    val results by remember {
        derivedStateOf {
            val q = query.trim().lowercase()
            vm.visibleItems.filter { item ->
                val typeOk = when (filter) {
                    SearchFilter.ALL -> true
                    SearchFilter.IMAGES -> !item.isVideo
                    SearchFilter.VIDEOS -> item.isVideo
                    SearchFilter.FAVORITES -> item.favorite
                }
                if (!typeOk) return@filter false
                if (q.isEmpty()) return@filter true
                item.name.lowercase().contains(q) ||
                    item.relPath.lowercase().contains(q) ||
                    itemDate(item).format(monthFmt).lowercase().contains(q)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Name, folder, or \"july 2025\"") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            FilterChip(
                selected = filter == SearchFilter.ALL,
                onClick = { filter = SearchFilter.ALL },
                label = { Text("All") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = filter == SearchFilter.IMAGES,
                onClick = { filter = SearchFilter.IMAGES },
                label = { Text("Images") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = filter == SearchFilter.VIDEOS,
                onClick = { filter = SearchFilter.VIDEOS },
                label = { Text("Videos") },
                modifier = Modifier.padding(end = 6.dp),
            )
            FilterChip(
                selected = filter == SearchFilter.FAVORITES,
                onClick = { filter = SearchFilter.FAVORITES },
                label = { Text("Favourites") },
            )
        }

        Text(
            "${results.size} result(s)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Box(Modifier.fillMaxSize()) {
            if (results.isEmpty()) EmptyState("Nothing found")
            else MediaGrid(
                items = results,
                columns = vm.gridColumns,
                selected = emptySet(),
                withHeaders = false,
                gridState = gridState,
                onToggleSelect = {},
                onOpen = { index ->
                    ViewerSession.items = results
                    ViewerSession.startIndex = index
                    ViewerSession.fromTrash = false
                    nav.navigate("viewer")
                },
            )
        }
    }
}
