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
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.android.launcher3.R

data class IconPackInfo(
    @JvmField val packageName: String,
    @JvmField val name: String,
    @JvmField val icon: Drawable?,
)

object IconPackUtil {

    private val ICON_PACK_INTENTS = arrayOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.dlto.atom.launcher.THEME",
    )

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = mutableListOf<IconPackInfo>()

        // System Icons entry with Snowboard icon
        packs.add(
            IconPackInfo(
                packageName = "",
                name = context.getString(R.string.system_icons),
                icon = context.packageManager.getApplicationIcon(context.packageName),
            )
        )

        val seenPackages = mutableSetOf<String>()

        for (action in ICON_PACK_INTENTS) {
            val intent = Intent(action)
            val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            for (activity in activities) {
                val pkg = activity.activityInfo.packageName
                if (seenPackages.add(pkg)) {
                    val appInfo = activity.activityInfo.applicationInfo
                    packs.add(
                        IconPackInfo(
                            packageName = pkg,
                            name = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                        ),
                    )
                }
            }
        }

        // Also check for apex/lawnicons-style packs via MAIN intent
        val apexIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("com.anddoes.launcher.THEME")
        }
        val apexActivities: List<ResolveInfo> = pm.queryIntentActivities(apexIntent, 0)
        for (activity in apexActivities) {
            val pkg = activity.activityInfo.packageName
            if (seenPackages.add(pkg)) {
                val appInfo = activity.activityInfo.applicationInfo
                packs.add(
                    IconPackInfo(
                        packageName = pkg,
                        name = appInfo.loadLabel(pm).toString(),
                        icon = appInfo.loadIcon(pm),
                    ),
                )
            }
        }

        return packs.sortedBy { it.name.lowercase() }
    }
}
