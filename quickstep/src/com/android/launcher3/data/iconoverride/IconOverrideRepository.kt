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

package com.android.launcher3.data.iconoverride

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.launcher3.LauncherAppState
import com.android.launcher3.icons.iconpack.IconPickerItem
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IconOverrideRepository(private val context: Context) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineName("IconOverrideRepository")
    )

    private val dao = SnowboardDatabase.getInstance(context).iconOverrideDao()

    private var _overridesMap by mutableStateOf(mapOf<ComponentKey, IconPickerItem>())
    val overridesMap: Map<ComponentKey, IconPickerItem>
        get() = _overridesMap

    init {
        scope.launch {
            dao.observeAll().collect { overrides ->
                _overridesMap = overrides.associateBy(
                    keySelector = { it.target },
                    valueTransform = { it.iconPickerItem },
                )
            }
        }
    }

    fun setOverride(target: ComponentKey, item: IconPickerItem) {
        _overridesMap = _overridesMap + (target to item)
        scope.launch {
            dao.insert(IconOverride(target, item))
            updatePackageIcons(target)
        }
    }

    fun deleteOverride(target: ComponentKey) {
        _overridesMap = _overridesMap - target
        scope.launch {
            dao.delete(target)
            updatePackageIcons(target)
        }
    }

    fun deleteAll() {
        _overridesMap = emptyMap()
        scope.launch {
            dao.deleteAll()
            MODEL_EXECUTOR.execute {
                val state = LauncherAppState.getInstance(context)
                state.iconCache.clearMemoryCache()
                state.model.reloadIfActive("icon-overrides-cleared")
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun updatePackageIcons(target: ComponentKey) {
        MODEL_EXECUTOR.execute {
            val state = LauncherAppState.getInstance(context)
            state.iconCache.updateIconsForPkg(
                target.componentName.packageName,
                target.user,
            )
            state.iconCache.clearMemoryCache()
            state.model.onPackageIconsUpdated(
                hashSetOf(target.componentName.packageName),
                target.user,
            )
        }
    }

    companion object {
        @Volatile
        private var sInstance: IconOverrideRepository? = null

        @JvmStatic
        fun getInstance(context: Context): IconOverrideRepository {
            return sInstance ?: synchronized(this) {
                sInstance ?: IconOverrideRepository(context.applicationContext)
                    .also { sInstance = it }
            }
        }

        @VisibleForTesting
        fun clearInstance() {
            sInstance?.destroy()
            sInstance = null
        }
    }
}
