package com.example.todoremind.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object AppBus {
    private val _add = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val add: SharedFlow<Unit> = _add
    fun postAdd() { _add.tryEmit(Unit) }
}
