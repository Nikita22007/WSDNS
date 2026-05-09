package ru.nikita22007.wsmdnsproxy.app

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIps(): List<String> {
        val ips = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (intf in interfaces) {
            if (intf.isLoopback || !intf.isUp) continue
            for (addr in intf.inetAddresses) {
                if (addr is Inet4Address) ips.add(addr.hostAddress)
            }
        }
        return ips
    }
}
