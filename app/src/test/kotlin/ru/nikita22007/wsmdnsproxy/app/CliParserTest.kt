package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CliParserTest {
    @Test
    fun `pairs each publish interface with its own optional port`() {
        val parsed = CliParser.parse(
            arrayOf("-l", "eth0", "-p", "eth0", "5357", "/p", "Wi-Fi", "-i", "fe80::1%3", "5359")
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

    @Test
    fun `supports native short and long aliases`() {
        val parsed = CliParser.parse(arrayOf("--listen-interface", "eth0", "/listen", "eth1", "--publish-interface", "eth0", "/publish", "eth1", "5358"))

        assertEquals(listOf("eth0", "eth1"), parsed.listenInterfaces)
        assertEquals(listOf(InterfaceRequest("eth0"), InterfaceRequest("eth1", 5358)), parsed.publishInterfaces)
    }

    @Test
    fun `rejects missing option values`() {
        assertThrows(CliUsageException::class.java) { CliParser.parse(arrayOf("-l", "--public")) }
        assertThrows(CliUsageException::class.java) { CliParser.parse(arrayOf("-p")) }
    }

    @Test
    fun `rejects unknown options and invalid ports`() {
        assertThrows(CliUsageException::class.java) { CliParser.parse(arrayOf("--wat")) }
        assertThrows(CliUsageException::class.java) { CliParser.parse(arrayOf("-p", "eth0", "70000")) }
        assertThrows(CliUsageException::class.java) { CliParser.parse(arrayOf("-p", "eth0", "not-a-port")) }
    }
}
