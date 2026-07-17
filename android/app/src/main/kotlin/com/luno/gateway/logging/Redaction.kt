package com.luno.gateway.logging

/**
 * The single, central PII scrubber for log lines (§9). Message bodies never reach
 * logs by construction; what does slip in is phone numbers, so this masks any
 * phone-number-like digit run (keeping the last two digits for correlation, e.g.
 * `+15551112222` → `+*********22`). Wired once as [LunoLogger]'s redactor so no call
 * site can leak a number. Idempotent: a masked value has too few digits to re-match.
 */
object Redaction {
    private val NUMBER = Regex("\\+?\\d{7,15}")

    fun redact(message: String): String =
        NUMBER.replace(message) { match ->
            val hasPlus = match.value.startsWith("+")
            val digits = if (hasPlus) match.value.substring(1) else match.value
            val masked = "*".repeat((digits.length - 2).coerceAtLeast(0)) + digits.takeLast(2)
            if (hasPlus) "+$masked" else masked
        }
}
