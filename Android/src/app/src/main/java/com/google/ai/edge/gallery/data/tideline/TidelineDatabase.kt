/*
 * Tideline Room database — Phase 3.
 *
 * Single-table v1. fallbackToDestructiveMigration is fine for a dev/demo build
 * (no real user data yet); turn on real migrations from Phase 4 onward if/when
 * we ship anything users keep.
 */

package com.google.ai.edge.gallery.data.tideline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TranslationEntity::class], version = 1, exportSchema = false)
abstract class TidelineDatabase : RoomDatabase() {

  abstract fun translationDao(): TranslationDao

  companion object {
    @Volatile
    private var instance: TidelineDatabase? = null

    fun get(context: Context): TidelineDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          TidelineDatabase::class.java,
          "tideline.db",
        )
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
          .also { instance = it }
      }
    }
  }
}
