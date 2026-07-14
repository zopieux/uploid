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

object ImageProcessor {

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

            // Collect all TAG_ fields via reflection to strip them systematically
            val fields = ExifInterface::class.java.fields
            for (field in fields) {
                if (field.name.startsWith("TAG_") && field.type == String::class.java) {
                    try {
                        val tagName = field.get(null) as? String
                        if (tagName != null) {
                            // Retain orientation and dimensions to prevent layout/display issues
                            if (tagName != ExifInterface.TAG_IMAGE_WIDTH &&
                                tagName != ExifInterface.TAG_IMAGE_LENGTH &&
                                tagName != ExifInterface.TAG_ORIENTATION &&
                                tagName != ExifInterface.TAG_PIXEL_X_DIMENSION &&
                                tagName != ExifInterface.TAG_PIXEL_Y_DIMENSION) {
                                exifInterface.setAttribute(tagName, null)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore reflection/unsupported operations
                    }
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
        processingType: ImageProcessingType
    ): ProcessedResult {
        val contentResolver = context.contentResolver

        // 1. If Original, keep as is
        if (processingType is ImageProcessingType.Original) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot read image URI")
            // Resolve mimetype or fallback to jpeg
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            return ProcessedResult(bytes, mimeType)
        }

        // 2. Compress image using the specified quality
        val quality = when (processingType) {
            is ImageProcessingType.Compress -> processingType.quality
            else -> 100
        }

        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image input stream")
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Failed to decode bitmap")

        val srcW = originalBitmap.width
        val srcH = originalBitmap.height

        // Determine target dimensions keeping aspect ratio (only shrink, never enlarge)
        val maxDim = 2800
        val srcMax = kotlin.math.max(srcW, srcH)
        val (targetW, targetH) = if (srcMax > maxDim) {
            val scale = maxDim.toDouble() / srcMax
            Pair((srcW * scale).toInt(), (srcH * scale).toInt())
        } else {
            Pair(srcW, srcH)
        }

        // Apply scaling if target dims are different, or keep original bitmap reference
        val scaledBitmap = if (targetW != srcW || targetH != srcH) {
            originalBitmap.scale(targetW, targetH, filter = true)
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        // Clean up bitmaps
        if (scaledBitmap !== originalBitmap) {
            scaledBitmap.recycle()
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
