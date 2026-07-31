package ru.nikita22007.wsmdnsproxy.app

data class CliArgs(
    val isolatedMode: Boolean?,
    val listenInterfaces: List<String>,
    val publishInterfaces: List<InterfaceRequest>,
    val showHelp: Boolean
)

data class InterfaceRequest(val target: String, val port: Int = 0)

class CliUsageException(message: String) : IllegalArgumentException(message)

object CliParser {
    fun parse(args: Array<String>): CliArgs {
        var isolatedMode: Boolean? = null
        val listenInterfaces = mutableListOf<String>()
        val publishInterfaces = mutableListOf<InterfaceRequest>()
        var showHelp = false

        var i = 0
        while (i < args.size) {
            val arg = args[i].lowercase()
            when {
                arg in listOf("-h", "--help", "/h", "/help", "-?", "/?") -> showHelp = true
                arg in listOf("--public", "/public") -> isolatedMode = false
                arg in listOf("-l", "--listen-interface", "/l", "/listen", "/listen-interface") -> {
                    listenInterfaces.add(requireValue(args, ++i, arg))
                }
                arg in listOf("-p", "--publish-interface", "/p", "/publish", "/publish-interface",
                    "-i", "--interface", "/i", "/interface") -> {
                    val target = requireValue(args, ++i, arg)
                    val portText = args.getOrNull(i + 1)?.takeUnless(::isOption)
                    val port = if (portText != null) {
                        portText.toIntOrNull()
                            ?: throw CliUsageException("Invalid port '$portText' for $arg")
                    } else 0
                    if (portText != null) i++
                    if (port !in 0..65535) {
                        throw CliUsageException("Port must be between 0 and 65535: $port")
                    }
                    publishInterfaces.add(InterfaceRequest(target, port))
                }
                else -> throw CliUsageException("Unknown option: ${args[i]}")
            }
            i++
        }
        return CliArgs(isolatedMode, listenInterfaces, publishInterfaces, showHelp)
    }

    private fun requireValue(args: Array<String>, index: Int, option: String): String {
        val value = args.getOrNull(index)
        if (value == null || isOption(value)) throw CliUsageException("Missing value for $option")
        return value
    }

    private fun isOption(value: String): Boolean = value.startsWith("-") || value.startsWith("/")

    fun printHelp() {
        println("""
            WSDNS v1.1.0 - by nikita22007
            Bridge mDNS services to Windows Network Explorer
            
            Usage:
              java -jar wsdns-{version}-all.jar [options]
            
            Options:
              /public, --public         Enable PUBLIC mode (visible to all network). 
                                        Default: Isolated (Stealth) on Windows, Public on Linux/Others.
              /l, -l, --listen-interface TARGET
                                        Listen for mDNS on an interface name or IP.
              /p, -p, --publish-interface TARGET [PORT]
                                        Publish WSD on an interface name or IP.
                                        Repeat for multiple interfaces; port defaults to random.
              /i, -i, --interface TARGET [PORT]
                                        Legacy alias for --publish-interface.
              /h, --help, /?, -h        Show this help and configuration guide.

            Stealth Mode (Isolated):
              On Windows, WSDNS defaults to 'Isolated Mode'. This means discovered devices 
              are visible ONLY to your computer via loopback (127.0.0.1). 
              This prevents cluttering the network environment for your colleagues.

            Configuration (config.json):
              The mapping rules are defined in 'config.json'.
              - mdnsType: The mDNS service type (e.g. _smb._tcp.local.)
              - wsdCategory: Windows PnP-X Category:
                  * Computers: Traditional file server (opens SMB on double-click)
                  * NetworkInfrastructure: Router/Switch icon (often opens URL)
                  * Storage.NAS: Network storage icon
                  * Other: Neutral device icon
              - presentationUrlTemplate: Web UI link template ({ip}, {port}, {name}).
              - priority: Higher value wins. Determines which icon/action is used 
                          if a device announces multiple services (e.g. SMB wins over HTTP).
        """.trimIndent())
    }
}
