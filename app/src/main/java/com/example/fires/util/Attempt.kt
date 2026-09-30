package com.example.fires.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Like runCatching, but safe inside coroutines.
 *
 * Plain runCatching also catches CancellationException, which breaks cancellation: a ViewModel
 * that is cleared would keep running. Here cancellation is rethrown, except for a timeout from
 * withTimeout(), which is reported as an ordinary failure so the screen can show a message.
 */
suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: TimeoutCancellationException) {
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
