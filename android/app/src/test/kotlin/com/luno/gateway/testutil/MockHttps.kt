package com.luno.gateway.testutil

import java.net.InetAddress
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * A MockWebServer speaking https:// (which [RestClient] hard-requires) plus an
 * OkHttpClient that trusts its self-signed cert. Call [close] when done.
 */
class MockHttps {
    val server = MockWebServer()
    val client: OkHttpClient

    init {
        val localhost = InetAddress.getByName("localhost").canonicalHostName
        val cert = HeldCertificate.Builder().addSubjectAlternativeName(localhost).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(cert).build()
        server.useHttps(serverCerts.sslSocketFactory(), false)

        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .build()
    }

    fun url(): String = server.url("/").toString().trimEnd('/')

    fun close() = server.shutdown()
}
