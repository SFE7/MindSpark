package com.mindspark.compose.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvConfigTest {
    @Test
    fun buildsUrlsFromHostIp() {
        val e = EnvConfig("192.168.1.20")
        assertEquals("http://192.168.1.20:8080", e.apiUrl)
        assertEquals("http://192.168.1.20:8180/realms/mindspark", e.issuer)
        assertEquals("ws://192.168.1.20:8080/ws/rooms/r1?token=tok", e.wsUrl("r1", "tok"))
    }

    @Test
    fun rejectsEmptyLocalhostAndEmulatorAddress() {
        for (bad in listOf("", "localhost", "10.0.2.2")) {
            assertThrows(IllegalArgumentException::class.java) { EnvConfig(bad) }
        }
    }
}
