package com.stocksense.app.engine

import java.net.URI

/** Cleartext is restricted to development loopback/emulator endpoints in debug builds. */
internal object GatewayEndpoint {
    fun isAllowed(url: String, debug: Boolean): Boolean = try {
        val uri = URI(url)
        uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.scheme == "https" || (debug && uri.scheme == "http" &&
                uri.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))
    } catch (_: Exception) {
        false
    }
}
