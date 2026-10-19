package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.ArrowForwardIos
import com.maxrave.simpmusic.ui.icon.SimpIcons
import kotlinx.coroutines.launch

@Composable
fun CarouselNavigationArrows(
    onScrollLeft: () -> Unit,
    onScrollRight: () -> Unit,
    canScrollLeft: Boolean = true,
    canScrollRight: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left arrow button
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (canScrollLeft) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        },
                    )
                    .clickable(
                        enabled = canScrollLeft,
                        onClick = onScrollLeft,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.ArrowBackIosNew,
                contentDescription = "Previous",
                tint =
                    if (canScrollLeft) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                modifier = Modifier.size(13.dp),
            )
        }
        // Right arrow button
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (canScrollRight) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        },
                    )
                    .clickable(
                        enabled = canScrollRight,
                        onClick = onScrollRight,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.ArrowForwardIos,
                contentDescription = "Next",
                tint =
                    if (canScrollRight) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
fun CarouselNavigationArrows(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val canScrollBackward by remember { derivedStateOf { lazyListState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { lazyListState.canScrollForward } }

    CarouselNavigationArrows(
        canScrollLeft = canScrollBackward,
        canScrollRight = canScrollForward,
        onScrollLeft = {
            coroutineScope.launch {
                val viewportWidth = lazyListState.layoutInfo.viewportSize.width
                val scrollDelta = if (viewportWidth > 0) viewportWidth * 0.75f else 400f
                lazyListState.animateScrollBy(-scrollDelta)
            }
        },
        onScrollRight = {
            coroutineScope.launch {
                val viewportWidth = lazyListState.layoutInfo.viewportSize.width
                val scrollDelta = if (viewportWidth > 0) viewportWidth * 0.75f else 400f
                lazyListState.animateScrollBy(scrollDelta)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun CarouselNavigationArrows(
    lazyGridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val canScrollBackward by remember { derivedStateOf { lazyGridState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { lazyGridState.canScrollForward } }

    CarouselNavigationArrows(
        canScrollLeft = canScrollBackward,
        canScrollRight = canScrollForward,
        onScrollLeft = {
            coroutineScope.launch {
                val viewportWidth = lazyGridState.layoutInfo.viewportSize.width
                val scrollDelta = if (viewportWidth > 0) viewportWidth * 0.75f else 400f
                lazyGridState.animateScrollBy(-scrollDelta)
            }
        },
        onScrollRight = {
            coroutineScope.launch {
                val viewportWidth = lazyGridState.layoutInfo.viewportSize.width
                val scrollDelta = if (viewportWidth > 0) viewportWidth * 0.75f else 400f
                lazyGridState.animateScrollBy(scrollDelta)
            }
        },
        modifier = modifier,
    )
}
