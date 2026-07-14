package com.uploid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uploid.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryView(
    history: List<HistoryEntry>,
    onClearClick: () -> Unit,
    onDeleteClick: (HistoryEntry) -> Unit,
    onShowSnackbar: (String) -> Unit
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
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                    val bitmapState = produceState<ImageBitmap?>(initialValue = null, entry.thumbnailBytes) {
                        value = withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(entry.thumbnailBytes, 0, entry.thumbnailBytes.size)?.asImageBitmap()
                        }
                    }
                    val bitmap = bitmapState.value
                    val isPrevSame = index > 0 && history[index - 1].timestamp == entry.timestamp
                    val isNextSame = index < history.size - 1 && history[index + 1].timestamp == entry.timestamp

                    val shape = remember(index, history.size, isPrevSame, isNextSame) {
                        val isFirstInList = index == 0
                        val isLastInList = index == history.size - 1
                        val isSingleInList = history.size == 1

                        val standardTop = if (isSingleInList || isFirstInList) 24.dp else 4.dp
                        val standardBottom = if (isSingleInList || isLastInList) 24.dp else 4.dp

                        val topStart = if (isPrevSame) 0.dp else standardTop
                        val bottomStart = if (isNextSame) 0.dp else standardBottom

                        RoundedCornerShape(
                            topStart = topStart,
                            topEnd = standardTop,
                            bottomStart = bottomStart,
                            bottomEnd = standardBottom
                        )
                    }

                    val bottomPadding = if (isNextSame) 0.dp else 4.dp

                    val displayLink = remember(entry.link) {
                        val clean = entry.link
                        if (clean.startsWith("https://", ignoreCase = true)) {
                            clean.substring(8)
                        } else if (clean.startsWith("http://", ignoreCase = true)) {
                            clean.substring(7)
                        } else {
                            clean
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(bottom = bottomPadding)
                            .background(MaterialTheme.colorScheme.surface, shape = shape)
                            .clip(shape)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
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
                                    text = displayLink,
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
                                        onShowSnackbar("Copied link to clipboard")
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

                        // Left vertical accent strip
                        if (isPrevSame || isNextSame) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
