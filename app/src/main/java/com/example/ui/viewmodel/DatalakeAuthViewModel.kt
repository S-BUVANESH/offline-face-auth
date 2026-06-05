package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biometrics.CommonBiometricEngine
import com.example.data.local.entity.CheckInLogEntity
import com.example.data.local.entity.PersonnelEntity
import com.example.data.repository.DatalakeAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val syncedCount: Int) : SyncState
    data class Error(val errorMessage: String) : SyncState
}

class DatalakeAuthViewModel(
    private val repository: DatalakeAuthRepository
) : ViewModel() {

    private val _isNetworkOnline = MutableStateFlow(false) // Defaults to OFFLINE, typical of remote field operations
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _personnel = MutableStateFlow<List<PersonnelEntity>>(emptyList())
    val personnel: StateFlow<List<PersonnelEntity>> = _personnel.asStateFlow()

    private val _logs = MutableStateFlow<List<CheckInLogEntity>>(emptyList())
    val logs: StateFlow<List<CheckInLogEntity>> = _logs.asStateFlow()

    init {
        // Observe local database flows
        viewModelScope.launch {
            repository.allPersonnel
                .catch { _personnel.value = emptyList() }
                .collect { _personnel.value = it }
        }
        viewModelScope.launch {
            repository.allLogs
                .catch { _logs.value = emptyList() }
                .collect { _logs.value = it }
        }
    }

    fun setNetworkOnline(online: Boolean) {
        _isNetworkOnline.value = online
        if (online) {
            // Automatically launch background synchronization
            syncWithAws()
        } else {
            // Reset sync status when going offline
            if (_syncState.value is SyncState.Success || _syncState.value is SyncState.Error) {
                _syncState.value = SyncState.Idle
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun registerPersonnel(name: String, employeeId: String, department: String, embedding: FloatArray, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val exists = repository.getPersonnelByEmployeeId(employeeId)
                if (exists != null) {
                    onComplete(false) // Employee ID duplicate
                    return@launch
                }
                val entity = PersonnelEntity(
                    name = name.trim(),
                    employeeId = employeeId.trim().uppercase(),
                    department = department.trim(),
                    landmarkEmbedding = CommonBiometricEngine.embeddingToString(embedding)
                )
                repository.insertPersonnel(entity)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun deletePersonnelRecord(personnelEntity: PersonnelEntity) {
        viewModelScope.launch {
            repository.deletePersonnel(personnelEntity)
        }
    }

    fun logAuthCheckIn(employeeId: String, name: String, department: String, livenessScore: Float, matchingConfidence: Float) {
        viewModelScope.launch {
            val logEntity = CheckInLogEntity(
                employeeId = employeeId,
                name = name,
                department = department,
                livenessScore = livenessScore,
                matchingConfidence = matchingConfidence,
                syncStatus = "PENDING"
            )
            repository.insertCheckInLog(logEntity)
            
            // Auto sync instantly if network connection is available
            if (_isNetworkOnline.value) {
                syncWithAws()
            }
        }
    }

    // Triggers network synchronization to AWS
    fun syncWithAws() {
        if (!_isNetworkOnline.value) {
            _syncState.value = SyncState.Error("Sync failed: Device is completely offline. Restore network connection to sync logs with AWS.")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            val result = repository.syncWithAwsServer()
            result.onSuccess { count ->
                _syncState.value = SyncState.Success(count)
            }
            result.onFailure { exception ->
                _syncState.value = SyncState.Error(exception.message ?: "Failed to synchronize logs with remote server.")
            }
        }
    }

    // Seed demographic demo records for testing
    fun seedDemographicSamples() {
        // No-op to ensure zero mock/sample entries exist in the production app
    }
}
