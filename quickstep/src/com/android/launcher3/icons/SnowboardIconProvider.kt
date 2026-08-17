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

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageItemInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherModel
import com.android.launcher3.data.iconoverride.IconOverrideRepository
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.iconpack.IconPackProvider
import com.android.launcher3.icons.iconpack.IconType
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.PluginManagerWrapper
import com.android.systemui.plugins.IconProcessorPlugin
import javax.inject.Inject
import javax.inject.Provider

@LauncherAppSingleton
class SnowboardIconProvider
@Inject
constructor(
    @ApplicationContext ctx: Context,
    themeManager: ThemeManager,
    modelProvider: Provider<LauncherModel>,
    iconChangeTracker: IconChangeTracker,
    iconCacheProvider: Provider<IconCache>,
    pluginManagerWrapper: PluginManagerWrapper,
    lifecycle: DaggerSingletonTracker,
) : LauncherIconProviderImpl(
    ctx, themeManager, modelProvider, iconChangeTracker,
    iconCacheProvider, pluginManagerWrapper, lifecycle,
) {

    private val iconPackProvider = IconPackProvider.getInstance(mContext)
    private val overrideRepo = IconOverrideRepository.getInstance(mContext)

    /**
     * When non-null, overrides the icon pack for preview rendering without writing to
     * SharedPreferences. This allows the preview surface to show a different icon pack
     * without affecting the real workspace.
     */
    @Volatile
    private var previewIconPackOverride: String? = null

    override fun setPreviewIconPackOverride(iconPackPackage: String?) {
        previewIconPackOverride = iconPackPackage
    }

    private fun getSelectedIconPackPackage(): String {
        // If a preview override is set, use it instead of reading from SharedPreferences
        if (previewIconPackOverride != null) return previewIconPackOverride!!
        return try {
            mContext.getSharedPreferences(
                LauncherFiles.SHARED_PREFERENCES_KEY,
                android.content.Context.MODE_PRIVATE,
            )
            .getString("pref_iconPackPackage", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun updateSystemState() {
        super.updateSystemState()
        mSystemState = mSystemState.withAdditionalValues(",ip:" + getSelectedIconPackPackage())
    }

    override fun getIcon(
        info: PackageItemInfo,
        appInfo: ApplicationInfo,
        iconDpi: Int,
    ): Drawable {
        val packageName = appInfo.packageName
        val componentName =
            if (info is ComponentInfo) {
                ComponentName(info.packageName, info.name)
            } else {
                mContext.packageManager.getLaunchIntentForPackage(packageName)?.component
            }
        val user = UserHandle.getUserHandleForUid(appInfo.uid)

        var iconEntry = if (componentName != null) {
            resolveIconEntry(componentName, user)
        } else {
            null
        }

        if (iconEntry != null) {
            if (iconEntry.type == IconType.Calendar) {
                iconEntry = iconEntry.resolveDynamicCalendar(getDay())
            }

            val iconPackIcon = iconPackProvider.getDrawable(iconEntry, iconDpi)
            if (iconPackIcon != null) {
                return iconPackIcon
            }
        }

        return super.getIcon(info, appInfo, iconDpi)
    }

    private fun resolveIconEntry(
        componentName: ComponentName,
        user: UserHandle,
    ): com.android.launcher3.icons.iconpack.IconEntry? {
        val componentKey = ComponentKey(componentName, user)

        // First: per-app icon override
        val overrideItem = overrideRepo.overridesMap[componentKey]
        if (overrideItem != null) {
            return overrideItem.toIconEntry()
        }

        // Second: icon pack
        val selectedPack = getSelectedIconPackPackage()
        if (selectedPack.isEmpty()) {
            return null
        }
        val iconPack = iconPackProvider.getIconPackOrSystem(selectedPack) ?: return null
        iconPack.loadBlocking()

        // Dynamic calendar check
        val calendarEntry = iconPack.getCalendar(componentName)
        if (calendarEntry != null) {
            return calendarEntry
        }

        // Normal icon from pack
        return iconPack.getIcon(componentName)
    }
}
