package com.example.todoremind.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.todoremind.R

object WidgetUpdater {
    fun update(ctx: Context) {
        try {
            val am = ctx.getSystemService(AppWidgetManager::class.java) ?: return
            val ids = am.getAppWidgetIds(ComponentName(ctx, TodoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) am.updateAppWidget(id, TodoWidgetProvider.buildViews(ctx, id))
            am.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        } catch (_: Exception) {}
    }
}