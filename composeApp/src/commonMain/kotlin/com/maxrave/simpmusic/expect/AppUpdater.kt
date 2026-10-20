package com.maxrave.simpmusic.expect

import kotlinx.coroutines.flow.StateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val progress: Float, val message: String = "") : UpdateState
    data class ReadyToRestart(val newExecutablePath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

expect object PlatformAppUpdater {
    val isSupported: Boolean
    val updateState: StateFlow<UpdateState>
    fun startUpdate(downloadUrl: String? = null, zsyncUrl: String? = null)
    fun restartApp()
    fun resetState()
}
