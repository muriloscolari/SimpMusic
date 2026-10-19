package com.maxrave.simpmusic.expect

import com.maxrave.simpmusic.DesktopAutostartManager

actual fun setDesktopAutostart(enabled: Boolean, startMinimized: Boolean) {
    DesktopAutostartManager.setAutostart(enabled, startMinimized)
}

actual fun isDesktopAutostartSupported(): Boolean = DesktopAutostartManager.isSupported()
