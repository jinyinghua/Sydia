package com.shaun.sydia.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.shaun.sydia.data.local.ChatHistoryEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToMemories: () -> Unit,
    isAssistantInterface: Boolean = false,
    onClose: () -> Unit = {}
) {
    val chatMessages = viewModel.chatStream.collectAsLazyPagingItems()
    var inputText by remember { mutableStateOf("") }
    var isPerceptionEnabled by remember { mutableStateOf(false) } // State for perception switch
    
    Scaffold(
        containerColor = if (isAssistantInterface) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Sydia")
                        if (isAssistantInterface) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = isPerceptionEnabled,
                                    onCheckedChange = { isPerceptionEnabled = it },
                                    modifier = Modifier.scale(0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Perception", style = MaterialTheme.typography.labelSmall)
                             }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (isAssistantInterface) {
                         IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    IconButton(onClick = onNavigateToMemories) {
                        Icon(Icons.Default.Face, contentDescription = "Memories")
                    }
                    if (!isAssistantInterface) {
                         IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                   
                    IconButton(onClick = { viewModel.resetContext() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Context")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isAssistantInterface && isPerceptionEnabled) "Sydia is watching..." else "Type a message...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true // Chat usually starts from bottom
            ) {
                items(
                    count = chatMessages.itemCount,
                    itemContent = { index ->
                        val message = chatMessages[index]
                        message?.let { ChatMessageItem(it) }
                    }
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatHistoryEntity) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser) {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp
        )
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (message.role == "system") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
            ) {
                 Text(
                    text = message.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.align(alignment)
                    .widthIn(max = 300.dp)
            ) {
                Surface(
                    color = containerColor,
                    shape = shape,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = message.content, 
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}
