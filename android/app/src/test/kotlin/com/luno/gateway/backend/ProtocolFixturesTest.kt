package com.luno.gateway.backend

import com.luno.gateway.backend.protocol.DecodeResult
import com.luno.gateway.backend.protocol.ProtocolCodec
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The node half of the cross-language wire check. `@luno/protocol` runs the same
 * corpus through its TypeScript codec and asserts the same thing, so a backend
 * SDK and this node cannot drift apart on the wire format without one of the two
 * suites failing.
 */
class ProtocolFixturesTest {
    private val codec = ProtocolCodec()
    private val root = Json.parseToJsonElement(fixturesFile().readText()).jsonObject

    @Test
    fun `golden frames decode and re-encode byte-identically`() {
        val frames = root.getValue("frames").jsonArray
        assertTrue("fixture corpus is empty", frames.isNotEmpty())

        frames.forEach { entry ->
            val fixture = entry.jsonObject
            val name = fixture.getValue("name").jsonPrimitive.content
            val wire = fixture.getValue("envelope").jsonObject.toString()

            val decoded = codec.decode(wire)
            assertTrue("$name: expected Ok, got $decoded", decoded is DecodeResult.Ok)
            assertEquals(name, wire, codec.encode((decoded as DecodeResult.Ok).frame))
        }
    }

    @Test
    fun `frames from a newer peer are quarantined, not rejected`() {
        val frames = root.getValue("unsupported").jsonArray
        assertTrue("unsupported corpus is empty", frames.isNotEmpty())

        frames.forEach { entry ->
            val fixture = entry.jsonObject
            val name = fixture.getValue("name").jsonPrimitive.content
            val envelope = fixture.getValue("envelope").jsonObject

            val decoded = codec.decode(envelope.toString())
            assertTrue("$name: expected Unsupported, got $decoded", decoded is DecodeResult.Unsupported)
            assertEquals(
                name,
                envelope.getValue("id").jsonPrimitive.content,
                (decoded as DecodeResult.Unsupported).envelope.id,
            )
        }
    }

    private companion object {
        fun fixturesFile(): File {
            val relative = "packages/protocol/fixtures/frames.json"
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                val candidate = File(directory, relative)
                if (candidate.isFile) return candidate
                directory = directory.parentFile
            }
            throw IllegalStateException("$relative not found above ${File("").absolutePath}")
        }
    }
}
