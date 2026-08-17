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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import com.android.launcher3.R
import com.android.launcher3.icons.ExtendedBitmapDrawable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

class CustomIconPack(context: Context, packPackageName: String) :
    IconPack(context, packPackageName) {

    private val packResources =
        context.packageManager.getResourcesForApplication(packPackageName)
    private val componentMap = mutableMapOf<ComponentName, IconEntry>()
    private val calendarMap = mutableMapOf<ComponentName, IconEntry>()

    private val idCache = mutableMapOf<String, Int>()

    override val label = context.packageManager.let { pm ->
        pm.getApplicationInfo(packPackageName, 0).loadLabel(pm).toString()
    }

    init {
        startLoad()
    }

    override fun getIcon(componentName: ComponentName) = componentMap[componentName]
    override fun getCalendar(componentName: ComponentName) = calendarMap[componentName]

    override fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val id = getDrawableId(iconEntry.name)
        if (id == 0) return null
        return try {
            ExtendedBitmapDrawable.wrap(
                packResources,
                packResources.getDrawableForDensity(id, iconDpi, null),
                true,
            )
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    fun createFromExternalPicker(icon: Intent.ShortcutIconResource): IconPickerItem? {
        @SuppressLint("DiscouragedApi")
        val id = packResources.getIdentifier(icon.resourceName, null, null)
        if (id == 0) return null
        val simpleName = packResources.getResourceEntryName(id)
        return IconPickerItem(packPackageName, simpleName, simpleName, IconType.Normal)
    }

    override fun loadInternal() {
        val parseXml = getXml("appfilter") ?: return
        val compStart = "ComponentInfo{"
        val compStartLength = compStart.length
        val compEnd = "}"
        val compEndLength = compEnd.length
        try {
            while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                if (parseXml.eventType != XmlPullParser.START_TAG) continue
                val name = parseXml.name
                val isCalendar = name == "calendar"
                when (name) {
                    "item", "calendar" -> {
                        var componentName: String? = parseXml["component"]
                        val drawableName = parseXml[if (isCalendar) "prefix" else "drawable"]
                        if (componentName != null && drawableName != null) {
                            if (componentName.startsWith(compStart) &&
                                componentName.endsWith(compEnd)
                            ) {
                                componentName = componentName.substring(
                                    compStartLength,
                                    componentName.length - compEndLength,
                                )
                            }
                            val parsed = ComponentName.unflattenFromString(componentName)
                            if (parsed != null) {
                                if (isCalendar) {
                                    calendarMap[parsed] = IconEntry(
                                        packPackageName, drawableName, IconType.Calendar,
                                    )
                                } else {
                                    componentMap[parsed] = IconEntry(
                                        packPackageName, drawableName, IconType.Normal,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getAllIcons(): Flow<List<IconPickerCategory>> = flow {
        load()

        val result = mutableListOf<IconPickerCategory>()

        var currentTitle: String? = null
        val currentItems = mutableListOf<IconPickerItem>()

        suspend fun endCategory() {
            if (currentItems.isEmpty()) return
            val title = currentTitle
                ?: context.getString(R.string.icon_picker_default_category)
            result.add(IconPickerCategory(title, ArrayList(currentItems)))
            currentTitle = null
            currentItems.clear()
        }

        val parser = getXml("drawable")
        while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "category" -> {
                    val title = parser["title"] ?: continue
                    endCategory()
                    currentTitle = title
                }

                "item" -> {
                    val drawableName = parser["drawable"] ?: continue
                    val resId = getDrawableId(drawableName)
                    if (resId != 0) {
                        val item = IconPickerItem(
                            packPackageName, drawableName, drawableName, IconType.Normal,
                        )
                        currentItems.add(item)
                    }
                }
            }
        }
        endCategory()
        emit(result)
    }.flowOn(Dispatchers.IO)

    @SuppressLint("DiscouragedApi")
    private fun getDrawableId(name: String) = idCache.getOrPut(name) {
        packResources.getIdentifier(name, "drawable", packPackageName)
    }

    private fun getXml(name: String): XmlPullParser? {
        val res: Resources
        try {
            res = context.packageManager.getResourcesForApplication(packPackageName)
            @SuppressLint("DiscouragedApi")
            val resourceId = res.getIdentifier(name, "xml", packPackageName)
            return if (0 != resourceId) {
                context.packageManager.getXml(packPackageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(res.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                parser
            }
        } catch (_: PackageManager.NameNotFoundException) {
        } catch (_: IOException) {
        } catch (_: XmlPullParserException) {
        }
        return null
    }
}

private operator fun XmlPullParser.get(key: String): String? =
    this.getAttributeValue(null, key)
