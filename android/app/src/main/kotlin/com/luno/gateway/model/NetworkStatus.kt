package com.luno.gateway.model

data class NetworkStatus(
    val connected: Boolean,
    val validated: Boolean,
    val transport: String,
    val metered: Boolean,
)
