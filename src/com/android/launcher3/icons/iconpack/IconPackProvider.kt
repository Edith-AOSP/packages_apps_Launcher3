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

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class IconPackProvider(private val context: Context) {

    private val iconPacks = mutableMapOf<String, IconPack?>()

    fun getIconPackOrSystem(packageName: String): IconPack? {
        if (packageName.isEmpty()) return SystemIconPack(context, packageName)
        return getIconPack(packageName)
    }

    fun getIconPack(packageName: String): IconPack? {
        if (packageName.isEmpty()) {
            return null
        }
        return iconPacks.getOrPut(packageName) {
            try {
                CustomIconPack(context, packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun getDrawable(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val iconPack = getIconPackOrSystem(iconEntry.packPackageName) ?: return null
        iconPack.loadBlocking()
        return iconPack.getIcon(iconEntry, iconDpi)
    }

    fun getAllIconPacks(): List<IconPack> {
        val packs = mutableListOf<IconPack>()
        packs.add(SystemIconPack(context, ""))
        val installedPacks = IconPackUtil.getInstalledIconPacks(context)
        for (info in installedPacks) {
            getIconPack(info.packageName)?.let { packs.add(it) }
        }
        return packs
    }

    companion object {
        @Volatile
        private var sInstance: IconPackProvider? = null

        @JvmStatic
        fun getInstance(context: Context): IconPackProvider {
            return sInstance ?: synchronized(this) {
                sInstance ?: IconPackProvider(context.applicationContext)
                    .also { sInstance = it }
            }
        }

        @JvmStatic
        fun newInstance(context: Context): IconPackProvider {
            return IconPackProvider(context.applicationContext)
        }
    }
}
