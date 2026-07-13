package com.uploid.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object LanczosScaler {

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val pix = PI * x
        return sin(pix) / pix
    }

    private fun lanczos3(x: Double): Double {
        val absX = kotlin.math.abs(x)
        if (absX >= 3.0) return 0.0
        return sinc(absX) * sinc(absX / 3.0)
    }

    fun resize(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        // Separable Lanczos 3 scaling.
        // Step 1: Scale horizontally (src -> temp)
        // Step 2: Scale vertically (temp -> dest)
        val temp = Bitmap.createBitmap(targetWidth, src.height, Bitmap.Config.ARGB_8888)
        val dest = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        val srcW = src.width
        val srcH = src.height

        val scaleX = srcW.toDouble() / targetWidth
        val scaleY = srcH.toDouble() / targetHeight

        // Horizontal Pass
        for (y in 0 until srcH) {
            val srcRow = IntArray(srcW)
            src.getPixels(srcRow, 0, srcW, 0, y, srcW, 1)

            val tempRow = IntArray(targetWidth)
            for (x in 0 until targetWidth) {
                val srcCenterX = (x + 0.5) * scaleX - 0.5
                val startX = kotlin.math.max(0, kotlin.math.ceil(srcCenterX - 3.0).toInt())
                val endX = kotlin.math.min(srcW - 1, kotlin.math.floor(srcCenterX + 3.0).toInt())

                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                var aSum = 0.0
                var wSum = 0.0

                for (sx in startX..endX) {
                    val weight = lanczos3(sx - srcCenterX)
                    if (weight != 0.0) {
                        val pixel = srcRow[sx]
                        val a = (pixel shr 24) and 0xFF
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF

                        rSum += r * weight
                        gSum += g * weight
                        bSum += b * weight
                        aSum += a * weight
                        wSum += weight
                    }
                }

                if (wSum == 0.0) {
                    tempRow[x] = srcRow[kotlin.math.min(srcW - 1, kotlin.math.max(0, srcCenterX.toInt()))]
                } else {
                    val a = (aSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val r = (rSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val g = (gSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val b = (bSum / wSum).coerceIn(0.0, 255.0).toInt()
                    tempRow[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            temp.setPixels(tempRow, 0, targetWidth, 0, y, targetWidth, 1)
        }

        // Vertical Pass
        for (x in 0 until targetWidth) {
            val tempCol = IntArray(srcH)
            temp.getPixels(tempCol, 0, 1, x, 0, 1, srcH)

            val destCol = IntArray(targetHeight)
            for (y in 0 until targetHeight) {
                val srcCenterY = (y + 0.5) * scaleY - 0.5
                val startY = kotlin.math.max(0, kotlin.math.ceil(srcCenterY - 3.0).toInt())
                val endY = kotlin.math.min(srcH - 1, kotlin.math.floor(srcCenterY + 3.0).toInt())

                var rSum = 0.0
                var gSum = 0.0
                var bSum = 0.0
                var aSum = 0.0
                var wSum = 0.0

                for (sy in startY..endY) {
                    val weight = lanczos3(sy - srcCenterY)
                    if (weight != 0.0) {
                        val pixel = tempCol[sy]
                        val a = (pixel shr 24) and 0xFF
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF

                        rSum += r * weight
                        gSum += g * weight
                        bSum += b * weight
                        aSum += a * weight
                        wSum += weight
                    }
                }

                if (wSum == 0.0) {
                    destCol[y] = tempCol[kotlin.math.min(srcH - 1, kotlin.math.max(0, srcCenterY.toInt()))]
                } else {
                    val a = (aSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val r = (rSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val g = (gSum / wSum).coerceIn(0.0, 255.0).toInt()
                    val b = (bSum / wSum).coerceIn(0.0, 255.0).toInt()
                    destCol[y] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            dest.setPixels(destCol, 0, 1, x, 0, 1, targetHeight)
        }

        temp.recycle()
        return dest
    }
}
