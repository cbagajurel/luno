package com.luno.gateway.transport

import com.luno.gateway.transport.sms.MultipartAssembler
import com.luno.gateway.transport.sms.SmsReceiver
import org.junit.Assert.assertEquals
import org.junit.Test

class InboundSmsTest {
    @Test
    fun `reassemble joins ordered segments back into the original body`() {
        val segments = listOf("Hello, this is the first part ", "and this is the second part.")
        assertEquals(
            "Hello, this is the first part and this is the second part.",
            MultipartAssembler.reassemble(segments),
        )
    }

    @Test
    fun `single-segment reassembly is the segment itself`() {
        assertEquals("just one", MultipartAssembler.reassemble(listOf("just one")))
    }

    @Test
    fun `buildInbound reassembles body, counts parts, and keeps sender and subId`() {
        val message = SmsReceiver.buildInbound(
            sender = "+15550001111",
            bodies = listOf("part one ", "part two"),
            receivedAt = 1234L,
            subscriptionId = 4,
        )
        assertEquals("+15550001111", message.sender)
        assertEquals("part one part two", message.body)
        assertEquals(2, message.parts)
        assertEquals(4, message.subscriptionId)
        assertEquals(1234L, message.receivedAt)
    }

    @Test
    fun `buildInbound dedupe id is stable for the same sender and timestamp`() {
        val a = SmsReceiver.buildInbound("+15550001111", listOf("hi"), 1234L, 2)
        val b = SmsReceiver.buildInbound("+15550001111", listOf("hi"), 1234L, 2)
        assertEquals(a.id, b.id)
    }

    @Test
    fun `buildInbound falls back to unknown sender when address is null`() {
        val message = SmsReceiver.buildInbound(null, listOf("x"), 1L, null)
        assertEquals("unknown", message.sender)
    }
}
