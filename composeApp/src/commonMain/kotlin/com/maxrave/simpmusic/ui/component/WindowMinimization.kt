package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.compositionLocalOf

/**
 * Indicates whether the application window is currently minimized, hidden or in the background.
 * When true, heavy visual animations, shaders, shimmers and canvas drawing loops must be paused
 * to drop CPU/GPU usage to near zero.
 */
val LocalWindowMinimized = compositionLocalOf { false }
