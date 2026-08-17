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
import androidx.annotation.NonNull
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.annotation.VisibleForTesting

@Database(entities = [IconOverride::class], version = 1)
@TypeConverters(Converters::class)
abstract class SnowboardDatabase : RoomDatabase() {

    abstract fun iconOverrideDao(): IconOverrideDao

    companion object {
        const val DB_NAME = "snowboard_prefs"

        @Volatile
        private var sInstance: SnowboardDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): SnowboardDatabase {
            return sInstance ?: synchronized(this) {
                sInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnowboardDatabase::class.java,
                    DB_NAME,
                ).build().also { sInstance = it }
            }
        }

        @VisibleForTesting
        fun clearInstance() {
            sInstance = null
        }
    }
}
