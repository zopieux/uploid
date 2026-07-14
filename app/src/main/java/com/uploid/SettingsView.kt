package com.uploid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uploid.data.UploadProvider
import com.uploid.ui.PurpleAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    providers: List<UploadProvider>,
    onAddClick: () -> Unit,
    onEditClick: (UploadProvider) -> Unit,
    onSetDefaultClick: (UploadProvider) -> Unit
) {
    ScreenSkeleton(
        title = "Providers",
        actionButton = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = "Add a new provider")
                    }
                },
                state = rememberTooltipState()
            ) {
                TextButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add provider"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    ) {
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No providers configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(providers, key = { _, provider -> provider.id }) { index, provider ->
                    val shape = getPixelGroupShape(index, providers.size)
                    ProviderRow(
                        name = provider.name,
                        targetUrl = provider.targetUrl,
                        shape = shape,
                        modifier = Modifier.clickable { onEditClick(provider) },
                        trailingContent = {
                            TooltipIconButton(
                                onClick = { onSetDefaultClick(provider) },
                                icon = if (provider.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Set default",
                                tooltipText = "Set default",
                                tint = if (provider.isDefault) PurpleAccent else MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.4f
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProviderSheet(
    provider: UploadProvider?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (
        name: String,
        url: String,
        param: String,
        user: String?,
        pass: String?,
        regex: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var targetUrl by remember { mutableStateOf(provider?.targetUrl ?: "") }
    var fileParam by remember { mutableStateOf(provider?.fileParam ?: "file") }
    var authUser by remember { mutableStateOf(provider?.authUser ?: "") }
    var authPass by remember { mutableStateOf(provider?.authPass ?: "") }
    var responseRegex by remember { mutableStateOf(provider?.responseRegex ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = if (provider == null) "Add provider" else "Edit provider",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            FormField(
                label = "Name",
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormField(
                label = "Target URL",
                value = targetUrl,
                onValueChange = { targetUrl = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormField(
                label = "File parameter",
                value = fileParam,
                onValueChange = { fileParam = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormField(
                label = "Basic auth username (optional)",
                value = authUser,
                onValueChange = { authUser = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormField(
                label = "Basic auth password (optional)",
                value = authPass,
                onValueChange = { authPass = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormField(
                label = "Response regex (optional)",
                value = responseRegex,
                onValueChange = { responseRegex = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (name.isNotBlank() && targetUrl.isNotBlank() && fileParam.isNotBlank()) {
                            onSave(
                                name,
                                targetUrl,
                                fileParam,
                                authUser.ifBlank { null },
                                authPass.ifBlank { null },
                                responseRegex.ifBlank { null }
                            )
                        }
                    }
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
