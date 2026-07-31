package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.net.Inet6Address
import java.net.InetAddress

class HttpUtilsTest {
    @Test
    fun `reads UTF-8 body up to configured limit`() {
        val value = "Привет, WSD"
        val input = ByteArrayInputStream(value.toByteArray(StandardCharsets.UTF_8))
        assertEquals(value, input.readUtf8Limited(64))
    }

    @Test
    fun `rejects body larger than configured limit`() {
        val input = ByteArrayInputStream(ByteArray(65))
        assertThrows(PayloadTooLargeException::class.java) { input.readUtf8Limited(64) }
    }

    @Test
    fun `formats IPv4 host without brackets`() {
        assertEquals("192.0.2.1", InetAddress.getByName("192.0.2.1").httpHost())
    }

    @Test
    fun `formats scoped IPv6 host for use in URI`() {
        val address = Inet6Address.getByAddress(null, byteArrayOf(
            0xFE.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1
        ), 3)
        val host = address.httpHost()

        assertEquals(true, host.startsWith("[") && host.endsWith("]"))
        assertEquals(true, host.contains("%253"))
    }
}
