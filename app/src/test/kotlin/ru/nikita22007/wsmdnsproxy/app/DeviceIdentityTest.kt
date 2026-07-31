package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `keeps identical service instances on different ports separate`() {
        val alternatePort = smb.copy(port = 1445)
        assertNotEquals(deviceIdentity(smb, false), deviceIdentity(alternatePort, false))
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

    @Test
    fun `uses preferred service instance as display name`() {
        assertEquals("NAS SMB", serviceDisplayName(smb, debugMode = false))
        assertEquals("NAS SMB-KProxy", serviceDisplayName(smb, debugMode = true))
    }

    @Test
    fun `device remains until its last discovered service is removed`() {
        val catalog = ServiceCatalog()
        catalog.put(smb)
        catalog.put(http)
        val identity = deviceIdentity(smb, groupByHost = true)

        catalog.remove(smb.key)
        assertEquals(listOf(http), catalog.forIdentity(identity, groupByHost = true))

        catalog.remove(http.key)
        assertTrue(catalog.forIdentity(identity, groupByHost = true).isEmpty())
    }
}
