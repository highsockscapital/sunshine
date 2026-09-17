package com.highsockscapital.sunshine.data

import kotlinx.coroutines.Job

/**
 * Tracks the active execution handle per session. Ownership is released only when
 * the session's job and its cancellation cleanup have actually completed, not when
 * cancellation is requested. This keeps a paused session visibly busy and blocks
 * replacement turns until the old run has fully unwound.
 */
internal class SessionExecutionRegistry<T : Any> {
    private val handles = mutableMapOf<String, T>()

    @Synchronized
    operator fun get(sessionId: String): T? = handles[sessionId]

    @Synchronized
    fun putIfAbsent(sessionId: String, handle: T): T? {
        val existing = handles[sessionId]
        if (existing != null) return existing
        handles[sessionId] = handle
        return null
    }

    @Synchronized
    fun remove(sessionId: String, handle: T): Boolean {
        if (handles[sessionId] !== handle) return false
        handles.remove(sessionId)
        return true
    }

    /**
     * Runs [action] only while [handle] still owns [sessionId], mutually exclusive
     * with completion handlers, so an ownership check and its dependent state
     * update cannot be interleaved with a release.
     */
    @Synchronized
    fun performIfOwned(
        sessionId: String,
        handle: T,
        action: () -> Unit,
    ) {
        if (handles[sessionId] === handle) action()
    }

    /**
     * Releases ownership and resets state when [job] completes. The completion
     * handler runs for every terminal state, including jobs cancelled before
     * their coroutine body started.
     */
    fun releaseOnCompletion(
        sessionId: String,
        handle: T,
        job: Job,
        onReleased: () -> Unit,
    ) {
        job.invokeOnCompletion {
            synchronized(this) {
                if (handles[sessionId] !== handle) return@synchronized
                handles.remove(sessionId)
                onReleased()
            }
        }
    }
}
