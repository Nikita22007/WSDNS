# WSDNS (mDNS to WS-Discovery Proxy)
[![Build and Release](https://github.com/Nikita22007/WSDNS/actions/workflows/build.yml/badge.svg)](https://github.com/Nikita22007/WSDNS/actions/workflows/build.yml)

**WSDNS** is a lightweight, cross-platform bridge that makes mDNS/Bonjour-enabled devices (like Raspberry Pi, NAS, or IoT gadgets) visible in the **Windows Explorer Network** environment using the WS-Discovery protocol.

## Features
- **Smart Mapping**: Automatically categorizes devices based on discovered services (SMB -> Computer, HTTP -> Network Infrastructure).
- **Presentation URL**: Right-click on a device in Windows Explorer to open its web interface.
- **Stealth (Isolated) Mode**: 
    - **Windows**: Defaults to `true`. Devices are visible **only to your computer** (local loopback). This prevents "polluting" the network discovery of your colleagues in office environments.
    - **Linux/Others**: Defaults to `false`. Acts as a network-wide bridge for all Windows clients in the same subnet.
- **Dynamic Configuration**: Customize mappings, categories, and URL templates via `config.json`.
- **Lightweight**: Built with Kotlin and Java's native `HttpServer`, optimized for GraalVM Native Image.

## Usage
### Running the JAR
```bash
java -jar wsdns-{version}-all.jar
```

### CLI Options
- `/public`, `--public`: Disable Stealth mode (visible to the whole network).
- `-l`, `--listen-interface`, `/l`, `/listen TARGET`: Listen for mDNS on an interface name or local IP. Repeatable.
- `-p`, `--publish-interface`, `/p`, `/publish TARGET [PORT]`: Publish WSD on an interface name or local IP. Repeatable; each interface may have its own HTTP port.
- `-i`, `--interface`, `/i`, `/interface TARGET [PORT]`: Legacy alias for `--publish-interface`.
- `/h`, `--help`: Show help and configuration guide.

Both IPv4 and IPv6 addresses are supported. If no listen interface is specified, all active interfaces are scanned. In public mode, WSD is published on every active interface unless publish interfaces are explicitly selected.

```bash
# Unix-style options
java -jar wsdns-{version}-all.jar --public -l eth0 -p eth0 5357 -p wlan0 5358

# Windows-style options; quote interface names containing spaces
java -jar wsdns-{version}-all.jar /public /l "Wi-Fi" /p "Wi-Fi" 5357
```

## Configuration Guide (`config.json`)
The `config.json` file is automatically generated on the first run. You can modify it to suit your needs.

### Global Settings
- `isolatedMode`: (Boolean) Enable/disable Stealth mode globally.
- `debugMode`: (Boolean) Adds a `-KProxy` suffix to discovered device names for easier identification.
- `groupServicesByHost`: (Boolean, default `true`) Group all service instances announced by the same mDNS hostname into one Windows device. Set to `false` to publish each service instance as a separate device.
- `enableIpv4`: (Boolean, default `true`) Enable IPv4 mDNS listening and WSD publishing.
- `enableIpv6`: (Boolean, default `true`) Enable IPv6 mDNS listening and WSD publishing. At least one IP family must remain enabled.

### Service Mappings (`mappings`)
Each entry defines how an mDNS service is presented to Windows:
- `mdnsType`: The mDNS service type to listen for (e.g., `_smb._tcp.local.`, `_http._tcp.local.`).
- `wsdCategory`: The Windows device category. Common values:
    - `Computers`: Displays as a PC. Opens SMB shares on double-click.
    - `NetworkInfrastructure`: Displays as a router/gateway. Often opens the web UI on double-click.
    - `Storage.NAS`: Displays as a network storage device.
    - `Other`: Generic device icon.
- `presentationUrlTemplate`: A template for the "View device web page" link. Supports placeholders:
    - `{ip}`: The IP address of the discovered device.
    - `{port}`: The port of the mDNS service.
    - `{name}`: The hostname of the device.
- `priority`: (Integer) If a device announces multiple services, the one with the highest priority determines the icon and double-click action.

## Acknowledgments
Special thanks to **Google** for the **Gemini** family of AI models and the **Gemini CLI** tool, which made the rapid development and architecture of this project possible.

## License
Distributed under the **GNU General Public License v3.0**. See `LICENSE` for details.

---
**Author**: nikita22007
**Version**: 1.1.0
