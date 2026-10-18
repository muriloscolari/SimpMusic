package com.maxrave.simpmusic.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.kmpalette.generatePalette
import com.kmpalette.palette.graphics.Palette
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.player.MediaPlayerListener
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.MainActivity
import com.maxrave.simpmusic.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val COMPACT_PLAYER_DECODE_PX = 256
private const val COMPACT_ART_CORNER_PX = 36f
private val DEFAULT_COMPACT_WIDGET_BG = Color(0xFF1C1B1F)
private val DEFAULT_COMPACT_WIDGET_BG_ARGB = DEFAULT_COMPACT_WIDGET_BG.toArgb()

private data class CompactWidgetSnapshot(
    val isPlaying: Boolean,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbUrl: String,
)

/**
 * Native AppWidget updater and observer for the Compact 1-row playback bar widget:
 * - Uses direct [AppWidgetManager.updateAppWidget] with [RemoteViews] instead of Jetpack Glance's
 *   WorkManager `SessionWorker`, eliminating background worker throttling on Samsung (OneUI) and
 *   Xiaomi (MIUI/HyperOS) as well as Glance session state freezes.
 * - Separates immediate synchronous UI updates (Play/Pause icon, Song Title, Artist) from
 *   asynchronous artwork/palette loading so button taps and track changes reflect in < 1ms.
 */
object CompactPlayerWidgetObserver : KoinComponent {
    private val mediaPlayerHandler by inject<MediaPlayerHandler>()

    // Dedicated non-cancellable scope so widget observation survives service restarts/releases.
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var isObserving = false

    @Volatile
    private var cachedThumbUrl: String = ""

    @Volatile
    private var cachedRoundedArtwork: Bitmap? = null

    @Volatile
    private var cachedBgColorArgb: Int = DEFAULT_COMPACT_WIDGET_BG_ARGB

    @Volatile
    private var placeholderRoundedBitmap: Bitmap? = null

    private var artworkJob: Job? = null
    private var loadingThumbUrl: String = ""

    private var appContextRef: Context? = null

    private val directPlayerListener =
        object : MediaPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val ctx = appContextRef ?: return
                runOnMainImmediate {
                    updateWidgetsImmediate(ctx, isPlayingOverride = isPlaying)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val ctx = appContextRef ?: return
                runOnMainImmediate {
                    onStateChanged(ctx)
                }
            }

            override fun onMediaItemTransition(
                mediaItem: GenericMediaItem?,
                reason: Int,
            ) {
                val ctx = appContextRef ?: return
                runOnMainImmediate {
                    onStateChanged(ctx, mediaItemOverride = mediaItem)
                }
            }
        }

    fun ensureObserving(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (isObserving) return
        synchronized(this) {
            if (isObserving) return
            isObserving = true

            widgetScope.launch {
                runCatching {
                    mediaPlayerHandler.player.removeListener(directPlayerListener)
                    mediaPlayerHandler.player.addListener(directPlayerListener)
                }
            }

            // 1. Immediate Play/Pause state observer
            widgetScope.launch {
                try {
                    mediaPlayerHandler.controlState
                        .map { it.isPlaying }
                        .distinctUntilChanged()
                        .collect { isPlaying ->
                            updateWidgetsImmediate(appContext, isPlayingOverride = isPlaying)
                        }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        Logger.e("CompactPlayerWidget", "controlState observer error: ${t.message}")
                    }
                }
            }

            // 2. Immediate mediaItem transition observer
            widgetScope.launch {
                try {
                    mediaPlayerHandler.nowPlaying.collect { mediaItem ->
                        onStateChanged(appContext, mediaItemOverride = mediaItem)
                    }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        Logger.e("CompactPlayerWidget", "nowPlaying observer error: ${t.message}")
                    }
                }
            }

            // 3. Enriched track / songEntity metadata observer
            widgetScope.launch {
                try {
                    mediaPlayerHandler.nowPlayingState.collect {
                        onStateChanged(appContext)
                    }
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        Logger.e("CompactPlayerWidget", "nowPlayingState observer error: ${t.message}")
                    }
                }
            }
        }
    }

    fun refreshWidgets(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        runOnMainImmediate {
            onStateChanged(appContext)
        }
    }

    fun handleWidgetAction(
        context: Context,
        action: String,
        onComplete: () -> Unit,
    ) {
        val appContext = context.applicationContext
        appContextRef = appContext
        widgetScope.launch {
            try {
                // Re-ensure direct listener is attached in case player was recreated
                runCatching {
                    mediaPlayerHandler.player.removeListener(directPlayerListener)
                    mediaPlayerHandler.player.addListener(directPlayerListener)
                }

                when (action) {
                    CompactPlayerWidgetReceiver.ACTION_PLAY_PAUSE -> {
                        val hasMedia =
                            mediaPlayerHandler.nowPlayingState.value.mediaItem != GenericMediaItem.EMPTY ||
                                mediaPlayerHandler.nowPlaying.value != null ||
                                runCatching { mediaPlayerHandler.player.currentMediaItem != null }.getOrDefault(false)

                        if (!hasMedia) {
                            ensureMediaServiceStarted(appContext)
                            updateWidgetsImmediate(appContext, isPlayingOverride = true)
                            mediaPlayerHandler.restoreQueueAndPlay()
                            onStateChanged(appContext)
                        } else {
                            val currentlyPlaying =
                                runCatching { mediaPlayerHandler.player.isPlaying }.getOrNull()
                                    ?: mediaPlayerHandler.controlState.value.isPlaying
                            val targetPlaying = !currentlyPlaying
                            if (targetPlaying) {
                                ensureMediaServiceStarted(appContext)
                            }
                            // Flip the Play/Pause icon on the home screen immediately (0ms wait)
                            updateWidgetsImmediate(appContext, isPlayingOverride = targetPlaying)
                            mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause)
                            delay(150)
                            onStateChanged(appContext)
                        }
                    }

                    CompactPlayerWidgetReceiver.ACTION_PREVIOUS -> {
                        ensureMediaServiceStarted(appContext)
                        mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous)
                        onStateChanged(appContext)
                        delay(150)
                        onStateChanged(appContext)
                    }

                    CompactPlayerWidgetReceiver.ACTION_NEXT -> {
                        ensureMediaServiceStarted(appContext)
                        mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                        onStateChanged(appContext)
                        delay(150)
                        onStateChanged(appContext)
                    }
                }
            } catch (t: Throwable) {
                Logger.e("CompactPlayerWidget", "Error handling widget action $action: ${t.message}")
            } finally {
                onComplete()
            }
        }
    }

    private fun onStateChanged(
        context: Context,
         isPlayingOverride: Boolean? = null,
        mediaItemOverride: GenericMediaItem? = null,
    ) {
        val appContext = context.applicationContext
        val snapshot = updateWidgetsImmediate(appContext, isPlayingOverride, mediaItemOverride) ?: return
        loadArtworkIfNeeded(appContext, snapshot.thumbUrl)
    }

    private fun updateWidgetsImmediate(
        context: Context,
        isPlayingOverride: Boolean? = null,
        mediaItemOverride: GenericMediaItem? = null,
    ): CompactWidgetSnapshot? {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val componentName = ComponentName(appContext, CompactPlayerWidgetReceiver::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return null

        val snapshot = extractSnapshot(isPlayingOverride, mediaItemOverride)
        val hasCachedArt = snapshot.thumbUrl.isNotBlank() && snapshot.thumbUrl == cachedThumbUrl && cachedRoundedArtwork != null
        val artworkBitmap =
            if (hasCachedArt) {
                cachedRoundedArtwork
            } else {
                getOrCreatePlaceholderBitmap(appContext)
            }
        val bgColorArgb =
            if (hasCachedArt) {
                cachedBgColorArgb
            } else if (snapshot.title.isNotBlank() && cachedRoundedArtwork != null) {
                cachedBgColorArgb
            } else {
                DEFAULT_COMPACT_WIDGET_BG_ARGB
            }

        val views = buildRemoteViews(appContext, snapshot, artworkBitmap, bgColorArgb)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
        return snapshot
    }

    private fun loadArtworkIfNeeded(
        context: Context,
        thumbUrl: String,
    ) {
        if (thumbUrl.isBlank()) {
            cachedThumbUrl = ""
            cachedRoundedArtwork = null
            cachedBgColorArgb = DEFAULT_COMPACT_WIDGET_BG_ARGB
            return
        }
        if (thumbUrl == cachedThumbUrl && cachedRoundedArtwork != null) {
            return
        }
        if (thumbUrl == loadingThumbUrl && artworkJob?.isActive == true) {
            return
        }

        artworkJob?.cancel()
        loadingThumbUrl = thumbUrl
        val appContext = context.applicationContext
        artworkJob =
            widgetScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        val rawBitmap = appContext.loadCompactWidgetArtwork(thumbUrl) ?: return@withContext null
                        val bgColor =
                            try {
                                rawBitmap.asImageBitmap().generatePalette().getDominantWidgetColor().toArgb()
                            } catch (t: Throwable) {
                                DEFAULT_COMPACT_WIDGET_BG_ARGB
                            }
                        val roundedBitmap = createRoundedSquareBitmap(rawBitmap, COMPACT_PLAYER_DECODE_PX, COMPACT_ART_CORNER_PX)
                        Pair(roundedBitmap, bgColor)
                    }

                if (result != null) {
                    cachedThumbUrl = thumbUrl
                    cachedRoundedArtwork = result.first
                    cachedBgColorArgb = result.second
                    updateWidgetsImmediate(appContext)
                }
            }
    }

    private fun extractSnapshot(
        isPlayingOverride: Boolean? = null,
        mediaItemOverride: GenericMediaItem? = null,
    ): CompactWidgetSnapshot {
        val state = mediaPlayerHandler.nowPlayingState.value
        val playerItem = runCatching { mediaPlayerHandler.player.currentMediaItem }.getOrNull()
        val nowPlayingItem = mediaPlayerHandler.nowPlaying.value

        val activeMediaItem =
            mediaItemOverride?.takeIf { it != GenericMediaItem.EMPTY }
                ?: playerItem?.takeIf { it != GenericMediaItem.EMPTY }
                ?: nowPlayingItem?.takeIf { it != GenericMediaItem.EMPTY }
                ?: state.mediaItem.takeIf { it != GenericMediaItem.EMPTY }

        val rawMediaId = activeMediaItem?.mediaId.orEmpty()
        val videoId = rawMediaId.removePrefix(MERGING_DATA_TYPE.VIDEO)

        // Only use songEntity / track when their videoId matches the currently active mediaItem
        val matchingSong = state.songEntity?.takeIf { videoId.isEmpty() || it.videoId == videoId }
        val matchingTrack = state.track?.takeIf { videoId.isEmpty() || it.videoId == videoId }

        val title =
            matchingSong?.title?.takeIf { it.isNotBlank() }
                ?: matchingTrack?.title?.takeIf { it.isNotBlank() }
                ?: activeMediaItem?.metadata?.title?.takeIf { it.isNotBlank() }
                ?: ""

        val artist =
            matchingSong?.artistName?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ?: matchingTrack?.artists?.mapNotNull { it.name.takeIf(String::isNotBlank) }?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ?: activeMediaItem?.metadata?.artist?.takeIf { it.isNotBlank() }
                ?: ""

        val thumbUrl =
            matchingSong?.thumbnails?.takeIf { it.isNotBlank() }
                ?: activeMediaItem?.metadata?.artworkUri?.takeIf { it.isNotBlank() }
                ?: matchingTrack?.thumbnails?.lastOrNull()?.url?.takeIf { it.isNotBlank() }
                ?: ""

        val isPlaying =
            isPlayingOverride
                ?: runCatching { mediaPlayerHandler.player.isPlaying }.getOrNull()
                ?: mediaPlayerHandler.controlState.value.isPlaying

        return CompactWidgetSnapshot(
            isPlaying = isPlaying,
            videoId = videoId,
            title = title,
            artist = artist,
            thumbUrl = thumbUrl,
        )
    }

    private fun buildRemoteViews(
        context: Context,
        snapshot: CompactWidgetSnapshot,
        artworkBitmap: Bitmap?,
        bgColorArgb: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_player)

        views.setInt(R.id.widget_bg, "setColorFilter", bgColorArgb)

        if (artworkBitmap != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.holder)
        }

        val displayTitle = snapshot.title.ifBlank { context.getString(R.string.app_name) }
        views.setTextViewText(R.id.widget_title, displayTitle)
        views.setContentDescription(R.id.widget_artwork, displayTitle)

        if (snapshot.artist.isNotBlank()) {
            views.setTextViewText(R.id.widget_artist, snapshot.artist)
            views.setViewVisibility(R.id.widget_artist, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_artist, View.GONE)
        }

        val playPauseIcon =
            if (snapshot.isPlaying) {
                R.drawable.rounded_pause_24
            } else {
                R.drawable.rounded_play_arrow_24
            }
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)
        views.setContentDescription(R.id.widget_btn_play_pause, if (snapshot.isPlaying) "Pause" else "Play")

        val isPlayingNow = snapshot.isPlaying && snapshot.title.isNotBlank()
        val openIntent = context.openCompactWidgetIntent(isPlaying = isPlayingNow)
        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                if (isPlayingNow) 101 else 100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_artwork, openPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_text_container, openPendingIntent)

        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            buildActionPendingIntent(context, CompactPlayerWidgetReceiver.ACTION_PREVIOUS, 201),
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play_pause,
            buildActionPendingIntent(context, CompactPlayerWidgetReceiver.ACTION_PLAY_PAUSE, 202),
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            buildActionPendingIntent(context, CompactPlayerWidgetReceiver.ACTION_NEXT, 203),
        )

        return views
    }

    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, CompactPlayerWidgetReceiver::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureMediaServiceStarted(context: Context) {
        runCatching {
            val serviceIntent =
                Intent().setClassName(
                    context.packageName,
                    "com.maxrave.media3.service.SimpleMediaService",
                )
            context.startService(serviceIntent)
        }
    }

    private fun getOrCreatePlaceholderBitmap(context: Context): Bitmap? {
        placeholderRoundedBitmap?.let { return it }
        return runCatching {
            val drawable = AppCompatResources.getDrawable(context, R.drawable.holder) ?: return null
            val raw = createBitmap(COMPACT_PLAYER_DECODE_PX, COMPACT_PLAYER_DECODE_PX)
            val canvas = Canvas(raw)
            drawable.setBounds(0, 0, COMPACT_PLAYER_DECODE_PX, COMPACT_PLAYER_DECODE_PX)
            drawable.draw(canvas)
            createRoundedSquareBitmap(raw, COMPACT_PLAYER_DECODE_PX, COMPACT_ART_CORNER_PX).also {
                placeholderRoundedBitmap = it
            }
        }.getOrNull()
    }

    private fun runOnMainImmediate(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            widgetScope.launch { block() }
        }
    }
}

private fun Context.openCompactWidgetIntent(isPlaying: Boolean): Intent =
    Intent(
        Intent.ACTION_VIEW,
        "simpmusic://player?playing=$isPlaying".toUri(),
    ).setClass(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

private fun createRoundedSquareBitmap(
    source: Bitmap,
    sizePx: Int,
    cornerRadiusPx: Float,
): Bitmap {
    val minEdge = minOf(source.width, source.height).coerceAtLeast(1)
    val cropX = (source.width - minEdge) / 2
    val cropY = (source.height - minEdge) / 2
    val cropped =
        if (cropX == 0 && cropY == 0 && source.width == minEdge && source.height == minEdge) {
            source
        } else {
            Bitmap.createBitmap(source, cropX, cropY, minEdge, minEdge)
        }
    val scaled =
        if (cropped.width == sizePx && cropped.height == sizePx) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, sizePx, sizePx, true)
        }

    val output = createBitmap(sizePx, sizePx)
    val canvas = Canvas(output)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
    val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
    canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
    return output
}

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

private suspend fun Context.loadCompactWidgetArtwork(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withTimeoutOrNull(4000L) {
        try {
            val request =
                ImageRequest
                    .Builder(applicationContext)
                    .data(url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(false)
                    .size(COMPACT_PLAYER_DECODE_PX, COMPACT_PLAYER_DECODE_PX)
                    .build()
            val result = SingletonImageLoader.get(applicationContext).execute(request)
            (result as? SuccessResult)?.image?.toBitmap()
        } catch (t: Throwable) {
            null
        }
    }
}
