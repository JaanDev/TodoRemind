package com.example.todoremind.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoremind.data.AppDb
import com.example.todoremind.data.DailyReminder
import com.example.todoremind.data.Todo
import com.example.todoremind.util.Scheduler
import com.example.todoremind.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDb.get(app)
    private val todoDao = db.todoDao()
    private val dailyDao = db.dailyDao()
    private val ctx: Context get() = getApplication()

    val todos: StateFlow<List<Todo>> =
        todoDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dailies: StateFlow<List<DailyReminder>> =
        dailyDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) { Log.e("MainViewModel", "err", e) }
            WidgetUpdater.update(ctx)
        }
    }

    fun saveTodo(id: Long?, text: String, due: Long?) = io {
        if (id == null) {
            val nid = todoDao.insert(Todo(text = text, dueTime = due))
            due?.let { Scheduler.scheduleTodo(ctx, it, nid) }
        } else {
            todoDao.updateFields(id, text, due)
            Scheduler.cancelTodoAlarm(ctx, id)
            if (due != null && due > System.currentTimeMillis()) Scheduler.scheduleTodo(ctx, due, id)
        }
    }

    fun toggleDone(t: Todo) = io {
        val done = !t.done
        todoDao.setDone(t.id, done, if (done) System.currentTimeMillis() else null)
        if (done) Scheduler.cancelTodoAlarm(ctx, t.id)
        else t.dueTime?.takeIf { it > System.currentTimeMillis() }?.let { Scheduler.scheduleTodo(ctx, it, t.id) }
    }

    fun deleteTodo(t: Todo) = io {
        todoDao.delete(t)
        Scheduler.cancelTodoAlarm(ctx, t.id)
    }

    fun saveDaily(id: Long?, text: String, hour: Int, minute: Int, enabled: Boolean) = io {
        if (id == null) {
            val d = DailyReminder(text = text, hour = hour, minute = minute, enabled = enabled)
            val nid = dailyDao.insert(d)
            if (enabled) Scheduler.scheduleDailyNext(ctx, d.copy(id = nid))
        } else {
            dailyDao.updateFields(id, text, hour, minute, enabled)
            Scheduler.cancelDailyAlarm(ctx, id)
            if (enabled) Scheduler.scheduleDailyNext(ctx, DailyReminder(id, text, hour, minute, true))
        }
    }

    fun toggleDaily(d: DailyReminder) = io {
        dailyDao.setEnabled(d.id, !d.enabled)
        if (d.enabled) Scheduler.cancelDailyAlarm(ctx, d.id)
        else Scheduler.scheduleDailyNext(ctx, d.copy(enabled = true))
    }

    fun deleteDaily(d: DailyReminder) = io {
        dailyDao.delete(d)
        Scheduler.cancelDailyAlarm(ctx, d.id)
    }
}
