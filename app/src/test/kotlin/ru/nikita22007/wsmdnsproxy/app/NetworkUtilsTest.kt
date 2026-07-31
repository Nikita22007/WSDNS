package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetAddress

class NetworkUtilsTest {
    @Test
    fun `filters addresses by enabled IP families`() {
        val ipv4 = InetAddress.getByName("192.0.2.1")
        val ipv6 = InetAddress.getByName("2001:db8::1")

        assertTrue(ipv4.isFamilyEnabled(enableIpv4 = true, enableIpv6 = false))
        assertFalse(ipv6.isFamilyEnabled(enableIpv4 = true, enableIpv6 = false))
        assertFalse(ipv4.isFamilyEnabled(enableIpv4 = false, enableIpv6 = true))
        assertTrue(ipv6.isFamilyEnabled(enableIpv4 = false, enableIpv6 = true))
    }

    @Test
    fun `config requires at least one IP family`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProxyConfig(enableIpv4 = false, enableIpv6 = false).validate()
        }
    }
}
