package com.luno.gateway.bridge

import com.luno.gateway.bridge.generated.LunoHostApi
import com.luno.gateway.telephony.DeviceStateStore
import com.luno.gateway.bridge.generated.DeviceState as DeviceStateDto

class LunoHostApiImpl(
    private val host: AgentHost,
    private val deviceStateStore: DeviceStateStore,
) : LunoHostApi {
    override fun ping(message: String): String = "$ECHO_PREFIX$message"

    override fun startAgent() = host.startAgent()

    override fun stopAgent() = host.stopAgent()

    override fun isAgentRunning(): Boolean = host.isAgentRunning()

    override fun requestNotificationPermission() = host.requestNotificationPermission()

    override fun getDeviceState(): DeviceStateDto = deviceStateStore.current.toDto()

    override fun hasPhonePermission(): Boolean = host.hasPhonePermission()

    override fun requestPhonePermission() = host.requestPhonePermission()

    companion object {
        const val ECHO_PREFIX = "Luno-Kotlin echo: "
    }
}
