package com.luno.gateway.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PinningTest {
    @Test
    fun `no pinner when pins are empty`() {
        assertNull(Pinning.buildPinner("luno.example.com", emptyList()))
    }

    @Test
    fun `no pinner when host is blank`() {
        assertNull(Pinning.buildPinner("", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
    }

    @Test
    fun `builds a pinner when host and pins are present`() {
        val pinner = Pinning.buildPinner(
            "luno.example.com",
            listOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            ),
        )
        assertNotNull(pinner)
    }
}
