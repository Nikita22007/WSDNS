package ru.nikita22007.wsmdnsproxy.app

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun resolveAddresses(target: String, enableIpv4: Boolean, enableIpv6: Boolean): List<String> {
        val matchingInterfaces = localInterfaces().filter {
            target.equals(it.name, ignoreCase = true) || target.equals(it.displayName, ignoreCase = true)
        }
        require(matchingInterfaces.size <= 1) { "Interface name '$target' is ambiguous" }
        matchingInterfaces.singleOrNull()?.let { networkInterface ->
            require(networkInterface.isUp) { "Interface '$target' is not up" }
            return usableAddresses(networkInterface, enableIpv4, enableIpv6).also {
                require(it.isNotEmpty()) { "Interface '$target' has no usable addresses" }
            }
        }

        val address = InetAddress.getByName(target)
        require(NetworkInterface.getByInetAddress(address) != null) { "Address $target is not assigned locally" }
        require(address.isFamilyEnabled(enableIpv4, enableIpv6)) {
            "Address family for $target is disabled in config"
        }
        return listOf(address.hostAddress)
    }

    fun getLocalIps(enableIpv4: Boolean = true, enableIpv6: Boolean = true): List<String> {
        val ips = mutableListOf<String>()
        for (intf in localInterfaces()) {
            if (intf.isLoopback || !intf.isUp) continue
            ips.addAll(usableAddresses(intf, enableIpv4, enableIpv6))
        }
        return ips
    }

    private fun usableAddresses(
        networkInterface: NetworkInterface,
        enableIpv4: Boolean,
        enableIpv6: Boolean
    ): List<String> =
        networkInterface.inetAddresses.toList()
            .filterNot { it.isAnyLocalAddress || it.isMulticastAddress }
            .filter { it.isFamilyEnabled(enableIpv4, enableIpv6) }
            .map(InetAddress::getHostAddress)

    private fun localInterfaces(): List<NetworkInterface> =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
}

internal fun InetAddress.isFamilyEnabled(enableIpv4: Boolean, enableIpv6: Boolean): Boolean =
    (enableIpv4 && this is Inet4Address) || (enableIpv6 && this is Inet6Address)
