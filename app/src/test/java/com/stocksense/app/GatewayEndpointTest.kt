package com.stocksense.app

import com.stocksense.app.engine.GatewayEndpoint
import org.junit.Assert.*
import org.junit.Test

class GatewayEndpointTest {
    @Test fun debugAllowsOnlyLoopbackAndEmulatorHttp() {
        for (host in listOf("127.0.0.1", "localhost", "10.0.2.2")) {
            assertTrue(GatewayEndpoint.isAllowed("http://$host:8080/v1/chat/completions", true))
            assertFalse(GatewayEndpoint.isAllowed("http://$host:8080/v1/chat/completions", false))
        }
        for (host in listOf("192.168.1.10", "example.com", "localhost.example.com")) {
            assertFalse(GatewayEndpoint.isAllowed("http://$host:8080/v1/chat/completions", true))
        }
    }
    @Test fun requiresValidUrlsWithoutCredentialsQueriesOrFragments() {
        assertTrue(GatewayEndpoint.isAllowed("https://example.com/v1/chat/completions", false))
        for (url in listOf("garbage", "file:///tmp/file", "https://key@example.com/path",
            "https://example.com/path?key=value", "https://example.com/path#fragment")) {
            assertFalse(GatewayEndpoint.isAllowed(url, true))
        }
    }
}
