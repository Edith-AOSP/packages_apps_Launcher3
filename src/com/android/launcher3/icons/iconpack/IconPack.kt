/*
 * Copyright 2024 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import com.android.launcher3.compat.AlphabeticIndexCompat
import java.util.concurrent.Semaphore
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus

sealed class IconPack(
    protected val context: Context,
    val packPackageName: String,
) {
    private var waiter: Semaphore? = Semaphore(0)
    private lateinit var deferredLoad: Deferred<Unit>

    abstract val label: String

    private val alphabeticIndexCompat by lazy { AlphabeticIndexCompat(context) }

    protected fun startLoad() {
        deferredLoad = scope.async(Dispatchers.IO) {
            loadInternal()
            waiter?.release()
            waiter = null
        }
    }

    suspend fun load() {
        return deferredLoad.await()
    }

    fun loadBlocking() {
        waiter?.run {
            acquireUninterruptibly()
            release()
        }
    }

    abstract fun getIcon(componentName: ComponentName): IconEntry?
    abstract fun getCalendar(componentName: ComponentName): IconEntry?

    abstract fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable?

    abstract fun getAllIcons(): Flow<List<IconPickerCategory>>

    protected abstract fun loadInternal()

    protected fun categorize(allItems: List<IconPickerItem>): List<IconPickerCategory> {
        return allItems
            .groupBy { alphabeticIndexCompat.computeSectionName(it.label) }
            .map { (sectionName, items) ->
                IconPickerCategory(
                    title = sectionName,
                    items = items,
                )
            }
            .sortedBy { it.title }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("IconPack")
    }
}
