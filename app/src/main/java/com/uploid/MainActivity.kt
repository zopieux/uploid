package com.uploid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uploid.data.AppDatabase
import com.uploid.data.HistoryEntry
import com.uploid.data.UploadProvider
import com.uploid.ui.UploidTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(private val db: AppDatabase) : ViewModel() {

    val providers: StateFlow<List<UploadProvider>> = db.uploadProviderDao()
        .getAllProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = db.historyDao()
        .getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProvider(provider: UploadProvider) {
        viewModelScope.launch {
            if (provider.id == 0L) {
                val newId = db.uploadProviderDao().insert(provider)
                if (provider.isDefault) {
                    db.uploadProviderDao().setDefaultProvider(newId)
                }
            } else {
                db.uploadProviderDao().update(provider)
                if (provider.isDefault) {
                    db.uploadProviderDao().setDefaultProvider(provider.id)
                }
            }
        }
    }

    fun deleteProvider(provider: UploadProvider) {
        viewModelScope.launch {
            db.uploadProviderDao().delete(provider)
        }
    }

    fun setAsDefault(provider: UploadProvider) {
        viewModelScope.launch {
            db.uploadProviderDao().setDefaultProvider(provider.id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.historyDao().clearHistory()
        }
    }
}

class MainViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val viewModel = ViewModelProvider(this, MainViewModelFactory(db))[MainViewModel::class.java]

        setContent {
            UploidTheme {
                MainScreen(viewModel)
            }
        }
    }
}

enum class Screen {
    Settings, History
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.Settings) }
    val providers by viewModel.providers.collectAsState()
    val history by viewModel.history.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var providerToEdit by remember { mutableStateOf<UploadProvider?>(null) }

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentScreen = Screen.Settings },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (currentScreen == Screen.Settings) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentScreen == Screen.Settings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { currentScreen = Screen.History },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (currentScreen == Screen.History) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentScreen == Screen.History) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                Screen.Settings -> {
                    SettingsView(
                        providers = providers,
                        onAddClick = {
                            providerToEdit = null
                            showAddEditDialog = true
                        },
                        onEditClick = { provider ->
                            providerToEdit = provider
                            showAddEditDialog = true
                        },
                        onDeleteClick = { provider ->
                            viewModel.deleteProvider(provider)
                        },
                        onSetDefaultClick = { provider ->
                            viewModel.setAsDefault(provider)
                        }
                    )
                }
                Screen.History -> {
                    HistoryView(
                        history = history,
                        onClearClick = {
                            viewModel.clearHistory()
                        }
                    )
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditProviderDialog(
            provider = providerToEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, url, param, user, pass, regex, isDefault ->
                val p = UploadProvider(
                    id = providerToEdit?.id ?: 0L,
                    name = name,
                    targetUrl = url,
                    fileParam = param,
                    authUser = user,
                    authPass = pass,
                    responseRegex = regex,
                    isDefault = isDefault
                )
                viewModel.saveProvider(p)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun SettingsView(
    providers: List<UploadProvider>,
    onAddClick: () -> Unit,
    onEditClick: (UploadProvider) -> Unit,
    onDeleteClick: (UploadProvider) -> Unit,
    onSetDefaultClick: (UploadProvider) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Providers",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "+ Add",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onAddClick() }
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetDefaultClick(provider) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (provider.isDefault) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(Default)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = provider.targetUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "File Param: ${provider.fileParam}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                            }
                            Row {
                                Text(
                                    text = "Edit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onEditClick(provider) }
                                        .padding(8.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable { onDeleteClick(provider) }
                                        .padding(8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditProviderDialog(
    provider: UploadProvider?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        url: String,
        param: String,
        user: String?,
        pass: String?,
        regex: String?,
        isDefault: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var targetUrl by remember { mutableStateOf(provider?.targetUrl ?: "") }
    var fileParam by remember { mutableStateOf(provider?.fileParam ?: "file") }
    var authUser by remember { mutableStateOf(provider?.authUser ?: "") }
    var authPass by remember { mutableStateOf(provider?.authPass ?: "") }
    var responseRegex by remember { mutableStateOf(provider?.responseRegex ?: "") }
    var isDefault by remember { mutableStateOf(provider?.isDefault ?: false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = if (provider == null) "Add Provider" else "Edit Provider",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. x0.at)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = targetUrl,
                    onValueChange = { targetUrl = it },
                    label = { Text("Target URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = fileParam,
                    onValueChange = { fileParam = it },
                    label = { Text("File Parameter (e.g. file)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = authUser,
                    onValueChange = { authUser = it },
                    label = { Text("Basic Auth Username (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = authPass,
                    onValueChange = { authPass = it },
                    label = { Text("Basic Auth Password (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = responseRegex,
                    onValueChange = { responseRegex = it },
                    label = { Text("Response Regex (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Text(
                        text = "Set as default provider",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && targetUrl.isNotBlank() && fileParam.isNotBlank()) {
                                onSave(
                                    name,
                                    targetUrl,
                                    fileParam,
                                    authUser.ifBlank { null },
                                    authPass.ifBlank { null },
                                    responseRegex.ifBlank { null },
                                    isDefault
                                )
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryView(
    history: List<HistoryEntry>,
    onClearClick: () -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onClearClick() }
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "History is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    val bitmap = remember(entry.thumbnailBytes) {
                        BitmapFactory.decodeByteArray(entry.thumbnailBytes, 0, entry.thumbnailBytes.size)?.asImageBitmap()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Copy link to clipboard
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("uploid link", entry.link)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied link to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "thumbnail",
                                modifier = Modifier
                                    .size(64.dp)
                                    .aspectRatio(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Img",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.link,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sdf.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, entry.link)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }
                                .padding(8.dp)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), thickness = 0.5.dp)
                }
            }
        }
    }
}
