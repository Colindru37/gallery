package com.vlad.gallery

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.lifecycleScope
import com.vlad.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * All mutations go through MediaStore "request" PendingIntents so they work on
 * media owned by other apps (camera, WhatsApp...). If the user grants this app
 * "Media management" special access, most of them resolve without a dialog.
 */
class MediaActions(
    private val activity: ComponentActivity,
    private val launchSender: (IntentSenderRequest) -> Unit,
    private val onChanged: () -> Unit,
) {
    private var afterWrite: (() -> Unit)? = null

    fun onWriteResult(granted: Boolean) {
        val block = afterWrite
        afterWrite = null
        if (granted) block?.invoke()
        onChanged()
    }

    private fun launch(pi: PendingIntent) =
        launchSender(IntentSenderRequest.Builder(pi.intentSender).build())

    private fun uris(items: List<MediaItem>): List<Uri> = items.map { it.uri }

    fun trash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launch(MediaStore.createTrashRequest(activity.contentResolver, uris(items), true))
    }

    fun restore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launch(MediaStore.createTrashRequest(activity.contentResolver, uris(items), false))
    }

    fun deleteForever(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launch(MediaStore.createDeleteRequest(activity.contentResolver, uris(items)))
    }

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) {
        if (items.isEmpty()) return
        launch(MediaStore.createFavoriteRequest(activity.contentResolver, uris(items), favorite))
    }

    fun share(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items[0].mime
                putExtra(Intent.EXTRA_STREAM, items[0].uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (items.all { !it.isVideo }) "image/*"
                else if (items.all { it.isVideo }) "video/*" else "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris(items)))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(intent, null))
    }

    fun rename(item: MediaItem, newName: String) {
        if (newName.isBlank()) return
        withWriteAccess(listOf(item)) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName.trim())
            }
            runCatching { activity.contentResolver.update(item.uri, values, null, null) }
                .onFailure { toast("Rename failed: ${it.message}") }
        }
    }

    /** Move by rewriting RELATIVE_PATH; MediaStore physically moves the file. */
    fun move(items: List<MediaItem>, targetRelPath: String) {
        if (items.isEmpty()) return
        withWriteAccess(items) {
            var failed = 0
            for (item in items) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelPath)
                }
                runCatching { activity.contentResolver.update(item.uri, values, null, null) }
                    .onFailure { failed++ }
            }
            if (failed > 0) toast("$failed item(s) could not be moved")
        }
    }

    fun copy(items: List<MediaItem>, targetRelPath: String) {
        if (items.isEmpty()) return
        activity.lifecycleScope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        val collection = if (item.isVideo)
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueCopyName(item.name, targetRelPath))
                            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelPath)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val dest = activity.contentResolver.insert(collection, values)
                            ?: throw IllegalStateException("insert returned null")
                        activity.contentResolver.openInputStream(item.uri)!!.use { input ->
                            activity.contentResolver.openOutputStream(dest)!!.use { output ->
                                input.copyTo(output)
                            }
                        }
                        activity.contentResolver.update(
                            dest,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null, null
                        )
                    } catch (e: Exception) {
                        failed++
                    }
                }
            }
            if (failed > 0) toast("$failed item(s) could not be copied")
            onChanged()
        }
    }

    private fun uniqueCopyName(name: String, targetRelPath: String): String {
        val dir = File(Environment.getExternalStorageDirectory(), targetRelPath)
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (!File(dir, candidate).exists()) return candidate
            n++
        }
    }

    /**
     * Direct ContentResolver updates need write access to each row. With the
     * Media management special access we can just do it; otherwise we ask the
     * system for a one-shot write grant, then run the block on approval.
     */
    private fun withWriteAccess(items: List<MediaItem>, block: () -> Unit) {
        if (MediaStore.canManageMedia(activity)) {
            block()
            onChanged()
        } else {
            afterWrite = block
            launch(MediaStore.createWriteRequest(activity.contentResolver, uris(items)))
        }
    }

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }
}

val LocalActions = staticCompositionLocalOf<MediaActions> {
    error("MediaActions not provided")
}
