package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personnel")
data class PersonnelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val employeeId: String,
    val department: String,
    val registeredAt: Long = System.currentTimeMillis(),
    val landmarkEmbedding: String // Comma-separated normalized landmark distances
)
