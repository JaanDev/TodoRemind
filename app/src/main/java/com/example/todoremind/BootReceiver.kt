package com.example.todoremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todoremind.util.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val ctx = context.applicationContext
                val p = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { Scheduler.rescheduleAll(ctx) } finally { p.finish() }
                }
            }
        }
    }
}
