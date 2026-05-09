# WSDNS (mDNS to WS-Discovery Proxy)

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
java -jar wsdns-all.jar
```

### CLI Options
- `/public`, `--public`: Disable Stealth mode (visible to the whole network).
- `/i`, `-i`, `--interface IP[:PORT]`: Bind to a specific interface.
- `/h`, `--help`: Show help and configuration guide.

## Configuration Guide (`config.json`)
The `config.json` file is automatically generated on the first run. You can modify it to suit your needs.

### Global Settings
- `isolatedMode`: (Boolean) Enable/disable Stealth mode globally.
- `debugMode`: (Boolean) Adds a `-KProxy` suffix to discovered device names for easier identification.

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
**Version**: 1.0.0
