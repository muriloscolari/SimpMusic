package com.maxrave.simpmusic.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class CompactPlayerWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        CompactPlayerWidgetObserver.ensureObserving(context)
        when (val action = intent.action) {
            ACTION_PLAY_PAUSE,
            ACTION_PREVIOUS,
            ACTION_NEXT,
            -> {
                val pendingResult = goAsync()
                CompactPlayerWidgetObserver.handleWidgetAction(context, action) {
                    runCatching { pendingResult.finish() }
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        CompactPlayerWidgetObserver.ensureObserving(context)
        CompactPlayerWidgetObserver.refreshWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        CompactPlayerWidgetObserver.ensureObserving(context)
        CompactPlayerWidgetObserver.refreshWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CompactPlayerWidgetObserver.ensureObserving(context)
        CompactPlayerWidgetObserver.refreshWidgets(context)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.maxrave.simpmusic.action.WIDGET_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.maxrave.simpmusic.action.WIDGET_PREVIOUS"
        const val ACTION_NEXT = "com.maxrave.simpmusic.action.WIDGET_NEXT"
    }
}
