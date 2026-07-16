package com.luno.gateway.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStateMachineTest {
    private fun next(state: ConnectionState, event: ConnectionEvent) =
        ConnectionStateMachine.transition(state, event)

    @Test
    fun `full happy path reaches READY`() {
        var state = ConnectionState.OFFLINE_NO_NETWORK
        state = next(state, ConnectionEvent.NETWORK_AVAILABLE)!!
        assertEquals(ConnectionState.DISCONNECTED, state)
        state = next(state, ConnectionEvent.CONNECT)!!
        assertEquals(ConnectionState.CONNECTING, state)
        state = next(state, ConnectionEvent.SOCKET_OPENED)!!
        assertEquals(ConnectionState.CONNECTED, state)
        state = next(state, ConnectionEvent.AUTH_OK)!!
        assertEquals(ConnectionState.AUTHENTICATED, state)
        state = next(state, ConnectionEvent.READY_UP)!!
        assertEquals(ConnectionState.READY, state)
    }

    @Test
    fun `drop from READY loops through reconnect and backoff back to connecting`() {
        var state = ConnectionState.READY
        state = next(state, ConnectionEvent.SOCKET_DROPPED)!!
        assertEquals(ConnectionState.RECONNECTING, state)
        state = next(state, ConnectionEvent.BACKOFF_SCHEDULED)!!
        assertEquals(ConnectionState.BACKING_OFF, state)
        state = next(state, ConnectionEvent.BACKOFF_ELAPSED)!!
        assertEquals(ConnectionState.CONNECTING, state)
    }

    @Test
    fun `heartbeat miss triggers reconnect from authenticated and ready`() {
        assertEquals(ConnectionState.RECONNECTING, next(ConnectionState.AUTHENTICATED, ConnectionEvent.HEARTBEAT_MISSED))
        assertEquals(ConnectionState.RECONNECTING, next(ConnectionState.READY, ConnectionEvent.HEARTBEAT_MISSED))
    }

    @Test
    fun `connect failure goes to reconnecting`() {
        assertEquals(ConnectionState.RECONNECTING, next(ConnectionState.CONNECTING, ConnectionEvent.CONNECT_FAILED))
    }

    @Test
    fun `auth failure returns to disconnected for re-auth`() {
        assertEquals(ConnectionState.DISCONNECTED, next(ConnectionState.CONNECTED, ConnectionEvent.AUTH_FAILED))
    }

    @Test
    fun `losing the network always wins from any state`() {
        ConnectionState.entries.forEach { state ->
            assertEquals(
                ConnectionState.OFFLINE_NO_NETWORK,
                next(state, ConnectionEvent.NETWORK_LOST),
            )
        }
    }

    @Test
    fun `network available only resumes from offline`() {
        assertEquals(ConnectionState.DISCONNECTED, next(ConnectionState.OFFLINE_NO_NETWORK, ConnectionEvent.NETWORK_AVAILABLE))
        assertNull(next(ConnectionState.READY, ConnectionEvent.NETWORK_AVAILABLE))
    }

    @Test
    fun `inapplicable events return null`() {
        assertNull(next(ConnectionState.DISCONNECTED, ConnectionEvent.AUTH_OK))
        assertNull(next(ConnectionState.READY, ConnectionEvent.READY_UP))
        assertNull(next(ConnectionState.BACKING_OFF, ConnectionEvent.SOCKET_OPENED))
        assertNull(next(ConnectionState.CONNECTING, ConnectionEvent.READY_UP))
    }
}
