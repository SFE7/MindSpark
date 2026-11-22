package com.mindspark.compose.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindspark.compose.MindSparkApplication
import com.mindspark.compose.core.network.UserApi
import com.mindspark.compose.core.network.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed interface AuthUiState {
    data object SignedOut : AuthUiState
    data object Loading : AuthUiState
    data class SignedIn(val user: UserDto) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val authService: AuthService,
    private val userApi: UserApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authService.sessionExpired.collect { expired ->
                if (expired) {
                    _uiState.value = AuthUiState.SignedOut
                }
            }
        }
        restoreSession()
    }

    private fun restoreSession() {
        val token = authService.validAccessToken()
        if (token == null) {
            _uiState.value = AuthUiState.SignedOut
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = userApi.sync()
                _uiState.value = AuthUiState.SignedIn(user)
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.SignedOut
            } catch (e: IOException) {
                _uiState.value = AuthUiState.SignedOut
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.SignedOut
            }
        }
    }

    fun onLoginResult(tokens: AuthTokens) {
        authService.saveLogin(tokens)
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = userApi.sync()
                _uiState.value = AuthUiState.SignedIn(user)
            } catch (e: HttpException) {
                _uiState.value = AuthUiState.Error("Server error (${e.code()})")
            } catch (e: IOException) {
                _uiState.value = AuthUiState.Error("Network connection error")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "User sync failed")
            }
        }
    }

    fun onLoginError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun onLoginCancelled() {
        _uiState.value = AuthUiState.SignedOut
    }

    fun signOut() {
        authService.logout()
        _uiState.value = AuthUiState.SignedOut
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MindSparkApplication)
                AuthViewModel(
                    authService = app.container.authService,
                    userApi = app.container.userApi
                )
            }
        }
    }
}
