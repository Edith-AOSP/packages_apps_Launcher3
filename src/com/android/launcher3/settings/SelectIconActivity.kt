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

package com.android.launcher3.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.android.compose.theme.PlatformTheme
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.data.iconoverride.IconOverrideRepository
import com.android.launcher3.icons.iconpack.IconPack
import com.android.launcher3.icons.iconpack.IconPackProvider
import com.android.launcher3.icons.iconpack.IconPickerCategory
import com.android.launcher3.icons.iconpack.IconPickerIconCache
import com.android.launcher3.icons.iconpack.IconPickerItem
import com.android.launcher3.icons.iconpack.filter
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class SelectIconActivity : ComponentActivity() {

    companion object {
        const val EXTRA_COMPONENT_KEY = "component_key"
        const val EXTRA_APP_LABEL = "app_label"

        @JvmStatic
        fun createIntent(context: Context, componentKey: ComponentKey, appLabel: String): Intent {
            return Intent(context, SelectIconActivity::class.java).apply {
                putExtra(EXTRA_COMPONENT_KEY, componentKey.toString())
                putExtra(EXTRA_APP_LABEL, appLabel)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val componentKeyString =
            intent.getStringExtra(EXTRA_COMPONENT_KEY)
                ?: run {
                    finish()
                    return
                }
        val componentKey =
            ComponentKey.fromString(componentKeyString)
                ?: run {
                    finish()
                    return
                }
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: ""

        setContent {
            PlatformTheme {
                IconPickerScreen(
                    appName = appLabel,
                    componentKey = componentKey,
                    onIconSelected = { item ->
                        IconOverrideRepository.getInstance(this).setOverride(componentKey, item)
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    appName: String,
    componentKey: ComponentKey,
    onIconSelected: (IconPickerItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val iconPackProvider = remember { IconPackProvider.getInstance(context) }
    val packs = remember { iconPackProvider.getAllIconPacks() }
    var selectedPack by remember { mutableStateOf<IconPack?>(null) }
    val repo = remember { IconOverrideRepository.getInstance(context) }

    BackHandler(selectedPack != null) { selectedPack = null }

    if (selectedPack == null) {
        val hasOverride = repo.overridesMap[componentKey] != null
        PackListScreen(
            appName = appName,
            packs = packs,
            onBack = onBack,
            onPackClick = { pack -> selectedPack = pack },
            onRestoreDefault =
                if (hasOverride)
                    ({
                        repo.deleteOverride(componentKey)
                        LauncherAppState.getInstance(context)
                            .model
                            .reloadIfActive("icon-override-restored")
                        onBack()
                    })
                else null,
        )
    } else {
        val pack = selectedPack!!
        IconPackScreen(
            pack = pack,
            onIconSelected = onIconSelected,
            onBack = { selectedPack = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackListScreen(
    appName: String,
    packs: List<IconPack>,
    onBack: () -> Unit,
    onPackClick: (IconPack) -> Unit,
    onRestoreDefault: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (onRestoreDefault != null) {
                item {
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(onClick = onRestoreDefault),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.restore_default_icon),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(packs) { pack ->
                Surface(
                    modifier =
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onPackClick(pack)
                        },
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        text = pack.label,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPackScreen(
    pack: IconPack,
    onIconSelected: (IconPickerItem) -> Unit,
    onBack: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface {
                IconPackSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(pack.label) },
                    onBack = onBack,
                    modifier =
                        Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        bottomBar = { Spacer(Modifier.height(16.dp)) },
    ) { padding ->
        val categories by
            produceState<List<IconPickerCategory>?>(null, pack) {
                withContext(Dispatchers.IO) { pack.getAllIcons().firstOrNull() }.also { value = it }
            }
        if (categories == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered =
                remember(categories!!, searchQuery) {
                    if (searchQuery.isBlank()) categories!!
                    else
                        categories!!.map { it.filter(searchQuery) }.filter { it.items.isNotEmpty() }
                }
            IconGrid(filtered, pack, onIconSelected, padding)
        }
    }
}

@Composable
private fun IconPackSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox =
                @Composable { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = remember { MutableInteractionSource() },
                        placeholder = placeholder,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                        contentPadding = PaddingValues(0.dp),
                        container = {},
                    )
                },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Clear, "Clear") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconGrid(
    categories: List<IconPickerCategory>,
    pack: IconPack,
    onIconSelected: (IconPickerItem) -> Unit,
    padding: PaddingValues,
) {
    val numColumns = 5
    val chunkedCategories =
        remember(categories) {
            categories.map { category -> category to category.items.chunked(numColumns) }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        chunkedCategories.forEach { (category, rows) ->
            stickyHeader(key = category.title) {
                Text(
                    text = category.title,
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(
                count = rows.size,
                key = { index -> "${category.title}:${rows[index].first().drawableName}" },
            ) { index ->
                val row = rows[index]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .aspectRatio(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onIconSelected(item) },
                            contentAlignment = Alignment.Center,
                        ) {
                            IconPreview(pack, item)
                        }
                    }
                    repeat(numColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val ICON_PREVIEW_SIZE_DP = 48

@Composable
fun IconPreview(pack: IconPack, item: IconPickerItem) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { ICON_PREVIEW_SIZE_DP.dp.roundToPx() }
    val bitmap by
        produceState<Bitmap?>(null, item, pack) {
            value =
                withContext(Dispatchers.IO) {
                    val cache = IconPickerIconCache.getInstance()
                    val key =
                        IconPickerIconCache.Key(
                            packPackageName = item.packPackageName,
                            drawableName = item.drawableName,
                            sizePx = iconSizePx,
                        )
                    cache.get(key)
                        ?: run {
                            val drawable =
                                pack.getIcon(
                                    item.toIconEntry(),
                                    context.resources.displayMetrics.densityDpi,
                                ) ?: return@withContext null
                            cache.render(drawable, iconSizePx).also { cache.put(key, it) }
                        }
                }
        }

    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                painter = remember(it) { BitmapPainter(it.asImageBitmap()) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
