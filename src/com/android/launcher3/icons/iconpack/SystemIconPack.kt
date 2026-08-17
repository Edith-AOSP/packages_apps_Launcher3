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
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemIconPack(context: Context, pkg: String) : IconPack(context, pkg) {

    override val label = context.getString(R.string.system_icons)
    private val appMap = run {
        val profiles = UserCache.INSTANCE.get(context).userProfiles
        val launcherApps: LauncherApps =
            context.getSystemService(LauncherApps::class.java)!!
        profiles
            .flatMap { launcherApps.getActivityList(null, Process.myUserHandle()) }
            .associateBy { ComponentKey(it.componentName, it.user) }
    }

    init {
        startLoad()
    }

    override fun getIcon(componentName: ComponentName) = IconEntry(
        packPackageName,
        ComponentKey(componentName, Process.myUserHandle()).toString(),
        IconType.Normal,
    )

    override fun getCalendar(componentName: ComponentName): IconEntry? = null

    override fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val key = ComponentKey.fromString(iconEntry.name)
        val app = appMap[key] ?: return null
        return app.getIcon(iconDpi)
    }

    override fun loadInternal() {
    }

    override fun getAllIcons(): Flow<List<IconPickerCategory>> = flow {
        val items = appMap
            .map { (key, info) ->
                IconPickerItem(
                    packPackageName = packPackageName,
                    drawableName = key.toString(),
                    label = info.label.toString(),
                    IconType.Normal,
                )
            }
        emit(categorize(items))
    }.flowOn(Dispatchers.IO)
}
