package com.uploid.util

import com.uploid.data.UploadProvider
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object Uploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun upload(
        provider: UploadProvider,
        fileBytes: ByteArray,
        mimeType: String,
        secretUrl: Boolean = false
    ): String {
        // Determine extension
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val filename = "upload_${System.currentTimeMillis()}.$extension"

        val mediaType = mimeType.toMediaTypeOrNull()
        val filePart = fileBytes.toRequestBody(mediaType)

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(provider.fileParam, filename, filePart)

        if (secretUrl) {
            requestBodyBuilder.addFormDataPart("secret", "1")
        }

        val requestBody = requestBodyBuilder.build()

        val requestBuilder = Request.Builder()
            .url(provider.targetUrl)
            .post(requestBody)

        // Basic Auth
        if (!provider.authUser.isNullOrBlank() && !provider.authPass.isNullOrBlank()) {
            val credential = Credentials.basic(provider.authUser, provider.authPass)
            requestBuilder.header("Authorization", credential)
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned failure code: ${response.code}")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty response from server")

            val cleanedBody = bodyString.trim()
            if (provider.responseRegex.isNullOrBlank()) {
                return cleanedBody
            } else {
                try {
                    val pattern = Pattern.compile(provider.responseRegex)
                    val matcher = pattern.matcher(cleanedBody)
                    if (matcher.find() && matcher.groupCount() >= 1) {
                        return matcher.group(1) ?: cleanedBody
                    }
                } catch (e: Exception) {
                    // Fallback to trimmed body if regex fails
                }
                return cleanedBody
            }
        }
    }
}
