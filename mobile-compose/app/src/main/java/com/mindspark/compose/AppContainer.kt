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
import com.mindspark.compose.features.rooms.data.NetworkRoomRepository
import com.mindspark.compose.features.rooms.data.RoomApi
import com.mindspark.compose.features.rooms.data.RoomRepository
import com.mindspark.compose.features.whiteboard.data.CategoryApi
import com.mindspark.compose.features.whiteboard.data.CategoryRepository
import com.mindspark.compose.features.whiteboard.data.CommentApi
import com.mindspark.compose.features.whiteboard.data.CommentRepository
import com.mindspark.compose.features.whiteboard.data.DrawingApi
import com.mindspark.compose.features.whiteboard.data.DrawingRepository
import com.mindspark.compose.features.whiteboard.data.IdeaApi
import com.mindspark.compose.features.whiteboard.data.IdeaRepository
import com.mindspark.compose.features.whiteboard.data.NetworkCategoryRepository
import com.mindspark.compose.features.whiteboard.data.NetworkCommentRepository
import com.mindspark.compose.features.whiteboard.data.NetworkDrawingRepository
import com.mindspark.compose.features.whiteboard.data.NetworkIdeaRepository
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
    val roomApi: RoomApi = retrofit.create(RoomApi::class.java)
    val roomRepository: RoomRepository = NetworkRoomRepository(roomApi)
    val ideaApi: IdeaApi = retrofit.create(IdeaApi::class.java)
    val ideaRepository: IdeaRepository = NetworkIdeaRepository(ideaApi)
    val categoryApi: CategoryApi = retrofit.create(CategoryApi::class.java)
    val categoryRepository: CategoryRepository = NetworkCategoryRepository(categoryApi)
    val commentApi: CommentApi = retrofit.create(CommentApi::class.java)
    val commentRepository: CommentRepository = NetworkCommentRepository(commentApi)
    val drawingApi: DrawingApi = retrofit.create(DrawingApi::class.java)
    val drawingRepository: DrawingRepository = NetworkDrawingRepository(drawingApi)
    val loginLauncher: LoginLauncher = LoginLauncher(env, context)
}
