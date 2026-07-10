package com.vlad.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vlad.gallery.data.MediaItem
import com.vlad.gallery.data.ViewerSession
import com.vlad.gallery.ui.AlbumsScreen
import com.vlad.gallery.ui.EditScreen
import com.vlad.gallery.ui.FolderScreen
import com.vlad.gallery.ui.MenuScreen
import com.vlad.gallery.ui.FavoritesScreen
import com.vlad.gallery.ui.HiddenScreen
import com.vlad.gallery.ui.SettingsScreen
import com.vlad.gallery.ui.TrashScreen
import com.vlad.gallery.ui.VideosScreen
import com.vlad.gallery.ui.SearchScreen
import com.vlad.gallery.ui.TimelineScreen
import com.vlad.gallery.ui.ViewerScreen
import com.vlad.gallery.ui.theme.GalleryTheme
import java.util.Base64

class MainActivity : ComponentActivity() {

    private val vm: GalleryViewModel by viewModels()
    lateinit var actions: MediaActions
        private set

    private val writeLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            actions.onWriteResult(res.resultCode == Activity.RESULT_OK)
            vm.clearSelection()
            vm.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        actions = MediaActions(
            activity = this,
            launchSender = { writeLauncher.launch(it) },
            onChanged = { vm.refresh() },
        )

        // Opened from another app with a specific image/video?
        val externalUri: Uri? =
            if (intent?.action == Intent.ACTION_VIEW) intent?.data else null

        setContent {
            GalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CompositionLocalProvider(LocalActions provides actions) {
                        Root(vm, externalUri)
                    }
                }
            }
        }
    }
}

@Composable
private fun Root(vm: GalleryViewModel, externalUri: Uri?) {
    val context = androidx.compose.ui.platform.LocalContext.current

    fun permissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
            PackageManager.PERMISSION_GRANTED

    var asked by rememberSaveable { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.hasPermission = permissionGranted()
        if (vm.hasPermission) vm.refresh()
    }

    LaunchedEffect(Unit) {
        vm.hasPermission = permissionGranted()
        if (vm.hasPermission) {
            vm.refresh()
        } else if (!asked) {
            asked = true
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
            )
        }
    }

    if (!vm.hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Gallery needs access to your photos and videos.")
            Button(
                onClick = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                        )
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Grant access") }
        }
        return
    }

    val nav = rememberNavController()

    // Single item handed to us by another app: jump straight into the viewer.
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            val item = resolveExternalItem(context, externalUri)
            ViewerSession.items = listOf(item)
            ViewerSession.startIndex = 0
            ViewerSession.fromTrash = false
            nav.navigate("viewer")
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScaffold(nav, vm) }
        composable("folder/{path}") { backStack ->
            val encoded = backStack.arguments?.getString("path") ?: ""
            val path = decodePath(encoded)
            FolderScreen(nav, vm, path)
        }
        composable("viewer") { ViewerScreen(nav, vm) }
        composable("search") { SearchScreen(nav, vm) }
        composable("favorites") { FavoritesScreen(nav, vm) }
        composable("videos") { VideosScreen(nav, vm) }
        composable("trash") { TrashScreen(nav, vm) }
        composable("hidden") { HiddenScreen(nav, vm) }
        composable("settings") { SettingsScreen(nav, vm) }
        composable("edit") { EditScreen(nav) }
    }
}

// Folder paths contain '/', which breaks nav route segments — base64url them.
fun encodePath(path: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))

fun decodePath(encoded: String): String =
    runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }.getOrDefault("")

private fun resolveExternalItem(context: android.content.Context, uri: Uri): MediaItem {
    var name = uri.lastPathSegment ?: "file"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val iSize = c.getColumnIndex(OpenableColumns.SIZE)
                if (iName >= 0) c.getString(iName)?.let { name = it }
                if (iSize >= 0) size = c.getLong(iSize)
            }
        }
    }
    val mime = context.contentResolver.getType(uri) ?: "image/*"
    return MediaItem(
        id = -1L,
        uri = uri,
        name = name,
        mime = mime,
        isVideo = mime.startsWith("video"),
        dateTaken = System.currentTimeMillis(),
        size = size,
        width = 0,
        height = 0,
        durationMs = 0,
        bucketId = -1,
        bucketName = "",
        relPath = "",
        favorite = false,
    )
}

@Composable
private fun HomeScaffold(nav: androidx.navigation.NavController, vm: GalleryViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            // Hide the bar while selecting so the selection action bar can live there
            if (vm.selected.isEmpty()) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            Icon(
                                if (tab == 0) Icons.Filled.Photo else Icons.Outlined.Photo,
                                null,
                            )
                        },
                        label = { Text("Pictures") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                if (tab == 1) Icons.Filled.Collections else Icons.Outlined.Collections,
                                null,
                            )
                        },
                        label = { Text("Albums") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Outlined.MoreHoriz, null) },
                        label = { Text("More") },
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> TimelineScreen(nav, vm)
                1 -> AlbumsScreen(nav, vm)
                else -> MenuScreen(nav, vm)
            }
        }
    }
}
