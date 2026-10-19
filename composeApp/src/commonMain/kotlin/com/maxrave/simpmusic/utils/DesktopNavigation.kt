package com.maxrave.simpmusic.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.TimeSource

enum class DesktopNavEvent {
    Back,
    Forward,
}

object DesktopNavigation {
    private val _events = MutableSharedFlow<DesktopNavEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var lastMark = TimeSource.Monotonic.markNow()

    fun navigateBack() {
        val elapsed = lastMark.elapsedNow()
        if (elapsed.inWholeMilliseconds > 200) {
            lastMark = TimeSource.Monotonic.markNow()
            _events.tryEmit(DesktopNavEvent.Back)
        }
    }

    fun navigateForward() {
        val elapsed = lastMark.elapsedNow()
        if (elapsed.inWholeMilliseconds > 200) {
            lastMark = TimeSource.Monotonic.markNow()
            _events.tryEmit(DesktopNavEvent.Forward)
        }
    }
}
