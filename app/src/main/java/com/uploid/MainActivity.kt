package com.uploid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uploid.data.AppDatabase
import com.uploid.data.HistoryEntry
import com.uploid.data.UploadProvider
import com.uploid.ui.PurpleAccent
import com.uploid.ui.UploidTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private var initialScreen by mutableStateOf(Screen.Settings)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val viewModel = ViewModelProvider(this, MainViewModelFactory(db))[MainViewModel::class.java]

        handleIntent(intent)

        setContent {
            UploidTheme {
                MainScreen(viewModel, initialScreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("select_tab") == "history") {
            initialScreen = Screen.History
        }
    }
}

enum class Screen {
    Settings, History
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, initialScreen: Screen = Screen.Settings) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    LaunchedEffect(initialScreen) {
        currentScreen = initialScreen
    }
    val providers by viewModel.providers.collectAsState()
    val history by viewModel.history.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var providerToEdit by remember { mutableStateOf<UploadProvider?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val intent = Intent(context, UploadActivity::class.java).apply {
                if (uris.size == 1) {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                } else {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), thickness = 0.5.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
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

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .background(PurpleAccent, shape = CircleShape)
                            .clip(CircleShape)
                            .clickable { filePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Manual upload image",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
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
                            },
                            onShowSnackbar = { message ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditProviderSheet(
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


