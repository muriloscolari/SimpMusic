package com.maxrave.simpmusic.expect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object PlatformAppUpdater {
    actual val isSupported: Boolean = false

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    actual val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    actual fun startUpdate(downloadUrl: String?, zsyncUrl: String?) {}

    actual fun restartApp() {}

    actual fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
