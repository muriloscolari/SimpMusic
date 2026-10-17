package com.maxrave.simpmusic.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kmpalette.palette.graphics.Palette
import com.kmpalette.rememberPaletteState
import com.maxrave.common.Config
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.MainActivity
import com.maxrave.simpmusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

private const val COMPACT_PLAYER_DECODE_PX = 512
private val DEFAULT_COMPACT_WIDGET_BG = Color(0xFF1C1B1F)

class CompactPlayPauseAction :
    ActionCallback,
    KoinComponent {
    private val mediaPlayerHandler by inject<MediaPlayerHandler>()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        if (mediaPlayerHandler.nowPlayingState.value.mediaItem == GenericMediaItem.EMPTY) {
            mediaPlayerHandler.restoreQueueAndPlay()
        } else {
            mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
        }
    }
}

class CompactPreviousAction :
    ActionCallback,
    KoinComponent {
    private val mediaPlayerHandler by inject<MediaPlayerHandler>()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
    }
}

class CompactNextAction :
    ActionCallback,
    KoinComponent {
    private val mediaPlayerHandler by inject<MediaPlayerHandler>()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
    }
}

object CompactPlayerWidgetObserver : KoinComponent {
    private val mediaPlayerHandler by inject<MediaPlayerHandler>()
    private val serviceScope by inject<CoroutineScope>(named(Config.SERVICE_SCOPE))
    private var observerJob: Job? = null

    fun ensureObserving(context: Context) {
        if (observerJob?.isActive == true) return
        val appContext = context.applicationContext
        synchronized(this) {
            if (observerJob?.isActive == true) return
            observerJob =
                serviceScope.launch {
                    var isFirstControl = true
                    launch {
                        mediaPlayerHandler.controlState
                            .map { it.isPlaying }
                            .distinctUntilChanged()
                            .collectLatest {
                                if (isFirstControl) {
                                    isFirstControl = false
                                    return@collectLatest
                                }
                                updateWidgetsIfPresent(appContext)
                            }
                    }

                    var isFirstTrack = true
                    launch {
                        mediaPlayerHandler.nowPlayingState
                            .map { state ->
                                val mediaId = state.mediaItem.mediaId
                                val title =
                                    state.songEntity?.title
                                        ?: state.track?.title
                                        ?: state.mediaItem.metadata.title
                                        ?: ""
                                val artworkUri =
                                    state.mediaItem.metadata.artworkUri
                                        ?: state.songEntity?.thumbnails
                                        ?: state.track?.thumbnails?.lastOrNull()?.url
                                        ?: ""
                                Triple(mediaId, title, artworkUri)
                            }
                            .distinctUntilChanged()
                            .collectLatest {
                                if (isFirstTrack) {
                                    isFirstTrack = false
                                    return@collectLatest
                                }
                                updateWidgetsIfPresent(appContext)
                            }
                    }
                }
        }
    }

    suspend fun updateWidgetsIfPresent(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(CompactPlayerWidget::class.java)
            if (glanceIds.isNotEmpty()) {
                CompactPlayerWidget().updateAll(context)
            }
        } catch (e: Exception) {
            Logger.e("CompactPlayerWidget", "Failed to update compact widget: ${e.message}")
        }
    }
}

/**
 * Compact 1-row playback bar widget inspired by Spotify and YouTube Music:
 * - Occupies a single vertical cell row (`targetCellHeight="1"`) and stretches to any horizontal width.
 * - Tinted with the dominant colour of the currently playing album cover.
 * - Left: rounded album artwork thumbnail.
 * - Middle: `Song Title` on top, `Artist` on bottom with single-line ellipsis that expands with horizontal resizing.
 * - Right: Previous, Play/Pause, and Next controls.
 * - Tapping the widget opens SimpMusic directly on the expanded Player screen if a song is playing,
 *   or on the Home screen when nothing is playing.
 */
class CompactPlayerWidget :
    GlanceAppWidget(),
    KoinComponent {
    override val sizeMode: SizeMode = SizeMode.Exact

    private val mediaPlayerHandler by inject<MediaPlayerHandler>()

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        CompactPlayerWidgetObserver.ensureObserving(context)

        provideContent {
            GlanceTheme {
                val controllerState by mediaPlayerHandler.controlState.collectAsState()
                val nowPlayingState by mediaPlayerHandler.nowPlayingState.collectAsState()

                val paletteState = rememberPaletteState()
                var artwork by remember { mutableStateOf<Bitmap?>(null) }
                var bgColor by remember { mutableStateOf(DEFAULT_COMPACT_WIDGET_BG) }

                val title by remember {
                    derivedStateOf {
                        nowPlayingState.songEntity?.title?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.track?.title?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.mediaItem.metadata.title?.takeIf { it.isNotBlank() }
                            ?: ""
                    }
                }
                val artist by remember {
                    derivedStateOf {
                        nowPlayingState.songEntity?.artistName?.joinToString(", ")?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.track?.artists?.mapNotNull { it.name.takeIf(String::isNotBlank) }?.joinToString(", ")?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.mediaItem.metadata.artist?.takeIf { it.isNotBlank() }
                            ?: ""
                    }
                }
                val thumbUrl by remember {
                    derivedStateOf {
                        nowPlayingState.mediaItem.metadata.artworkUri?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.songEntity?.thumbnails?.takeIf { it.isNotBlank() }
                            ?: nowPlayingState.track?.thumbnails?.lastOrNull()?.url?.takeIf { it.isNotBlank() }
                    }
                }

                LaunchedEffect(artwork) {
                    val bmp = artwork
                    if (bmp != null) {
                        paletteState.generate(bmp.asImageBitmap())
                    } else {
                        bgColor = DEFAULT_COMPACT_WIDGET_BG
                    }
                }

                LaunchedEffect(Unit) {
                    snapshotFlow { paletteState.palette }
                        .distinctUntilChanged()
                        .collectLatest { palette ->
                            bgColor =
                                if (artwork != null) {
                                    palette.getDominantWidgetColor()
                                } else {
                                    DEFAULT_COMPACT_WIDGET_BG
                                }
                        }
                }

                LaunchedEffect(thumbUrl) {
                    artwork = context.loadBitmap(thumbUrl, (thumbUrl ?: "") + "BIGGER", COMPACT_PLAYER_DECODE_PX)
                }

                val isPlayingNow = controllerState.isPlaying && title.isNotBlank()
                val openIntent = context.openCompactWidgetIntent(isPlaying = isPlayingNow)
                val widgetHeight = LocalSize.current.height
                val artSize = (widgetHeight - 6.dp).coerceIn(44.dp, 64.dp)

                Row(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(bgColor))
                            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
                            .clickable(
                                actionStartActivity(openIntent),
                                rippleOverride = R.drawable.no_ripple,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: Album cover thumbnail
                    Box(
                        modifier =
                            GlanceModifier
                                .size(artSize)
                                .clickable(
                                    actionStartActivity(openIntent),
                                    rippleOverride = R.drawable.no_ripple,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = artwork
                        if (bitmap != null) {
                            Image(
                                provider = ImageProvider(bitmap),
                                contentDescription = title.ifBlank { context.getString(R.string.app_name) },
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp),
                            )
                        } else {
                            Image(
                                provider = ImageProvider(R.drawable.holder),
                                contentDescription = context.getString(R.string.app_name),
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp),
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(8.dp))

                    // Middle: Song title (top) and artist (bottom)
                    Column(
                        modifier =
                            GlanceModifier
                                .defaultWeight()
                                .clickable(
                                    actionStartActivity(openIntent),
                                    rippleOverride = R.drawable.no_ripple,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title.ifBlank { context.getString(R.string.app_name) },
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                        if (artist.isNotBlank()) {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = artist,
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                            )
                        }
                    }

                    // Right: Previous, Play/Pause, Next controls
                    val systemRtl = Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                    val previousButton: @Composable () -> Unit = {
                        Image(
                            provider = ImageProvider(R.drawable.rounded_skip_previous_24),
                            contentDescription = "Previous",
                            colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                            modifier =
                                GlanceModifier
                                    .size(34.dp)
                                    .padding(4.dp)
                                    .clickable(
                                        onClick = actionRunCallback<CompactPreviousAction>(),
                                        rippleOverride = R.drawable.no_ripple,
                                    ),
                        )
                    }
                    val nextButton: @Composable () -> Unit = {
                        Image(
                            provider = ImageProvider(R.drawable.rounded_skip_next_24),
                            contentDescription = "Next",
                            colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                            modifier =
                                GlanceModifier
                                    .size(34.dp)
                                    .padding(4.dp)
                                    .clickable(
                                        onClick = actionRunCallback<CompactNextAction>(),
                                        rippleOverride = R.drawable.no_ripple,
                                    ),
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (systemRtl) nextButton() else previousButton()
                        Spacer(GlanceModifier.width(4.dp))
                        Image(
                            provider =
                                if (controllerState.isPlaying) {
                                    ImageProvider(R.drawable.rounded_pause_24)
                                } else {
                                    ImageProvider(R.drawable.rounded_play_arrow_24)
                                },
                            contentDescription = if (controllerState.isPlaying) "Pause" else "Play",
                            colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                            modifier =
                                GlanceModifier
                                    .size(38.dp)
                                    .padding(4.dp)
                                    .clickable(
                                        onClick = actionRunCallback<CompactPlayPauseAction>(),
                                        rippleOverride = R.drawable.no_ripple,
                                    ),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        if (systemRtl) previousButton() else nextButton()
                    }
                }
            }
        }
    }
}

private fun Context.openCompactWidgetIntent(isPlaying: Boolean): Intent =
    Intent(
        Intent.ACTION_VIEW,
        "simpmusic://player?playing=$isPlaying".toUri(),
    ).setClass(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/**
 * Extracts the dominant colour from the album artwork [Palette], scaling luminance only when
 * needed so white text and transport icons remain crisp on very bright covers while preserving
 * the dominant hue and saturation.
 */
private fun Palette?.getDominantWidgetColor(): Color {
    val p = this ?: return DEFAULT_COMPACT_WIDGET_BG
    val rgb =
        p.getDominantColor(0).takeIf { it != 0 }
            ?: p.getVibrantColor(0).takeIf { it != 0 }
            ?: p.getMutedColor(0).takeIf { it != 0 }
            ?: p.getDarkVibrantColor(0).takeIf { it != 0 }
            ?: p.getDarkMutedColor(0).takeIf { it != 0 }
            ?: return DEFAULT_COMPACT_WIDGET_BG
    val base = Color(rgb)
    val luminance = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
    return when {
        luminance > 0.45f -> {
            val scale = 0.45f / luminance
            Color(
                red = (base.red * scale).coerceIn(0f, 1f),
                green = (base.green * scale).coerceIn(0f, 1f),
                blue = (base.blue * scale).coerceIn(0f, 1f),
                alpha = 1f,
            )
        }
        luminance < 0.04f -> DEFAULT_COMPACT_WIDGET_BG
        else -> base.copy(alpha = 1f)
    }
}
