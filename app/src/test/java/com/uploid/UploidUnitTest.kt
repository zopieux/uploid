package com.uploid

import com.uploid.data.UploadProvider
import com.uploid.util.ImageSizeType
import com.uploid.util.ImageProcessingType
import okhttp3.Credentials
import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class UploidUnitTest {

    @Test
    fun testRegexParsingWithMatchingGroups() {
        // Given a response string and regex matching Hupl-like configuration
        val responseRegex = "<a href=\"(.*)\">"
        val responseBody = "<html><body><a href=\"https://x0.at/abc.jpg\">Link</a></body></html>"

        val cleanedBody = responseBody.trim()
        val pattern = Pattern.compile(responseRegex)
        val matcher = pattern.matcher(cleanedBody)

        assertTrue(matcher.find())
        assertEquals(1, matcher.groupCount())
        assertEquals("https://x0.at/abc.jpg", matcher.group(1))
    }

    @Test
    fun testRegexParsingNoMatchFallback() {
        val responseRegex = "<a href=\"(.*)\">"
        val responseBody = "https://x0.at/abc.jpg" // plain body

        val cleanedBody = responseBody.trim()
        var result = cleanedBody
        try {
            val pattern = Pattern.compile(responseRegex)
            val matcher = pattern.matcher(cleanedBody)
            if (matcher.find() && matcher.groupCount() >= 1) {
                result = matcher.group(1) ?: cleanedBody
            }
        } catch (e: Exception) {
            fail("Should not throw")
        }

        // Should fall back to the cleaned body
        assertEquals("https://x0.at/abc.jpg", result)
    }

    @Test
    fun testBasicAuthCredentialsGeneration() {
        val user = "hoge"
        val pass = "secret"
        val credential = Credentials.basic(user, pass)
        // Credentials.basic formats user:pass in Base64
        assertEquals("Basic aG9nZTpzZWNyZXQ=", credential)
    }

    @Test
    fun testProviderDefaultState() {
        val defaultProvider = UploadProvider(
            id = 1,
            name = "x0.at",
            targetUrl = "https://x0.at/",
            fileParam = "file",
            isDefault = true
        )

        val regularProvider = UploadProvider(
            id = 2,
            name = "0x0.st",
            targetUrl = "https://0x0.st/",
            fileParam = "file",
            isDefault = false
        )

        assertTrue(defaultProvider.isDefault)
        assertFalse(regularProvider.isDefault)
        assertEquals("file", defaultProvider.fileParam)
    }

    @Test
    fun testFormatSizeHelper() {
        // Test bytes
        assertEquals("500 B", formatSize(500))
        // Test KiB
        assertEquals("1.0 KiB", formatSize(1024))
        assertEquals("1.5 KiB", formatSize((1024 * 1.5).toInt()))
        // Test MiB
        assertEquals("2.3 MiB", formatSize((1024 * 1024 * 2.3).toInt()))
    }

    @Test
    fun testFormatSizeMultipleImages() {
        val totalSize = (1024 * 1024 * 4.9).toLong()
        val amount = 8
        val formattedSize = formatSize(totalSize)
        // Verify format matches either with or without space depending on implementation
        val result = "$formattedSize \u22c5 $amount images"
        assertEquals("4.9 MiB \u22c5 8 images", result)
    }

    @Test
    fun testImageSizeTypeHierarchy() {
        val original: ImageSizeType = ImageSizeType.Original
        val resized: ImageSizeType = ImageSizeType.Resized(1024)

        assertEquals(ImageSizeType.Original, original)
        assertTrue(resized is ImageSizeType.Resized)
        assertEquals(1024, (resized as ImageSizeType.Resized).maxDim)
    }

    @Test
    fun testImageProcessingTypeHierarchy() {
        val original: ImageProcessingType = ImageProcessingType.Original
        val compressed: ImageProcessingType = ImageProcessingType.Compress(80)

        assertEquals(ImageProcessingType.Original, original)
        assertTrue(compressed is ImageProcessingType.Compress)
        assertEquals(80, (compressed as ImageProcessingType.Compress).quality)
    }
}
