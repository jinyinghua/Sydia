package com.shaun.sydia.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shaun.sydia.data.remote.AIService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        viewModel: SettingsViewModel,
        onBack: () -> Unit,
        onNavigateToMemories: () -> Unit
) {
    val chatProvider by viewModel.chatModelProvider.collectAsState()
    val chatModel by viewModel.chatModelName.collectAsState()
    val chatApiKey by viewModel.chatApiKey.collectAsState()
    val chatBaseUrl by viewModel.chatBaseUrl.collectAsState()
    val chatStreamEnabled by viewModel.chatStreamEnabled.collectAsState()

    val embedProvider by viewModel.embeddingModelProvider.collectAsState()
    val embedModel by viewModel.embeddingModelName.collectAsState()
    val embedApiKey by viewModel.embeddingApiKey.collectAsState()
    val embedBaseUrl by viewModel.embeddingBaseUrl.collectAsState()

    val contextLength by viewModel.contextLength.collectAsState()
    val extractionFrequency by viewModel.extractionFrequency.collectAsState()
    // val personality by viewModel.personality.collectAsState()

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("设置") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                )
                            }
                        }
                )
            }
    ) { innerPadding ->
        Column(
                modifier =
                        Modifier.padding(innerPadding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SectionTitle("大模型配置")
            ProviderSelector(
                    label = "格式",
                    selected = chatProvider,
                    onSelected = { newProvider ->
                        val defaultModel = AIService.getDefaultModel(newProvider)
                        val defaultUrl = AIService.getDefaultBaseUrl(newProvider)
                        viewModel.updateChatSettings(newProvider, defaultModel, chatApiKey, defaultUrl)
                    }
            )
            SettingsTextField("Model Name", chatModel) {
                viewModel.updateChatSettings(chatProvider, it, chatApiKey, chatBaseUrl)
            }
            SettingsTextField("API Key", chatApiKey, isPassword = true) {
                viewModel.updateChatSettings(chatProvider, chatModel, it, chatBaseUrl)
            }
            SettingsTextField("Base URL", chatBaseUrl) {
                viewModel.updateChatSettings(chatProvider, chatModel, chatApiKey, it)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("流式传输", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = chatStreamEnabled,
                    onCheckedChange = { viewModel.updateChatStreamEnabled(it) }
                )
            }

            HorizontalDivider()

            SectionTitle("向量模型配置")
            ProviderSelector(
                    label = "格式",
                    selected = embedProvider,
                    onSelected = { newProvider ->
                        val defaultModel = AIService.getDefaultModel(newProvider)
                        val defaultUrl = AIService.getDefaultBaseUrl(newProvider)
                        viewModel.updateEmbeddingSettings(newProvider, defaultModel, embedApiKey, defaultUrl)
                    }
            )
            SettingsTextField("Model Name", embedModel) {
                viewModel.updateEmbeddingSettings(embedProvider, it, embedApiKey, embedBaseUrl)
            }
            SettingsTextField("API Key", embedApiKey, isPassword = true) {
                viewModel.updateEmbeddingSettings(embedProvider, embedModel, it, embedBaseUrl)
            }
            SettingsTextField("Base URL", embedBaseUrl) {
                viewModel.updateEmbeddingSettings(embedProvider, embedModel, embedApiKey, it)
            }

            HorizontalDivider()

            SectionTitle("记忆与对话逻辑")
            Text("记忆提取频率 (每 $extractionFrequency 次对话)")
            Slider(
                    value = extractionFrequency.toFloat(),
                    onValueChange = { viewModel.updateExtractionFrequency(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 9
            )

            Text("上下文长度 (最近 $contextLength 条对话)")
            Slider(
                    value = contextLength.toFloat(),
                    onValueChange = { viewModel.updateContextLength(it.toInt()) },
                    valueRange = 5f..100f,
                    steps = 95
            )

            // Text("性格预设: $personality")
            // Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            //    listOf("尖锐", "防守", "友好").forEach { p ->
            //        FilterChip(
            //            selected = personality == p,
            //            onClick = { viewModel.updatePersonality(p) },
            //            label = { Text(p) }
            //        )
            //    }
            // }

            HorizontalDivider()

            SectionTitle("记忆管理")
            Button(onClick = onNavigateToMemories, modifier = Modifier.fillMaxWidth()) {
                Text("查看记忆")
            }

            HorizontalDivider()

            SectionTitle("Skills商店")
            Button(onClick = { /* TODO: Open Skills Store */}, modifier = Modifier.fillMaxWidth()) {
                Text("管理Skills")
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTextField(
        label: String,
        value: String,
        isPassword: Boolean = false,
        onValueChange: (String) -> Unit
) {
    var textFieldValue by
            remember(value) {
                mutableStateOf(
                        androidx.compose.ui.text.input.TextFieldValue(
                                value,
                                androidx.compose.ui.text.TextRange(value.length)
                        )
                )
            }

    OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onValueChange(it.text)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation =
                    if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation()
                    else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
fun ProviderSelector(label: String, selected: String, onSelected: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("OpenAI", "Gemini", "Claude").forEach { provider ->
                FilterChip(
                        selected = selected == provider,
                        onClick = { onSelected(provider) },
                        label = { Text(provider) }
                )
            }
        }
    }
}
