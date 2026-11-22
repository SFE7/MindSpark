package com.mindspark.compose.core.auth

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkHttpAuthGatewayTest {

    private val server = MockWebServer()
    private lateinit var gateway: OkHttpAuthGateway

    @Before fun setUp() {
        server.start()
        val base = server.url("/realms/mindspark").toString().trimEnd('/')
        gateway = OkHttpAuthGateway(
            tokenUrl = "$base/protocol/openid-connect/token",
            logoutUrl = "$base/protocol/openid-connect/logout",
            client = OkHttpClient()
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun refreshRequestHasCorrectFormFieldsAndReturnsTokens() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"a2","refresh_token":"r2","id_token":"i2","expires_in":300}"""
            )
        )
        val tokens = gateway.refresh("r1")
        val req = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue("grant_type missing", body.contains("grant_type=refresh_token"))
        assertTrue("client_id missing", body.contains("client_id=mobile"))
        assertTrue("refresh_token missing", body.contains("refresh_token=r1"))
        assertEquals("a2", tokens.accessToken)
        assertEquals("r2", tokens.refreshToken)
        assertEquals("i2", tokens.idToken)
    }

    @Test fun non200ResponseThrows() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertThrows(Exception::class.java) { gateway.refresh("bad_token") }
    }

    @Test fun refreshResponseWithoutNewRefreshTokenPreservesOriginalRefreshToken() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"a3","expires_in":300}"""
            )
        )
        val tokens = gateway.refresh("original_refresh_token")
        assertEquals("a3", tokens.accessToken)
        assertEquals("original_refresh_token", tokens.refreshToken)
    }
}
