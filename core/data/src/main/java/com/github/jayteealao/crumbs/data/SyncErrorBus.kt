package com.github.jayteealao.crumbs.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncErrorBus @Inject constructor() {

    private val _events = MutableSharedFlow<SyncErrorEvent>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<SyncErrorEvent> = _events.asSharedFlow()

    fun emit(event: SyncErrorEvent): Boolean = _events.tryEmit(event)

    /**
     * Clear the replay slot. Call this once the UI has acknowledged the
     * current error (e.g. when the access token becomes available again and
     * the banner is dismissed). Without this, replay=1 would resurrect the
     * stale event on every warm-start subscription, producing a one-frame
     * banner flash whenever the route recomposes.
     */
    fun clear() {
        _events.resetReplayCache()
    }
}
