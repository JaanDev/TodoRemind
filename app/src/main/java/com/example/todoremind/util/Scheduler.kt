package com.example.todoremind.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.todoremind.AlarmReceiver
import com.example.todoremind.MainActivity
import com.example.todoremind.data.AppDb
import com.example.todoremind.data.DailyReminder
import com.example.todoremind.widget.TodoWidgetProvider
import java.util.Calendar

object Scheduler {
    const val EXTRA_TYPE = "atype"
    const val EXTRA_ID = "aid"
    const val TYPE_TODO = 1
    const val TYPE_DAILY = 2
    const val TYPE_SNOOZE_TODO = 3
    const val TYPE_SNOOZE_DAILY = 4

    private fun intent(ctx: Context, type: Int, id: Long) =
        Intent(ctx, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM)
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_ID, id)

    private fun requestCode(type: Int, id: Long) = type * 1_000_000 + id.toInt()

    private fun pi(ctx: Context, type: Int, id: Long, flags: Int) =
        PendingIntent.getBroadcast(
            ctx, requestCode(type, id), intent(ctx, type, id),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: Exception) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
            }
        }
    }

    fun scheduleTodo(ctx: Context, due: Long, id: Long) {
        if (due <= System.currentTimeMillis()) return
        setExact(ctx, due, pi(ctx, TYPE_TODO, id, PendingIntent.FLAG_UPDATE_CURRENT))
        // Запланировать обновление виджета на момент просрочки
        scheduleWidgetUpdate(ctx, due, id)
    }

    fun cancelTodoAlarm(ctx: Context, id: Long) {
        pi(ctx, TYPE_TODO, id, PendingIntent.FLAG_NO_CREATE)?.cancel()
        cancelWidgetUpdate(ctx, id)
    }

    fun scheduleDailyNext(ctx: Context, d: DailyReminder) {
        if (!d.enabled) return
        setExact(
            ctx,
            nextDaily(d.hour, d.minute),
            pi(ctx, TYPE_DAILY, d.id, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun cancelDailyAlarm(ctx: Context, id: Long) {
        pi(ctx, TYPE_DAILY, id, PendingIntent.FLAG_NO_CREATE)?.cancel()
    }

    fun scheduleSnooze(ctx: Context, type: Int, id: Long) {
        setExact(
            ctx,
            System.currentTimeMillis() + 3_600_000L,
            pi(ctx, type, id, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun nextDaily(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!t.after(now)) t.add(Calendar.DAY_OF_YEAR, 1)
        return t.timeInMillis
    }

    /**
     * Запланировать обновление виджета на момент просрочки задачи
     */
    private fun scheduleWidgetUpdate(ctx: Context, at: Long, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(ctx, TodoWidgetProvider::class.java)
            .setAction(TodoWidgetProvider.ACTION_UPDATE_WIDGET)
        val pi = PendingIntent.getBroadcast(
            ctx, (800_000 + id).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: Exception) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Отменить обновление виджета (когда задача сделана или удалена)
     */
    private fun cancelWidgetUpdate(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(ctx, TodoWidgetProvider::class.java)
            .setAction(TodoWidgetProvider.ACTION_UPDATE_WIDGET)
        val pi = PendingIntent.getBroadcast(
            ctx, (800_000 + id).toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.cancel()
    }

    suspend fun rescheduleAll(ctx: Context) {
        val db = AppDb.get(ctx)
        val now = System.currentTimeMillis()
        db.todoDao().getAllSync().forEach { t ->
            if (!t.done && t.dueTime != null && t.dueTime > now) {
                scheduleTodo(ctx, t.dueTime, t.id)
            }
        }
        db.dailyDao().getAllSync().forEach { d ->
            if (d.enabled) scheduleDailyNext(ctx, d)
        }
    }
}