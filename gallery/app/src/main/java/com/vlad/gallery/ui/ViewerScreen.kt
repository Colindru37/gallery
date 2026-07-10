package com.vlad.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vlad.gallery.GalleryViewModel
import com.vlad.gallery.LocalActions
import com.vlad.gallery.data.EditSession
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.data.ViewerSession
import com.vlad.gallery.ui.theme.FavoriteRed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(nav: NavController, vm: GalleryViewModel) {
    val actions = LocalActions.current
    val fromTrash = ViewerSession.fromTrash

    // Keep the viewer list in sync with the source of truth so deletions/
    // favorites reflect immediately. Items removed from the store drop out.
    val liveItems = remember(vm.items, vm.trashedItems) {
        val pool = (if (fromTrash) vm.trashedItems else vm.items).associateBy { it.id }
        ViewerSession.items.mapNotNull { original ->
            if (original.id == -1L) original else pool[original.id]
        }
    }

    if (liveItems.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    // The pageCount lambda is remembered once; read the list through State so
    // it stays correct after items are trashed/restored.
    val liveItemsState = androidx.compose.runtime.rememberUpdatedState(liveItems)
    val pagerState = rememberPagerState(
        initialPage = ViewerSession.startIndex.coerceIn(0, liveItems.lastIndex)
    ) { liveItemsState.value.size }

    var chrome by remember { mutableStateOf(true) }
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val current = liveItems[pagerState.currentPage.coerceIn(0, liveItems.lastIndex)]
    val isExternal = current.id == -1L

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = pagerScrollEnabled,
            key = { liveItems[it].id.takeIf { id -> id != -1L } ?: liveItems[it].uri },
        ) { page ->
            val item = liveItems[page]
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    isActive = page == pagerState.settledPage,
                    onTap = { chrome = !chrome },
                )
            } else {
                ZoomableImage(
                    item = item,
                    resetKey = pagerState.settledPage,
                    onTap = { chrome = !chrome },
                    setPagerScroll = { pagerScrollEnabled = it },
                )
            }
        }

        // top chrome
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isExternal) {
                        Text(
                            formatFullDate(current),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!isExternal && !fromTrash) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DriveFileRenameOutline, null)
                                },
                                onClick = { menuOpen = false; showRename = true },
                            )
                        }
                    }
                }
            }
        }

        // bottom chrome
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fromTrash) {
                    IconButton(onClick = { actions.restore(listOf(current)) }) {
                        Icon(Icons.Outlined.RestoreFromTrash, "Restore", tint = Color.White)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { actions.share(listOf(current)) }) {
                        Icon(Icons.Outlined.Share, "Share", tint = Color.White)
                    }
                    if (!isExternal) {
                        IconButton(
                            onClick = { actions.setFavorite(listOf(current), !current.favorite) }
                        ) {
                            if (current.favorite)
                                Icon(Icons.Filled.Favorite, "Unfavorite", tint = FavoriteRed)
                            else
                                Icon(Icons.Outlined.FavoriteBorder, "Favorite", tint = Color.White)
                        }
                        if (!current.isVideo) {
                            IconButton(onClick = {
                                EditSession.item = current
                                nav.navigate("edit")
                            }) {
                                Icon(Icons.Outlined.Edit, "Edit", tint = Color.White)
                            }
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Outlined.Info, "Details", tint = Color.White)
                    }
                    if (!isExternal) {
                        IconButton(onClick = { confirmTrash = true }) {
                            Icon(Icons.Outlined.Delete, "Move to trash", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            InfoSheet(current)
        }
    }
    if (showRename) {
        RenameDialog(
            currentName = current.name,
            onRename = { actions.rename(current, it) },
            onDismiss = { showRename = false },
        )
    }
    if (confirmTrash) {
        ConfirmDialog(
            title = "Move to trash?",
            text = "This item will be moved to the trash.",
            confirmLabel = "Move to trash",
            onConfirm = { actions.trash(listOf(current)) },
            onDismiss = { confirmTrash = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete permanently?",
            text = "This item will be deleted forever. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { actions.deleteForever(listOf(current)) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoSheet(item: MediaItem) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        InfoRow("Date", formatFullDate(item))
        if (item.relPath.isNotEmpty()) InfoRow("Path", item.relPath.trimEnd('/'))
        if (item.width > 0 && item.height > 0)
            InfoRow("Resolution", "${item.width} × ${item.height}")
        InfoRow("Size", formatSize(item.size))
        InfoRow("Type", item.mime)
        if (item.isVideo) InfoRow("Duration", formatDuration(item.durationMs))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Full-res image with pinch zoom, pan, and double-tap zoom. */
@Composable
private fun ZoomableImage(
    item: MediaItem,
    resetKey: Int,
    onTap: () -> Unit,
    setPagerScroll: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (s - 1f) * boxSize.width / 2f
        val maxY = (s - 1f) * boxSize.height / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    // leaving this page (or item changed) -> reset zoom
    LaunchedEffect(resetKey, item.uri) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(scale) {
        setPagerScroll(scale <= 1.02f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { pos ->
                        if (scale > 1.02f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            offset = clampOffset((center - pos) * (2.5f - 1f), 2.5f)
                        }
                    },
                )
            }
            .pointerInput(item.uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zooming = event.changes.size >= 2
                        if (zooming || scale > 1.02f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            scale = newScale
                            offset = clampOffset(offset + pan, newScale)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .size(coil.size.Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/** Video page: poster frame + play button; tap play to start inline playback. */
@Composable
private fun VideoPage(
    item: MediaItem,
    isActive: Boolean,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive) playing = false
    }

    if (!playing) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).size(1280).build(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = { playing = true },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.PlayArrow, "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    } else {
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(Media3Item.fromUri(item.uri))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(Unit) {
            onDispose { player.release() }
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
