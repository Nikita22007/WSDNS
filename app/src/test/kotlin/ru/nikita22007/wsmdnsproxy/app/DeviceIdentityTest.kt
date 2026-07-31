package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DeviceIdentityTest {
    private val smb = MdnsServiceInfo(MdnsServiceKey("192.0.2.2", "smb", "nas smb"), "NAS SMB", "nas.local", "_smb._tcp.local.", "192.0.2.1", 445)
    private val http = MdnsServiceInfo(MdnsServiceKey("192.0.2.2", "http", "nas web"), "NAS WEB", "nas.local", "_http._tcp.local.", "192.0.2.1", 80)

    @Test
    fun `groups different services from the same host by default`() {
        assertEquals(deviceIdentity(smb, true), deviceIdentity(http, true))
    }

    @Test
    fun `keeps service instances separate when grouping is disabled`() {
        assertNotEquals(deviceIdentity(smb, false), deviceIdentity(http, false))
    }

    @Test
    fun `falls back to remaining service when preferred service disappears`() {
        val mappings = listOf(
            ServiceMapping("_smb._tcp.local.", "Computers", priority = 100),
            ServiceMapping("_http._tcp.local.", "NetworkInfrastructure", priority = 50)
        )

        assertEquals(smb, selectPreferredService(listOf(smb, http), mappings)?.first)
        assertEquals(http, selectPreferredService(listOf(http), mappings)?.first)
    }
}
