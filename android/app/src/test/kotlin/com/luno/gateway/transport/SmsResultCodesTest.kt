package com.luno.gateway.transport

import android.app.Activity
import android.telephony.SmsManager
import com.luno.gateway.model.ErrorClass
import com.luno.gateway.transport.sms.SmsResultCodes
import com.luno.gateway.transport.sms.SmsSendResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsResultCodesTest {
    @Test
    fun `RESULT_OK maps to Sent`() {
        assertEquals(SmsSendResult.Sent, SmsResultCodes.fromSentResultCode(Activity.RESULT_OK))
    }

    @Test
    fun `radio off and no service are transient (retryable)`() {
        for (code in listOf(SmsManager.RESULT_ERROR_RADIO_OFF, SmsManager.RESULT_ERROR_NO_SERVICE)) {
            val result = SmsResultCodes.fromSentResultCode(code) as SmsSendResult.Failed
            assertEquals(ErrorClass.TRANSIENT, result.error.errorClass)
            assertTrue(result.error.retryable)
        }
    }

    @Test
    fun `limit exceeded is throttled (retryable)`() {
        val result = SmsResultCodes.fromSentResultCode(SmsManager.RESULT_ERROR_LIMIT_EXCEEDED) as SmsSendResult.Failed
        assertEquals(ErrorClass.THROTTLED, result.error.errorClass)
        assertTrue(result.error.retryable)
    }

    @Test
    fun `null pdu is terminal (not retryable)`() {
        val result = SmsResultCodes.fromSentResultCode(SmsManager.RESULT_ERROR_NULL_PDU) as SmsSendResult.Failed
        assertEquals(ErrorClass.TERMINAL, result.error.errorClass)
        assertTrue(!result.error.retryable)
    }

    @Test
    fun `unknown code falls back to transient with a labelled code`() {
        val result = SmsResultCodes.fromSentResultCode(9999) as SmsSendResult.Failed
        assertEquals(ErrorClass.TRANSIENT, result.error.errorClass)
        assertEquals("unknown_9999", result.error.code)
    }
}
