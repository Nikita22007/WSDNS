package ru.nikita22007.wsmdnsproxy.app

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

class WsdResponder(val localIp: String, private val fixedHttpPort: Int = 0) {
    private val devices = ConcurrentHashMap<String, WsdDevice>()
    private val instanceId = System.currentTimeMillis() / 1000
    private val sequenceId = "urn:uuid:${UUID.randomUUID()}"
    private val messageCount = AtomicLong(1)
    private var actualHttpPort = 0
    private var httpServer: HttpServer? = null

    private val localAddress = InetAddress.getByName(localIp)
    private val multicastAddr = InetAddress.getByName(
        if (localAddress is Inet6Address) "ff02::c" else "239.255.255.250"
    )
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
        val device = devices.remove(uuid) ?: return
        thread {
            repeat(3) {
                if (devices.containsKey(uuid)) return@thread
                sendBye(device)
                Thread.sleep(200)
            }
        }
    }

    fun getDevice(uuid: String): WsdDevice? = devices[uuid]

    fun start() {
        startHttpServer()
        startUdpListener()
    }

    private fun startHttpServer() {
        val server = HttpServer.create(InetSocketAddress(localAddress, fixedHttpPort), 0)
        server.createContext("/") { exchange ->
            handleMetadataRequest(exchange)
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        
        actualHttpPort = server.address.port
        httpServer = server
        println("WSD HTTP Server for $localIp started on port $actualHttpPort")
    }

    private fun handleMetadataRequest(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "POST") {
                exchange.responseHeaders.set("Allow", "POST")
                exchange.sendResponseHeaders(405, -1)
                return
            }

            val uuid = exchange.requestURI.path.trim('/')
            val device = devices[uuid]
            if (device == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }

            val body = try {
                exchange.requestBody.readUtf8Limited(MAX_METADATA_REQUEST_BYTES)
            } catch (_: PayloadTooLargeException) {
                exchange.sendResponseHeaders(413, -1)
                return
            }
            val messageIdMatch = Regex("<(?:.*?:)?MessageID>(.*?)</(?:.*?:)?MessageID>").find(body)
            val messageId = messageIdMatch?.groupValues?.get(1) ?: ""

            val remoteAddress = exchange.remoteAddress.address.hostAddress
            println(">>> Metadata request (${exchange.requestMethod}) from $remoteAddress for ${device.name}. RelatesTo: $messageId")

            val responseBytes = generateMetadataXml(device, messageId).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/soap+xml; charset=utf-8")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        } finally {
            exchange.close()
        }
    }

    private fun startUdpListener() {
        thread {
            try {
                val socket = MulticastSocket(wsdPort)
                val networkInterface = NetworkInterface.getByInetAddress(localAddress)
                socket.networkInterface = networkInterface
                socket.joinGroup(InetSocketAddress(multicastAddr, wsdPort), networkInterface)

                val buffer = ByteArray(8192)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)

                    if (message.contains("/discovery/Probe")) {
                        val messageIdMatch = Regex("<(?:.*?:)?MessageID>(.*?)</(?:.*?:)?MessageID>").find(message)
                        val messageId = messageIdMatch?.groupValues?.get(1) ?: ""
                        
                        devices.values.forEach { device ->
                            sendProbeMatch(packet.address, packet.port, device, messageId)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "WSD listener failed on $localIp", e)
            }
        }
    }

    private fun sendHello(device: WsdDevice) {
        val xAddr = "http://${httpHost()}:$actualHttpPort/${device.uuid}"
        val types = if (device.category == "Computers") "wsdp:Device pub:Computer" else "wsdp:Device"
        val xml = """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Hello</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsd:AppSequence InstanceId="$instanceId" SequenceId="$sequenceId" MessageNumber="${messageCount.getAndIncrement()}" />
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
            sendDatagram(bytes, multicastAddr, wsdPort)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to send WSD Hello for ${device.uuid} on $localIp", e)
        }
    }

    private fun sendBye(device: WsdDevice) {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Bye</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsd:AppSequence InstanceId="$instanceId" SequenceId="$sequenceId" MessageNumber="${messageCount.getAndIncrement()}" />
    </soap:Header>
    <soap:Body>
        <wsd:Bye>
            <wsa:EndpointReference><wsa:Address>${device.endpointReference}</wsa:Address></wsa:EndpointReference>
        </wsd:Bye>
    </soap:Body>
</soap:Envelope>""".trimIndent()

        try {
            val bytes = xml.toByteArray()
            sendDatagram(bytes, multicastAddr, wsdPort)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to send WSD Bye for ${device.uuid} on $localIp", e)
        }
    }

    private fun sendProbeMatch(address: InetAddress, port: Int, device: WsdDevice, relatesTo: String) {
        val xAddr = "http://${httpHost()}:$actualHttpPort/${device.uuid}"
        val types = if (device.category == "Computers") "wsdp:Device pub:Computer" else "wsdp:Device"
        val xml = """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsa:RelatesTo>${relatesTo.xmlText()}</wsa:RelatesTo>
        <wsd:AppSequence InstanceId="$instanceId" SequenceId="$sequenceId" MessageNumber="${messageCount.getAndIncrement()}" />
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
            sendDatagram(bytes, address, port)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to send WSD ProbeMatch for ${device.uuid} on $localIp", e)
        }
    }

    private fun sendDatagram(bytes: ByteArray, address: InetAddress, port: Int) {
        DatagramSocket(InetSocketAddress(localAddress, 0)).use { socket ->
            socket.send(DatagramPacket(bytes, bytes.size, address, port))
        }
    }

    private fun httpHost(): String = if (localAddress is Inet6Address) "[$localIp]" else localIp

    private fun generateMetadataXml(device: WsdDevice, relatesTo: String): String {
        val presentationUrl = device.presentationUrl
        val presentationUrlXml = if (presentationUrl != null) {
            "\n                    <wsdp:PresentationUrl>${presentationUrl.xmlText()}</wsdp:PresentationUrl>"
        } else ""

        val hostTypes = if (device.category == "Computers") "pub:Computer" else "wsdp:Device"
        val pubComputerXml = if (device.category == "Computers") {
            "\n                        <pub:Computer>${device.realHostname.xmlText()}.local/Workgroup:${device.workgroup.xmlText()}</pub:Computer>"
        } else ""

        return """<?xml version="1.0" encoding="utf-8"?>
$envelopeHeader
    <soap:Header>
        <wsa:To>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>
        <wsa:Action>http://schemas.xmlsoap.org/ws/2004/09/transfer/GetResponse</wsa:Action>
        <wsa:MessageID>urn:uuid:${UUID.randomUUID()}</wsa:MessageID>
        <wsa:RelatesTo>${relatesTo.xmlText()}</wsa:RelatesTo>
    </soap:Header>
    <soap:Body>
        <wsx:Metadata xmlns:wsx="http://schemas.xmlsoap.org/ws/2004/09/mex">
            <wsx:MetadataSection Dialect="http://schemas.xmlsoap.org/ws/2006/02/devprof/ThisModel">
                <wsdp:ThisModel>
                    <wsdp:Manufacturer>Kotlin-mDNS-Proxy</wsdp:Manufacturer>
                    <wsdp:ModelName>Virtual Computer</wsdp:ModelName>$presentationUrlXml
                    <pnpx:DeviceCategory>${device.category.xmlText()}</pnpx:DeviceCategory>
                </wsdp:ThisModel>
            </wsx:MetadataSection>
            <wsx:MetadataSection Dialect="http://schemas.xmlsoap.org/ws/2006/02/devprof/ThisDevice">
                <wsdp:ThisDevice>
                    <wsdp:FriendlyName>${device.name.xmlText()}</wsdp:FriendlyName>
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

    private companion object {
        const val MAX_METADATA_REQUEST_BYTES = 64 * 1024
        val logger: Logger = Logger.getLogger(WsdResponder::class.java.name)
    }
}
