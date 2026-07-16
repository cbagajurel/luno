package com.luno.gateway.transport.sms

import android.telephony.SmsManager

/**
 * Outbound splitting and inbound reassembly. `divideMessage` accounts for GSM-7
 * vs UCS-2 encoding, so a body with an emoji splits at 70 chars, not 160. Inbound,
 * a concatenated SMS arrives as its ordered segments in one broadcast, so joining
 * their bodies reproduces the original message.
 */
object MultipartAssembler {
    fun split(manager: SmsManager, body: String): List<String> = manager.divideMessage(body)

    fun reassemble(parts: List<String>): String = parts.joinToString(separator = "")
}
