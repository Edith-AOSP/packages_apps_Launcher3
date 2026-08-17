/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.icons.iconpack

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.roundToInt

/**
 * Cache of rendered icon preview bitmaps for the icon picker, keyed by the icon pack package,
 * drawable name, and target pixel size. Rendering is done at the requested size instead of the
 * drawable's intrinsic size so that large packs do not allocate full-resolution bitmaps per cell.
 */
class IconPickerIconCache(maxCacheSizeBytes: Int) {

    data class Key(val packPackageName: String, val drawableName: String, val sizePx: Int)

    private val cache =
        object : LruCache<Key, Bitmap>(maxCacheSizeBytes) {
            override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount
        }

    @Synchronized fun get(key: Key): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: Key, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    /** Renders [drawable] into a [sizePx] square bitmap, preserving its aspect ratio. */
    fun render(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val bounds =
            if (intrinsicWidth > 0 && intrinsicHeight > 0 && intrinsicWidth != intrinsicHeight) {
                val largestDimension =
                    if (intrinsicWidth > intrinsicHeight) intrinsicWidth else intrinsicHeight
                val scale = sizePx.toFloat() / largestDimension
                val width = (intrinsicWidth * scale).roundToInt()
                val height = (intrinsicHeight * scale).roundToInt()
                val left = (sizePx - width) / 2
                val top = (sizePx - height) / 2
                Rect(left, top, left + width, top + height)
            } else {
                Rect(0, 0, sizePx, sizePx)
            }
        drawable.bounds = bounds
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val MIN_CACHE_SIZE_BYTES = 8 * 1024 * 1024
        private const val MAX_CACHE_SIZE_BYTES = 32 * 1024 * 1024

        @Volatile private var sInstance: IconPickerIconCache? = null

        @JvmStatic
        fun getInstance(): IconPickerIconCache =
            sInstance
                ?: synchronized(this) {
                    sInstance
                        ?: IconPickerIconCache(calculateCacheSizeBytes()).also { sInstance = it }
                }

        private fun calculateCacheSizeBytes(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            return (maxMemory / 16)
                .coerceIn(MIN_CACHE_SIZE_BYTES.toLong(), MAX_CACHE_SIZE_BYTES.toLong())
                .toInt()
        }
    }
}
