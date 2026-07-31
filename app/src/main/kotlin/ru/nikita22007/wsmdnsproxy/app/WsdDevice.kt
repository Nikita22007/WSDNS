package ru.nikita22007.wsmdnsproxy.app

data class WsdDevice(
    val uuid: String,
    var name: String,
    val realHostname: String,
    var category: String = "Other",
    var presentationUrl: String? = null,
    val workgroup: String = "WORKGROUP"
) {
    val endpointReference: String = "urn:uuid:$uuid"
}
