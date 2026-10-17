package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

internal data class PlatformBackEvent(
    val progress: Float,
    val swipeEdge: Int,
)

@Composable
internal expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)

@Composable
internal expect fun PlatformPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
)

