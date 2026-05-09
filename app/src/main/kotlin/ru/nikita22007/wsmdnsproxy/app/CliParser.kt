package ru.nikita22007.wsmdnsproxy.app

data class CliArgs(
    val isolatedMode: Boolean?,
    val requestedInterfaces: List<InterfaceRequest>,
    val showHelp: Boolean
)

data class InterfaceRequest(val ip: String, val port: Int = 0)

object CliParser {
    fun parse(args: Array<String>, defaultIsolated: Boolean): CliArgs {
        var isolatedMode: Boolean? = null
        val requestedInterfaces = mutableListOf<InterfaceRequest>()
        var showHelp = false

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
        return CliArgs(isolatedMode, requestedInterfaces, showHelp)
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
}
