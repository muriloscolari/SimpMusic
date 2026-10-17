package com.maxrave.simpmusic.expect.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
internal actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        onBack(
            progressFlow.map { event ->
                PlatformBackEvent(
                    progress = event.progress,
                    swipeEdge = event.swipeEdge,
                )
            },
        )
    }
}

