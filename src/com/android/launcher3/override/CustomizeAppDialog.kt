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

package com.android.launcher3.override

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.data.iconoverride.IconOverrideRepository
import com.android.launcher3.icons.iconpack.IconEntry
import com.android.launcher3.icons.iconpack.IconPackProvider
import com.android.launcher3.icons.iconpack.IconType
import com.android.launcher3.settings.SelectIconActivity
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.edith.snowboard.preferences.SnowboardPreferenceManager

@Composable
fun CustomizeDialog(
    icon: Drawable,
    title: String,
    onTitleChange: (String) -> Unit,
    defaultTitle: String,
    launchSelectIcon: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        val iconPainter = remember(icon) {
            val bitmap = Bitmap.createBitmap(
                icon.intrinsicWidth.coerceAtLeast(1),
                icon.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            BitmapPainter(bitmap.asImageBitmap())
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 24.dp)
                .clip(MaterialTheme.shapes.small)
                .then(
                    if (launchSelectIcon != null) {
                        Modifier.clickable(onClick = launchSelectIcon)
                    } else {
                        Modifier
                    },
                )
                .padding(all = 8.dp),
        ) {
            Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
            )
            if (launchSelectIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            label = { Text(stringResource(R.string.customize_app_label_hint)) },
            isError = title.isEmpty(),
            trailingIcon = {
                if (title != defaultTitle) {
                    IconButton(onClick = { onTitleChange(defaultTitle) }) {
                        Icon(
                            imageVector = Icons.Rounded.Undo,
                            contentDescription = null,
                        )
                    }
                }
            },
        )
        content?.invoke()
    }
}

@Composable
fun CustomizeAppDialog(
    icon: Drawable,
    defaultTitle: String,
    componentKey: ComponentKey,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = SnowboardPreferenceManager.getInstance(context)
    val repo = IconOverrideRepository.getInstance(context)
    var title by remember {
        mutableStateOf(prefs.getCustomLabel(componentKey) ?: defaultTitle)
    }
    val launcherAppState = LauncherAppState.getInstance(context)

    // Reactively load the icon: use override if set, otherwise default
    val currentIcon by produceState(icon, repo.overridesMap[componentKey]) {
        value = withContext(Dispatchers.IO) {
            val overrideItem = repo.overridesMap[componentKey]
            if (overrideItem != null) {
                val provider = IconPackProvider.getInstance(context)
                val pack = provider.getIconPackOrSystem(overrideItem.packPackageName)
                if (pack != null) {
                    pack.loadBlocking()
                    val entry = IconEntry(
                        packPackageName = overrideItem.packPackageName,
                        name = overrideItem.drawableName,
                        type = IconType.Normal,
                    )
                    pack.getIcon(entry, context.resources.displayMetrics.densityDpi)
                        ?: icon
                } else {
                    icon
                }
            } else {
                icon
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val previousTitle = prefs.getCustomLabel(componentKey)
            val newTitle = if (title.isNotEmpty() && title != defaultTitle) title else null
            if (newTitle != previousTitle) {
                prefs.setCustomLabel(componentKey, newTitle)
                val model = launcherAppState.model
                model.onPackageIconsUpdated(
                    hashSetOf(componentKey.componentName.packageName),
                    componentKey.user,
                )
            }
        }
    }

    CustomizeDialog(
        icon = currentIcon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle,
        launchSelectIcon = {
            context.startActivity(
                SelectIconActivity.createIntent(context, componentKey, defaultTitle)
            )
        },
        modifier = modifier,
    )
}
