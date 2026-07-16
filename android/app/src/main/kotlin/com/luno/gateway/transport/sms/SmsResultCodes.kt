package com.luno.gateway.transport.sms

import android.app.Activity
import android.telephony.SmsManager
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass

sealed interface SmsSendResult {
    data object Sent : SmsSendResult

    data class Failed(val error: DomainError) : SmsSendResult
}

/**
 * Maps the `sentIntent` result code delivered by the platform to our error
 * taxonomy (§10). The `RESULT_ERROR_*` values are compile-time constants, so
 * this stays a pure function with no device dependency.
 */
object SmsResultCodes {
    fun fromSentResultCode(resultCode: Int): SmsSendResult = when (resultCode) {
        Activity.RESULT_OK -> SmsSendResult.Sent

        SmsManager.RESULT_ERROR_RADIO_OFF ->
            failed(ErrorClass.TRANSIENT, "radio_off", "Radio is off (airplane mode?)")

        SmsManager.RESULT_ERROR_NO_SERVICE ->
            failed(ErrorClass.TRANSIENT, "no_service", "No cellular service")

        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED ->
            failed(ErrorClass.THROTTLED, "limit_exceeded", "Carrier/device send limit exceeded")

        SmsManager.RESULT_ERROR_NULL_PDU ->
            failed(ErrorClass.TERMINAL, "null_pdu", "Malformed message (null PDU)")

        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
            failed(ErrorClass.TRANSIENT, "generic_failure", "Generic radio failure")

        else ->
            failed(ErrorClass.TRANSIENT, "unknown_$resultCode", "Unknown send result $resultCode")
    }

    private fun failed(errorClass: ErrorClass, code: String, message: String) =
        SmsSendResult.Failed(DomainError(errorClass, code, message))
}
