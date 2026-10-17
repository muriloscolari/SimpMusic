package com.maxrave.simpmusic.expect.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.maxrave.logger.Logger

/**
 * Enforces the maximum available refresh rate (e.g. 120Hz) on the target [Window].
 * In Android / MIUI / HyperOS, Dialogs and sub-windows default to unconfigured refresh rate
 * params, which triggers MIUI to cap refresh rate to 60Hz. Setting preferredDisplayModeId
 * registers PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE (priority 7) in DisplayModeDirector,
 * which takes precedence over PRIORITY_MIUI_REFRESH_RATE (priority 5) and forces 120Hz.
 */
fun applyHighRefreshRate(window: Window) {
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
            minField.setFloat(params, maxFps)
            val maxField = params.javaClass.getField("preferredMaxDisplayRefreshRate")
            maxField.setFloat(params, maxFps)
            changed = true
        } catch (_: Throwable) {
            // Not available on all Android versions / vendor implementations
        }
        if (changed) {
            window.attributes = params
        }
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
            findWindow(view)?.let { applyHighRefreshRate(it) }
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
        findWindow(view)?.let { applyHighRefreshRate(it) }
    }
}
