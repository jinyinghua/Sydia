package com.shaun.sydia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shaun.sydia.ui.chat.ChatScreen
import com.shaun.sydia.ui.chat.ChatViewModel
import com.shaun.sydia.ui.memory.MemoryScreen
import com.shaun.sydia.ui.settings.SettingsScreen

import androidx.lifecycle.viewmodel.compose.viewModel
import com.shaun.sydia.SydiaApplication
import com.shaun.sydia.ui.settings.SettingsViewModel
import com.shaun.sydia.ui.settings.SettingsViewModelFactory

@Composable
fun SydiaNavHost(chatViewModel: ChatViewModel, app: SydiaApplication) {
    val navController = rememberNavController()
    
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.settingsRepository)
    )

    NavHost(navController = navController, startDestination = "settings") {
        composable("chat") {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToMemories = { navController.navigate("memories") }
            )
        }
        composable("memories") {
            MemoryScreen(viewModel = chatViewModel)
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToMemories = { navController.navigate("memories") }
            )
        }
    }
}
