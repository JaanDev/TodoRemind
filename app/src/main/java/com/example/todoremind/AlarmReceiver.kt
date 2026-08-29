package com.example.todoremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.todoremind.data.AppDb
import com.example.todoremind.util.Notif
import com.example.todoremind.util.Prefs
import com.example.todoremind.util.Scheduler
import com.example.todoremind.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.example.todoremind.ACTION"
        const val ACTION_ALARM = "com.example.todoremind.ALARM"
        const val EXTRA_OP = "op"
        const val EXTRA_ID = "id"
        const val EXTRA_KIND = "kind"
        const val KIND_TODO = 0
        const val KIND_DAILY = 1
        const val OP_TOGGLE_TODO = 1
        const val OP_TOGGLE_DAILY = 2
        const val OP_TOGGLE_SHOW_DONE = 3
        const val OP_OPEN_ADD = 4
        const val OP_OPEN_APP = 5
        const val OP_REFRESH = 6
        const val OP_DONE = 10
        const val OP_SNOOZE = 11
        const val OP_DISMISS = 12
        const val OP_DISABLE_DAILY = 13
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            ACTION_ALARM -> async(ctx) { handleAlarm(ctx, intent) }
            ACTION -> {
                val op = intent.getIntExtra(EXTRA_OP, -1)
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val kind = intent.getIntExtra(EXTRA_KIND, KIND_TODO)
                when (op) {
                    OP_DISMISS -> Notif.cancel(ctx, Notif.notifId(kind, id))
                    OP_SNOOZE -> {
                        Notif.cancel(ctx, Notif.notifId(kind, id))
                        Scheduler.scheduleSnooze(
                            ctx,
                            if (kind == KIND_DAILY) Scheduler.TYPE_SNOOZE_DAILY else Scheduler.TYPE_SNOOZE_TODO,
                            id
                        )
                    }
                    OP_OPEN_ADD -> openApp(ctx, true)
                    OP_OPEN_APP -> openApp(ctx, false)
                    else -> async(ctx) { handleOp(ctx, op, id) }
                }
            }
        }
    }

    private suspend fun handleAlarm(ctx: Context, intent: Intent) {
        val db = AppDb.get(ctx)
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1)
        when (intent.getIntExtra(Scheduler.EXTRA_TYPE, -1)) {
            Scheduler.TYPE_TODO, Scheduler.TYPE_SNOOZE_TODO ->
                db.todoDao().getSync(id)?.takeIf { !it.done }?.let { Notif.showTodo(ctx, it) }
            Scheduler.TYPE_DAILY ->
                db.dailyDao().getSync(id)?.takeIf { it.enabled }?.let {
                    Notif.showDaily(ctx, it)
                    Scheduler.scheduleDailyNext(ctx, it) // следующий день
                }
            Scheduler.TYPE_SNOOZE_DAILY ->
                db.dailyDao().getSync(id)?.takeIf { it.enabled }?.let { Notif.showDaily(ctx, it) }
        }
    }

    private suspend fun handleOp(ctx: Context, op: Int, id: Long) {
        val db = AppDb.get(ctx)
        when (op) {
            OP_TOGGLE_TODO -> {
                val t = db.todoDao().getSync(id) ?: return
                val nd = !t.done
                db.todoDao().setDone(id, nd, if (nd) System.currentTimeMillis() else null)
                if (nd) Scheduler.cancelTodoAlarm(ctx, id)
                else t.dueTime?.takeIf { it > System.currentTimeMillis() }?.let { Scheduler.scheduleTodo(ctx, it, id) }
            }
            OP_DONE -> {
                db.todoDao().setDone(id, true, System.currentTimeMillis())
                Scheduler.cancelTodoAlarm(ctx, id)
                Notif.cancel(ctx, Notif.notifId(KIND_TODO, id))
            }
            OP_TOGGLE_DAILY -> {
                val d = db.dailyDao().getSync(id) ?: return
                db.dailyDao().setEnabled(id, !d.enabled)
                if (d.enabled) Scheduler.cancelDailyAlarm(ctx, id)
                else Scheduler.scheduleDailyNext(ctx, d.copy(enabled = true))
            }
            OP_DISABLE_DAILY -> {
                db.dailyDao().setEnabled(id, false)
                Scheduler.cancelDailyAlarm(ctx, id)
                Notif.cancel(ctx, Notif.notifId(KIND_DAILY, id))
            }
            OP_TOGGLE_SHOW_DONE -> Prefs.setShowDone(ctx, !Prefs.showDone(ctx))
            OP_REFRESH -> Scheduler.rescheduleAll(ctx)
        }
        WidgetUpdater.update(ctx)
    }

    private fun openApp(ctx: Context, add: Boolean) {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (add) i.putExtra(MainActivity.EXTRA_OPEN_ADD, true)
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    private fun async(ctx: Context, block: suspend (Context) -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try { block(ctx) } catch (e: Exception) { Log.e("AlarmReceiver", "error", e) }
            finally { pending.finish() }
        }
    }
}
