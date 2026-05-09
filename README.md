# WSDNS (mDNS to WS-Discovery Proxy)

**WSDNS** is a lightweight, cross-platform bridge that makes mDNS/Bonjour-enabled devices (like Raspberry Pi, NAS, or IoT gadgets) visible in the **Windows Explorer Network** environment using the WS-Discovery protocol.

## Features
- **Smart Mapping**: Automatically categorizes devices based on discovered services (SMB -> Computer, HTTP -> Network Infrastructure).
- **Presentation URL**: Right-click on a device in Windows Explorer to open its web interface.
- **Isolated Mode**: Stealth mode for office environments—keep discovered devices visible only to you.
- **Dynamic Configuration**: Customize mappings, categories, and URL templates via `config.json`.
- **Lightweight**: Built with Kotlin and Java's native `HttpServer`, optimized for GraalVM Native Image.

## Usage
### Running the JAR
```bash
java -jar wsdns-all.jar
```

### CLI Options
- `/public`, `--public`: Enable PUBLIC mode (visible to the whole network).
- `/i`, `-i`, `--interface IP[:PORT]`: Bind to a specific interface.
- `/h`, `--help`: Show help and configuration guide.

## Acknowledgments
Special thanks to **Google** for the **Gemini** family of AI models and the **Gemini CLI** tool, which made the rapid development and architecture of this project possible.

## License
Distributed under the **GNU General Public License v3.0**. See `LICENSE` for details.

---
**Author**: nikita22007
**Version**: 1.0.0
