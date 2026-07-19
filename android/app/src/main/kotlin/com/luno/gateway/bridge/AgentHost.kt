package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.PermissionStatus

interface AgentHost {
    fun startAgent()
    fun stopAgent()
    fun isAgentRunning(): Boolean
    fun requestNotificationPermission()
    fun phonePermissionStatus(): PermissionStatus
    fun requestPhonePermission(onResult: (PermissionStatus) -> Unit)
    fun smsPermissionStatus(): PermissionStatus
    fun requestSmsPermission(onResult: (PermissionStatus) -> Unit)
    fun openAppSettings()
    fun isReceiveSmsSupported(): Boolean
    fun receiveSmsPermissionStatus(): PermissionStatus
    fun requestReceiveSmsPermission(onResult: (PermissionStatus) -> Unit)
}
