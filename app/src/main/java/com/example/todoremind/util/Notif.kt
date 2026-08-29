package com.example.todoremind.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.todoremind.AlarmReceiver
import com.example.todoremind.MainActivity
import com.example.todoremind.R
import com.example.todoremind.data.DailyReminder
import com.example.todoremind.data.Todo

object Notif {
    const val CHANNEL_ID = "reminders"
    private const val DAILY_OFFSET = 1_000_000

    fun notifId(kind: Int, id: Long) =
        if (kind == AlarmReceiver.KIND_DAILY) (DAILY_OFFSET + id).toInt() else id.toInt()

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
        ch.description = ctx.getString(R.string.channel_desc)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 250, 200, 250, 200, 250)
        ch.enableLights(true)
        ch.setBypassDnd(true) // пробивает «Не беспокоить»
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ch.setSound(
            sound,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        nm.createNotificationChannel(ch)
    }

    private fun contentPi(ctx: Context, notifId: Int) = PendingIntent.getActivity(
        ctx, notifId,
        Intent(ctx, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(ctx: Context, notifId: Int, op: Int, id: Long, kind: Int, title: String, icon: Int): NotificationCompat.Action {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION)
            .putExtra(AlarmReceiver.EXTRA_OP, op)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .putExtra(AlarmReceiver.EXTRA_KIND, kind)
        val pi = PendingIntent.getBroadcast(ctx, notifId * 10 + op, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action.Builder(icon, title, pi).build()
    }

    private fun base(ctx: Context, title: String, text: String, notifId: Int) =
        NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title — $text"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setColor(0xFF6750A4.toInt())
            .setContentIntent(contentPi(ctx, notifId))
            .setFullScreenIntent(contentPi(ctx, notifId), true) // всплывает на заблокированном экране

    fun showTodo(ctx: Context, t: Todo) {
        ensureChannel(ctx)
        val nid = notifId(AlarmReceiver.KIND_TODO, t.id)
        val whenText = t.dueTime?.let { Fmt.due(ctx, it) } ?: ctx.getString(R.string.no_time)
        val b = base(ctx, t.text, ctx.getString(R.string.notif_todo_sub, whenText), nid)
            .addAction(action(ctx, nid, AlarmReceiver.OP_DONE, t.id, AlarmReceiver.KIND_TODO, ctx.getString(R.string.act_done), R.drawable.ic_check))
            .addAction(action(ctx, nid, AlarmReceiver.OP_SNOOZE, t.id, AlarmReceiver.KIND_TODO, ctx.getString(R.string.act_snooze), R.drawable.ic_clock))
            .addAction(action(ctx, nid, AlarmReceiver.OP_DISMISS, t.id, AlarmReceiver.KIND_TODO, ctx.getString(R.string.act_skip), R.drawable.ic_close))
        post(ctx, nid, b.build())
    }

    fun showDaily(ctx: Context, d: DailyReminder) {
        ensureChannel(ctx)
        val nid = notifId(AlarmReceiver.KIND_DAILY, d.id)
        val b = base(ctx, d.text, ctx.getString(R.string.notif_daily_sub, String.format("%02d:%02d", d.hour, d.minute)), nid)
            .addAction(action(ctx, nid, AlarmReceiver.OP_DISMISS, d.id, AlarmReceiver.KIND_DAILY, ctx.getString(R.string.act_skip), R.drawable.ic_close))
            .addAction(action(ctx, nid, AlarmReceiver.OP_SNOOZE, d.id, AlarmReceiver.KIND_DAILY, ctx.getString(R.string.act_snooze), R.drawable.ic_clock))
            .addAction(action(ctx, nid, AlarmReceiver.OP_DISABLE_DAILY, d.id, AlarmReceiver.KIND_DAILY, ctx.getString(R.string.act_off), R.drawable.ic_bell_off))
        post(ctx, nid, b.build())
    }

    fun cancel(ctx: Context, notifId: Int) {
        try { NotificationManagerCompat.from(ctx).cancel(notifId) } catch (_: Exception) {}
    }

    private fun post(ctx: Context, id: Int, n: Notification) {
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: Exception) {}
    }
}
