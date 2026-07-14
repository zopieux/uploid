package com.uploid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uploid.data.AppDatabase
import com.uploid.data.HistoryEntry
import com.uploid.data.UploadProvider
import com.uploid.ui.UploidTheme
import com.uploid.util.ImageProcessingType
import com.uploid.util.ImageProcessor
import com.uploid.util.Uploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

fun formatSize(bytes: Long): String {
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

fun formatSize(bytes: Int): String = formatSize(bytes.toLong())

private fun sliderPositionToProcessingType(position: Int): ImageProcessingType {
    return when (position) {
        6 -> ImageProcessingType.Original
        0 -> ImageProcessingType.Compress(50)
        1 -> ImageProcessingType.Compress(60)
        2 -> ImageProcessingType.Compress(70)
        3 -> ImageProcessingType.Compress(80)
        4 -> ImageProcessingType.Compress(90)
        5 -> ImageProcessingType.Compress(100)
        else -> ImageProcessingType.Original
    }
}

class UploadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentUris = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (intent.type?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } else {
                    null
                }
                if (uri != null) listOf(uri) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (intent.type?.startsWith("image/") == true) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                } else {
                    null
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }

        if (intentUris.isEmpty()) {
            Toast.makeText(this, "No image shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            UploidTheme {
                ShareScreen(
                    imageUris = intentUris,
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    imageUris: List<Uri>,
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

    // Load downscaled preview bitmaps safely for all URIs
    var previewBitmaps by remember { mutableStateOf<Map<Uri, Bitmap>>(emptyMap()) }

    val currentStep = sliderValue.toInt()
    var liveSizeText by remember { mutableStateOf("...") }
    val processedCache =
        remember { mutableStateMapOf<Pair<Uri, ImageProcessingType>, com.uploid.util.ProcessedResult>() }
    var isSizeLargerThanOriginal by remember { mutableStateOf(false) }

    // Multi-upload counts
    var succeededCount by remember { mutableStateOf(0) }
    var failedCount by remember { mutableStateOf(0) }
    var remainingCount by remember { mutableStateOf(0) }

    LaunchedEffect(currentStep, imageUris) {
        val currentProcessingType = sliderPositionToProcessingType(currentStep)
        val originalProcessingType = ImageProcessingType.Original

        val allStepCached =
            imageUris.all { uri -> processedCache.containsKey(Pair(uri, currentProcessingType)) }
        val allOriginalCached =
            imageUris.all { uri -> processedCache.containsKey(Pair(uri, originalProcessingType)) }

        if (allStepCached && allOriginalCached) {
            val totalSize = imageUris.sumOf { uri ->
                processedCache[Pair(uri, currentProcessingType)]?.bytes?.size?.toLong() ?: 0L
            }
            val originalTotalSize = imageUris.sumOf { uri ->
                processedCache[Pair(uri, originalProcessingType)]?.bytes?.size?.toLong() ?: 0L
            }
            isSizeLargerThanOriginal = currentStep < 6 && totalSize > originalTotalSize

            liveSizeText = formatSize(totalSize)
        } else {
            liveSizeText = "..."
            withContext(Dispatchers.Default) {
                var allSucceeded = true
                val results = coroutineScope {
                    imageUris.map { uri ->
                        async {
                            var processed = processedCache[Pair(uri, currentProcessingType)]
                            var originalProcessed = processedCache[Pair(uri, originalProcessingType)]
                            var success = true

                            if (processed == null) {
                                try {
                                    processed = ImageProcessor.processImage(context, uri, currentProcessingType)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    success = false
                                }
                            }

                            if (originalProcessed == null) {
                                try {
                                    originalProcessed = ImageProcessor.processImage(context, uri, originalProcessingType)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    success = false
                                }
                            }

                            Triple(uri, processed, originalProcessed) to success
                        }
                    }.awaitAll()
                }

                var totalSize = 0L
                var originalTotalSize = 0L
                val updatesToCache = mutableListOf<Pair<Pair<Uri, ImageProcessingType>, com.uploid.util.ProcessedResult>>()

                for ((triple, success) in results) {
                    val (uri, processed, originalProcessed) = triple
                    if (!success || processed == null || originalProcessed == null) {
                        allSucceeded = false
                        break
                    }
                    totalSize += processed.bytes.size.toLong()
                    originalTotalSize += originalProcessed.bytes.size.toLong()

                    if (!processedCache.containsKey(Pair(uri, currentProcessingType))) {
                        updatesToCache.add(Pair(uri, currentProcessingType) to processed)
                    }
                    if (!processedCache.containsKey(Pair(uri, originalProcessingType))) {
                        updatesToCache.add(Pair(uri, originalProcessingType) to originalProcessed)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (allSucceeded) {
                        updatesToCache.forEach { (key, value) ->
                            processedCache[key] = value
                        }
                        isSizeLargerThanOriginal = currentStep < 6 && totalSize > originalTotalSize
                        liveSizeText = formatSize(totalSize)
                    } else {
                        liveSizeText = ""
                        isSizeLargerThanOriginal = false
                    }
                }
            }
        }
    }

    // Load providers & preview
    LaunchedEffect(imageUris) {
        withContext(Dispatchers.IO) {
            // Load providers
            val pList = db.uploadProviderDao().getAllProvidersSync()
            providers = pList
            selectedProvider = pList.find { it.isDefault } ?: pList.firstOrNull()

            // Decode downscaled preview
            val tempMap = mutableMapOf<Uri, Bitmap>()
            for (uri in imageUris) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use {
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
                    context.contentResolver.openInputStream(uri)?.use {
                        val bm = BitmapFactory.decodeStream(it, null, decodeOptions)
                        if (bm != null) {
                            tempMap[uri] = bm
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                previewBitmaps = tempMap
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

                // Image Preview Carousel (stays throughout all states)
                val maxPreviewHeight = (configuration.screenHeightDp * 0.20f).dp

                if (imageUris.size == 1) {
                    val singleUri = imageUris.first()
                    val previewBitmap = previewBitmaps[singleUri]
                    val imageRatio = previewBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f

                    if (previewBitmap != null) {
                        Box(
                            modifier = Modifier
                                .heightIn(max = maxPreviewHeight)
                                .aspectRatio(imageRatio, matchHeightConstraintsFirst = true)
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
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
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { imageUris.size })
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxPreviewHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            val uri = imageUris[page]
                            val bitmap = previewBitmaps[uri]
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(
                                                bitmap.width.toFloat() / bitmap.height.toFloat(),
                                                matchHeightConstraintsFirst = true
                                            )
                                            .background(
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                    alpha = 0.05f
                                                ), RoundedCornerShape(8.dp)
                                            )
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Image preview $page",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.FillBounds
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(1f)
                                            .background(
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                    alpha = 0.05f
                                                ), RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        // Gradient fade edges overlay
                        // Left edge fade
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(48.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Right edge fade
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(48.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
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
                            6 -> if (imageUris.size == 1) "original" else "originals"
                            else -> if (imageUris.size == 1) "original" else "originals"
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (liveSizeText == "...") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                } else if (isSizeLargerThanOriginal) {
                                    val warningColor = Color(0xFFD32F2F) // Non-aggressive red (Material 500/700 style)
                                    val tooltipMessage = "This compression level is counter-productive and a lower quality (or original) should be picked."
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                        tooltip = {
                                            PlainTooltip {
                                                Text(text = tooltipMessage)
                                            }
                                        },
                                        state = rememberTooltipState(),
                                        modifier = Modifier.alignByBaseline()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Warning: size larger than original",
                                                tint = warningColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = liveSizeText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = warningColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = liveSizeText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.alignByBaseline()
                                    )
                                }

                                if (imageUris.size > 1) {
                                    Text(
                                        text = "\u22c5 ${imageUris.size} images",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.alignByBaseline()
                                    )
                                }
                            }
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
                            tooltipText = "Remove all EXIF / metadata from uploaded images."
                        )

                        ReusableToggleRow(
                            title = "Secret URL",
                            checked = secretUrlEnabled,
                            onCheckedChange = {
                                secretUrlEnabled = it
                                prefs.edit().putBoolean("pref_secret_url", it).apply()
                            },
                            tooltipText = "Request the upload provider for a longer URL that cannot be easily guessed. Not all providers support this."
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    UploadState.Uploading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            if (imageUris.size == 1) {
                                Text(
                                    text = "Uploading your image...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    text = "Uploading: $succeededCount succeeded, $failedCount failed, $remainingCount remaining",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
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
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
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
                                        val totalToUpload = imageUris.size
                                        succeededCount = 0
                                        failedCount = 0
                                        remainingCount = totalToUpload
                                        coroutineScope.launch {
                                            val batchTimestamp = System.currentTimeMillis()
                                            var lastSuccessfulLink = ""
                                            val accumulatedEntries = mutableListOf<HistoryEntry>()
                                            for (uri in imageUris) {
                                                try {
                                                    val (resultLink, entry) = withContext(Dispatchers.IO) {
                                                        val targetType =
                                                            sliderPositionToProcessingType(
                                                                targetPosition
                                                            )
                                                        val processed =
                                                            processedCache[Pair(uri, targetType)]
                                                                ?: ImageProcessor.processImage(
                                                                    context,
                                                                    uri,
                                                                    targetType
                                                                )
                                                        val finalBytes = if (stripMetadataEnabled) {
                                                            ImageProcessor.stripMetadata(context, processed.bytes, processed.mimeType)
                                                        } else {
                                                            processed.bytes
                                                        }
                                                        val link = Uploader.upload(provider, finalBytes, processed.mimeType, secretUrlEnabled)
                                                        val thumb = ImageProcessor.createThumbnail(context, uri)
                                                        val entry = HistoryEntry(
                                                            thumbnailBytes = thumb,
                                                            link = link,
                                                            timestamp = batchTimestamp
                                                        )
                                                        Pair(link, entry)
                                                    }
                                                    accumulatedEntries.add(entry)
                                                    lastSuccessfulLink = resultLink
                                                    succeededCount++
                                                    remainingCount--
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    val errorMessage = "Upload failed: ${e.localizedMessage ?: "Unknown error"}"
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(errorMessage)
                                                    }
                                                    failedCount++
                                                    remainingCount--
                                                }
                                            }

                                            if (accumulatedEntries.isNotEmpty()) {
                                                withContext(Dispatchers.IO) {
                                                    db.historyDao().insertAll(accumulatedEntries)
                                                }
                                            }

                                            if (totalToUpload == 1) {
                                                if (succeededCount == 1) {
                                                    uploadResultLink = lastSuccessfulLink
                                                    uploadState = UploadState.Success
                                                } else {
                                                    uploadState = UploadState.Idle
                                                    snackbarHostState.showSnackbar("Upload failed.")
                                                }
                                            } else {
                                                if (succeededCount >= 1) {
                                                    val mainIntent = Intent(context, MainActivity::class.java).apply {
                                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                                        putExtra("select_tab", "history")
                                                    }
                                                    context.startActivity(mainIntent)
                                                    onFinish()
                                                } else {
                                                    uploadState = UploadState.Idle
                                                    snackbarHostState.showSnackbar("All uploads failed.")
                                                }
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
