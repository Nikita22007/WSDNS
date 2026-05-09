package ru.nikita22007.wsmdnsproxy.app

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import java.net.*
import java.util.*
import kotlin.concurrent.thread

class WsdResponder(val localIp: String) {
    private val devices = mutableMapOf<String, WsdDevice>()
    private val instanceId = System.currentTimeMillis() / 1000
    private val sequenceId = "urn:uuid:${UUID.randomUUID()}"
    private var messageCount = 1
    private var httpPort = 0

    private val multicastAddr = InetAddress.getByName("239.255.255.250")
    private val wsdPort = 3702

    private val envelopeHeader = """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:wsdp="http://schemas.xmlsoap.org/ws/2006/02/devprof" xmlns:pub="http://schemas.microsoft.com/windows/pub/2005/07" xmlns:pnpx="http://schemas.microsoft.com/windows/pnpx/2005/10">"""

    fun addDevice(device: WsdDevice) {
        devices[device.uuid] = device
        thread {
            repeat(3) {
                sendHello(device)
                Thread.sleep(200)
            }
        }
    }

    fun removeDevice(uuid: String) {
        devices.remove(uuid)
    }

    fun getDevice(uuid: String): WsdDevice? {
        return devices[uuid]
    }

    suspend fun start() {
        startHttpServer()
        startUdpListener()
    }

    private suspend fun startHttpServer() {
        val server = embeddedServer(Netty, port = 0) {
            routing {
                route("/{uuid}") {
                    get { handleMetadataRequest(call) }
                    post { handleMetadataRequest(call) }
                }
            }
        }
        server.start(wait = false)
        httpPort = server.resolvedConnectors().first().port
        println("WSD HTTP Server started on port $httpPort")
    }

    private suspend fun handleMetadataRequest(call: ApplicationCall) {
        val uuid = call.parameters["uuid"]
        val device = devices[uuid]
        if (device == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val body = runCatching { call.receiveText() }.getOrDefault("")
        val messageIdMatch = Regex("<(?:.*?:)?MessageID>(.*?)</(?:.*?:)?MessageID>").find(body)
        val messageId = messageIdMatch?.groupValues?.get(1) ?: ""
        
        println(">>> Metadata request (HTTP ${call.request.httpMethod.value}) for ${device.name}. RelatesTo: $messageId")
        
        val response = generateMetadataXml(device, messageId)
        call.respondBytes(response.toByteArray(), ContentType.parse("application/soap+xml"))
    }

    private fun startUdpListener() {
        thread {
            try {
                val socket = MulticastSocket(wsdPort)
                socket.networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName(localIp))
                socket.joinGroup(multicastAddr)

                val buffer = ByteArray(8192)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)

                    if (message.contains("/discovery/Probe")) {
                        // Извлекаем MessageID надежно (префикс может быть a: или wsa:)
                        val messageIdMatch = Regex("<(?:.*?:)?MessageID>(.*?)</(?:.*?:)?MessageID>").find(message)
                        val messageId = messageIdMatch?.groupValues?.get(1) ?: ""

                        val typesMatch = Regex("<(?:.*?:)?Types>(.*?)</(?:.*?:)?Types>").find(message)
                        val requestedTypes = typesMatch?.groupValues?.get(1) ?: "None"
                        
                        println("\n--- Received Probe from ${packet.address}:${packet.port} ---")
                        println("Requested Types: $requestedTypes | MessageID: $messageId")

                        // Отвечаем от имени всех устройств
                        devices.values.forEach { device ->
                            println("Sending ProbeMatch for ${device.name} to ${packet.address}:${packet.port}")
                            sendProbeMatch(packet.address, packet.port, device, messageId)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendHello(device: WsdDevice) {
        val xAddr = "http://$localIp:$httpPort/${device.uuid}"
        val types = if (device.category == "Computers") "wsdp:Device pub:Computer" else "wsdp:Device"
        val xml = """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Hello</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsd:AppSequence InstanceId="$instanceId" SequenceId="$sequenceId" MessageNumber="${messageCount++}" />
    </soap:Header>
    <soap:Body>
        <wsd:Hello>
            <wsa:EndpointReference><wsa:Address>${device.endpointReference}</wsa:Address></wsa:EndpointReference>
            <wsd:Types>$types</wsd:Types>
            <wsd:XAddrs>$xAddr</wsd:XAddrs>
            <wsd:MetadataVersion>1</wsd:MetadataVersion>
        </wsd:Hello>
    </soap:Body>
</soap:Envelope>""".trimIndent()

        try {
            val bytes = xml.toByteArray()
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, multicastAddr, wsdPort)) }
        } catch (e: Exception) {}
    }

    private fun sendProbeMatch(address: InetAddress, port: Int, device: WsdDevice, relatesTo: String) {
        val xAddr = "http://$localIp:$httpPort/${device.uuid}"
        val types = if (device.category == "Computers") "wsdp:Device pub:Computer" else "wsdp:Device"
        val xml = """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsa:RelatesTo>$relatesTo</wsa:RelatesTo>
        <wsd:AppSequence InstanceId="$instanceId" SequenceId="$sequenceId" MessageNumber="${messageCount++}" />
    </soap:Header>
    <soap:Body>
        <wsd:ProbeMatches>
            <wsd:ProbeMatch>
                <wsa:EndpointReference><wsa:Address>${device.endpointReference}</wsa:Address></wsa:EndpointReference>
                <wsd:Types>$types</wsd:Types>
                <wsd:XAddrs>$xAddr</wsd:XAddrs>
                <wsd:MetadataVersion>1</wsd:MetadataVersion>
            </wsd:ProbeMatch>
        </wsd:ProbeMatches>
    </soap:Body>
</soap:Envelope>""".trimIndent()

        try {
            val bytes = xml.toByteArray()
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, address, port)) }
        } catch (e: Exception) {}
    }

    private fun generateMetadataXml(device: WsdDevice, relatesTo: String): String {
        val presentationUrlXml = if (device.presentationUrl != null) {
            "\n                    <wsdp:PresentationUrl>${device.presentationUrl}</wsdp:PresentationUrl>"
        } else ""

        val hostTypes = if (device.category == "Computers") "pub:Computer" else "wsdp:Device"
        val pubComputerXml = if (device.category == "Computers") {
            "\n                        <pub:Computer>${device.realHostname}.local/Workgroup:${device.workgroup}</pub:Computer>"
        } else ""

        return """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2004/09/transfer/GetResponse</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsa:RelatesTo>$relatesTo</wsa:RelatesTo>
    </soap:Header>
    <soap:Body>
        <wsx:Metadata xmlns:wsx="http://schemas.xmlsoap.org/ws/2004/09/mex">
            <wsx:MetadataSection Dialect="http://schemas.xmlsoap.org/ws/2006/02/devprof/ThisModel">
                <wsdp:ThisModel>
                    <wsdp:Manufacturer>Kotlin-mDNS-Proxy</wsdp:Manufacturer>
                    <wsdp:ModelName>Virtual Computer</wsdp:ModelName>$presentationUrlXml
                    <pnpx:DeviceCategory>${device.category}</pnpx:DeviceCategory>
                </wsdp:ThisModel>
            </wsx:MetadataSection>
            <wsx:MetadataSection Dialect="http://schemas.xmlsoap.org/ws/2006/02/devprof/ThisDevice">
                <wsdp:ThisDevice>
                    <wsdp:FriendlyName>${device.name}</wsdp:FriendlyName>
                    <wsdp:FirmwareVersion>1.0</wsdp:FirmwareVersion>
                    <wsdp:SerialNumber>1</wsdp:SerialNumber>
                </wsdp:ThisDevice>
            </wsx:MetadataSection>
            <wsx:MetadataSection Dialect="http://schemas.xmlsoap.org/ws/2006/02/devprof/Relationship">
                <wsdp:Relationship Type="http://schemas.xmlsoap.org/ws/2006/02/devprof/host">
                    <wsdp:Host>
                        <wsa:EndpointReference><wsa:Address>${device.endpointReference}</wsa:Address></wsa:EndpointReference>
                        <wsdp:Types>$hostTypes</wsdp:Types>
                        <wsdp:ServiceId>${device.endpointReference}</wsdp:ServiceId>$pubComputerXml
                    </wsdp:Host>
                </wsdp:Relationship>
            </wsx:MetadataSection>
        </wsx:Metadata>
    </soap:Body>
</soap:Envelope>""".trimIndent()
    }
}
