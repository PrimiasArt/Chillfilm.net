package com.phyll.filmhub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Configures bounded RAM and Disk caches specifically tuned for TV360 Box
 * hardware constraints (2GB RAM / 8GB internal eMMC storage).
 */
class PhyllApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("poster_cache"))
                    .maxSizeBytes(35L * 1024L * 1024L) // Strict 35 MB cap for 8GB TV360 Box
                    .build()
            }
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
}
