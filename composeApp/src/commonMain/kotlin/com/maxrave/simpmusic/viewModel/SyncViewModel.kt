package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.domain.repository.SyncRepository
import com.maxrave.domain.repository.SyncStatus
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val status: StateFlow<SyncStatus> = syncRepository.status

    private val _freshRecoveryKey = MutableStateFlow<String?>(null)
    val freshRecoveryKey: StateFlow<String?> = _freshRecoveryKey.asStateFlow()

    fun defaultDeviceName(): String =
        when (getPlatform()) {
            Platform.Android -> "SimpMusic Android"
            Platform.Desktop -> "SimpMusic Desktop"
            Platform.iOS -> "SimpMusic iOS"
        }

    fun openDialog() {
        syncRepository.clearError()
        if (status.value.authenticated) {
            viewModelScope.launch {
                syncRepository.refreshDevices()
            }
        }
    }

    fun dismissFreshRecoveryKey() {
        _freshRecoveryKey.value = null
    }

    fun updateServerUrl(serverUrl: String, enabled: Boolean = status.value.enabled) {
        viewModelScope.launch {
            syncRepository.configure(serverUrl, enabled)
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncRepository.configure(status.value.serverUrl, enabled)
        }
    }

    fun register(
        serverUrl: String,
        email: String,
        password: String,
        deviceName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                syncRepository.configure(serverUrl, true)
                val generatedKey = syncRepository.register(email, password, deviceName)
                _freshRecoveryKey.value = generatedKey
            }
        }
    }

    fun login(
        serverUrl: String,
        email: String,
        password: String,
        deviceName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                syncRepository.configure(serverUrl, true)
                syncRepository.login(email, password, deviceName)
            }
        }
    }

    fun recover(
        serverUrl: String,
        email: String,
        recoveryKey: String,
        newPassword: String,
        deviceName: String,
    ) {
        viewModelScope.launch {
            runCatching {
                syncRepository.configure(serverUrl, true)
                syncRepository.recover(email, recoveryKey, newPassword, deviceName)
            }
        }
    }

    fun synchronizeNow() {
        viewModelScope.launch {
            syncRepository.synchronize()
            syncRepository.refreshDevices()
        }
    }

    fun revokeDevice(deviceId: String) {
        viewModelScope.launch {
            syncRepository.revokeDevice(deviceId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _freshRecoveryKey.value = null
            syncRepository.logout()
        }
    }
}
