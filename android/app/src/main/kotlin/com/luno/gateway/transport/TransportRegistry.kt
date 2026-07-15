package com.luno.gateway.transport

class TransportRegistry {
    private val transports = LinkedHashMap<TransportId, Transport>()

    fun register(transport: Transport) {
        transports[transport.id] = transport
    }

    fun get(id: TransportId): Transport? = transports[id]

    fun all(): List<Transport> = transports.values.toList()

    fun sending(): List<Transport> =
        transports.values.filter { TransportCapability.SEND in it.capabilities }
}
