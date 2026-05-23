/*
 * Translation DAO — Phase 3.
 *
 * Recent-first ordering by id matches Python core's CLI list (which orders by
 * implicit ROWID). Cap at LATEST_LIMIT for the UI; if Phase 4 portfolio demos
 * need more history, paging or count APIs can be added then.
 */

package com.google.ai.edge.gallery.data.tideline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val LATEST_LIMIT = 50

@Dao
interface TranslationDao {

  @Insert
  suspend fun insert(entity: TranslationEntity): Long

  @Query("SELECT * FROM translations ORDER BY id DESC LIMIT $LATEST_LIMIT")
  fun observeLatest(): Flow<List<TranslationEntity>>

  @Query("SELECT COUNT(*) FROM translations")
  suspend fun count(): Int
}
