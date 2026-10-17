package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // Desktop uses the on-screen back button.
}

@Composable
internal actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
) {
    // Desktop uses the on-screen back button.
}

