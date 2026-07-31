package ru.nikita22007.wsmdnsproxy.app

import java.util.concurrent.ConcurrentHashMap
import java.lang.Thread.sleep
import java.util.concurrent.atomic.AtomicBoolean

class ProxyOrchestrator(
    private val config: ProxyConfig,
    private val listenInterfaces: List<String>,
    private val publishInterfaces: List<InterfaceRequest>
) {
    private val activeDevices = ConcurrentHashMap<String, String>()
    private val activeServices = ConcurrentHashMap<MdnsServiceKey, MdnsServiceInfo>()
    private lateinit var responders: List<WsdResponder>
    private lateinit var scanners: List<MdnsScanner>
    private val stopped = AtomicBoolean(false)

    fun run() {
        val isolated = config.isolatedMode
        println("Running in ${if (isolated) "ISOLATED" else "PUBLIC"} mode.")
        
        // 1. Инициализируем респондеры
        responders = if (isolated) {
            println("WSD Responder started in ISOLATED mode.")
            listOf(WsdResponder("127.0.0.1", publishInterfaces.firstOrNull()?.port ?: 0))
        } else {
            publishInterfaces.flatMap { request ->
                NetworkUtils.resolveAddresses(request.target).map { WsdResponder(it, request.port) }
            }
        }

        val mDNSTypes = config.mappings.map { it.mdnsType }.distinct()

        // 2. Инициализируем сканеры
        val externalIps = if (listenInterfaces.isEmpty()) {
            NetworkUtils.getLocalIps()
        } else {
            listenInterfaces.flatMap(NetworkUtils::resolveAddresses).distinct()
        }
        scanners = externalIps.map { ip ->
            MdnsScanner(
                ip,
                mDNSTypes,
                onServiceFound = ::handleDiscoveredService,
                onServiceRemoved = ::handleRemovedService
            )
        }

        responders.forEach { it.start() }
        scanners.forEach { it.start() }

        println("mDNS-WSD Proxy is fully operational.")
        val shutdownHook = Thread({ stop() }, "wsdns-shutdown")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            while (!stopped.get()) sleep(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            stop()
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (_: IllegalStateException) {
                // JVM shutdown is already in progress.
            }
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        if (::responders.isInitialized) responders.forEach { it.stop() }
        if (::scanners.isInitialized) scanners.forEach { it.stop() }
        println("mDNS-WSD Proxy stopped.")
    }

    @Synchronized
    private fun handleDiscoveredService(info: MdnsServiceInfo) {
        val previous = activeServices.put(info.key, info)
        previous?.let { old ->
            val oldIdentity = deviceIdentity(old, config.groupServicesByHost)
            val newIdentity = deviceIdentity(info, config.groupServicesByHost)
            if (oldIdentity != newIdentity) reconcileDevice(oldIdentity)
        }
        reconcileDevice(deviceIdentity(info, config.groupServicesByHost))
    }

    @Synchronized
    private fun handleRemovedService(key: MdnsServiceKey) {
        val removed = activeServices.remove(key) ?: return
        reconcileDevice(deviceIdentity(removed, config.groupServicesByHost))
    }

    private fun reconcileDevice(identity: String) {
        val services = activeServices.values.filter {
            deviceIdentity(it, config.groupServicesByHost) == identity
        }
        val existingUuid = activeDevices[identity]

        if (services.isEmpty()) {
            if (existingUuid != null) {
                activeDevices.remove(identity)
                responders.forEach { it.removeDevice(existingUuid) }
                println(">>> Device disappeared: $identity")
            }
            return
        }

        val (service, mapping) = selectPreferredService(services, config.mappings) ?: return

        val baseDisplayName = if (config.groupServicesByHost) service.hostname.uppercase() else service.name
        val displayName = if (config.debugMode) "$baseDisplayName-KProxy" else baseDisplayName
        val uuid = java.util.UUID.nameUUIDFromBytes(identity.toByteArray()).toString()
        val presentationUrl = mapping.presentationUrlTemplate
            ?.replace("{ip}", service.ip)
            ?.replace("{port}", service.port.toString())
            ?.replace("{name}", service.name)

        val existing = existingUuid?.let { responders.first().getDevice(it) }
        val device = existing ?: WsdDevice(uuid, displayName, service.hostname)
        val updated = device.category != mapping.wsdCategory || device.presentationUrl != presentationUrl
        device.category = mapping.wsdCategory
        device.presentationUrl = presentationUrl

        if (existingUuid == null) {
            activeDevices[identity] = uuid
            responders.forEach { it.addDevice(device) }
            println(">>> New device discovered: $displayName (${device.category})")
        } else if (updated) {
            responders.forEach { it.addDevice(device) }
            println(">>> Updating metadata for device: $displayName")
        }
    }
}

internal fun deviceIdentity(info: MdnsServiceInfo, groupByHost: Boolean): String =
    if (groupByHost) {
        "host:${info.hostname.lowercase()}"
    } else {
        "service:${info.hostname.lowercase()}|${info.type.lowercase()}|${info.name.lowercase()}"
    }

internal fun selectPreferredService(
    services: Collection<MdnsServiceInfo>,
    mappings: Collection<ServiceMapping>
): Pair<MdnsServiceInfo, ServiceMapping>? = services.mapNotNull { service ->
    mappings
        .filter { it.mdnsType == service.type }
        .maxByOrNull { it.priority }
        ?.let { service to it }
}.maxByOrNull { it.second.priority }
