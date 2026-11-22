package com.mindspark.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mindspark.compose.core.auth.AuthUiState
import com.mindspark.compose.core.auth.AuthViewModel
import com.mindspark.compose.features.auth.LoginScreen
import com.mindspark.compose.features.rooms.ui.HomeScreen
import com.mindspark.compose.features.whiteboard.ui.RoomScreen
import com.mindspark.compose.ui.Routes
import com.mindspark.compose.ui.theme.MindSparkTheme

@Composable
fun MindSparkApp() {
    MindSparkTheme {
        val context = LocalContext.current
        val app = context.applicationContext as MindSparkApplication
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
        val authState by authViewModel.uiState.collectAsState()
        val navController = rememberNavController()

        LaunchedEffect(authState) {
            when (authState) {
                is AuthUiState.SignedIn -> {
                    if (navController.currentDestination?.route != Routes.HOME) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                is AuthUiState.SignedOut -> {
                    if (navController.currentDestination?.route != Routes.LOGIN) {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                else -> Unit
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    loginLauncher = app.container.loginLauncher
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenRoom = { roomId ->
                        navController.navigate(Routes.room(roomId))
                    },
                    onSignOut = { authViewModel.signOut() }
                )
            }
            composable(Routes.ROOM) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
                RoomScreen(
                    roomId = roomId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
