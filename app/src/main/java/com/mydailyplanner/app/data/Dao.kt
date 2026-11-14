package com.mydailyplanner.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities ORDER BY startMillis ASC")
    fun observeAll(): Flow<List<ActivityItem>>

    @Query("SELECT * FROM activities ORDER BY startMillis ASC")
    suspend fun getAll(): List<ActivityItem>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityItem>)

    @Update
    suspend fun update(item: ActivityItem)

    @Delete
    suspend fun delete(item: ActivityItem)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()
}
