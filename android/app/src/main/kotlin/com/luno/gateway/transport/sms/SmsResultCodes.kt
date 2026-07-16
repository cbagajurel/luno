package com.luno.gateway.transport.sms

import android.app.Activity
import com.luno.gateway.model.DomainError
import com.luno.gateway.model.ErrorClass

sealed interface SmsSendResult {
    data object Sent : SmsSendResult

    data class Failed(val error: DomainError) : SmsSendResult
}

/**
 * Maps the `sentIntent` result code to our error taxonomy (§10). Covers both the
 * classic `RESULT_ERROR_*` codes (API 4) and the modern `RESULT_*` codes (API 30+);
 * they are plain int constants, so this stays a pure, device-independent function.
 * Anything unrecognised is treated as transient (retryable) rather than swallowed.
 */
object SmsResultCodes {
    fun fromSentResultCode(resultCode: Int): SmsSendResult = when (resultCode) {
        Activity.RESULT_OK, RESULT_ERROR_NONE -> SmsSendResult.Sent

        RESULT_ERROR_GENERIC_FAILURE -> transient("generic_failure", "Generic radio failure")
        RESULT_ERROR_RADIO_OFF -> transient("radio_off", "Radio is off (airplane mode?)")
        RESULT_ERROR_NO_SERVICE -> transient("no_service", "No cellular service")
        RESULT_RADIO_NOT_AVAILABLE -> transient("radio_not_available", "Radio not available")
        RESULT_NETWORK_REJECT -> transient("network_reject", "Network rejected the message")
        RESULT_INVALID_STATE -> transient("invalid_state", "Modem in invalid state")
        RESULT_NO_MEMORY -> transient("no_memory", "Out of memory")
        RESULT_SYSTEM_ERROR -> transient("system_error", "System error")
        RESULT_MODEM_ERROR -> transient("modem_error", "Modem error")
        RESULT_NETWORK_ERROR -> transient("network_error", "Network error (carrier rejected or unreachable)")
        RESULT_INTERNAL_ERROR -> transient("internal_error", "Internal error")
        RESULT_NO_RESOURCES -> transient("no_resources", "No resources")

        RESULT_ERROR_LIMIT_EXCEEDED -> throttled("limit_exceeded", "Carrier/device send limit exceeded")

        RESULT_ERROR_NULL_PDU -> terminal("null_pdu", "Malformed message (null PDU)")
        RESULT_ERROR_FDN_CHECK_FAILURE -> terminal("fdn_check", "Blocked by Fixed Dialing Numbers")
        RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> terminal("short_code_not_allowed", "Short code not allowed")
        RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> terminal("short_code_never_allowed", "Short code never allowed")
        RESULT_INVALID_ARGUMENTS -> terminal("invalid_arguments", "Invalid arguments")
        RESULT_INVALID_SMS_FORMAT -> terminal("invalid_format", "Invalid SMS format")
        RESULT_ENCODING_ERROR -> terminal("encoding_error", "Encoding error")
        RESULT_INVALID_SMSC_ADDRESS -> terminal("invalid_smsc", "Invalid SMSC address")
        RESULT_OPERATION_NOT_ALLOWED -> terminal("operation_not_allowed", "Operation not allowed")
        RESULT_CANCELLED -> terminal("cancelled", "Send cancelled")
        RESULT_REQUEST_NOT_SUPPORTED -> terminal("not_supported", "Request not supported")

        else -> transient("unknown_$resultCode", "Unknown send result $resultCode")
    }

    /** Human-readable name for logs (best effort). */
    fun name(resultCode: Int): String = when (val r = fromSentResultCode(resultCode)) {
        is SmsSendResult.Sent -> "OK"
        is SmsSendResult.Failed -> r.error.code
    }

    private fun transient(code: String, message: String) = fail(ErrorClass.TRANSIENT, code, message)
    private fun throttled(code: String, message: String) = fail(ErrorClass.THROTTLED, code, message)
    private fun terminal(code: String, message: String) = fail(ErrorClass.TERMINAL, code, message)

    private fun fail(errorClass: ErrorClass, code: String, message: String) =
        SmsSendResult.Failed(DomainError(errorClass, code, message))

    // SmsManager result-code constants, inlined so the mapping is testable off-device.
    private const val RESULT_ERROR_GENERIC_FAILURE = 1
    private const val RESULT_ERROR_RADIO_OFF = 2
    private const val RESULT_ERROR_NULL_PDU = 3
    private const val RESULT_ERROR_NO_SERVICE = 4
    private const val RESULT_ERROR_LIMIT_EXCEEDED = 5
    private const val RESULT_ERROR_FDN_CHECK_FAILURE = 6
    private const val RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 7
    private const val RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 8
    private const val RESULT_ERROR_NONE = 0
    private const val RESULT_RADIO_NOT_AVAILABLE = 9
    private const val RESULT_NETWORK_REJECT = 10
    private const val RESULT_INVALID_ARGUMENTS = 11
    private const val RESULT_INVALID_STATE = 12
    private const val RESULT_NO_MEMORY = 13
    private const val RESULT_INVALID_SMS_FORMAT = 14
    private const val RESULT_SYSTEM_ERROR = 15
    private const val RESULT_MODEM_ERROR = 16
    private const val RESULT_NETWORK_ERROR = 17
    private const val RESULT_ENCODING_ERROR = 18
    private const val RESULT_INVALID_SMSC_ADDRESS = 19
    private const val RESULT_OPERATION_NOT_ALLOWED = 20
    private const val RESULT_INTERNAL_ERROR = 21
    private const val RESULT_NO_RESOURCES = 22
    private const val RESULT_CANCELLED = 23
    private const val RESULT_REQUEST_NOT_SUPPORTED = 24
}

enum class DeliveryOutcome { DELIVERED, FAILED, PENDING }

/**
 * Classifies a GSM/3GPP delivery-report TP-Status: 0x00–0x1F = delivered,
 * 0x20–0x3F = still trying (keep waiting), 0x40+ = permanent failure. A final
 * report resolves the part; PENDING is ignored until one arrives (or timeout).
 */
object SmsDeliveryStatus {
    fun classify(tpStatus: Int): DeliveryOutcome = when {
        tpStatus < 0x20 -> DeliveryOutcome.DELIVERED
        tpStatus < 0x40 -> DeliveryOutcome.PENDING
        else -> DeliveryOutcome.FAILED
    }
}
