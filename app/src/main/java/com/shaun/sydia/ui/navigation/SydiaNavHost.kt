package com.shaun.sydia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shaun.sydia.ui.chat.ChatScreen
import com.shaun.sydia.ui.chat.ChatViewModel
import com.shaun.sydia.ui.memory.MemoryScreen
import com.shaun.sydia.ui.settings.SettingsScreen

@Composable
fun SydiaNavHost(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "settings") {
        composable("chat") {
            ChatScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToMemories = { navController.navigate("memories") }
            )
        }
        composable("memories") {
            MemoryScreen(viewModel = viewModel)
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMemories = { navController.navigate("memories") }
            )
        }
    }
}
