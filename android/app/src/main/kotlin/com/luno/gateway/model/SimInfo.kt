package com.luno.gateway.model

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val isEmbedded: Boolean,
    val simState: String,
)
