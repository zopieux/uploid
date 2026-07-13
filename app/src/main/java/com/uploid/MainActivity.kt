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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    fun restoreProvider(provider: UploadProvider) {
        viewModelScope.launch {
            db.uploadProviderDao().insert(provider)
            if (provider.isDefault) {
                db.uploadProviderDao().setDefaultProvider(provider.id)
            }
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

    fun deleteHistoryEntry(entry: HistoryEntry) {
        viewModelScope.launch {
            db.historyDao().delete(entry)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
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
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == Screen.History) {
                        (slideInHorizontally(animationSpec = tween(220)) { width -> width } + fadeIn(animationSpec = tween(220)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> -width } + fadeOut(animationSpec = tween(220)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(220)) { width -> -width } + fadeIn(animationSpec = tween(220)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { width -> width } + fadeOut(animationSpec = tween(220)))
                    }
                },
                label = "tab_transition",
                modifier = Modifier.fillMaxSize()
            ) { screen ->
                when (screen) {
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
                            },
                            onDeleteClick = { entry ->
                                viewModel.deleteHistoryEntry(entry)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditProviderDialog(
            provider = providerToEdit,
            onDismiss = { showAddEditDialog = false },
            onDelete = providerToEdit?.let { provider ->
                {
                    viewModel.deleteProvider(provider)
                    pendingDeleteJob?.cancel()
                    pendingDeleteJob = coroutineScope.launch {
                        val snackbarJob = coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Deleted ${provider.name}",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Indefinite
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.restoreProvider(provider)
                            }
                        }
                        kotlinx.coroutines.delay(10000L)
                        snackbarJob.cancel()
                    }
                    showAddEditDialog = false
                }
            },
            onSave = { name, url, param, user, pass, regex ->
                val p = UploadProvider(
                    id = providerToEdit?.id ?: 0L,
                    name = name,
                    targetUrl = url,
                    fileParam = param,
                    authUser = user,
                    authPass = pass,
                    responseRegex = regex,
                    isDefault = providerToEdit?.isDefault ?: false
                )
                viewModel.saveProvider(p)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun ScreenSkeleton(
    title: String,
    actionButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
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
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (actionButton != null) {
                actionButton()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

private fun getPixelGroupShape(index: Int, totalCount: Int): RoundedCornerShape {
    return if (totalCount == 1) {
        RoundedCornerShape(24.dp)
    } else {
        when (index) {
            0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            totalCount - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        }
    }
}

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
            TooltipIconButton(
                onClick = onAddClick,
                icon = Icons.Default.Add,
                contentDescription = "Add provider",
                tooltipText = "Add provider"
            )
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(providers, key = { _, provider -> provider.id }) { index, provider ->
                    val shape = getPixelGroupShape(index, providers.size)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = shape)
                            .clip(shape)
                            .clickable { onEditClick(provider) }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = provider.targetUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TooltipIconButton(
                                onClick = { onSetDefaultClick(provider) },
                                icon = if (provider.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Set default",
                                tooltipText = "Set default",
                                tint = if (provider.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        }
                    }
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
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
fun AddEditProviderDialog(
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
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

                    Row {
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
                                        responseRegex.ifBlank { null }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tooltipText: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltipText)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@Composable
fun HistoryView(
    history: List<HistoryEntry>,
    onClearClick: () -> Unit,
    onDeleteClick: (HistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    ScreenSkeleton(
        title = "History",
        actionButton = {
            if (history.isNotEmpty()) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onClearClick() }
                        .padding(8.dp)
                )
            }
        }
    ) {
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                    val bitmap = remember(entry.thumbnailBytes) {
                        BitmapFactory.decodeByteArray(entry.thumbnailBytes, 0, entry.thumbnailBytes.size)?.asImageBitmap()
                    }
                    val shape = getPixelGroupShape(index, history.size)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = shape)
                            .clip(shape)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Thumbnail",
                                modifier = Modifier
                                    .size(64.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No image",
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TooltipIconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("uploid link", entry.link)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied link to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Default.ContentCopy,
                                contentDescription = "Copy link",
                                tooltipText = "Copy link"
                            )

                            TooltipIconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, entry.link)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                },
                                icon = Icons.Default.Share,
                                contentDescription = "Share link",
                                tooltipText = "Share link"
                            )

                            TooltipIconButton(
                                onClick = {
                                    onDeleteClick(entry)
                                },
                                icon = Icons.Default.Delete,
                                contentDescription = "Delete entry",
                                tooltipText = "Delete entry"
                            )
                        }
                    }
                }
            }
        }
    }
}
