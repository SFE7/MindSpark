package com.mindspark.compose.core.auth

import com.mindspark.compose.core.network.UserApi
import com.mindspark.compose.core.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeGateway
    private lateinit var store: InMemoryTokenStore
    private lateinit var authService: AuthService
    private lateinit var fakeUserApi: FakeUserApi
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = FakeGateway()
        store = InMemoryTokenStore()
        authService = AuthService(gateway, store)
        fakeUserApi = FakeUserApi()
        viewModel = AuthViewModel(authService, fakeUserApi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginSavesTokensCallsSyncAndExposesUser() = runTest(testDispatcher) {
        viewModel.onLoginResult(tokens("a1"))
        advanceUntilIdle()

        assertEquals(1, fakeUserApi.syncCalls)
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        assertEquals("a@b.tn", (state as AuthUiState.SignedIn).user.email)
        assertEquals("a1", store.read()?.accessToken)
    }

    @Test
    fun sessionExpiredFlagSignsOut() = runTest(testDispatcher) {
        viewModel.onLoginResult(tokens("a1"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is AuthUiState.SignedIn)

        gateway.failRefresh = true
        try {
            authService.refreshAccessToken("a1")
        } catch (_: SessionExpired) {}

        advanceUntilIdle()
        assertEquals(AuthUiState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun startupWithStoredSessionSyncsUser() = runTest(testDispatcher) {
        store.save(tokens("stored_tok"))
        val restoredVm = AuthViewModel(authService, fakeUserApi)
        advanceUntilIdle()

        assertEquals(1, fakeUserApi.syncCalls)
        val state = restoredVm.uiState.value
        assertTrue(state is AuthUiState.SignedIn)
        assertEquals("ilyes", (state as AuthUiState.SignedIn).user.username)
    }

    @Test
    fun startupSyncFailureGoesToSignedOut() = runTest(testDispatcher) {
        store.save(tokens("stored_tok"))
        fakeUserApi.failSync = true
        val restoredVm = AuthViewModel(authService, fakeUserApi)
        advanceUntilIdle()

        assertEquals(1, fakeUserApi.syncCalls)
        assertEquals(AuthUiState.SignedOut, restoredVm.uiState.value)
    }

    @Test
    fun cancelledOrFailedLoginLeavesLoadingState() = runTest(testDispatcher) {
        viewModel.onLoginCancelled()
        assertEquals(AuthUiState.SignedOut, viewModel.uiState.value)

        viewModel.onLoginError("Failed to authenticate")
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Failed to authenticate", (state as AuthUiState.Error).message)
    }

    private fun tokens(access: String) =
        AuthTokens(access, "r1", "i1", Long.MAX_VALUE)

    class FakeUserApi : UserApi {
        var syncCalls = 0
        var failSync = false

        override suspend fun sync(): UserDto {
            syncCalls++
            if (failSync) throw IOException("Sync error")
            return UserDto(id = 1L, username = "ilyes", email = "a@b.tn")
        }
    }
}
