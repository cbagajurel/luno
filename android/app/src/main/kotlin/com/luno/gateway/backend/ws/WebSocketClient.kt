package com.luno.gateway.backend.ws

import com.luno.gateway.logging.LunoLogger
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface SocketEvent {
    data object Open : SocketEvent

    data class Message(val text: String) : SocketEvent

    data class Closed(val code: Int, val reason: String) : SocketEvent

    /** [httpCode] is the upgrade response code when the handshake was rejected (e.g. 401). */
    data class Failure(val httpCode: Int?, val error: Throwable?) : SocketEvent
}

/** The socket surface [ConnectionManager] drives; real impl is [WebSocketClient]. */
interface Socket {
    fun connect(wsUrl: String, credential: String, onEvent: (SocketEvent) -> Unit)

    fun send(text: String): Boolean

    fun close()
}

/**
 * A single OkHttp WebSocket to the backend. Refuses anything but `wss://` — the
 * credential rides in the Authorization header and must never cross the wire in
 * the clear (§8, threat model) — unless [allowInsecure] is set, which the app only
 * does in debug builds for LAN pairing. Owns no reconnect logic; that's
 * [ConnectionManager].
 */
class WebSocketClient(
    baseClient: OkHttpClient = OkHttpClient(),
    private val logger: LunoLogger,
    pinner: CertificatePinner? = null,
    private val allowInsecure: Boolean = false,
) : Socket {
    // Off by default (no pins configured yet); when the backend supplies pins the
    // socket enforces them on top of the usual CA validation.
    private val client: OkHttpClient =
        if (pinner != null) baseClient.newBuilder().certificatePinner(pinner).build() else baseClient

    private var webSocket: WebSocket? = null

    override fun connect(wsUrl: String, credential: String, onEvent: (SocketEvent) -> Unit) {
        val secure = wsUrl.startsWith("wss://")
        if (!secure && !(allowInsecure && wsUrl.startsWith("ws://"))) {
            onEvent(SocketEvent.Failure(null, IllegalArgumentException("WSS required, refused $wsUrl")))
            return
        }
        val request =
            Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer $credential")
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = onEvent(SocketEvent.Open)

                    override fun onMessage(webSocket: WebSocket, text: String) = onEvent(SocketEvent.Message(text))

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(NORMAL_CLOSURE, null)
                        onEvent(SocketEvent.Closed(code, reason))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                        onEvent(SocketEvent.Failure(response?.code, t))
                },
            )
    }

    override fun send(text: String): Boolean = webSocket?.send(text) ?: false

    override fun close() {
        webSocket?.close(NORMAL_CLOSURE, "client closing")
        webSocket = null
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000
    }
}
