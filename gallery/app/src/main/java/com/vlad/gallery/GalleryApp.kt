package com.vlad.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class GalleryApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(ImageDecoderDecoder.Factory()) // animated GIF/WebP
            }
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.3).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(768L * 1024 * 1024)
                    .build()
            }
            .crossfade(100)
            .build()
}
