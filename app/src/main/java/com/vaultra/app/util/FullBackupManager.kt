package com.vaultra.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.FileUtils
import android.provider.MediaStore
import android.util.Base64
import com.vaultra.app.crypto.CryptoManager
import com.vaultra.app.data.CardEntry
import com.vaultra.app.data.DocumentEntry
import com.vaultra.app.data.Entry
import com.vaultra.app.data.FuelEntry
import com.vaultra.app.data.TodoEntry
import com.vaultra.app.data.TodoCategory
import com.vaultra.app.data.ChecklistItem
import com.vaultra.app.data.DiaryEntry
import com.vaultra.app.data.VaultDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A complete backup covering passwords, cards, documents, and every attached
 * image — packaged as a single .zip so nothing is left behind. The sensitive
 * text data (passwords, card/document numbers, notes) is encrypted with a key
 * derived from a password you choose at export time, same scheme as the
 * password-only JSON backup. Attachment images are stored in the zip as-is,
 * the same way they already sit on-device — full container-level password
 * protection for the zip itself would need a third-party zip library, which
 * isn't included here to avoid adding an untested dependency.
 */
object FullBackupManager {
    private const val ITERATIONS = 150_000
    private const val VAULT_JSON_ENTRY = "vault.json"
    private const val DB_ENTRY = "vault.db"
    private const val PREFS_ENTRY = "prefs.xml"
    private const val ATTACHMENTS_DIR = "attachments/"

    data class ImportOutcome(
        val entries: List<Entry>,
        val cards: List<CardEntry>,
        val documents: List<DocumentEntry>,
        val fuelEntries: List<FuelEntry>,
        val todos: List<TodoEntry> = emptyList(),
        val todoCategories: List<TodoCategory> = emptyList(),
        val diaryEntries: List<DiaryEntry> = emptyList(),
        val salt: ByteArray,
        val iv: ByteArray
    )

    suspend fun export(
        context: Context,
        db: VaultDatabase,
        password: CharArray,
        masterKey: ByteArray,
        cryptoManager: CryptoManager
    ): Uri? {
        return try {
            val entries = db.entryDao().getAll().first()
            val cards = db.cardDao().getAll().first()
            val documents = db.documentDao().getAll().first()
            val fuelEntries = db.fuelDao().getAll().first()
            val todos = db.todoDao().all()
            val todoCategories = db.todoCategoryDao().all()
            val diaryEntries = db.diaryDao().all()

            // Collect every attachment file referenced by cards, documents, fuel receipts, and tasks.
            val attachmentPaths = (cards.flatMap { it.images } + documents.flatMap { it.images } + fuelEntries.mapNotNull { it.receiptPath } + todos.flatMap { it.attachmentPaths }).distinct()

            val entriesArr = JSONArray().apply {
                entries.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id); put("name", e.name); put("username", e.username); put("password", e.password)
                        put("url", e.url); put("notes", e.notes); put("category", e.category)
                        put("totpSecret", e.totpSecret); put("updatedAt", e.updatedAt)
                    })
                }
            }
            val cardsArr = JSONArray().apply {
                cards.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("nickname", c.nickname); put("bankName", c.bankName)
                        put("cardholderName", c.cardholderName); put("cardNumber", c.cardNumber)
                        put("expiryMonth", c.expiryMonth); put("expiryYear", c.expiryYear); put("cvv", c.cvv)
                        put("network", c.network); put("isFavorite", c.isFavorite); put("updatedAt", c.updatedAt)
                        put("images", JSONArray(c.images.map { File(it).name }))
                    })
                }
            }
            val documentsArr = JSONArray().apply {
                documents.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id); put("docType", d.docType); put("holderName", d.holderName)
                        put("docNumber", d.docNumber); put("notes", d.notes); put("updatedAt", d.updatedAt)
                        put("images", JSONArray(d.images.map { File(it).name }))
                    })
                }
            }

            val fuelArr = JSONArray().apply {
                fuelEntries.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id)
                        put("vehicleName", f.vehicleName)
                        put("vehicleType", f.vehicleType)
                        put("fuelType", f.fuelType)
                        put("odometer", f.odometer)
                        put("previousOdometer", f.previousOdometer)
                        put("distance", f.distance)
                        put("fuelQuantity", f.fuelQuantity)
                        put("pricePerLiter", f.pricePerLiter)
                        put("totalAmount", f.totalAmount)
                        put("station", f.station)
                        put("timestamp", f.timestamp)
                        put("notes", f.notes)
                        put("receiptPath", f.receiptPath)
                        put("location", f.location)
                        put("updatedAt", f.updatedAt)
                    })
                }
            }
            val todosArr = JSONArray().apply { todos.forEach { t -> put(JSONObject().apply { put("id", t.id); put("title", t.title); put("description", t.description); put("categoryId", t.categoryId); put("tags", t.tags); put("notes", t.notes); put("priority", t.priority); put("dueAt", t.dueAt); put("status", t.status); put("isCompleted", t.isCompleted); put("isPinned", t.isPinned); put("isFavorite", t.isFavorite); put("isArchived", t.isArchived); put("isDraft", t.isDraft); put("progress", t.progress); put("checklist", JSONArray(t.checklist.map { c -> JSONObject().apply { put("id", c.id); put("text", c.text); put("isDone", c.isDone) } })); put("attachmentPaths", JSONArray(t.attachmentPaths.map { File(it).name })); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt) }) } }
            val todoCategoriesArr = JSONArray().apply { todoCategories.forEach { c -> put(JSONObject().apply { put("id", c.id); put("name", c.name); put("colorHex", c.colorHex); put("icon", c.icon); put("isBuiltIn", c.isBuiltIn); put("updatedAt", c.updatedAt) }) } }
            val diaryArr = JSONArray().apply { diaryEntries.forEach { d -> put(JSONObject().apply { put("id", d.id); put("title", d.title); put("body", d.body); put("mood", d.mood); put("weather", d.weather); put("tags", d.tags); put("isFavorite", d.isFavorite); put("isArchived", d.isArchived); put("createdAt", d.createdAt); put("updatedAt", d.updatedAt) }) } }
            val payload = JSONObject().apply {
                put("entries", entriesArr)
                put("cards", cardsArr)
                put("documents", documentsArr)
                put("fuelEntries", fuelArr)
                put("todos", todosArr)
                put("todoCategories", todoCategoriesArr)
                put("diaryEntries", diaryArr)

                // Store crypto materials needed to reconstruct master password
                cryptoManager.getStoredSaltAndIv()?.let { (salt, iv) ->
                    put("masterSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    put("masterIv", Base64.encodeToString(iv, Base64.NO_WRAP))
                }
            }
            val plaintext = payload.toString().toByteArray(Charsets.UTF_8)

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(password, salt)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plaintext)

            val envelope = JSONObject().apply {
                put("app", "vaultra")
                put("version", 2)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }

            val filename = "vaultra-full-backup-${System.currentTimeMillis()}.zip"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

            resolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry(VAULT_JSON_ENTRY))
                    zip.write(envelope.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    attachmentPaths.forEach { path ->
                        val file = File(path)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry(ATTACHMENTS_DIR + file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restores from a full backup. Attachment images are copied into fresh
     * files under a new UUID (avoiding collisions with anything already on
     * device) and card/document image lists are remapped to the new paths.
     */
    fun import(
        context: Context,
        uri: Uri,
        password: CharArray
    ): ImportOutcome? {
        return try {
            var envelopeText: String? = null
            val extractedFiles = HashMap<String, String>() // original filename -> new absolute path
            val attachmentsDir = File(context.filesDir, "attachments/restored").apply { if (!exists()) mkdirs() }

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == VAULT_JSON_ENTRY -> {
                                envelopeText = zip.readBytes().toString(Charsets.UTF_8)
                            }
                            entry.name.startsWith(ATTACHMENTS_DIR) -> {
                                val originalName = entry.name.removePrefix(ATTACHMENTS_DIR)
                                val ext = originalName.substringAfterLast('.', "jpg")
                                val newFile = File(attachmentsDir, "${UUID.randomUUID()}.$ext")
                                newFile.outputStream().use { out -> zip.copyTo(out) }
                                extractedFiles[originalName] = newFile.absolutePath
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val text = envelopeText ?: return null
            val envelope = JSONObject(text)
            if (envelope.optString("app") != "vaultra") return null

            val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext) // wrong password throws here

            val payload = JSONObject(String(plaintext, Charsets.UTF_8))

            val masterSalt = Base64.decode(payload.getString("masterSalt"), Base64.NO_WRAP)
            val masterIv = Base64.decode(payload.getString("masterIv"), Base64.NO_WRAP)

            val entries = ArrayList<Entry>()
            val entriesArr = payload.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until entriesArr.length()) {
                val o = entriesArr.getJSONObject(i)
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                entries.add(
                    Entry(
                        id = id, name = o.optString("name"), username = o.optString("username"),
                        password = o.optString("password"), url = o.optString("url"), notes = o.optString("notes"),
                        category = o.optString("category", "Other"), totpSecret = o.optString("totpSecret"),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val cards = ArrayList<CardEntry>()
            val cardsArr = payload.optJSONArray("cards") ?: JSONArray()
            for (i in 0 until cardsArr.length()) {
                val o = cardsArr.getJSONObject(i)
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                val imgNames = o.optJSONArray("images")
                val remappedImages = (0 until (imgNames?.length() ?: 0))
                    .mapNotNull { idx -> extractedFiles[imgNames!!.getString(idx)] }
                cards.add(
                    CardEntry(
                        id = id, nickname = o.optString("nickname"), bankName = o.optString("bankName"),
                        cardholderName = o.optString("cardholderName"), cardNumber = o.optString("cardNumber"),
                        expiryMonth = o.optString("expiryMonth"), expiryYear = o.optString("expiryYear"),
                        cvv = o.optString("cvv"), network = o.optString("network"),
                        isFavorite = o.optBoolean("isFavorite", false), updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        images = remappedImages
                    )
                )
            }

            val documents = ArrayList<DocumentEntry>()
            val documentsArr = payload.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until documentsArr.length()) {
                val o = documentsArr.getJSONObject(i)
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                val imgNames = o.optJSONArray("images")
                val remappedImages = (0 until (imgNames?.length() ?: 0))
                    .mapNotNull { idx -> extractedFiles[imgNames!!.getString(idx)] }
                documents.add(
                    DocumentEntry(
                        id = id, docType = o.optString("docType"), holderName = o.optString("holderName"),
                        docNumber = o.optString("docNumber"), notes = o.optString("notes"),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()), images = remappedImages
                    )
                )
            }

            val fuelEntries = ArrayList<FuelEntry>()
            val fuelArr = payload.optJSONArray("fuelEntries") ?: JSONArray()
            for (i in 0 until fuelArr.length()) {
                val o = fuelArr.getJSONObject(i)
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                fuelEntries.add(
                    FuelEntry(
                        id = id,
                        vehicleName = o.optString("vehicleName"),
                        vehicleType = o.optString("vehicleType"),
                        fuelType = o.optString("fuelType"),
                        odometer = o.optLong("odometer"),
                        previousOdometer = o.optLong("previousOdometer"),
                        distance = o.optLong("distance"),
                        fuelQuantity = o.optDouble("fuelQuantity"),
                        pricePerLiter = o.optDouble("pricePerLiter"),
                        totalAmount = o.optDouble("totalAmount"),
                        station = o.optString("station"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        notes = o.optString("notes"),
                        receiptPath = o.optString("receiptPath").ifBlank { null },
                        location = o.optString("location").ifBlank { null },
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val todos = ArrayList<TodoEntry>()
            val todoArr = payload.optJSONArray("todos") ?: JSONArray()
            for (i in 0 until todoArr.length()) {
                val o = todoArr.getJSONObject(i)
                val checklistArr = o.optJSONArray("checklist")
                val checklist = (0 until (checklistArr?.length() ?: 0)).map { idx ->
                    val c = checklistArr!!.getJSONObject(idx)
                    ChecklistItem(id = c.optString("id"), text = c.optString("text"), isDone = c.optBoolean("isDone"))
                }
                val attachNames = o.optJSONArray("attachmentPaths")
                val remappedAttachments = (0 until (attachNames?.length() ?: 0))
                    .mapNotNull { idx -> extractedFiles[attachNames!!.getString(idx)] }
                todos.add(
                    TodoEntry(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = o.optString("title"),
                        description = o.optString("description"),
                        categoryId = o.optString("categoryId").ifBlank { null },
                        tags = o.optString("tags"),
                        notes = o.optString("notes"),
                        priority = o.optInt("priority", 2),
                        dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt"),
                        status = o.optString("status", "Pending"),
                        isCompleted = o.optBoolean("isCompleted"),
                        isPinned = o.optBoolean("isPinned"),
                        isFavorite = o.optBoolean("isFavorite"),
                        isArchived = o.optBoolean("isArchived"),
                        isDraft = o.optBoolean("isDraft"),
                        progress = o.optInt("progress"),
                        checklist = checklist,
                        attachmentPaths = remappedAttachments,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            val todoCategories = ArrayList<TodoCategory>()
            val todoCategoriesArr = payload.optJSONArray("todoCategories") ?: JSONArray()
            for (i in 0 until todoCategoriesArr.length()) {
                val o = todoCategoriesArr.getJSONObject(i)
                todoCategories.add(
                    TodoCategory(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = o.optString("name"),
                        colorHex = o.optString("colorHex", "#E63950"),
                        icon = o.optString("icon", "custom"),
                        isBuiltIn = o.optBoolean("isBuiltIn"),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            val diaryEntries = ArrayList<DiaryEntry>()
            val diaryArr = payload.optJSONArray("diaryEntries") ?: JSONArray()
            for (i in 0 until diaryArr.length()) { val o = diaryArr.getJSONObject(i); diaryEntries.add(DiaryEntry(o.optString("id").ifBlank { UUID.randomUUID().toString() }, o.optString("title"), o.optString("body"), o.optString("mood"), o.optString("weather"), o.optString("tags"), o.optBoolean("isFavorite"), o.optBoolean("isArchived"), o.optLong("createdAt", System.currentTimeMillis()), o.optLong("updatedAt", System.currentTimeMillis()))) }
            ImportOutcome(
                entries = entries,
                cards = cards,
                documents = documents,
                fuelEntries = fuelEntries,
                todos = todos,
                todoCategories = todoCategories,
                diaryEntries = diaryEntries,
                salt = masterSalt,
                iv = masterIv
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
