package ru.nikita22007.wsmdnsproxy.app

import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun resolveAddresses(target: String): List<String> {
        val matchingInterfaces = localInterfaces().filter {
            target.equals(it.name, ignoreCase = true) || target.equals(it.displayName, ignoreCase = true)
        }
        require(matchingInterfaces.size <= 1) { "Interface name '$target' is ambiguous" }
        matchingInterfaces.singleOrNull()?.let { networkInterface ->
            require(networkInterface.isUp) { "Interface '$target' is not up" }
            return usableAddresses(networkInterface).also {
                require(it.isNotEmpty()) { "Interface '$target' has no usable addresses" }
            }
        }

        val address = InetAddress.getByName(target)
        require(NetworkInterface.getByInetAddress(address) != null) { "Address $target is not assigned locally" }
        return listOf(address.hostAddress)
    }

    fun getLocalIps(): List<String> {
        val ips = mutableListOf<String>()
        for (intf in localInterfaces()) {
            if (intf.isLoopback || !intf.isUp) continue
            ips.addAll(usableAddresses(intf))
        }
        return ips
    }

    private fun usableAddresses(networkInterface: NetworkInterface): List<String> =
        networkInterface.inetAddresses.toList()
            .filterNot { it.isAnyLocalAddress || it.isMulticastAddress }
            .map(InetAddress::getHostAddress)

    private fun localInterfaces(): List<NetworkInterface> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
}
