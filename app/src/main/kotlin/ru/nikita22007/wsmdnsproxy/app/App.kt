package ru.nikita22007.wsmdnsproxy.app

import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

data class InterfaceRequest(val ip: String, val port: Int = 0)

fun main(args: Array<String>) = runBlocking {
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    
    // Default values
    var isolatedMode = isWindows // Windows defaults to Isolated
    val requestedInterfaces = mutableListOf<InterfaceRequest>()
    var showHelp = false

    // Custom CLI Parser
    var i = 0
    while (i < args.size) {
        val arg = args[i].lowercase()
        when {
            arg in listOf("-h", "--help", "/h", "/help", "-?", "/?") -> showHelp = true
            arg in listOf("--public", "/public") -> isolatedMode = false
            arg in listOf("-i", "--interface", "/i", "/interface") -> {
                if (i + 1 < args.size) {
                    val value = args[++i]
                    val parts = value.split(":")
                    val ip = parts[0]
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    requestedInterfaces.add(InterfaceRequest(ip, port))
                }
            }
        }
        i++
    }

    if (showHelp) {
        printHelp()
        return@runBlocking
    }

    // 1. Загружаем конфиг (он может переопределить isolatedMode, если явно не задан флаг)
    val config = ConfigManager.loadConfig()
    
    // Если флаг --public не был передан, берем из конфига, иначе оставляем результат парсинга
    val finalIsolatedMode = if (args.any { it.contains("public") }) false else config.isolatedMode

    // 2. Определяем интерфейсы
    val interfacesToUse = if (requestedInterfaces.isNotEmpty()) {
        requestedInterfaces
    } else {
        getLocalIps().map { InterfaceRequest(it) }
    }

    if (interfacesToUse.isEmpty()) {
        println("Error: No active network interfaces found and none specified.")
        return@runBlocking
    }

    println("Running in ${if (finalIsolatedMode) "ISOLATED" else "PUBLIC"} mode.")
    println("Active interfaces: ${interfacesToUse.joinToString { "${it.ip}${if (it.port > 0) ":${it.port}" else ""}" }}")

    // 3. Инициализируем респондеры
    val responders = if (finalIsolatedMode) {
        listOf(WsdResponder("127.0.0.1", requestedInterfaces.firstOrNull()?.port ?: 0))
    } else {
        interfacesToUse.map { WsdResponder(it.ip, it.port) }
    }
    
    val activeDevices = ConcurrentHashMap<String, String>()
    val mDNSTypes = config.mappings.map { it.mdnsType }.distinct()

    // 4. Запускаем сканеры (всегда на реальных интерфейсах)
    val scanners = getLocalIps().map { ip ->
        MdnsScanner(ip, mDNSTypes, 
            onServiceFound = { info ->
                val name = info.name
                val displayName = if (config.debugMode) "$name-KProxy" else name
                
                val mapping = config.mappings
                    .filter { it.mdnsType == info.type }
                    .maxByOrNull { it.priority } ?: return@MdnsScanner

                val existingUuid = activeDevices[name]
                val device = if (existingUuid != null) {
                    responders.first().getDevice(existingUuid) ?: WsdDevice(name = displayName, realHostname = name)
                } else {
                    WsdDevice(name = displayName, realHostname = name)
                }

                var updated = false
                val currentPriority = config.mappings
                    .filter { it.wsdCategory == device.category }
                    .maxByOrNull { it.priority }?.priority ?: -1

                if (mapping.priority >= currentPriority) {
                    if (device.category != mapping.wsdCategory) {
                        device.category = mapping.wsdCategory
                        updated = true
                    }
                    val newUrl = mapping.presentationUrlTemplate
                        ?.replace("{ip}", info.ip)
                        ?.replace("{port}", info.port.toString())
                        ?.replace("{name}", name)

                    if (device.presentationUrl != newUrl) {
                        device.presentationUrl = newUrl
                        updated = true
                    }
                }

                if (existingUuid == null) {
                    println(">>> New device: $name (${device.category})")
                    activeDevices[name] = device.uuid
                    responders.forEach { it.addDevice(device) }
                } else if (updated) {
                    println(">>> Updated device: $name")
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

    println("mDNS-WSD Proxy is fully operational.")
    while(true) { kotlinx.coroutines.delay(1000) }
}

fun printHelp() {
    println("""
        WS-mDNS-Proxy - Bridge mDNS services to Windows Network Explorer
        
        Usage:
          ws-mdns-proxy [options]
        
        Options:
          /public, --public         Enable PUBLIC mode (visible to all network). 
                                    Default: Isolated on Windows, Public on others.
          /i, -i, --interface IP[:PORT] 
                                    Bind to specific interface. Disables auto-lookup.
                                    Port is optional (random if omitted).
          /h, --help, /?            Show this help and configuration guide.

        Configuration (config.json):
          The mapping rules are defined in 'config.json'.
          - mdnsType: The mDNS service type to listen for (e.g. _smb._tcp.local.)
          - wsdCategory: Windows PnP-X Category:
              * Computers: Traditional file server (opens SMB on double-click)
              * NetworkInfrastructure: Router/Switch icon (often opens URL)
              * Storage.NAS: Network storage icon
              * Other: Neutral device icon
          - presentationUrlTemplate: Template for the web UI link. 
              Available variables: {ip}, {port}, {name}.
          - priority: Higher priority wins when a device has multiple services.
    """.trimIndent())
}

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
