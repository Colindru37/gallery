package com.vlad.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relPath: String, // e.g. "DCIM/Camera/" — always with trailing slash, "" if unknown
    val favorite: Boolean,
)

/** One node in the folder tree built from relative paths. */
class FolderNode(val name: String, val path: String) {
    val children = LinkedHashMap<String, FolderNode>()
    val items = mutableListOf<MediaItem>()

    val totalCount: Int
        get() = items.size + children.values.sumOf { it.totalCount }

    /** Newest item anywhere under this node, used as the album cover. */
    val cover: MediaItem?
        get() {
            var best = items.firstOrNull()
            for (child in children.values) {
                val c = child.cover ?: continue
                if (best == null || c.dateTaken > best!!.dateTaken) best = c
            }
            return best
        }

    fun find(path: String): FolderNode? {
        if (path.isEmpty() || path == this.path) return this
        var node = this
        val rel = path.removePrefix(this.path)
        for (seg in rel.split('/').filter { it.isNotEmpty() }) {
            node = node.children[seg] ?: return null
        }
        return node
    }
}

object MediaRepository {

    fun queryAll(context: Context, trashedOnly: Boolean = false): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                )
            )
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
            )
        }

        val out = ArrayList<MediaItem>(4096)
        context.contentResolver.query(collection, projection, queryArgs, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iBId = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iRel = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val isVideo =
                    c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val uri = if (isVideo)
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                else
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                val taken = c.getLong(iTaken).let { if (it > 0) it else c.getLong(iMod) * 1000 }
                val relPath = normalizeRelPath(
                    c.getString(iRel),
                    c.getString(iData),
                )

                out.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        name = c.getString(iName) ?: "unknown",
                        mime = c.getString(iMime) ?: if (isVideo) "video/*" else "image/*",
                        isVideo = isVideo,
                        dateTaken = taken,
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        durationMs = if (isVideo) c.getLong(iDur) else 0L,
                        bucketId = c.getLong(iBId),
                        bucketName = c.getString(iBName) ?: "Storage",
                        relPath = relPath,
                        favorite = c.getInt(iFav) == 1,
                    )
                )
            }
        }
        out.sortByDescending { it.dateTaken }
        return out
    }

    private fun normalizeRelPath(relative: String?, data: String?): String {
        if (!relative.isNullOrBlank()) {
            return if (relative.endsWith("/")) relative else "$relative/"
        }
        // Fallback for legacy rows: derive folder from the absolute path
        if (!data.isNullOrBlank()) {
            val parent = data.substringBeforeLast('/', "")
            val marker = "/storage/emulated/0/"
            if (parent.startsWith(marker)) {
                val rel = parent.removePrefix(marker)
                return if (rel.isEmpty()) "" else "$rel/"
            }
        }
        return ""
    }

    /** Build the nested folder tree that powers the Albums tab. */
    fun buildTree(items: List<MediaItem>): FolderNode {
        val root = FolderNode("Storage", "")
        for (item in items) {
            var node = root
            var acc = ""
            for (seg in item.relPath.split('/').filter { it.isNotEmpty() }) {
                acc += "$seg/"
                node = node.children.getOrPut(seg) { FolderNode(seg, acc) }
            }
            node.items.add(item)
        }
        sortTree(root)
        return root
    }

    private fun sortTree(node: FolderNode) {
        val sorted = node.children.values.sortedBy { it.name.lowercase() }
        node.children.clear()
        for (child in sorted) {
            node.children[child.name] = child
            sortTree(child)
        }
    }
}
