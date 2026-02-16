package com.shaun.sydia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shaun.sydia.ui.chat.ChatViewModel
import com.shaun.sydia.ui.chat.ChatViewModelFactory
import com.shaun.sydia.ui.navigation.SydiaNavHost
import com.shaun.sydia.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as SydiaApplication
        val chatRepository = app.chatRepository

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = ChatViewModelFactory(
                            app.chatRepository, 
                            app.memoryRepository, 
                            app.settingsRepository, 
                            app.aiService
                        )
                    )
                    SydiaNavHost(chatViewModel = chatViewModel, app = app)
                }
            }
        }
    }
}