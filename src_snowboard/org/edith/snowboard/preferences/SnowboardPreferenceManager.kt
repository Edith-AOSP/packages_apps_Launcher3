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

package org.edith.snowboard.preferences

import android.content.Context
import android.text.TextUtils
import com.android.launcher3.LauncherFiles
import com.android.launcher3.Utilities
import com.android.launcher3.util.ComponentKey
import org.json.JSONException
import org.json.JSONObject

class SnowboardPreferenceManager private constructor(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(
        LauncherFiles.SHARED_PREFERENCES_KEY,
        Context.MODE_PRIVATE,
    )

    var iconPackPackage: String
        get() = prefs.getString(PREF_ICON_PACK_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(PREF_ICON_PACK_PACKAGE, value).apply()

    var themedIconPackPackage: String
        get() = prefs.getString(PREF_THEMED_ICON_PACK_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(PREF_THEMED_ICON_PACK_PACKAGE, value).apply()

    fun getCustomLabel(key: ComponentKey): String? {
        val json = loadCustomLabelMap()
        return json.optString(key.toString(), null)
    }

    fun setCustomLabel(key: ComponentKey, label: String?) {
        val json = loadCustomLabelMap()
        if (label != null) {
            json.put(key.toString(), label)
        } else {
            json.remove(key.toString())
        }
        saveCustomLabelMap(json)
    }

    fun removeCustomLabel(key: ComponentKey) {
        setCustomLabel(key, null)
    }

    fun hasCustomLabel(key: ComponentKey): Boolean {
        return loadCustomLabelMap().has(key.toString())
    }

    private fun loadCustomLabelMap(): JSONObject {
        val raw = prefs.getString(PREF_APP_NAME_MAP, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: JSONException) {
            JSONObject()
        }
    }

    private fun saveCustomLabelMap(json: JSONObject) {
        prefs.edit().putString(PREF_APP_NAME_MAP, json.toString()).apply()
    }

    companion object {
        const val PREF_ICON_PACK_PACKAGE = "pref_iconPackPackage"
        const val PREF_THEMED_ICON_PACK_PACKAGE = "pref_themedIconPackPackage"
        const val PREF_APP_NAME_MAP = "pref_appNameMap"

        @Volatile
        private var sInstance: SnowboardPreferenceManager? = null

        @JvmStatic
        fun getInstance(context: Context): SnowboardPreferenceManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: SnowboardPreferenceManager(context.applicationContext as Context)
                    .also { sInstance = it }
            }
        }
    }
}
