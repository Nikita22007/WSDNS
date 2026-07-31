package ru.nikita22007.wsmdnsproxy.app

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import java.net.InetAddress

data class MdnsServiceInfo(
    val name: String,
    val hostname: String,
    val type: String,
    val ip: String,
    val port: Int
)

class MdnsScanner(val localIp: String, val serviceTypes: List<String>, val onServiceFound: (MdnsServiceInfo) -> Unit) {
    private var jmdns: JmDNS? = null

    fun start() {
        jmdns = JmDNS.create(InetAddress.getByName(localIp))
        
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns?.requestServiceInfo(event.type, event.name)
            }

            override fun serviceRemoved(event: ServiceEvent) {}

            override fun serviceResolved(event: ServiceEvent) {
                val ip = event.info.hostAddresses.firstOrNull() ?: return
                val cleanName = event.name.split(".").first().split("@").last().uppercase()
                val hostname = event.info.server
                    .removeSuffix(".")
                    .removeSuffix(".local")
                    .ifBlank { cleanName }
                
                onServiceFound(MdnsServiceInfo(
                    name = cleanName,
                    hostname = hostname,
                    type = event.type,
                    ip = ip,
                    port = event.info.port
                ))
            }
        }

        serviceTypes.forEach { type ->
            jmdns?.addServiceListener(type, listener)
        }
        
        println("mDNS Scanner started on $localIp (listening ${serviceTypes.size} types)")
    }

    fun stop() { jmdns?.close() }
}
