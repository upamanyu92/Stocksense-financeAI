package com.stocksense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stocksense.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onNavigateToLlmSettings: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Profile Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("User Profile", style = MaterialTheme.typography.titleMedium)
                        if (!uiState.isEditing) {
                            IconButton(onClick = { viewModel.startEditing() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    }

                    if (uiState.isEditing) {
                        // Edit mode
                        OutlinedTextField(
                            value = uiState.editName,
                            onValueChange = { viewModel.updateEditName(it) },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uiState.editEmail,
                            onValueChange = { viewModel.updateEditEmail(it) },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.saveProfile() },
                                enabled = !uiState.isSaving
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Save")
                                }
                            }
                            OutlinedButton(onClick = { viewModel.cancelEditing() }) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        // Display mode
                        if (uiState.preferences.isLoggedIn) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.padding(12.dp).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column {
                                    Text(
                                        uiState.preferences.displayName.ifBlank { "User" },
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uiState.preferences.email.isNotBlank()) {
                                        Text(
                                            uiState.preferences.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Not logged in. Tap edit to set up your profile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Button(onClick = { viewModel.startEditing() }) {
                                Text("Set Up Profile")
                            }
                        }
                    }
                }
            }

            // Preferences Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Preferences", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Notifications toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Push Notifications", fontWeight = FontWeight.Medium)
                            Text(
                                "Receive alerts for price changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = uiState.preferences.notificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotifications(it) }
                        )
                    }

                    HorizontalDivider()

                    // Dark theme toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark Theme", fontWeight = FontWeight.Medium)
                            Text(
                                "Use dark color scheme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = uiState.preferences.darkThemeEnabled,
                            onCheckedChange = { viewModel.toggleDarkTheme(it) }
                        )
                    }

                    HorizontalDivider()

                    // Quality mode selector
                    Column {
                        Text("Default LLM Quality", fontWeight = FontWeight.Medium)
                        Text(
                            "Select default model quality for AI insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("LITE", "BALANCED", "PRO").forEach { mode ->
                                FilterChip(
                                    selected = uiState.preferences.defaultQualityMode == mode,
                                    onClick = { viewModel.updateQualityMode(mode) },
                                    label = { Text(mode) }
                                )
                            }
                        }
                    }
                }
            }

            // LLM Model Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onNavigateToLlmSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LLM Model Settings", fontWeight = FontWeight.Medium)
                            Text(
                                "Manage AI model downloads and imports",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "LLM Settings",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Account Actions
            if (uiState.preferences.isLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Log Out")
                }
            }
        }
    }
}
