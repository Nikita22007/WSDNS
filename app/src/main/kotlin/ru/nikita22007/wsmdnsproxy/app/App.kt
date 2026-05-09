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
            onServiceFound = { info ->
                val name = info.name
                val displayName = if (DEBUG_MODE) "$name-KProxy" else name
                
                // Ищем существующее устройство или создаем новое
                val existingUuid = activeDevices[name]
                val device = if (existingUuid != null) {
                    responders.first().getDevice(existingUuid) ?: WsdDevice(name = displayName, realHostname = name)
                } else {
                    WsdDevice(name = displayName, realHostname = name)
                }

                var updated = false

                // Умный маппинг типов
                if (info.type.contains("_smb._tcp")) {
                    if (device.category != "Computers") {
                        device.category = "Computers"
                        updated = true
                    }
                } else if (info.type.contains("_http._tcp") && device.category != "Computers") {
                    // Если это HTTP и мы еще не решили, что это файловый сервер
                    if (device.category != "NetworkInfrastructure") {
                        device.category = "NetworkInfrastructure"
                        device.presentationUrl = "http://${info.ip}:${info.port}"
                        updated = true
                    }
                } else if (info.type.contains("_sftp-ssh._tcp") && device.category != "Computers") {
                    if (device.category != "Storage.NAS") {
                        device.category = "Storage.NAS"
                        device.presentationUrl = "sftp://${info.ip}:${info.port}"
                        updated = true
                    }
                }

                if (existingUuid == null) {
                    println(">>> Proxying new mDNS device: $name (Type: ${info.type}, Category: ${device.category})")
                    activeDevices[name] = device.uuid
                    responders.forEach { it.addDevice(device) }
                } else if (updated) {
                    println(">>> Updating device: $name (New Category: ${device.category}, URL: ${device.presentationUrl})")
                    // Для обновления WSD требует отправить BYE и снова HELLO.
                    // Упростим: просто удалим и добавим.
                    responders.forEach { 
                        it.removeDevice(device.uuid)
                        it.addDevice(device)
                    }
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

