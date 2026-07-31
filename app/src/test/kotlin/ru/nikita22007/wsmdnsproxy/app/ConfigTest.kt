package ru.nikita22007.wsmdnsproxy.app

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigTest {
    @Test
    fun `round trips grouping and IP family settings`() {
        val expected = ProxyConfig(
            groupServicesByHost = false,
            enableIpv4 = false,
            enableIpv6 = true
        )

        val encoded = Json.encodeToString(expected)
        assertEquals(expected, Json.decodeFromString<ProxyConfig>(encoded))
    }
}
