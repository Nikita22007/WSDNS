package ru.nikita22007.wsmdnsproxy.app

import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

const val DEBUG_MODE = false
const val ISOLATED_MODE = true // true: видно только на этом ПК (127.0.0.1), false: видно всем в сети

fun main() = runBlocking {
    val externalIps = getLocalIps()
    if (externalIps.isEmpty()) {
        println("No active network interfaces found!")
        return@runBlocking
    }
    
    println("Active IPs found: ${externalIps.joinToString(", ")}")
    
    val responders = if (ISOLATED_MODE) {
        println("WSD Responder started in ISOLATED mode. Devices will be visible ONLY on this PC.")
        listOf(WsdResponder("127.0.0.1"))
    } else {
        println("WSD Responder started in PUBLIC mode. Devices will be visible to EVERYONE in the network.")
        externalIps.map { WsdResponder(it) }
    }
    
    // Храним маппинг: имя mDNS -> UUID виртуального устройства
    val activeDevices = ConcurrentHashMap<String, String>()

    val scanners = externalIps.map { ip ->
        MdnsScanner(ip, 
            onDeviceFound = { name ->
                val displayName = if (DEBUG_MODE) "$name-KProxy" else name
                val newDevice = WsdDevice(name = displayName, realHostname = name)
                
                // putIfAbsent вернет null, если ключа не было (т.е. устройство новое)
                if (activeDevices.putIfAbsent(name, newDevice.uuid) == null) {
                    println(">>> Proxying new mDNS device: $name as $displayName")
                    responders.forEach { it.addDevice(newDevice) }
                }
            },
            onDeviceLost = { name ->
                val uuid = activeDevices.remove(name)
                if (uuid != null) {
                    println("<<< Removing proxied device: $name")
                    responders.forEach { it.removeDevice(uuid) }
                }
            }
        )
    }

    responders.forEach { it.start() }
    scanners.forEach { it.start() }

    println("mDNS to WS-Discovery Proxy is fully operational.")
    
    while(true) {
        kotlinx.coroutines.delay(1000)
    }
}

fun getLocalIps(): List<String> {
    val ips = mutableListOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces()
    if (interfaces != null) {
        for (intf in interfaces) {
            if (intf.isLoopback || !intf.isUp) continue
            
            for (addr in intf.inetAddresses) {
                if (addr is Inet4Address) {
                    ips.add(addr.hostAddress)
                }
            }
        }
    }
    return ips
}

