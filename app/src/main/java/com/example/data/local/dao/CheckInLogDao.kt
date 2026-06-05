package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CheckInLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInLogDao {
    @Query("SELECT * FROM check_in_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CheckInLogEntity>>

    @Query("SELECT * FROM check_in_logs WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingLogs(): List<CheckInLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CheckInLogEntity): Long

    @Query("UPDATE check_in_logs SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Int>)

    @Query("DELETE FROM check_in_logs WHERE syncStatus = 'SYNCED'")
    suspend fun purgeSyncedLogs()

    @Query("DELETE FROM check_in_logs")
    suspend fun clearAll()
}
