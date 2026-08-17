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

package com.android.launcher3.popup

import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.Intent
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.override.ComposeBottomSheet
import com.android.launcher3.override.CustomizeAppDialog
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.ActivityContext

object SnowboardCustomizeShortcut {

    @JvmField
    val CUSTOMIZE = SystemShortcut.Factory<ActivityContext> { activity, itemInfo, originalView ->
        val componentKey = itemInfo.componentKey
        if (componentKey == null) {
            return@Factory null
        }
        Customize(activity, itemInfo, originalView, componentKey)
    }

    class Customize(
        private val activity: ActivityContext,
        itemInfo: ItemInfo,
        originalView: View,
        private val componentKey: ComponentKey,
    ) : SystemShortcut<ActivityContext>(
        R.drawable.ic_customize,
        R.string.customize_shortcut_label,
        activity,
        itemInfo,
        originalView,
    ) {

        override fun onClick(v: View) {
            AbstractFloatingView.closeAllOpenViews(mTarget)

            val ctx = activity.asContext()
            val launcherApps = ctx.getSystemService(LauncherApps::class.java) ?: return
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setComponent(componentKey.componentName)
            }
            val lai: LauncherActivityInfo = try {
                launcherApps.resolveActivity(intent, componentKey.user) ?: return
            } catch (_: Exception) {
                return
            }

            val defaultTitle = lai.label.toString()
            val icon = lai.getIcon(0)

            val launcher = Launcher.getLauncher(ctx)
            ComposeBottomSheet.show(
                context = launcher,
                contentPaddings = PaddingValues(bottom = 64.dp),
            ) {
                CustomizeAppDialog(
                    icon = icon,
                    defaultTitle = defaultTitle,
                    componentKey = componentKey,
                ) { close(true) }
            }
        }
    }
}
