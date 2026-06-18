package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnackbarBus @Inject constructor() {

    private val _events = MutableSharedFlow<SnackbarEvent>(
        replay = 0,
        // 16 slots absorb rapid multi-delete bursts without dropping undo affordances.
        // DROP_OLDEST is retained as a safety net for pathological floods.
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SnackbarEvent> = _events.asSharedFlow()

    suspend fun emit(event: SnackbarEvent) = _events.emit(event)
}
