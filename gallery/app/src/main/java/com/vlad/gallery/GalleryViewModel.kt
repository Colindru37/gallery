package com.vlad.gallery

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vlad.gallery.data.FolderNode
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.data.MediaRepository
import com.vlad.gallery.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    var hasPermission by mutableStateOf(false)
    var loading by mutableStateOf(true)
        private set

    var items by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var trashedItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    var hiddenPaths by mutableStateOf(Prefs.hiddenPaths(app))
        private set
    var gridColumns by mutableIntStateOf(Prefs.gridColumns(app))
        private set

    /** Ids of selected media; selection mode is active while non-empty. */
    var selected by mutableStateOf<Set<Long>>(emptySet())

    val visibleItems by derivedStateOf {
        if (hiddenPaths.isEmpty()) items
        else items.filter { item -> hiddenPaths.none { item.relPath.startsWith(it) } }
    }

    val albumTree by derivedStateOf { MediaRepository.buildTree(visibleItems) }

    val favorites by derivedStateOf { visibleItems.filter { it.favorite } }

    val videos by derivedStateOf { visibleItems.filter { it.isVideo } }

    private var refreshJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshDebounced()
        }
    }

    init {
        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer
        )
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        if (!hasPermission) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val fresh = withContext(Dispatchers.IO) { MediaRepository.queryAll(app) }
            val trash = withContext(Dispatchers.IO) { MediaRepository.queryAll(app, trashedOnly = true) }
            items = fresh
            trashedItems = trash
            loading = false
        }
    }

    private fun refreshDebounced() {
        if (!hasPermission) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(400)
            val app = getApplication<Application>()
            val fresh = withContext(Dispatchers.IO) { MediaRepository.queryAll(app) }
            val trash = withContext(Dispatchers.IO) { MediaRepository.queryAll(app, trashedOnly = true) }
            items = fresh
            trashedItems = trash
            loading = false
        }
    }

    fun setColumns(value: Int) {
        val v = value.coerceIn(2, 6)
        gridColumns = v
        Prefs.setGridColumns(getApplication(), v)
    }

    fun hidePath(path: String) {
        hiddenPaths = hiddenPaths + path
        Prefs.setHiddenPaths(getApplication(), hiddenPaths)
    }

    fun unhidePath(path: String) {
        hiddenPaths = hiddenPaths - path
        Prefs.setHiddenPaths(getApplication(), hiddenPaths)
    }

    fun toggleSelect(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectedItems(source: List<MediaItem>): List<MediaItem> =
        source.filter { it.id in selected }
}
