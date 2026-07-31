package ru.nikita22007.wsmdnsproxy.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.charset.StandardCharsets

internal class PayloadTooLargeException : Exception()

internal fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(4096)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw PayloadTooLargeException()
        output.write(buffer, 0, read)
    }
    return output.toString(StandardCharsets.UTF_8.name())
}

internal fun InetAddress.httpHost(): String =
    if (this is Inet6Address) "[${hostAddress.replace("%", "%25")}]" else hostAddress
