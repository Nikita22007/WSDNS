package ru.nikita22007.wsmdnsproxy.app

import java.util.*

data class WsdDevice(
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val realHostname: String,
    var category: String = "Other", // По умолчанию ставим "Другие"
    var presentationUrl: String? = null,
    val workgroup: String = "WORKGROUP"
) {
    val endpointReference: String = "urn:uuid:$uuid"
}
