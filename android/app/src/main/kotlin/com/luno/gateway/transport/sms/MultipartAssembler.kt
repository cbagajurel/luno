package com.luno.gateway.transport.sms

import android.telephony.SmsManager

/**
 * Outbound splitting: turns a message body into the parts the radio will send.
 * `divideMessage` accounts for GSM-7 vs UCS-2 encoding, so a body with an emoji
 * splits at 70 chars, not 160. (Inbound reassembly lands in M11.)
 */
object MultipartAssembler {
    fun split(manager: SmsManager, body: String): List<String> = manager.divideMessage(body)
}
