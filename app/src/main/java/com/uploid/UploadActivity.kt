package com.uploid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uploid.data.AppDatabase
import com.uploid.data.HistoryEntry
import com.uploid.data.UploadProvider
import com.uploid.ui.UploidTheme
import com.uploid.util.ImageProcessor
import com.uploid.util.Uploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import android.text.TextUtils
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb

fun formatSize(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val unit = when (exp) {
        1 -> "KiB"
        2 -> "MiB"
        3 -> "GiB"
        else -> "TiB"
    }
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), unit)
}

class UploadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentUri = if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } else {
            null
        }

        if (intentUri == null) {
            Toast.makeText(this, "No image shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            UploidTheme {
                ShareScreen(
                    imageUri = intentUri,
                    onFinish = { finish() }
                )
            }
        }
    }
}

enum class UploadState {
    Idle, Uploading, Success
}

@Composable
fun ReusableToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        Text(
            text = tooltipText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}



@Composable
fun ShareScreen(
    imageUri: Uri,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }

    var providers by remember { mutableStateOf<List<UploadProvider>>(emptyList()) }
    var selectedProvider by remember { mutableStateOf<UploadProvider?>(null) }
    var uploadState by remember { mutableStateOf(UploadState.Idle) }
    var uploadResultLink by remember { mutableStateOf("") }

    // Slider position: 0 to 6 (snappy)
    var sliderValue by remember { mutableStateOf(6f) }

    val prefs = remember { context.getSharedPreferences("uploid_prefs", Context.MODE_PRIVATE) }
    var stripMetadataEnabled by remember { mutableStateOf(prefs.getBoolean("pref_strip_metadata", true)) }
    var secretUrlEnabled by remember { mutableStateOf(prefs.getBoolean("pref_secret_url", false)) }

    // Load image preview bitmap safely (downscaled for UI performance)
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val currentStep = sliderValue.toInt()
    var liveSizeText by remember { mutableStateOf("...") }
    val processedCache = remember { mutableStateMapOf<Int, com.uploid.util.ProcessedResult>() }

    LaunchedEffect(currentStep, imageUri) {
        val cached = processedCache[currentStep]
        if (cached != null) {
            liveSizeText = formatSize(cached.bytes.size)
        } else {
            withContext(Dispatchers.Default) {
                try {
                    val processed = ImageProcessor.processImage(context, imageUri, currentStep)
                    val bytesCount = processed.bytes.size
                    val formatted = formatSize(bytesCount)
                    withContext(Dispatchers.Main) {
                        processedCache[currentStep] = processed
                        liveSizeText = formatted
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        liveSizeText = ""
                    }
                }
            }
        }
    }

    // Load providers & preview
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            // Load providers
            val pList = db.uploadProviderDao().getAllProvidersSync()
            providers = pList
            selectedProvider = pList.find { it.isDefault } ?: pList.firstOrNull()

            // Decode downscaled preview
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                val maxDim = kotlin.math.max(options.outWidth, options.outHeight)
                var sampleSize = 1
                if (maxDim > 1024) {
                    sampleSize = maxDim / 1024
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                context.contentResolver.openInputStream(imageUri)?.use {
                    previewBitmap = BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "uploid",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Image Preview (stays throughout all states)
                val maxPreviewHeight = (configuration.screenHeightDp * 0.20f).dp
                val imageRatio = previewBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f

                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(max = maxPreviewHeight)
                            .aspectRatio(imageRatio, matchHeightConstraintsFirst = true)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Image preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .height(maxPreviewHeight)
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (uploadState) {
                    UploadState.Idle -> {
                        // Providers selection
                        Text(
                            text = "Select provider",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (providers.isEmpty()) {
                            Text(
                                text = "No providers available. Please configure one in Settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                providers.forEachIndexed { index, provider ->
                                    val shape = getPixelGroupShape(index, providers.size)
                                    ProviderRow(
                                        name = provider.name,
                                        targetUrl = null,
                                        shape = shape,
                                        modifier = Modifier.clickable { selectedProvider = provider },
                                        leadingContent = {
                                            RadioButton(
                                                selected = (selectedProvider?.id == provider.id),
                                                onClick = { selectedProvider = provider }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Compact single-row headers for Quality
                        val label = when (sliderValue.toInt()) {
                            0 -> "JPEG, 50%"
                            1 -> "JPEG, 60%"
                            2 -> "JPEG, 70%"
                            3 -> "JPEG, 80%"
                            4 -> "JPEG, 90%"
                            5 -> "JPEG, 100%"
                            6 -> "Original"
                            else -> "Original"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Quality",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "($label)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = liveSizeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it.coerceIn(0f, 6f) },
                            valueRange = 0f..6f,
                            steps = 5,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ReusableToggleRow(
                            title = "Strip metadata",
                            checked = stripMetadataEnabled,
                            onCheckedChange = {
                                stripMetadataEnabled = it
                                prefs.edit().putBoolean("pref_strip_metadata", it).apply()
                            },
                            tooltipText = "Remove all Exif and location metadata from the JPEG or PNG file before uploading."
                        )

                        ReusableToggleRow(
                            title = "Secret URL",
                            checked = secretUrlEnabled,
                            onCheckedChange = {
                                secretUrlEnabled = it
                                prefs.edit().putBoolean("pref_secret_url", it).apply()
                            },
                            tooltipText = "Requests the upload provider for a longer URL that cannot be easily guessed."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    UploadState.Uploading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Uploading your image...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    UploadState.Success -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Upload successful!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                            AndroidView(
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        maxLines = 1
                                        ellipsize = TextUtils.TruncateAt.MIDDLE
                                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                        setTypeface(null, android.graphics.Typeface.BOLD)
                                    }
                                },
                                update = { textView ->
                                    textView.text = uploadResultLink
                                    textView.setTextColor(primaryColor)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("uploid link", uploadResultLink)
                                        clipboard.setPrimaryClip(clip)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Copied to clipboard")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy link")
                                }

                                Button(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, uploadResultLink)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, null)
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Share link")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(140.dp))
            }

            if (uploadState != UploadState.Uploading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(horizontal = 24.dp)
                        .padding(top = 48.dp, bottom = 16.dp)
                ) {
                    when (uploadState) {
                        UploadState.Idle -> {
                            Button(
                                onClick = {
                                    val provider = selectedProvider
                                    if (provider != null) {
                                        uploadState = UploadState.Uploading
                                        val targetPosition = sliderValue.toInt()
                                        coroutineScope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    val processed = processedCache[targetPosition] ?: ImageProcessor.processImage(context, imageUri, targetPosition)
                                                    val finalBytes = if (stripMetadataEnabled) {
                                                        ImageProcessor.stripMetadata(context, processed.bytes, processed.mimeType)
                                                    } else {
                                                        processed.bytes
                                                    }
                                                    val link = Uploader.upload(provider, finalBytes, processed.mimeType, secretUrlEnabled)
                                                    val thumb = ImageProcessor.createThumbnail(context, imageUri)
                                                    db.historyDao().insert(
                                                        HistoryEntry(
                                                            thumbnailBytes = thumb,
                                                            link = link,
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                    )
                                                    link
                                                }
                                                uploadResultLink = result
                                                uploadState = UploadState.Success
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                snackbarHostState.showSnackbar("Upload failed: ${e.localizedMessage ?: "Unknown error"}")
                                                uploadState = UploadState.Idle
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Please configure/select a provider")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = (selectedProvider != null)
                            ) {
                                Text("Upload", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        UploadState.Success -> {
                            Button(
                                onClick = onFinish,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Close", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
