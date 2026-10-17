package com.fueltracker.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {

    private const val TEMP_DIR_NAME = "receipt_captures"
    private const val RECEIPTS_DIR_NAME = "receipts"

    fun getTempCaptureDir(context: Context): File {
        val dir = File(context.cacheDir, TEMP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPermanentReceiptsDir(context: Context): File {
        val dir = File(context.filesDir, RECEIPTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createTempPictureUri(context: Context): Uri {
        val tempDir = getTempCaptureDir(context)
        val tempFile = File(tempDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        tempFile.createNewFile()
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, tempFile)
    }

    fun saveStagedImageToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val receiptsDir = getPermanentReceiptsDir(context)
            val destinationFile = File(receiptsDir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                copyStreamToFile(input, destinationFile)
            }
            destinationFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun copyStreamToFile(inputStream: InputStream, destinationFile: File): Long {
        destinationFile.parentFile?.mkdirs()
        return inputStream.use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun deleteReceiptImage(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun clearTempCaptures(context: Context) {
        try {
            val tempDir = getTempCaptureDir(context)
            tempDir.listFiles()?.forEach { file ->
                file.delete()
            }
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
    }
}
