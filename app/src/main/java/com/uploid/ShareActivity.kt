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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

class ShareActivity : ComponentActivity() {

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

    // Slider position: 0 to 4 (snappy)
    var sliderValue by remember { mutableStateOf(0f) }

    // Load image preview bitmap safely (downscaled for UI performance)
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "uploid",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Image Preview (stays throughout all states)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Image preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (uploadState) {
                UploadState.Idle -> {
                    // Providers selection
                    Text(
                        text = "Select Provider",
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
                        providers.forEach { provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProvider = provider }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedProvider?.id == provider.id),
                                    onClick = { selectedProvider = provider }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selectedProvider?.id == provider.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = provider.targetUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Quality slider
                    Text(
                        text = "Quality Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val label = when (sliderValue.toInt()) {
                        0 -> "Original (No modification)"
                        1 -> "High (JPEG 100)"
                        2 -> "Medium-High (JPEG 83)"
                        3 -> "Medium-Low (JPEG 66)"
                        4 -> "Low (JPEG 50)"
                        else -> "Original"
                    }

                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it.coerceIn(0f, 4f) },
                        valueRange = 0f..4f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val provider = selectedProvider
                            if (provider != null) {
                                uploadState = UploadState.Uploading
                                val targetPosition = sliderValue.toInt()
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val processed = ImageProcessor.processImage(context, imageUri, targetPosition)
                                            val link = Uploader.upload(provider, processed.bytes, processed.mimeType)
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
                                        Toast.makeText(context, "Upload failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                        uploadState = UploadState.Idle
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please configure/select a provider", Toast.LENGTH_SHORT).show()
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
                            text = "Upload Successful!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = uploadResultLink,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
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
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy Link")
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
                                Text("Share Link")
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onFinish,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
