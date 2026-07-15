package com.luno.gateway.util

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
