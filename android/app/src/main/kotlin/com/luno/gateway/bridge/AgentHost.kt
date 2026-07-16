package com.luno.gateway.bridge

interface AgentHost {
    fun startAgent()
    fun stopAgent()
    fun isAgentRunning(): Boolean
    fun requestNotificationPermission()
    fun hasPhonePermission(): Boolean
    fun requestPhonePermission()
    fun hasSmsPermission(): Boolean
    fun requestSmsPermission()
    fun hasReceiveSmsPermission(): Boolean
    fun requestReceiveSmsPermission()
}
