package com.mindspark.compose.core.network

import com.mindspark.compose.core.auth.AuthGateway
import com.mindspark.compose.core.auth.AuthService
import com.mindspark.compose.core.auth.AuthTokens
import com.mindspark.compose.core.auth.InMemoryTokenStore
import com.mindspark.compose.core.config.EnvConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HttpClientTest {

    private val server = MockWebServer()
    private lateinit var gateway: TestAuthGateway
    private lateinit var store: InMemoryTokenStore
    private lateinit var authService: AuthService
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        gateway = TestAuthGateway()
        store = InMemoryTokenStore()
        authService = AuthService(gateway, store)
        client = buildOkHttp(authService)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun addsBearerHeader() {
        authService.saveLogin(AuthTokens("a1", "r1", "i1", Long.MAX_VALUE))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val req = Request.Builder().url(server.url("/test")).build()
        client.newCall(req).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer a1", recorded.getHeader("Authorization"))
    }

    @Test
    fun on401RefreshesOnceAndRetriesWithNewToken() {
        authService.saveLogin(AuthTokens("a1", "r1", "i1", Long.MAX_VALUE))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val req = Request.Builder().url(server.url("/test")).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
        }

        assertEquals(2, server.requestCount)
        val firstReq = server.takeRequest()
        assertEquals("Bearer a1", firstReq.getHeader("Authorization"))
        val secondReq = server.takeRequest()
        assertEquals("Bearer a2", secondReq.getHeader("Authorization"))
        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun rf1_threeParallel401sCauseExactlyOneRefresh() {
        authService.saveLogin(AuthTokens("a1", "r1", "i1", Long.MAX_VALUE))
        // Enqueue 3 401s followed by 3 200s
        repeat(3) { server.enqueue(MockResponse().setResponseCode(401)) }
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) }

        val executor = Executors.newFixedThreadPool(3)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(3)

        repeat(3) {
            executor.submit {
                try {
                    startLatch.await()
                    val req = Request.Builder().url(server.url("/test")).build()
                    client.newCall(req).execute().use { resp ->
                        assertEquals(200, resp.code)
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun rf1_refreshFailureSurfaces401AndSetsSessionExpired() {
        gateway.failRefresh = true
        authService.saveLogin(AuthTokens("a1", "r1", "i1", Long.MAX_VALUE))
        server.enqueue(MockResponse().setResponseCode(401))

        val req = Request.Builder().url(server.url("/test")).build()
        client.newCall(req).execute().use { resp ->
            assertEquals(401, resp.code)
        }

        assertTrue(authService.sessionExpired.value)
    }

    @Test
    fun apiErrorsTellHttpFromConnectionFailures() {
        val httpEx = HttpException(
            Response.error<String>(
                403,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val ioEx = IOException("Connection reset")
        val otherEx = IllegalStateException("Something broke")

        assertEquals(403, httpCode(httpEx))
        assertFalse(isConnectionError(httpEx))

        assertNull(httpCode(ioEx))
        assertTrue(isConnectionError(ioEx))

        assertNull(httpCode(otherEx))
        assertFalse(isConnectionError(otherEx))
    }

    private class TestAuthGateway : AuthGateway {
        var refreshCalls = 0
        var failRefresh = false

        override fun refresh(refreshToken: String): AuthTokens {
            refreshCalls++
            if (failRefresh) throw IOException("Refresh network failed")
            return AuthTokens("a2", "r2", "i2", Long.MAX_VALUE)
        }

        override fun logout(refreshToken: String) {}
    }
}
