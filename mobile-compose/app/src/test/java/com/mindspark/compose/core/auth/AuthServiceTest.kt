package com.mindspark.compose.core.auth

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AuthServiceTest {

    private lateinit var gateway: FakeGateway
    private lateinit var store: InMemoryTokenStore
    private lateinit var service: AuthService

    @Before
    fun setup() {
        gateway = FakeGateway()
        store = InMemoryTokenStore()
        service = AuthService(gateway, store, clock = { 0L })
    }

    @Test fun saveLoginStoresTokens() {
        service.saveLogin(tokens("a1"))
        assertEquals("a1", store.read()!!.accessToken)
    }

    @Test fun secondRefreshWithSameFailedTokenDoesNotCallGatewayAgain() {
        service.saveLogin(tokens("a1"))
        val first = service.refreshAccessToken("a1")   // gateway returns a2
        val second = service.refreshAccessToken("a1")  // already refreshed; skip gateway
        assertEquals("a2", first)
        assertEquals("a2", second)
        assertEquals(1, gateway.refreshCalls)
    }

    @Test fun refreshFailureClearsStoreAndThrowsSessionExpired() {
        gateway.failRefresh = true
        service.saveLogin(tokens("a1"))
        assertThrows(SessionExpired::class.java) { service.refreshAccessToken("a1") }
        assertNull(store.read())
        assertTrue(service.sessionExpired.value)
    }

    @Test fun validAccessTokenRefreshesWhenExpiringSoon() {
        // token expires at 60_000 ms; clock returns 50_000 ms → expiring in 10 s (< 30 s threshold)
        val expiry = 60_000L
        val soonExpiring = AuthTokens("a1", "r1", "i1", expiry)
        val svc = AuthService(gateway, store, clock = { expiry - 10_000L })
        svc.saveLogin(soonExpiring)
        val token = svc.validAccessToken()
        assertEquals("a2", token)
        assertEquals(1, gateway.refreshCalls)
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private fun tokens(access: String) =
        AuthTokens(access, "r1", "i1", Long.MAX_VALUE)
}

// ── Fakes (reused by OkHttpAuthGatewayTest and later tasks) ─────────────────

class FakeGateway : AuthGateway {
    var refreshCalls = 0
    var failRefresh = false

    override fun refresh(refreshToken: String): AuthTokens {
        refreshCalls++
        if (failRefresh) throw IOException("network error")
        return AuthTokens("a2", "r2", "i2", Long.MAX_VALUE)
    }

    override fun logout(refreshToken: String) { /* no-op */ }
}

class InMemoryTokenStore : TokenStore {
    private var stored: AuthTokens? = null
    override fun read(): AuthTokens? = stored
    override fun save(t: AuthTokens) { stored = t }
    override fun clear() { stored = null }
}
