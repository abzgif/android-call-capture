package com.callcapture.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.callcapture.app.ui.screens.HomeScreen
import com.callcapture.app.ui.screens.PermissionSetupScreen
import com.callcapture.app.ui.screens.SettingsScreen
import com.callcapture.app.ui.theme.OmniCallTheme
import com.callcapture.app.ui.viewmodel.MainViewModel
import com.callcapture.app.ui.viewmodel.SettingsViewModel
import com.callcapture.app.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OmniCallTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        mainViewModel = mainViewModel,
                        settingsViewModel = settingsViewModel,
                        hasMissingPermissions = !PermissionHelper.hasRecordAudioPermission(this) ||
                                !PermissionHelper.isAccessibilityServiceEnabled(this)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    hasMissingPermissions: Boolean
) {
    val navController = rememberNavController()
    val startDestination = if (hasMissingPermissions) "permissions" else "home"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350))
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissions = { navController.navigate("permissions") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("permissions") {
            PermissionSetupScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate("home") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
