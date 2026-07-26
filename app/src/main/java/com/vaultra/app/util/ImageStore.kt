package com.vaultra.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
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

    /* -------------------------------------------------------------- */
    /*  Generic (any-mime) attachments — used by the To-Do module,      */
    /*  which accepts images, PDFs, and documents rather than just     */
    /*  photos. Files are copied into the same private-storage scheme  */
    /*  as [importImages] above, just without assuming an image type.  */
    /* -------------------------------------------------------------- */

    /** Copies picked content:// URIs (any mime type) into private app storage, preserving
     *  each file's original extension where possible so the type stays recognizable. */
    fun importFiles(context: Context, uris: List<Uri>, subfolder: String): List<String> {
        val dir = folder(context, subfolder)
        val paths = mutableListOf<String>()
        for (uri in uris) {
            try {
                val displayName = queryDisplayName(context, uri)
                val ext = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 6 }
                    ?: context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it.length <= 6 }
                    ?: "dat"
                val outFile = File(dir, "${UUID.randomUUID()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (outFile.exists() && outFile.length() > 0) paths.add(outFile.absolutePath)
            } catch (_: Exception) {
                // Skip any single file that fails to import rather than failing the whole batch.
            }
        }
        return paths
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun mimeTypeForExt(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun isImagePath(path: String): Boolean =
        File(path).extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun isPdfPath(path: String): Boolean = File(path).extension.lowercase() == "pdf"

    /** Human-readable file size, e.g. "240 KB" — shown next to non-image attachments. */
    fun readableSize(path: String): String {
        val bytes = File(path).let { if (it.exists()) it.length() else 0L }
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) { value /= 1024; unitIndex++ }
        return if (unitIndex == 0) "$bytes B" else String.format("%.1f %s", value, units[unitIndex])
    }

    /** Copies any local attachment (image, PDF, or document) out to the device's public
     *  Downloads/Vaultra folder with the correct mime type for its extension. */
    fun exportFileToDownloads(context: Context, path: String): Boolean {
        val srcFile = File(path)
        if (!srcFile.exists()) return false
        val ext = srcFile.extension.ifBlank { "dat" }
        val displayName = "Vaultra_${System.currentTimeMillis()}.$ext"
        val mime = mimeTypeForExt(ext)
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
