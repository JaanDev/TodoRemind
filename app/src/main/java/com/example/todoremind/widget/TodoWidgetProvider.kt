package com.example.todoremind.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.example.todoremind.AlarmReceiver
import com.example.todoremind.R
import com.example.todoremind.util.Fmt
import com.example.todoremind.util.Prefs

class TodoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.todoremind.ACTION_UPDATE_WIDGET"

        fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val config = Configuration(ctx.resources.configuration)
            val themedContext = ctx.createConfigurationContext(config)

            val views = RemoteViews(themedContext.packageName, R.layout.widget_todo)
            val showDone = Prefs.showDone(themedContext)

            views.setTextViewText(R.id.widget_date, Fmt.today())
            views.setOnClickPendingIntent(R.id.widget_title_wrap, pi(themedContext, AlarmReceiver.OP_OPEN_APP))
            views.setOnClickPendingIntent(R.id.widget_btn_add, pi(themedContext, AlarmReceiver.OP_OPEN_ADD))
            views.setOnClickPendingIntent(R.id.widget_btn_toggle_done, pi(themedContext, AlarmReceiver.OP_TOGGLE_SHOW_DONE))
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, pi(themedContext, AlarmReceiver.OP_REFRESH))
            views.setImageViewResource(
                R.id.widget_btn_toggle_done,
                if (showDone) R.drawable.ic_eye else R.drawable.ic_eye_off
            )

            val adapterIntent = Intent(themedContext, TodoWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            views.setRemoteAdapter(R.id.widget_list, adapterIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val template = PendingIntent.getBroadcast(
                themedContext, 0,
                Intent(themedContext, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, template)
            return views
        }

        private fun pi(ctx: Context, op: Int): PendingIntent {
            val i = Intent(ctx, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION)
                .putExtra(AlarmReceiver.EXTRA_OP, op)
            return PendingIntent.getBroadcast(ctx, 1000 + op, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, buildViews(context, id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            WidgetUpdater.update(context)
        }
    }
}