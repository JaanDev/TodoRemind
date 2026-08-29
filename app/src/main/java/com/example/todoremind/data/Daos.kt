package com.example.todoremind.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY done ASC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos ORDER BY done ASC, createdAt DESC")
    fun getAllSync(): List<Todo>

    @Query("SELECT * FROM todos WHERE id=:id")
    suspend fun get(id: Long): Todo?

    @Query("SELECT * FROM todos WHERE id=:id")
    fun getSync(id: Long): Todo?

    @Insert
    suspend fun insert(t: Todo): Long

    @Delete
    suspend fun delete(t: Todo)

    @Query("UPDATE todos SET text=:text, dueTime=:due WHERE id=:id")
    suspend fun updateFields(id: Long, text: String, due: Long?)

    @Query("UPDATE todos SET done=:done, doneAt=:doneAt WHERE id=:id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?)
}

@Dao
interface DailyDao {
    @Query("SELECT * FROM dailies ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<DailyReminder>>

    @Query("SELECT * FROM dailies ORDER BY hour, minute, id")
    fun getAllSync(): List<DailyReminder>

    @Query("SELECT * FROM dailies WHERE id=:id")
    fun getSync(id: Long): DailyReminder?

    @Insert
    suspend fun insert(d: DailyReminder): Long

    @Delete
    suspend fun delete(d: DailyReminder)

    @Query("UPDATE dailies SET text=:text, hour=:hour, minute=:minute, enabled=:enabled WHERE id=:id")
    suspend fun updateFields(id: Long, text: String, hour: Int, minute: Int, enabled: Boolean)

    @Query("UPDATE dailies SET enabled=:enabled WHERE id=:id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
