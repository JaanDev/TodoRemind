package com.example.todoremind.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.todoremind.AlarmReceiver
import com.example.todoremind.R
import com.example.todoremind.data.AppDb
import com.example.todoremind.data.DailyReminder
import com.example.todoremind.data.Todo
import com.example.todoremind.util.Fmt
import com.example.todoremind.util.Prefs

class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Берём текущую конфигурацию системы (светлая/тёмная тема)
        val config = Configuration(applicationContext.resources.configuration)
        // Создаём новый контекст с этой конфигурацией
        val themedContext = applicationContext.createConfigurationContext(config)
        return TodoFactory(themedContext)
    }
}

class TodoFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private sealed interface Row {
        data class Header(val text: String) : Row
        data class TodoRow(val t: Todo) : Row
        data class DailyRow(val d: DailyReminder) : Row
    }

    private val rows = mutableListOf<Row>()

    override fun onCreate() {}
    override fun onDestroy() { rows.clear() }
    override fun getCount() = rows.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true

    override fun onDataSetChanged() {
        try {
            val db = AppDb.get(ctx)
            val todos = db.todoDao().getAllSync()
            val dailies = db.dailyDao().getAllSync()
            rows.clear()

            val active = todos.filter { !it.done }
                .sortedWith(compareBy<Todo, Long?>(nullsLast<Long>()) { it.dueTime }.thenBy { it.createdAt })
            if (active.isNotEmpty()) {
                rows += Row.Header(ctx.getString(R.string.w_tasks))
                active.forEach { rows += Row.TodoRow(it) }
            }
            if (dailies.isNotEmpty()) {
                rows += Row.Header(ctx.getString(R.string.w_daily))
                dailies.forEach { rows += Row.DailyRow(it) }
            }
            val done = todos.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
            if (Prefs.showDone(ctx) && done.isNotEmpty()) {
                rows += Row.Header(ctx.getString(R.string.w_done))
                done.forEach { rows += Row.TodoRow(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e("TodoFactory", "onDataSetChanged error", e)
            rows.clear()
        }
    }

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in rows.indices) return null
        return when (val r = rows[position]) {
            is Row.Header -> RemoteViews(ctx.packageName, R.layout.widget_header).apply {
                setTextViewText(R.id.widget_header_text, r.text)
            }
            is Row.TodoRow -> fillTodo(r.t)
            is Row.DailyRow -> fillDaily(r.d)
        }
    }

    private fun fillTodo(t: Todo): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_item)
        v.setTextViewText(R.id.widget_item_title, t.text)

        val overdue = !t.done && Fmt.isOverdue(t.dueTime)
        val meta = when {
            t.done -> t.doneAt?.let { ctx.getString(R.string.w_done_at, Fmt.due(ctx, it)) }
                ?: ctx.getString(R.string.w_done_simple)
            t.dueTime != null -> {
                val prefix = if (overdue) ctx.getString(R.string.overdue) + " · " else ""
                prefix + Fmt.due(ctx, t.dueTime)
            }
            else -> ctx.getString(R.string.w_no_due)
        }
        v.setTextViewText(R.id.widget_item_meta, meta)

        val checkSymbol = if (t.done) "✓" else "○"
        v.setTextViewText(R.id.widget_item_check, checkSymbol)

        val alpha = if (t.done) 0.55f else 1f
        v.setFloat(R.id.widget_item_title, "setAlpha", alpha)
        v.setFloat(R.id.widget_item_meta, "setAlpha", alpha)

        // Цвета: красный для просроченных, обычные — для остальных
        val errorColor = androidx.core.content.ContextCompat.getColor(ctx, R.color.widget_error)
        val primaryColor = androidx.core.content.ContextCompat.getColor(ctx, R.color.widget_text_primary)
        val secondaryColor = androidx.core.content.ContextCompat.getColor(ctx, R.color.widget_text_secondary)

        if (overdue) {
            v.setTextColor(R.id.widget_item_title, errorColor)
            v.setTextColor(R.id.widget_item_meta, errorColor)
        } else if (t.done) {
            v.setTextColor(R.id.widget_item_title, secondaryColor)
            v.setTextColor(R.id.widget_item_meta, secondaryColor)
        } else {
            v.setTextColor(R.id.widget_item_title, primaryColor)
            v.setTextColor(R.id.widget_item_meta, secondaryColor)
        }

        v.setOnClickFillInIntent(
            R.id.widget_item_check,
            Intent().putExtra(AlarmReceiver.EXTRA_OP, AlarmReceiver.OP_TOGGLE_TODO)
                .putExtra(AlarmReceiver.EXTRA_ID, t.id)
        )
        v.setOnClickFillInIntent(
            R.id.widget_row_root,
            Intent().putExtra(AlarmReceiver.EXTRA_OP, AlarmReceiver.OP_OPEN_APP)
        )
        return v
    }

    private fun fillDaily(d: DailyReminder): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_item)
        v.setTextViewText(R.id.widget_item_title, d.text)
        val time = String.format("%02d:%02d", d.hour, d.minute)
        v.setTextViewText(
            R.id.widget_item_meta,
            ctx.getString(R.string.daily_meta, time) +
                    if (!d.enabled) " · " + ctx.getString(R.string.w_off) else ""
        )
        val checkSymbol = if (d.enabled) "✓" else "○"
        v.setTextViewText(R.id.widget_item_check, checkSymbol)
        v.setFloat(R.id.widget_item_title, "setAlpha", if (d.enabled) 1f else 0.55f)
        v.setFloat(R.id.widget_item_meta, "setAlpha", if (d.enabled) 1f else 0.55f)
        v.setOnClickFillInIntent(
            R.id.widget_item_check,
            Intent().putExtra(AlarmReceiver.EXTRA_OP, AlarmReceiver.OP_TOGGLE_DAILY)
                .putExtra(AlarmReceiver.EXTRA_ID, d.id)
        )
        v.setOnClickFillInIntent(
            R.id.widget_row_root,
            Intent().putExtra(AlarmReceiver.EXTRA_OP, AlarmReceiver.OP_OPEN_APP)
        )
        return v
    }
}
