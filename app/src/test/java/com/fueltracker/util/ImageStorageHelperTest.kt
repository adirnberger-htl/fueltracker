package com.fueltracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ImageStorageHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun copyStreamToFile_writesContentSuccessfully() {
        val testData = "Fake image content data".toByteArray()
        val destFile = File(tempFolder.root, "saved_image.jpg")

        val bytesCopied = ImageStorageHelper.copyStreamToFile(ByteArrayInputStream(testData), destFile)

        assertEquals(testData.size.toLong(), bytesCopied)
        assertTrue(destFile.exists())
        assertEquals(String(testData), destFile.readText())
    }

    @Test
    fun deleteReceiptImage_deletesExistingFileAndReturnsTrue() {
        val testFile = File(tempFolder.root, "receipt_to_delete.jpg")
        testFile.writeText("sample receipt")
        assertTrue(testFile.exists())

        val result = ImageStorageHelper.deleteReceiptImage(testFile.absolutePath)

        assertTrue(result)
        assertFalse(testFile.exists())
    }

    @Test
    fun deleteReceiptImage_handlesNonExistentOrNullPathSafely() {
        assertFalse(ImageStorageHelper.deleteReceiptImage(null))
        assertFalse(ImageStorageHelper.deleteReceiptImage(""))
        assertFalse(ImageStorageHelper.deleteReceiptImage("/non/existent/path/receipt.jpg"))
    }
}
