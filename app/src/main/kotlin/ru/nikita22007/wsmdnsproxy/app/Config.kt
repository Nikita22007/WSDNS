package ru.nikita22007.wsmdnsproxy.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ServiceMapping(
    val mdnsType: String,
    val wsdCategory: String,
    val presentationUrlTemplate: String? = null,
    val priority: Int = 0 // Чем выше, тем важнее правило (например SMB важнее HTTP)
)

@Serializable
data class ProxyConfig(
    val isolatedMode: Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true),
    val debugMode: Boolean = false,
    val groupServicesByHost: Boolean = true,
    val enableIpv4: Boolean = true,
    val enableIpv6: Boolean = true,
    val mappings: List<ServiceMapping> = listOf(
        ServiceMapping("_smb._tcp.local.", "Computers", priority = 100),
        ServiceMapping("_http._tcp.local.", "NetworkInfrastructure", "http://{ip}:{port}", priority = 50),
        ServiceMapping("_sftp-ssh._tcp.local.", "Storage.NAS", "sftp://{ip}:{port}", priority = 40),
        ServiceMapping("_ssh._tcp.local.", "NetworkInfrastructure", "ssh://{ip}", priority = 30)
    )
)

internal fun ProxyConfig.validate() {
    require(enableIpv4 || enableIpv6) { "At least one of enableIpv4 or enableIpv6 must be true" }
}

object ConfigManager {
    private val configFile = File("config.json")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun loadConfig(): ProxyConfig {
        return if (configFile.exists()) {
            try {
                json.decodeFromString<ProxyConfig>(configFile.readText())
            } catch (e: Exception) {
                println("Error loading config.json, using defaults. Error: ${e.message}")
                ProxyConfig()
            }
        } else {
            val defaultConfig = ProxyConfig()
            saveConfig(defaultConfig)
            println("Default config.json created.")
            defaultConfig
        }
    }

    private fun saveConfig(config: ProxyConfig) {
        configFile.writeText(json.encodeToString(config))
    }
}
