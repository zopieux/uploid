package com.uploid.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

sealed interface ImageProcessingType {
    object Original : ImageProcessingType
    data class Compress(val quality: Int) : ImageProcessingType
}

sealed class ImageSizeType {
    object Original : ImageSizeType()
    data class Resized(val maxDim: Int) : ImageSizeType()
}

object ImageProcessor {

    private class Contribution(
        val minIdx: Int,
        val maxIdx: Int,
        val weights: DoubleArray
    )

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val piX = Math.PI * x
        return Math.sin(piX) / piX
    }

    private fun lanczos3(x: Double): Double {
        val absX = Math.abs(x)
        if (absX >= 3.0) return 0.0
        return sinc(absX) * sinc(absX / 3.0)
    }

    private fun precomputeContributions(srcSize: Int, dstSize: Int): Array<Contribution> {
        val scale = dstSize.toDouble() / srcSize
        val filterScale = if (scale < 1.0) scale else 1.0
        val support = 3.0 / filterScale

        return Array(dstSize) { dstIdx ->
            val center = (dstIdx + 0.5) / scale - 0.5
            val minIdx = Math.ceil(center - support).toInt().coerceAtLeast(0)
            val maxIdx = Math.floor(center + support).toInt().coerceAtMost(srcSize - 1)

            val count = maxIdx - minIdx + 1
            val weights = DoubleArray(count)
            var sum = 0.0
            for (i in 0 until count) {
                val srcIdx = minIdx + i
                val w = lanczos3((srcIdx - center) * filterScale)
                weights[i] = w
                sum += w
            }
            if (sum != 0.0) {
                for (i in 0 until count) {
                    weights[i] /= sum
                }
            }
            Contribution(minIdx, maxIdx, weights)
        }
    }

    fun resizeLanczos3(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcW = src.width
        val srcH = src.height

        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        // Horizontal pass
        val horizontalContributions = precomputeContributions(srcW, targetW)
        val intermediatePixels = IntArray(targetW * srcH)
        for (y in 0 until srcH) {
            val ySrcOffset = y * srcW
            val yInterpOffset = y * targetW
            for (x in 0 until targetW) {
                val contrib = horizontalContributions[x]
                var aSum = 0.0
                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                for (i in 0 until contrib.weights.size) {
                    val srcIdx = ySrcOffset + (contrib.minIdx + i)
                    val p = srcPixels[srcIdx]
                    val w = contrib.weights[i]
                    aSum += ((p shr 24) and 0xff) * w
                    rSum += ((p shr 16) and 0xff) * w
                    gSum += ((p shr 8) and 0xff) * w
                    bSum += (p and 0xff) * w
                }
                val aFinal = Math.round(aSum).toInt().coerceIn(0, 255)
                val rFinal = Math.round(rSum).toInt().coerceIn(0, 255)
                val gFinal = Math.round(gSum).toInt().coerceIn(0, 255)
                val bFinal = Math.round(bSum).toInt().coerceIn(0, 255)
                intermediatePixels[yInterpOffset + x] = (aFinal shl 24) or (rFinal shl 16) or (gFinal shl 8) or bFinal
            }
        }

        // Vertical pass
        val verticalContributions = precomputeContributions(srcH, targetH)
        val dstPixels = IntArray(targetW * targetH)
        for (x in 0 until targetW) {
            for (y in 0 until targetH) {
                val contrib = verticalContributions[y]
                var aSum = 0.0
                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                for (i in 0 until contrib.weights.size) {
                    val srcY = contrib.minIdx + i
                    val p = intermediatePixels[srcY * targetW + x]
                    val w = contrib.weights[i]
                    aSum += ((p shr 24) and 0xff) * w
                    rSum += ((p shr 16) and 0xff) * w
                    gSum += ((p shr 8) and 0xff) * w
                    bSum += (p and 0xff) * w
                }
                val aFinal = Math.round(aSum).toInt().coerceIn(0, 255)
                val rFinal = Math.round(rSum).toInt().coerceIn(0, 255)
                val gFinal = Math.round(gSum).toInt().coerceIn(0, 255)
                val bFinal = Math.round(bSum).toInt().coerceIn(0, 255)
                dstPixels[y * targetW + x] = (aFinal shl 24) or (rFinal shl 16) or (gFinal shl 8) or bFinal
            }
        }

        val dstBitmap = Bitmap.createBitmap(targetW, targetH, src.config ?: Bitmap.Config.ARGB_8888)
        dstBitmap.setPixels(dstPixels, 0, targetW, 0, 0, targetW, targetH)
        return dstBitmap
    }

    // Lazily-initialized list of ExifInterface tag names collected once via reflection
    private val exifTags: List<String> by lazy {
        val tags = mutableListOf<String>()
        val fields = ExifInterface::class.java.fields
        for (field in fields) {
            if (field.name.startsWith("TAG_") && field.type == String::class.java) {
                try {
                    val tagName = field.get(null) as? String
                    if (tagName != null) {
                        tags.add(tagName)
                    }
                } catch (e: Exception) {
                    // Ignore reflection/unsupported operations
                }
            }
        }
        tags
    }

    /**
     * Strips EXIF metadata from JPEG or PNG byte arrays using the well-known
     * AndroidX ExifInterface library. Retains layout/rendering-essential tags
     * like orientation and dimensions, but clears all privacy-sensitive fields.
     */
    fun stripMetadata(context: Context, bytes: ByteArray, mimeType: String): ByteArray {
        if (mimeType != "image/jpeg" && mimeType != "image/png" && mimeType != "image/webp") {
            return bytes
        }
        val tempFile = File.createTempFile("uploid_strip", null, context.cacheDir)
        return try {
            tempFile.writeBytes(bytes)
            val exifInterface = ExifInterface(tempFile.absolutePath)

            // Use the cached exifTags instead of performing reflection each time
            for (tagName in exifTags) {
                // Retain orientation and dimensions to prevent layout/display issues
                if (tagName != ExifInterface.TAG_IMAGE_WIDTH &&
                    tagName != ExifInterface.TAG_IMAGE_LENGTH &&
                    tagName != ExifInterface.TAG_ORIENTATION &&
                    tagName != ExifInterface.TAG_PIXEL_X_DIMENSION &&
                    tagName != ExifInterface.TAG_PIXEL_Y_DIMENSION) {
                    exifInterface.setAttribute(tagName, null)
                }
            }
            // Explicitly ensure GPS attributes are removed
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exifInterface.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)

            exifInterface.saveAttributes()
            tempFile.readBytes()
        } catch (e: Exception) {
            e.printStackTrace()
            bytes
        } finally {
            tempFile.delete()
        }
    }

    fun processImage(
        context: Context,
        uri: Uri,
        sizeType: ImageSizeType,
        processingType: ImageProcessingType
    ): ProcessedResult {
        val contentResolver = context.contentResolver

        // 1. If both are Original, keep original bytes as is
        if (sizeType is ImageSizeType.Original && processingType is ImageProcessingType.Original) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot read image URI")
            // Resolve mimetype or fallback to jpeg
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            return ProcessedResult(bytes, mimeType)
        }

        // If no compression is requested and image size is within requested maximum dimension, return original bytes
        if (processingType is ImageProcessingType.Original && sizeType is ImageSizeType.Resized) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val srcMax = kotlin.math.max(options.outWidth, options.outHeight)
            if (srcMax <= sizeType.maxDim) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Cannot read image URI")
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                return ProcessedResult(bytes, mimeType)
            }
        }

        // Decode the original bitmap
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image input stream")
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Failed to decode bitmap")

        val srcW = originalBitmap.width
        val srcH = originalBitmap.height

        // Resize if requested and image is too large
        val resizedBitmap = if (sizeType is ImageSizeType.Resized) {
            val maxDim = sizeType.maxDim
            val srcMax = kotlin.math.max(srcW, srcH)
            if (srcMax > maxDim) {
                val scale = maxDim.toDouble() / srcMax
                val targetW = (srcW * scale).toInt().coerceAtLeast(1)
                val targetH = (srcH * scale).toInt().coerceAtLeast(1)
                resizeLanczos3(originalBitmap, targetW, targetH)
            } else {
                originalBitmap
            }
        } else {
            originalBitmap
        }

        // Compress
        val quality = when (processingType) {
            is ImageProcessingType.Compress -> processingType.quality
            else -> 100
        }

        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        // Recycle bitmaps safely
        if (resizedBitmap !== originalBitmap) {
            resizedBitmap.recycle()
        }
        originalBitmap.recycle()

        return ProcessedResult(outputStream.toByteArray(), "image/jpeg")
    }

    /**
     * Generates a small JPEG thumbnail (capped at 120px max dimension)
     * and returns its bytes.
     */
    fun createThumbnail(context: Context, uri: Uri): ByteArray {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return ByteArray(0)

        // Use inSampleSize to decode efficiently
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        val srcW = options.outWidth
        val srcH = options.outHeight
        if (srcW <= 0 || srcH <= 0) return ByteArray(0)

        val targetMax = 120
        var inSampleSize = 1
        val maxDim = kotlin.math.max(srcW, srcH)
        if (maxDim > targetMax) {
            inSampleSize = maxDim / targetMax
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val stream = contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions) ?: return ByteArray(0)
        stream.close()

        // Scale for thumbnail keeping aspect ratio (only shrink, never enlarge)
        val srcMaxThumb = kotlin.math.max(bitmap.width, bitmap.height)
        val (thumbW, thumbH) = if (srcMaxThumb > targetMax) {
            val scale = targetMax.toDouble() / srcMaxThumb
            Pair((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
        } else {
            Pair(bitmap.width, bitmap.height)
        }

        val scaled = bitmap.scale(thumbW, thumbH, filter = true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)

        if (scaled !== bitmap) {
            scaled.recycle()
        }
        bitmap.recycle()

        return out.toByteArray()
    }
}

data class ProcessedResult(
    val bytes: ByteArray,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProcessedResult
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
