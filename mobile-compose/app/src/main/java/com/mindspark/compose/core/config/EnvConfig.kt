package com.mindspark.compose.core.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class EnvConfig(val hostIp: String) {
    init {
        require(hostIp.isNotEmpty() && hostIp != "localhost" && hostIp != "10.0.2.2") {
            "hostIp must be a valid LAN IP address (got: '$hostIp')"
        }
    }

    val apiUrl: String = "http://$hostIp:8080"
    val issuer: String = "http://$hostIp:8180/realms/mindspark"

    fun wsUrl(roomId: String, token: String): String {
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString())
        return "ws://$hostIp:8080/ws/rooms/$roomId?token=$encodedToken"
    }
}
