package ru.nikita22007.wsmdnsproxy.app

import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun resolveAddresses(target: String): List<String> {
        val networkInterface = NetworkInterface.getByName(target)
        if (networkInterface != null) return usableAddresses(networkInterface)

        val address = InetAddress.getByName(target)
        require(NetworkInterface.getByInetAddress(address) != null) { "Address $target is not assigned locally" }
        return listOf(address.hostAddress)
    }

    fun getLocalIps(): List<String> {
        val ips = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (intf in interfaces) {
            if (intf.isLoopback || !intf.isUp) continue
            ips.addAll(usableAddresses(intf))
        }
        return ips
    }

    private fun usableAddresses(networkInterface: NetworkInterface): List<String> =
        networkInterface.inetAddresses.toList()
            .filterNot { it.isAnyLocalAddress || it.isMulticastAddress }
            .map(InetAddress::getHostAddress)
}
