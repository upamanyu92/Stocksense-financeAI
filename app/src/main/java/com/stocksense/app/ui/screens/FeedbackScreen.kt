package com.stocksense.app.ui.screens

import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stocksense.app.ui.theme.ElectricBlue
import com.stocksense.app.ui.theme.Graphite
import com.stocksense.app.ui.theme.MutedGrey
import com.stocksense.app.viewmodel.FeedbackViewModel
import kotlinx.coroutines.launch

private const val FEEDBACK_EMAIL = "upamanyu.site@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.addAttachment(it, resolveAttachmentName(context, it))
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.addAttachment(it, resolveAttachmentName(context, it))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Graphite)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Share your feedback", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "You can include screenshots or any files that help explain the issue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGrey
                    )
                    Text(
                        "From: ${uiState.userPreferences.displayName.ifBlank { "User" }} · ${uiState.userPreferences.email.ifBlank { "No email set" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricBlue
                    )
                }
            }

            OutlinedTextField(
                value = uiState.feedbackText,
                onValueChange = viewModel::updateFeedbackText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("Message") },
                placeholder = { Text("Tell us what went well, what broke, or what you'd like next.") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add image")
                }
                Button(
                    onClick = { fileLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add file")
                }
            }

            if (uiState.attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attachments", fontWeight = FontWeight.SemiBold)
                    uiState.attachments.forEach { attachment ->
                        Surface(
                            color = Graphite,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = attachment.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = { viewModel.removeAttachment(attachment.uri) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val subject = buildFeedbackSubject(
                        username = uiState.userPreferences.displayName,
                        email = uiState.userPreferences.email
                    )
                    val body = buildFeedbackBody(
                        username = uiState.userPreferences.displayName,
                        email = uiState.userPreferences.email,
                        feedback = uiState.feedbackText
                    )
                    val attachments = ArrayList<Uri>(uiState.attachments.map { it.uri })
                    val intent = Intent(
                        if (attachments.isEmpty()) Intent.ACTION_SENDTO else Intent.ACTION_SEND_MULTIPLE
                    ).apply {
                        if (attachments.isEmpty()) {
                            data = Uri.parse("mailto:$FEEDBACK_EMAIL")
                        }
                        type = if (attachments.isEmpty()) null else "*/*"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        if (attachments.isNotEmpty()) {
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = buildAttachmentClipData(attachments)
                        }
                    }

                    try {
                        context.startActivity(Intent.createChooser(intent, "Send feedback"))
                    } catch (_: ActivityNotFoundException) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No email app available on this device.")
                        }
                    }
                },
                enabled = uiState.feedbackText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Send feedback")
            }
        }
    }
}

private fun buildAttachmentClipData(attachments: List<Uri>): ClipData? {
    if (attachments.isEmpty()) return null
    val clipData = ClipData.newRawUri("feedback_attachment", attachments.first())
    attachments.drop(1).forEach { clipData.addItem(ClipData.Item(it)) }
    return clipData
}

private fun persistReadPermission(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Best effort only; the immediate share intent still gets read permission.
    }
}

private fun buildFeedbackSubject(username: String, email: String): String =
    "stock sense app feedback ( ${username.ifBlank { "user" }} ) ${email.ifBlank { "no-email" }}"

private fun buildFeedbackBody(username: String, email: String, feedback: String): String = buildString {
    appendLine("Username: ${username.ifBlank { "User" }}")
    appendLine("Email: ${email.ifBlank { "Not provided" }}")
    appendLine()
    appendLine("Feedback:")
    append(feedback)
}

private fun resolveAttachmentName(context: android.content.Context, uri: Uri): String {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            cursor.getString(nameIndex)
        } else {
            uri.lastPathSegment ?: "Attachment"
        }
    } ?: (uri.lastPathSegment ?: "Attachment")
}
