package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.PersonnelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonnelDao {
    @Query("SELECT * FROM personnel ORDER BY name ASC")
    fun getAllPersonnel(): Flow<List<PersonnelEntity>>

    @Query("SELECT * FROM personnel WHERE id = :id LIMIT 1")
    suspend fun getPersonnelById(id: Int): PersonnelEntity?

    @Query("SELECT * FROM personnel WHERE employeeId = :empId LIMIT 1")
    suspend fun getPersonnelByEmployeeId(empId: String): PersonnelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonnel(personnel: PersonnelEntity): Long

    @Delete
    suspend fun deletePersonnel(personnel: PersonnelEntity)

    @Query("DELETE FROM personnel")
    suspend fun clearAll()
}
