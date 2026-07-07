package dev.mellow.core.common

sealed interface MellowResult<out T> {
    data class Success<T>(val data: T) : MellowResult<T>
    data class Error(val exception: Throwable) : MellowResult<Nothing>
    data object Loading : MellowResult<Nothing>
}
