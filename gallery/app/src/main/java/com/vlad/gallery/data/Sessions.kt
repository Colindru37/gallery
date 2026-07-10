package com.vlad.gallery.data

/**
 * In-memory handoff between screens. Media lists are too big to serialize into
 * nav arguments, so the sender sets these right before navigating.
 */
object ViewerSession {
    var items: List<MediaItem> = emptyList()
    var startIndex: Int = 0
    /** True when the viewer shows trashed items (restore/delete-forever actions). */
    var fromTrash: Boolean = false
}

object EditSession {
    var item: MediaItem? = null
}
