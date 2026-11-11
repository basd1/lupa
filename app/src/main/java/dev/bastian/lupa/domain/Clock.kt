package dev.bastian.lupa.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Source of the current time, injected so logic never reads the system clock directly. */
fun interface Clock {
    fun now(): Long
}

val SystemClock = Clock { System.currentTimeMillis() }

/** Dispatchers behind one object so tests can replace all of them. */
class AppDispatchers(
    val default: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO,
)
