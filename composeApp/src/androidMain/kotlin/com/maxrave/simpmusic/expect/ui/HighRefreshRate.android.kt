package com.maxrave.simpmusic.expect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PerformanceHintManager
import android.os.Process
import android.view.Display
import android.view.FrameMetrics
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.maxrave.logger.Logger
import java.io.File
import java.util.Collections
import java.util.WeakHashMap

private const val TARGET_120HZ_DURATION_NANOS = 8_333_333L

private object AdpfHighRefreshBooster {
    private var hintSession: PerformanceHintManager.Session? = null
    private var sessionInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attachedWindows = Collections.newSetFromMap(WeakHashMap<Window, Boolean>())

    private fun findRenderThreadTid(): Int? {
        return try {
            val taskDir = File("/proc/self/task")
            taskDir.listFiles()?.firstNotNullOfOrNull { dir ->
                val commFile = File(dir, "comm")
                if (commFile.exists() && commFile.readText().trim() == "RenderThread") {
                    dir.name.toIntOrNull()
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureSession(context: Context): PerformanceHintManager.Session? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (sessionInitialized) return hintSession
        sessionInitialized = true
        try {
            val phm = context.getSystemService(PerformanceHintManager::class.java) ?: return null
            val uiTid = Process.myTid()
            val renderTid = findRenderThreadTid()
            val tids = if (renderTid != null && renderTid != uiTid) {
                intArrayOf(uiTid, renderTid)
            } else {
                intArrayOf(uiTid)
            }
            hintSession = phm.createHintSession(tids, TARGET_120HZ_DURATION_NANOS)
                ?: phm.createHintSession(intArrayOf(uiTid), TARGET_120HZ_DURATION_NANOS)
        } catch (e: Throwable) {
            Logger.w("HighRefreshRate", "ADPF HintSession unavailable: ${e.message}")
        }
        return hintSession
    }

    fun attachToWindow(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val session = ensureSession(window.context) ?: return
        if (!attachedWindows.add(window)) return

        try {
            val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                try {
                    val anim = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
                    val layout = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                    val draw = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
                    val sync = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
                    val issue = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
                    val swap = frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
                    val workDuration = anim + layout + draw + sync + issue + swap
                    if (workDuration in 100_000L..100_000_000L) {
                        session.reportActualWorkDuration(workDuration)
                    }
                } catch (_: Throwable) {
                }
            }
            window.addOnFrameMetricsAvailableListener(listener, mainHandler)
        } catch (_: Throwable) {
        }
    }
}

/**
 * Enforces the maximum available refresh rate (e.g. 120Hz) on the target [Window].
 * In Android / MIUI / HyperOS, Dialogs and sub-windows default to unconfigured refresh rate
 * params, which triggers MIUI to cap refresh rate to 60Hz. Setting preferredDisplayModeId
 * registers PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE (priority 7) in DisplayModeDirector,
 * which takes precedence over PRIORITY_MIUI_REFRESH_RATE (priority 5) and forces 120Hz.
 */
fun applyHighRefreshRate(window: Window, targetView: View? = null) {
    try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display
                ?: (window.context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay
        }
        val modes = display?.supportedModes ?: emptyArray()
        val maxMode = modes.maxByOrNull { it.refreshRate }
        val maxFps = maxMode?.refreshRate ?: 120f

        val params = window.attributes
        var changed = false

        if (maxMode != null && maxMode.modeId != 0 && params.preferredDisplayModeId != maxMode.modeId) {
            params.preferredDisplayModeId = maxMode.modeId
            changed = true
        }
        @Suppress("DEPRECATION")
        if (params.preferredRefreshRate != maxFps) {
            params.preferredRefreshRate = maxFps
            changed = true
        }
        try {
            val minField = params.javaClass.getField("preferredMinDisplayRefreshRate")
            if (minField.getFloat(params) != maxFps) {
                minField.setFloat(params, maxFps)
                changed = true
            }
            val maxField = params.javaClass.getField("preferredMaxDisplayRefreshRate")
            if (maxField.getFloat(params) != maxFps) {
                maxField.setFloat(params, maxFps)
                changed = true
            }
        } catch (_: Throwable) {
            // Not available on all Android versions / vendor implementations
        }
        if (changed) {
            window.attributes = params
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.frameRateBoostOnTouchEnabled = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            targetView?.requestedFrameRate = View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        }
        AdpfHighRefreshBooster.attachToWindow(window)
    } catch (e: Throwable) {
        Logger.w("HighRefreshRate", "Failed to set high refresh rate: ${e.message}")
    }
}

/**
 * Traverses up the view hierarchy to locate either a Dialog window (via DialogWindowProvider)
 * or the hosting Activity's window.
 */
fun findWindow(view: View): Window? {
    var current: Any? = view
    while (current != null) {
        if (current is DialogWindowProvider) {
            return current.window
        }
        current = (current as? View)?.parent ?: (current as? ViewParent)?.parent
    }
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}

@Composable
actual fun ForceHighRefreshRate() {
    val view = LocalView.current
    DisposableEffect(view) {
        fun apply() {
            findWindow(view)?.let { applyHighRefreshRate(it, view) }
        }
        apply()
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                apply()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        }
        view.addOnAttachStateChangeListener(listener)
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
        }
    }
    SideEffect {
        findWindow(view)?.let { applyHighRefreshRate(it, view) }
    }
}
