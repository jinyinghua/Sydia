package com.shaun.sydia.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToMemories: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Memory Configuration", style = MaterialTheme.typography.titleMedium)
            
            var extractionFrequency by remember { mutableStateOf(3f) }
            Text("Extraction Frequency (Every ${extractionFrequency.toInt()} turns)")
            Slider(
                value = extractionFrequency,
                onValueChange = { extractionFrequency = it },
                valueRange = 1f..10f,
                steps = 9
            )

            HorizontalDivider()

            Text("Chat Configuration", style = MaterialTheme.typography.titleMedium)
            
            var contextLength by remember { mutableStateOf(10f) }
            Text("Context Length (Last ${contextLength.toInt()} messages)")
            Slider(
                value = contextLength,
                onValueChange = { contextLength = it },
                valueRange = 5f..50f,
                steps = 45
            )

            var selectedPersonality by remember { mutableStateOf("Sharp") }
            Text("Personality Preset: $selectedPersonality")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedPersonality == "Sharp",
                    onClick = { selectedPersonality = "Sharp" },
                    label = { Text("Sharp") }
                )
                FilterChip(
                    selected = selectedPersonality == "Defensive",
                    onClick = { selectedPersonality = "Defensive" },
                    label = { Text("Defensive") }
                )
            }

            HorizontalDivider()
            
            Text("Data Management", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onNavigateToMemories, modifier = Modifier.fillMaxWidth()) {
                Text("View Memories (Brain Dump)")
            }

            HorizontalDivider()
            
            Text("Skills Store", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { /* TODO: Open Skills Store */ }) {
                Text("Manage Skills")
            }
        }
    }
}
