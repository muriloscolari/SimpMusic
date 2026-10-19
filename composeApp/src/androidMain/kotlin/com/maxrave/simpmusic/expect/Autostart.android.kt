package com.maxrave.simpmusic.expect

actual fun setDesktopAutostart(enabled: Boolean, startMinimized: Boolean) {
    // No-op on Android
}

actual fun isDesktopAutostartSupported(): Boolean = false
