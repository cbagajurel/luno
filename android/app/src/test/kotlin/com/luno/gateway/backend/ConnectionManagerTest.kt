package com.luno.gateway.backend

import com.luno.gateway.agent.ConnectionState
import com.luno.gateway.backend.auth.DeviceCredential
import com.luno.gateway.backend.protocol.Ack
import com.luno.gateway.backend.protocol.Control
import com.luno.gateway.backend.protocol.ProtocolCodec
import com.luno.gateway.backend.protocol.ProtocolFrame
import com.luno.gateway.backend.ws.ConnectionManager
import com.luno.gateway.backend.ws.ReconnectPolicy
import com.luno.gateway.backend.ws.Socket
import com.luno.gateway.backend.ws.SocketEvent
import com.luno.gateway.testutil.testLogger
import com.luno.gateway.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {
    private class FakeSocket : Socket {
        private var handler: ((SocketEvent) -> Unit)? = null
        var connectCount = 0
        var closeCount = 0
        var lastWsUrl: String? = null
        var lastCredential: String? = null
        val sent = mutableListOf<String>()

        override fun connect(wsUrl: String, credential: String, onEvent: (SocketEvent) -> Unit) {
            connectCount++
            lastWsUrl = wsUrl
            lastCredential = credential
            handler = onEvent
        }

        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun close() {
            closeCount++
            handler = null
        }

        fun emit(event: SocketEvent) = handler?.invoke(event)
    }

    private val codec = ProtocolCodec()
    private val credential =
        DeviceCredential(
            backendUrl = "https://backend.example",
            wsUrl = "wss://backend.example/ws",
            deviceId = "dev-1",
            credential = "secret-token",
        )

    private fun serverControl(control: Control): String =
        codec.encode(ProtocolFrame.control(1, "s-id", TS, "dev-1", 1, control))

    private fun serverAck(): String =
        codec.encode(ProtocolFrame.ack(1, "s-id", TS, "dev-1", 2, Ack("client-id")))

    private fun manager(
        socket: Socket,
        scope: CoroutineScope,
        online: MutableStateFlow<Boolean>,
        credentialProvider: () -> DeviceCredential? = { credential },
    ) = ConnectionManager(
        socket = socket,
        codec = codec,
        reconnectPolicy = ReconnectPolicy(),
        scope = scope,
        clock = Clock { 1_000L },
        online = online,
        credentialProvider = credentialProvider,
        logger = testLogger(),
    )

    /** Runs [body] with a manager on the test scope, then stops it so the loop coroutines finish. */
    private fun scenario(
        online: Boolean = true,
        credentialProvider: () -> DeviceCredential? = { credential },
        body: TestScope.(FakeSocket, ConnectionManager) -> Unit,
    ) = runTest {
        val socket = FakeSocket()
        val cm = manager(socket, this, MutableStateFlow(online), credentialProvider)
        try {
            cm.start()
            advanceUntilIdle()
            body(socket, cm)
        } finally {
            cm.stop()
            advanceUntilIdle()
        }
    }

    private fun FakeSocket.handshake(cm: ConnectionManager, scope: TestScope) {
        emit(SocketEvent.Open)
        emit(SocketEvent.Message(serverControl(Control.VersionNegotiate(listOf(1), selected = 1))))
        emit(SocketEvent.Message(serverAck()))
        scope.advanceUntilIdle()
    }

    @Test
    fun `full handshake reaches READY and sends the negotiate then resync controls`() =
        scenario { socket, cm ->
            assertEquals(ConnectionState.CONNECTING, cm.state.value)
            assertEquals("wss://backend.example/ws", socket.lastWsUrl)
            assertEquals("secret-token", socket.lastCredential)

            socket.emit(SocketEvent.Open)
            advanceUntilIdle()
            assertEquals(ConnectionState.CONNECTED, cm.state.value)

            socket.emit(SocketEvent.Message(serverControl(Control.VersionNegotiate(listOf(1), selected = 1))))
            advanceUntilIdle()
            assertEquals(ConnectionState.AUTHENTICATED, cm.state.value)

            socket.emit(SocketEvent.Message(serverAck()))
            advanceUntilIdle()
            assertEquals(ConnectionState.READY, cm.state.value)
            assertEquals(1, socket.connectCount)
            assertEquals(2, socket.sent.size)
        }

    @Test
    fun `no network at start parks in OFFLINE_NO_NETWORK without connecting`() =
        scenario(online = false) { socket, cm ->
            assertEquals(ConnectionState.OFFLINE_NO_NETWORK, cm.state.value)
            assertEquals(0, socket.connectCount)
        }

    @Test
    fun `an unpaired node stays DISCONNECTED and never opens a socket`() =
        scenario(credentialProvider = { null }) { socket, cm ->
            assertEquals(ConnectionState.DISCONNECTED, cm.state.value)
            assertEquals(0, socket.connectCount)
        }

    @Test
    fun `a rejected credential pauses instead of reconnect-looping`() =
        scenario { socket, cm ->
            socket.emit(SocketEvent.Failure(httpCode = 401, error = null))
            advanceUntilIdle()

            assertEquals(ConnectionState.DISCONNECTED, cm.state.value)
            assertEquals(1, socket.connectCount)
        }

    @Test
    fun `a dropped socket backs off and reconnects`() =
        scenario { socket, cm ->
            socket.handshake(cm, this)
            assertEquals(ConnectionState.READY, cm.state.value)

            socket.emit(SocketEvent.Closed(1001, "gone"))
            advanceUntilIdle()

            assertEquals(2, socket.connectCount)
            assertEquals(ConnectionState.CONNECTING, cm.state.value)
        }

    @Test
    fun `losing the network tears the socket down`() = runTest {
        val socket = FakeSocket()
        val online = MutableStateFlow(true)
        val cm = manager(socket, this, online)
        try {
            cm.start()
            advanceUntilIdle()
            socket.emit(SocketEvent.Open)
            advanceUntilIdle()

            online.value = false
            advanceUntilIdle()

            assertEquals(ConnectionState.OFFLINE_NO_NETWORK, cm.state.value)
            assertEquals(1, socket.closeCount)
        } finally {
            cm.stop()
            advanceUntilIdle()
        }
    }

    @Test
    fun `disconnect tears down but keeps the loop running`() =
        scenario { socket, cm ->
            socket.emit(SocketEvent.Open)
            advanceUntilIdle()

            cm.disconnect()
            advanceUntilIdle()

            assertEquals(ConnectionState.DISCONNECTED, cm.state.value)
        }

    companion object {
        private const val TS = "2026-01-01T00:00:00Z"
    }
}
