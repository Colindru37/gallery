package com.vlad.gallery.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.navigation.NavController
import com.vlad.gallery.data.EditSession
import com.vlad.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DragMode { NONE, MOVE, TL, TR, BL, BR }

@Composable
fun EditScreen(nav: NavController) {
    val item = EditSession.item
    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val preview by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeBitmap(context, item.uri, maxDim = 2048) }.getOrNull()
        }
    }

    var rotation by remember { mutableIntStateOf(0) } // degrees, multiples of 90
    var flipH by remember { mutableStateOf(false) }
    // crop rect as fractions of the *displayed* (rotated/flipped) bitmap
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var saving by remember { mutableStateOf(false) }

    val displayed = remember(preview, rotation, flipH) {
        preview?.let { transformBitmap(it, rotation, flipH) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cancel", tint = Color.White)
            }
            Text(
                "Edit",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = displayed != null && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                saveEdited(context, item, rotation, flipH, crop)
                            }.isSuccess
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            if (ok) "Saved as copy" else "Save failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) nav.popBackStack()
                    }
                },
                modifier = Modifier.padding(end = 12.dp),
            ) { Text(if (saving) "Saving…" else "Save copy") }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val bmp = displayed
            if (bmp == null) {
                CircularProgressIndicator()
            } else {
                CropCanvas(
                    bitmap = bmp,
                    crop = crop,
                    onCropChange = { crop = it },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = {
                rotation = (rotation + 270) % 360
                crop = Rect(0f, 0f, 1f, 1f)
            }) {
                Icon(Icons.Outlined.RotateLeft, "Rotate left", tint = Color.White)
            }
            IconButton(onClick = {
                rotation = (rotation + 90) % 360
                crop = Rect(0f, 0f, 1f, 1f)
            }) {
                Icon(Icons.Outlined.RotateRight, "Rotate right", tint = Color.White)
            }
            IconButton(onClick = {
                flipH = !flipH
                crop = Rect(0f, 0f, 1f, 1f)
            }) {
                Icon(Icons.Outlined.Flip, "Flip", tint = Color.White)
            }
            IconButton(onClick = {
                rotation = 0; flipH = false; crop = Rect(0f, 0f, 1f, 1f)
            }) {
                Icon(Icons.Outlined.RestartAlt, "Reset", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    crop: Rect,
    onCropChange: (Rect) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = minOf(boxW / bitmap.width, boxH / bitmap.height)
        val imgW = bitmap.width * scale
        val imgH = bitmap.height * scale
        val imgLeft = (boxW - imgW) / 2f
        val imgTop = (boxH - imgH) / 2f
        val imgRect = Rect(imgLeft, imgTop, imgLeft + imgW, imgTop + imgH)

        var dragMode by remember { mutableStateOf(DragMode.NONE) }
        // The pointerInput closure outlives recompositions; read the crop via
        // State so drags always see the latest rect.
        val liveCrop = androidx.compose.runtime.rememberUpdatedState(crop)

        fun cropPx(): Rect {
            val c = liveCrop.value
            return Rect(
                imgRect.left + c.left * imgW,
                imgRect.top + c.top * imgH,
                imgRect.left + c.right * imgW,
                imgRect.top + c.bottom * imgH,
            )
        }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, imgW, imgH) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = cropPx()
                            val grab = 48.dp.toPx()
                            dragMode = when {
                                (pos - Offset(r.left, r.top)).getDistance() < grab -> DragMode.TL
                                (pos - Offset(r.right, r.top)).getDistance() < grab -> DragMode.TR
                                (pos - Offset(r.left, r.bottom)).getDistance() < grab -> DragMode.BL
                                (pos - Offset(r.right, r.bottom)).getDistance() < grab -> DragMode.BR
                                r.contains(pos) -> DragMode.MOVE
                                else -> DragMode.NONE
                            }
                        },
                        onDragEnd = { dragMode = DragMode.NONE },
                        onDrag = { change, amount ->
                            change.consume()
                            val dx = amount.x / imgW
                            val dy = amount.y / imgH
                            val minSize = 0.08f
                            var c = liveCrop.value
                            c = when (dragMode) {
                                DragMode.TL -> Rect(
                                    (c.left + dx).coerceIn(0f, c.right - minSize),
                                    (c.top + dy).coerceIn(0f, c.bottom - minSize),
                                    c.right, c.bottom,
                                )
                                DragMode.TR -> Rect(
                                    c.left,
                                    (c.top + dy).coerceIn(0f, c.bottom - minSize),
                                    (c.right + dx).coerceIn(c.left + minSize, 1f),
                                    c.bottom,
                                )
                                DragMode.BL -> Rect(
                                    (c.left + dx).coerceIn(0f, c.right - minSize),
                                    c.top,
                                    c.right,
                                    (c.bottom + dy).coerceIn(c.top + minSize, 1f),
                                )
                                DragMode.BR -> Rect(
                                    c.left, c.top,
                                    (c.right + dx).coerceIn(c.left + minSize, 1f),
                                    (c.bottom + dy).coerceIn(c.top + minSize, 1f),
                                )
                                DragMode.MOVE -> {
                                    val w = c.width
                                    val h = c.height
                                    val nl = (c.left + dx).coerceIn(0f, 1f - w)
                                    val nt = (c.top + dy).coerceIn(0f, 1f - h)
                                    Rect(nl, nt, nl + w, nt + h)
                                }
                                DragMode.NONE -> c
                            }
                            onCropChange(c)
                        },
                    )
                }
        ) {
            val r = cropPx()
            // dim everything outside the crop
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(imgRect.left, imgRect.top), Size(imgW, r.top - imgRect.top))
            drawRect(dim, Offset(imgRect.left, r.bottom), Size(imgW, imgRect.bottom - r.bottom))
            drawRect(dim, Offset(imgRect.left, r.top), Size(r.left - imgRect.left, r.height))
            drawRect(dim, Offset(r.right, r.top), Size(imgRect.right - r.right, r.height))
            // frame
            drawRect(
                Color.White,
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = Stroke(width = 2.dp.toPx()),
            )
            // thirds
            val thin = Stroke(width = 1.dp.toPx())
            for (i in 1..2) {
                drawLine(
                    Color.White.copy(alpha = 0.4f),
                    Offset(r.left + r.width * i / 3f, r.top),
                    Offset(r.left + r.width * i / 3f, r.bottom),
                    strokeWidth = thin.width,
                )
                drawLine(
                    Color.White.copy(alpha = 0.4f),
                    Offset(r.left, r.top + r.height * i / 3f),
                    Offset(r.right, r.top + r.height * i / 3f),
                    strokeWidth = thin.width,
                )
            }
            // corner handles
            val handle = 18.dp.toPx()
            val hw = 4.dp.toPx()
            val corners = listOf(
                floatArrayOf(r.left, r.top, 1f, 1f),
                floatArrayOf(r.right, r.top, -1f, 1f),
                floatArrayOf(r.left, r.bottom, 1f, -1f),
                floatArrayOf(r.right, r.bottom, -1f, -1f),
            )
            for (corner in corners) {
                val cx = corner[0]; val cy = corner[1]; val sx = corner[2]; val sy = corner[3]
                drawLine(Color.White, Offset(cx, cy), Offset(cx + handle * sx, cy), strokeWidth = hw)
                drawLine(Color.White, Offset(cx, cy), Offset(cx, cy + handle * sy), strokeWidth = hw)
            }
        }
    }
}

// ---------- bitmap plumbing ----------

private fun decodeBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val biggest = maxOf(w, h)
        if (biggest > maxDim) {
            val ratio = maxDim.toFloat() / biggest
            decoder.setTargetSize((w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1))
        }
    }
}

private fun transformBitmap(src: Bitmap, rotation: Int, flipH: Boolean): Bitmap {
    if (rotation == 0 && !flipH) return src
    val matrix = Matrix().apply {
        if (flipH) postScale(-1f, 1f)
        if (rotation != 0) postRotate(rotation.toFloat())
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

private fun saveEdited(
    context: Context,
    item: MediaItem,
    rotation: Int,
    flipH: Boolean,
    crop: Rect,
) {
    // Full-res decode for the actual save (preview was capped at 2048)
    val full = decodeBitmap(context, item.uri, maxDim = 8192)
    val transformed = transformBitmap(full, rotation, flipH)

    val left = (crop.left * transformed.width).toInt().coerceIn(0, transformed.width - 1)
    val top = (crop.top * transformed.height).toInt().coerceIn(0, transformed.height - 1)
    val w = (crop.width * transformed.width).toInt().coerceIn(1, transformed.width - left)
    val h = (crop.height * transformed.height).toInt().coerceIn(1, transformed.height - top)
    val cropped =
        if (left == 0 && top == 0 && w == transformed.width && h == transformed.height) transformed
        else Bitmap.createBitmap(transformed, left, top, w, h)

    val base = item.name.substringBeforeLast('.')
    val newName = "${base}_edit_${System.currentTimeMillis() % 1_000_000}.jpg"
    val relPath = item.relPath.ifEmpty { "Pictures/" }

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val dest = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ) ?: throw IllegalStateException("insert failed")

    context.contentResolver.openOutputStream(dest)!!.use { out ->
        if (!cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
            throw IllegalStateException("compress failed")
        }
    }
    context.contentResolver.update(
        dest,
        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
        null, null,
    )
}
