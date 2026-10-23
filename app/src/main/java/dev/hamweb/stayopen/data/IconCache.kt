package dev.hamweb.stayopen.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

/** Bounded reusable icon cache. Clearing references is not a claim about reclaimed physical RAM. */
object IconCache {
    private val images = LruCache<String, Bitmap>(48)
    fun peek(packageName: String): Bitmap? = images.get(packageName)
    fun load(context: Context, packageName: String): Bitmap? = images.get(packageName) ?: runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72, Bitmap.Config.ARGB_8888)
            .also { images.put(packageName, it) }
    }.getOrNull()
    fun clear(): Int {
        val count = images.size()
        images.evictAll()
        // Do not recycle bitmaps: a visible Compose image may still be using one.
        return count
    }
}
