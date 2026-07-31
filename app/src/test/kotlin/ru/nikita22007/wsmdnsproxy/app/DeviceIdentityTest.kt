package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DeviceIdentityTest {
    private val smb = MdnsServiceInfo("NAS SMB", "nas.local", "_smb._tcp.local.", "192.0.2.1", 445)
    private val http = MdnsServiceInfo("NAS WEB", "nas.local", "_http._tcp.local.", "192.0.2.1", 80)

    @Test
    fun `groups different services from the same host by default`() {
        assertEquals(deviceIdentity(smb, true), deviceIdentity(http, true))
    }

    @Test
    fun `keeps service instances separate when grouping is disabled`() {
        assertNotEquals(deviceIdentity(smb, false), deviceIdentity(http, false))
    }
}
