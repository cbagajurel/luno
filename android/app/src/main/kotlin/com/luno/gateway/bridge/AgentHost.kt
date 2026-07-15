package com.luno.gateway.bridge

/**
 * The Activity-backed capabilities the Pigeon HostApi needs but cannot do
 * itself: starting/stopping the service (needs a Context) and requesting a
 * runtime permission (needs an Activity). [MainActivity] supplies the
 * implementation so [LunoHostApiImpl] stays a thin, testable adapter.
 */
interface AgentHost {
    fun startAgent()
    fun stopAgent()
    fun isAgentRunning(): Boolean
    fun requestNotificationPermission()
    fun hasPhonePermission(): Boolean
    fun requestPhonePermission()
}
