package com.luno.gateway.model

/**
 * A delivery report for one part of a sent message, correlated back by
 * [messageId] + [partIndex]. Arrives asynchronously (minutes later, out of
 * order, or never — see the delivery timeout in the tracker). See §7.3.
 */
data class DeliveryReport(
    val messageId: String,
    val partIndex: Int,
    val delivered: Boolean,
    val at: Long,
)
