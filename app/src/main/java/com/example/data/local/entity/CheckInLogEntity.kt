package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_logs")
data class CheckInLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: String,
    val name: String,
    val department: String,
    val timestamp: Long = System.currentTimeMillis(),
    val livenessScore: Float, // Liveness rating or status
    val matchingConfidence: Float, // Similarity score (0.0 to 1.0)
    val syncStatus: String // "PENDING" or "SYNCED"
)
