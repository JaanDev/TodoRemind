package com.example.todoremind.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val dueTime: Long? = null,          // null = без напоминания
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null
)

@Entity(tableName = "dailies")
data class DailyReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)
