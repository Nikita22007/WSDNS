package ru.nikita22007.wsmdnsproxy.app

import java.util.concurrent.ConcurrentHashMap
import java.lang.Thread.sleep

class ProxyOrchestrator(private val config: ProxyConfig, private val interfaces: List<InterfaceRequest>) {
    private val activeDevices = ConcurrentHashMap<String, String>()
    private lateinit var responders: List<WsdResponder>
    private lateinit var scanners: List<MdnsScanner>

    fun run() {
        val isolated = config.isolatedMode
        println("Running in ${if (isolated) "ISOLATED" else "PUBLIC"} mode.")
        
        // 1. Инициализируем респондеры
        responders = if (isolated) {
            println("WSD Responder started in ISOLATED mode.")
            listOf(WsdResponder("127.0.0.1", interfaces.firstOrNull()?.port ?: 0))
        } else {
            interfaces.map { WsdResponder(it.ip, it.port) }
        }

        val mDNSTypes = config.mappings.map { it.mdnsType }.distinct()

        // 2. Инициализируем сканеры
        val externalIps = NetworkUtils.getLocalIps()
        scanners = externalIps.map { ip ->
            MdnsScanner(ip, mDNSTypes) { info -> handleDiscoveredService(info) }
        }

        responders.forEach { it.start() }
        scanners.forEach { it.start() }

        println("mDNS-WSD Proxy is fully operational.")
        while (true) { sleep(1000) }
    }

    private fun handleDiscoveredService(info: MdnsServiceInfo) {
        val name = info.name
        val displayName = if (config.debugMode) "$name-KProxy" else name
        
        // Генерируем стабильный UUID на основе реального имени хоста
        val deviceUuid = java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString()
        
        val mapping = config.mappings
            .filter { it.mdnsType == info.type }
            .maxByOrNull { it.priority } ?: return

        val existingUuid = activeDevices[name]
        val device = if (existingUuid != null) {
            responders.first().getDevice(existingUuid) ?: WsdDevice(uuid = deviceUuid, name = displayName, realHostname = name)
        } else {
            WsdDevice(uuid = deviceUuid, name = displayName, realHostname = name)
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
            println(">>> New device discovered: $name (${device.category})")
            activeDevices[name] = device.uuid
            responders.forEach { it.addDevice(device) }
        } else if (updated) {
            println(">>> Updating metadata for device: $name")
            responders.forEach { 
                it.removeDevice(device.uuid)
                it.addDevice(device)
            }
        }
    }
}
