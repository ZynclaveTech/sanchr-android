package com.sanchr.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * A sealed type representing the result of an operation that can succeed or fail.
 * Used throughout the app for consistent error handling at the data/domain boundary.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable) : Result<Nothing>
    data object Loading : Result<Nothing>
}

/**
 * Maps a successful result to a new type.
 */
fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}

/**
 * Returns the data if successful, or null otherwise.
 */
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    else -> null
}

/**
 * Returns the data if successful, or the provided default value.
 */
fun <T> Result<T>.getOrDefault(default: T): T = when (this) {
    is Result.Success -> data
    else -> default
}

/**
 * Returns true if this is a successful result.
 */
val Result<*>.isSuccess: Boolean get() = this is Result.Success

/**
 * Returns true if this is an error result.
 */
val Result<*>.isError: Boolean get() = this is Result.Error

/**
 * Wraps a Flow emission in Result, catching any exceptions as Result.Error.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    map<T, Result<T>> { Result.Success(it) }
        .catch { emit(Result.Error(it)) }

/**
 * Executes a suspend block and wraps the result in Result, catching exceptions.
 */
suspend fun <T> runCatchingResult(block: suspend () -> T): Result<T> =
    try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
