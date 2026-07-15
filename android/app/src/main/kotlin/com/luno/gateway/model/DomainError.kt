package com.luno.gateway.model

enum class ErrorClass {
    TRANSIENT,
    TERMINAL,
    THROTTLED,
    AUTH,
    INTERNAL,
}

data class DomainError(
    val errorClass: ErrorClass,
    val code: String,
    val message: String,
) {
    val retryable: Boolean
        get() = errorClass == ErrorClass.TRANSIENT || errorClass == ErrorClass.THROTTLED
}
