package com.luno.gateway.model

data class SignalInfo(
    val subscriptionId: Int,
    val dbm: Int?,
    val level: Int,
)
