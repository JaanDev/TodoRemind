package com.example.todoremind.util

import android.content.Context
import com.example.todoremind.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Fmt {
    private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun time(ms: Long): String = hm.format(Date(ms))

    fun today(): String = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    fun dueDate(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val now = Calendar.getInstance()
        val pattern = if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "d MMMM" else "d MMMM yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

    /**
     * Локализованный формат:
     *   "Сегодня, 17:00" / "Today, 5:00 PM"
     *   "Завтра, 09:30"  / "Tomorrow, 9:30 AM"
     *   "Вчера, 20:15"   / "Yesterday, 8:15 PM"
     *   "14 ноября, 12:00" / "November 14, 12:00"
     *   "14 ноября 2026, 12:00" (для другого года)
     */
    fun due(ctx: Context, ms: Long): String {
        val today0 = startOfDay(System.currentTimeMillis())
        val day0 = startOfDay(ms)
        val days = ((day0 - today0) / 86_400_000L).toInt()
        val t = time(ms)
        val label = when (days) {
            0 -> ctx.getString(R.string.date_today)
            1 -> ctx.getString(R.string.date_tomorrow)
            -1 -> ctx.getString(R.string.date_yesterday)
            else -> dueDate(ms)
        }
        return ctx.getString(R.string.date_at_time, label, t)
    }

    fun isOverdue(ms: Long?): Boolean =
        ms != null && ms < System.currentTimeMillis()

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextFullHour(): Long = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun tomorrowAt(h: Int, m: Int): Long =
        withTime(startOfDay(System.currentTimeMillis()) + 86_400_000L, h, m)

    fun withDate(base: Long, dateUtc: Long): Long {
        val src = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtc }
        return Calendar.getInstance().apply {
            timeInMillis = base
            set(Calendar.YEAR, src.get(Calendar.YEAR))
            set(Calendar.MONTH, src.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, src.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun withTime(base: Long, h: Int, m: Int): Long = Calendar.getInstance().apply {
        timeInMillis = base
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}