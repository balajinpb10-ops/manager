package com.vaultra.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Handles photo attachments for cards and documents entirely on-device.
 * There is no server or backend involved: pictures picked from the gallery are
 * copied once into this app's private storage (never uploaded anywhere), and
 * can later be copied back out to the public Downloads folder if the user
 * wants a plain file they can open, share, or edit outside the app.
 */
object ImageStore {

    private fun folder(context: Context, subfolder: String): File {
        val dir = File(context.filesDir, "attachments/$subfolder")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Copies picked content:// URIs into private app storage. Returns the new absolute file paths. */
    fun importImages(context: Context, uris: List<Uri>, subfolder: String): List<String> {
        val dir = folder(context, subfolder)
        val paths = mutableListOf<String>()
        for (uri in uris) {
            try {
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() && it.length <= 5 } ?: "jpg"
                val outFile = File(dir, "${UUID.randomUUID()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (outFile.exists() && outFile.length() > 0) paths.add(outFile.absolutePath)
            } catch (_: Exception) {
                // Skip any single image that fails to import rather than failing the whole batch.
            }
        }
        return paths
    }

    /** Permanently deletes attachment files from private storage (e.g. after removal or entry delete). */
    fun deleteImages(paths: List<String>) {
        paths.forEach { path ->
            try { File(path).delete() } catch (_: Exception) { }
        }
    }

    /**
     * Copies a local attachment out to the device's public Downloads/Vaultra folder
     * so the user can find it in a normal file/gallery app and edit or share it.
     * Returns true on success.
     */
    fun exportToDownloads(context: Context, path: String): Boolean {
        val srcFile = File(path)
        if (!srcFile.exists()) return false
        val ext = srcFile.extension.ifBlank { "jpg" }
        val displayName = "Vaultra_${System.currentTimeMillis()}.$ext"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/$ext")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vaultra")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> srcFile.inputStream().use { it.copyTo(out) } } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vaultra")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, displayName)
                srcFile.inputStream().use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun showDownloadResult(context: Context, success: Boolean) {
        Toast.makeText(
            context,
            if (success) "Saved to Downloads/Vaultra" else "Couldn't save — check storage permission",
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Copies picked content:// URIs of any file type (used by the To-Do module for PDFs/docs, not just photos). */
    fun importFiles(context: Context, uris: List<Uri>, subfolder: String): List<String> = importImages(context, uris, subfolder)

    fun isImage(path: String): Boolean = mimeTypeFor(path).startsWith("image/")

    fun mimeTypeFor(path: String): String {
        val ext = File(path).extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Opens a private attachment in an external viewer app via FileProvider (read-only, single-use grant). */
    fun viewExternally(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeFor(path))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Same as exportToDownloads but uses the file's real mime type — needed for non-image attachments like PDFs. */
    fun exportAnyToDownloads(context: Context, path: String): Boolean {
        val srcFile = File(path)
        if (!srcFile.exists()) return false
        val ext = srcFile.extension.ifBlank { "dat" }
        val mime = mimeTypeFor(path)
        val displayName = "Vaultra_${System.currentTimeMillis()}.$ext"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vaultra")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> srcFile.inputStream().use { it.copyTo(out) } } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vaultra")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, displayName)
                srcFile.inputStream().use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
