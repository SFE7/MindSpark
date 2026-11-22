package com.mindspark.compose

import android.content.Context
import com.mindspark.compose.core.auth.AuthGateway
import com.mindspark.compose.core.auth.AuthService
import com.mindspark.compose.core.auth.LoginLauncher
import com.mindspark.compose.core.auth.OkHttpAuthGateway
import com.mindspark.compose.core.auth.SharedPrefsTokenStore
import com.mindspark.compose.core.auth.TokenStore
import com.mindspark.compose.core.config.EnvConfig
import com.mindspark.compose.core.network.UserApi
import com.mindspark.compose.core.network.buildOkHttp
import com.mindspark.compose.core.network.buildRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class AppContainer(context: Context) {
    val env: EnvConfig = EnvConfig(BuildConfig.HOST_IP)
    val tokenStore: TokenStore = SharedPrefsTokenStore(context)
    val authGateway: AuthGateway = OkHttpAuthGateway(env, OkHttpClient())
    val authService: AuthService = AuthService(authGateway, tokenStore)
    val okHttp: OkHttpClient = buildOkHttp(authService)
    val retrofit: Retrofit = buildRetrofit(env, okHttp)
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val userApi: UserApi = retrofit.create(UserApi::class.java)
    val loginLauncher: LoginLauncher = LoginLauncher(env, context)
}
