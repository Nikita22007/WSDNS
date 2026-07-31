package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

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
}
