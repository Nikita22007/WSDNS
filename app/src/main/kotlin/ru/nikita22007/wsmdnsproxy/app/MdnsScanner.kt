package ru.nikita22007.wsmdnsproxy.app

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import java.net.InetAddress

class MdnsScanner(val localIp: String, val onDeviceFound: (String) -> Unit, val onDeviceLost: (String) -> Unit) {
    private var jmdns: JmDNS? = null

    fun start() {
        jmdns = JmDNS.create(InetAddress.getByName(localIp))
        
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                // Когда сервис добавлен, нужно запросить детали
                jmdns?.requestServiceInfo(event.type, event.name)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                println("mDNS Service removed: ${event.name}")
                onDeviceLost(event.name)
            }

            override fun serviceResolved(event: ServiceEvent) {
                println("mDNS Service resolved: ${event.name} (${event.info.hostAddresses.firstOrNull()})")
                // Нам нужно только имя хоста (без .local)
                val cleanName = event.name.split(".").first().uppercase()
                onDeviceFound(cleanName)
            }
        }

        // Слушаем популярные типы сервисов, которые обычно есть на серверах
        jmdns?.addServiceListener("_http._tcp.local.", listener)
        jmdns?.addServiceListener("_smb._tcp.local.", listener)
        jmdns?.addServiceListener("_device-info._tcp.local.", listener)
        jmdns?.addServiceListener("_sftp-ssh._tcp.local.", listener)
        
        println("mDNS Scanner started on $localIp")
    }

    fun stop() {
        jmdns?.close()
    }
}
