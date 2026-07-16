package com.luno.gateway.agent

enum class ConnectionState {
    OFFLINE_NO_NETWORK,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    READY,
    RECONNECTING,
    BACKING_OFF,
}

enum class ConnectionEvent {
    NETWORK_AVAILABLE,
    NETWORK_LOST,
    CONNECT,
    SOCKET_OPENED,
    CONNECT_FAILED,
    AUTH_OK,
    AUTH_FAILED,
    READY_UP,
    SOCKET_DROPPED,
    HEARTBEAT_MISSED,
    BACKOFF_SCHEDULED,
    BACKOFF_ELAPSED,
}

/**
 * The connection lifecycle (§6) as a pure transition table. Losing the network
 * always wins from any state; everything else follows the diagram. Backoff
 * timing and the "reset only after a stable READY" rule live in
 * [com.luno.gateway.backend.ws.ReconnectPolicy], not here.
 */
object ConnectionStateMachine {
    /** The next state for [event] in [state], or null if the event doesn't apply. */
    fun transition(state: ConnectionState, event: ConnectionEvent): ConnectionState? {
        if (event == ConnectionEvent.NETWORK_LOST) return ConnectionState.OFFLINE_NO_NETWORK

        return when (state) {
            ConnectionState.OFFLINE_NO_NETWORK ->
                when (event) {
                    ConnectionEvent.NETWORK_AVAILABLE -> ConnectionState.DISCONNECTED
                    else -> null
                }

            ConnectionState.DISCONNECTED ->
                when (event) {
                    ConnectionEvent.CONNECT -> ConnectionState.CONNECTING
                    else -> null
                }

            ConnectionState.CONNECTING ->
                when (event) {
                    ConnectionEvent.SOCKET_OPENED -> ConnectionState.CONNECTED
                    ConnectionEvent.CONNECT_FAILED -> ConnectionState.RECONNECTING
                    else -> null
                }

            ConnectionState.CONNECTED ->
                when (event) {
                    ConnectionEvent.AUTH_OK -> ConnectionState.AUTHENTICATED
                    ConnectionEvent.AUTH_FAILED -> ConnectionState.DISCONNECTED
                    ConnectionEvent.SOCKET_DROPPED -> ConnectionState.RECONNECTING
                    else -> null
                }

            ConnectionState.AUTHENTICATED ->
                when (event) {
                    ConnectionEvent.READY_UP -> ConnectionState.READY
                    ConnectionEvent.SOCKET_DROPPED,
                    ConnectionEvent.HEARTBEAT_MISSED,
                    -> ConnectionState.RECONNECTING
                    else -> null
                }

            ConnectionState.READY ->
                when (event) {
                    ConnectionEvent.SOCKET_DROPPED,
                    ConnectionEvent.HEARTBEAT_MISSED,
                    -> ConnectionState.RECONNECTING
                    else -> null
                }

            ConnectionState.RECONNECTING ->
                when (event) {
                    ConnectionEvent.BACKOFF_SCHEDULED -> ConnectionState.BACKING_OFF
                    else -> null
                }

            ConnectionState.BACKING_OFF ->
                when (event) {
                    ConnectionEvent.BACKOFF_ELAPSED -> ConnectionState.CONNECTING
                    else -> null
                }
        }
    }
}
