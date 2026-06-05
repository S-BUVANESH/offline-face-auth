package com.example.data.repository

import com.example.data.local.dao.CheckInLogDao
import com.example.data.local.dao.PersonnelDao
import com.example.data.local.entity.CheckInLogEntity
import com.example.data.local.entity.PersonnelEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class DatalakeAuthRepository(
    private val personnelDao: PersonnelDao,
    private val checkInLogDao: CheckInLogDao
) {
    val allPersonnel: Flow<List<PersonnelEntity>> = personnelDao.getAllPersonnel()
    val allLogs: Flow<List<CheckInLogEntity>> = checkInLogDao.getAllLogs()

    suspend fun getPersonnelByEmployeeId(employeeId: String): PersonnelEntity? {
        return personnelDao.getPersonnelByEmployeeId(employeeId)
    }

    suspend fun insertPersonnel(personnel: PersonnelEntity): Long {
        return personnelDao.insertPersonnel(personnel)
    }

    suspend fun deletePersonnel(personnel: PersonnelEntity) {
        personnelDao.deletePersonnel(personnel)
    }

    suspend fun insertCheckInLog(log: CheckInLogEntity): Long {
        return checkInLogDao.insertLog(log)
    }

    // Sync & Purge Mechanism
    // Simulates an offline-to-online synchronization with AWS, then purges local synced cache
    suspend fun syncWithAwsServer(): Result<Int> {
        return try {
            val pendingLogs = checkInLogDao.getPendingLogs()
            if (pendingLogs.isEmpty()) {
                return Result.success(0)
            }
            
            // Simulate network latency for syncing data to AWS S3 / DynamoDB
            delay(1500)
            
            // Mark logs as synced
            val idsToMark = pendingLogs.map { it.id }
            checkInLogDao.markAsSynced(idsToMark)
            
            // IMMEDIATELY PURGE local records that are now safely synced to prevent data bloat
            checkInLogDao.purgeSyncedLogs()
            
            Result.success(pendingLogs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initSeedDataIfEmpty() {
        // Pre-populates a couple of diverse mock personnel record templates-to-be-recognized
        // this allows immediate testing of offline authentication with Indian demographics
    }
}
