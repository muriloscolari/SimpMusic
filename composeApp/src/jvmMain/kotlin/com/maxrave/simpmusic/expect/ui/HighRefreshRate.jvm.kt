package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

@Composable
actual fun ForceHighRefreshRate() {
    // No-op on Desktop: desktop systems already respect monitor refresh rates (e.g. 120Hz/144Hz)
}
