package com.uploid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.uploid.data.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val THUMB_SIZE = 64.dp
private val THUMB_PADDING = 16.dp
private val CONNECTOR_WIDTH = THUMB_SIZE / 2
private val SLOT_RADIUS = 10.dp
private val SLOT_OFFSET_Y = 3.dp
private val GAP_SIZE = 4.dp
private val CONNECTOR_HEIGHT = 10.dp

private class PuzzleShape(
    val hasTopHole: Boolean,
    val hasBottomHole: Boolean,
    val isFirstInList: Boolean,
    val isLastInList: Boolean,
    val isSingleInList: Boolean
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val px = with(density) {
            object {
                val standardTop = (if (isSingleInList || isFirstInList) 24.dp else GAP_SIZE).toPx()
                val standardBottom =
                    (if (isSingleInList || isLastInList) 24.dp else GAP_SIZE).toPx()
                val xStart = (THUMB_PADDING + (THUMB_SIZE - CONNECTOR_WIDTH) / 2).toPx()
                val xEnd =
                    (THUMB_PADDING + (THUMB_SIZE - CONNECTOR_WIDTH) / 2 + CONNECTOR_WIDTH).toPx()
                val rHole = SLOT_RADIUS.toPx()
                val offsetY = SLOT_OFFSET_Y.toPx()
            }
        }

        val path = Path().apply {
            val roundRect = RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(px.standardTop),
                topRightCornerRadius = CornerRadius(px.standardTop),
                bottomLeftCornerRadius = CornerRadius(px.standardBottom),
                bottomRightCornerRadius = CornerRadius(px.standardBottom)
            )
            addRoundRect(roundRect)
        }

        var resultPath = path

        if (hasTopHole) {
            val holePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = px.xStart,
                        top = -px.offsetY - px.rHole,
                        right = px.xEnd,
                        bottom = -px.offsetY + px.rHole,
                        cornerRadius = CornerRadius(px.rHole)
                    )
                )
            }
            resultPath = Path.combine(PathOperation.Difference, resultPath, holePath)
        }

        if (hasBottomHole) {
            val holePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = px.xStart,
                        top = size.height + px.offsetY - px.rHole,
                        right = px.xEnd,
                        bottom = size.height + px.offsetY + px.rHole,
                        cornerRadius = CornerRadius(px.rHole)
                    )
                )
            }
            resultPath = Path.combine(PathOperation.Difference, resultPath, holePath)
        }

        return Outline.Generic(resultPath)
    }
}

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
        title = "Past uploads",
        actionButton = {
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear all"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear all",
                        fontWeight = FontWeight.Bold
                    )
                }
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
                contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                    val bitmapState = produceState<ImageBitmap?>(initialValue = null, entry.thumbnailBytes) {
                        value = withContext(Dispatchers.Default) {
                            BitmapFactory.decodeByteArray(entry.thumbnailBytes, 0, entry.thumbnailBytes.size)?.asImageBitmap()
                        }
                    }
                    val bitmap = bitmapState.value
                    val isNextSame = index < history.size - 1 && history[index + 1].timestamp == entry.timestamp

                    val isFirstInList = index == 0
                    val isLastInList = index == history.size - 1
                    val isSingleInList = history.size == 1


                    val isPrevSame = index > 0 && history[index - 1].timestamp == entry.timestamp
                    val shape = remember(
                        isPrevSame,
                        isNextSame,
                        isFirstInList,
                        isLastInList,
                        isSingleInList
                    ) {
                        PuzzleShape(
                            hasTopHole = isPrevSame,
                            hasBottomHole = isNextSame,
                            isFirstInList = isFirstInList,
                            isLastInList = isLastInList,
                            isSingleInList = isSingleInList
                        )
                    }

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
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(bottom = GAP_SIZE)
                                .background(MaterialTheme.colorScheme.surface, shape = shape)
                                .clip(shape)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(THUMB_PADDING),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier
                                            .size(THUMB_SIZE)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(THUMB_SIZE)
                                            .background(
                                                MaterialTheme.colorScheme.onBackground.copy(
                                                    alpha = 0.05f
                                                ), RoundedCornerShape(12.dp)
                                            ),
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
                        }

                        // Accent connector pill bridging the bottom cutout
                        if (isNextSame) {
                            val pillWidth =
                                CONNECTOR_WIDTH - (SLOT_RADIUS - CONNECTOR_HEIGHT / 2) * 2
                            val paddingStart =
                                THUMB_PADDING + (THUMB_SIZE - CONNECTOR_WIDTH) / 2 + (SLOT_RADIUS - CONNECTOR_HEIGHT / 2)
                            val offsetY = (CONNECTOR_HEIGHT - GAP_SIZE) / 2
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = paddingStart)
                                    .offset(y = offsetY)
                                    .size(width = pillWidth, height = CONNECTOR_HEIGHT)
                                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
