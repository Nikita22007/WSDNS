package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CliParserTest {
    @Test
    fun `pairs each publish interface with its own optional port`() {
        val parsed = CliParser.parse(
            arrayOf("-l", "eth0", "-p", "eth0", "5357", "/p", "Wi-Fi", "-i", "fe80::1%3", "5359"),
            defaultIsolated = false
        )

        assertEquals(listOf("eth0"), parsed.listenInterfaces)
        assertEquals(
            listOf(
                InterfaceRequest("eth0", 5357),
                InterfaceRequest("Wi-Fi", 0),
                InterfaceRequest("fe80::1%3", 5359)
            ),
            parsed.publishInterfaces
        )
    }
}
