package com.vaultra.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.first
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

@Database(
    entities = [Entry::class, CardEntry::class, DocumentEntry::class, FuelEntry::class, TodoEntry::class, DiaryEntry::class, Vehicle::class, TodoCategory::class, DocumentCategory::class, DocumentFolder::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun cardDao(): CardDao
    abstract fun documentDao(): DocumentDao
    abstract fun fuelDao(): FuelDao
    abstract fun todoDao(): TodoDao
    abstract fun diaryDao(): DiaryDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun todoCategoryDao(): TodoCategoryDao
    abstract fun documentCategoryDao(): DocumentCategoryDao
    abstract fun documentFolderDao(): DocumentFolderDao

    companion object {
        @Volatile private var instance: VaultDatabase? = null

        /** Opens (or creates) the encrypted database using the derived key as the SQLCipher passphrase. */
        fun getInstance(context: Context, passphrase: ByteArray): VaultDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context, passphrase).also { instance = it }
            }
        }

        /** Call after locking the app so a future unlock re-opens with a fresh instance. */
        fun close() {
            instance?.close()
            instance = null
        }

        /**
         * Re-encrypts the vault under a new passphrase when the master password changes.
         * Rather than calling SQLCipher's raw rekey (which uses a different key-derivation
         * path than Room's SupportFactory and isn't safe to mix), this reads everything out
         * under the old key, recreates the database file fresh, and re-inserts it all
         * under the new key. Leaves the database open on the new key when done.
         */
        suspend fun reencrypt(context: Context, oldPassphrase: ByteArray, newPassphrase: ByteArray) {
            val oldDb = getInstance(context, oldPassphrase)
            val entries = oldDb.entryDao().getAll().first()
            val cards = oldDb.cardDao().getAll().first()
            val documents = oldDb.documentDao().all()
            val fuelEntries = oldDb.fuelDao().getAll().first()
            val todos = oldDb.todoDao().all()
            val diary = oldDb.diaryDao().all()
            val vehicles = oldDb.vehicleDao().getAll().first()
            val categories = oldDb.todoCategoryDao().all()
            val documentCategories = oldDb.documentCategoryDao().all()
            val documentFolders = oldDb.documentFolderDao().all()

            close()
            val dbFile = context.getDatabasePath("vaultra_vault.db")
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            val newDb = getInstance(context, newPassphrase)
            entries.forEach { newDb.entryDao().upsert(it) }
            cards.forEach { newDb.cardDao().upsert(it) }
            documents.forEach { newDb.documentDao().upsert(it) }
            fuelEntries.forEach { newDb.fuelDao().upsert(it) }
            todos.forEach { newDb.todoDao().upsert(it) }
            diary.forEach { newDb.diaryDao().upsert(it) }
            vehicles.forEach { newDb.vehicleDao().upsert(it) }
            categories.forEach { newDb.todoCategoryDao().upsert(it) }
            documentCategories.forEach { newDb.documentCategoryDao().upsert(it) }
            documentFolders.forEach { newDb.documentFolderDao().upsert(it) }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fuel_entries` (" +
                            "`id` TEXT NOT NULL, " +
                            "`vehicleName` TEXT NOT NULL, " +
                            "`vehicleType` TEXT NOT NULL, " +
                            "`fuelType` TEXT NOT NULL, " +
                            "`odometer` INTEGER NOT NULL, " +
                            "`previousOdometer` INTEGER NOT NULL, " +
                            "`distance` INTEGER NOT NULL, " +
                            "`fuelQuantity` REAL NOT NULL, " +
                            "`pricePerLiter` REAL NOT NULL, " +
                            "`totalAmount` REAL NOT NULL, " +
                            "`station` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`notes` TEXT NOT NULL, " +
                            "`receiptPath` TEXT, " +
                            "`location` TEXT, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `todo_entries` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `details` TEXT NOT NULL, `project` TEXT NOT NULL, `category` TEXT NOT NULL, `tags` TEXT NOT NULL, `priority` INTEGER NOT NULL, `dueAt` INTEGER, `isCompleted` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `diary_entries` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `mood` TEXT NOT NULL, `weather` TEXT NOT NULL, `tags` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vehicles` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`registrationNumber` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`fuelType` TEXT NOT NULL, " +
                            "`tankCapacity` REAL NOT NULL, " +
                            "`photoPath` TEXT, " +
                            "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                database.execSQL("ALTER TABLE `fuel_entries` ADD COLUMN `vehicleId` TEXT")
                database.execSQL("ALTER TABLE `fuel_entries` ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todo_categories` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`colorHex` TEXT NOT NULL, " +
                            "`icon` TEXT NOT NULL, " +
                            "`isBuiltIn` INTEGER NOT NULL DEFAULT 0, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                val now = System.currentTimeMillis()
                val builtIns = listOf(
                    Triple("cat_personal", "Personal", "#E63950") to "personal",
                    Triple("cat_work", "Work", "#4C6FFF") to "work",
                    Triple("cat_shopping", "Shopping", "#FF9F45") to "shopping",
                    Triple("cat_study", "Study", "#7C5CFC") to "study",
                    Triple("cat_health", "Health", "#2ECC71") to "health",
                    Triple("cat_finance", "Finance", "#FFD24C") to "finance",
                    Triple("cat_travel", "Travel", "#22C1C3") to "travel"
                )
                builtIns.forEach { (info, icon) ->
                    val (id, name, colorHex) = info
                    database.execSQL(
                        "INSERT OR IGNORE INTO `todo_categories` (`id`, `name`, `colorHex`, `icon`, `isBuiltIn`, `updatedAt`) VALUES (?, ?, ?, ?, 1, ?)",
                        arrayOf(id, name, colorHex, icon, now)
                    )
                }

                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE `todo_entries` SET `description` = `details`")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `categoryId` TEXT")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'Pending'")
                database.execSQL("UPDATE `todo_entries` SET `status` = CASE WHEN `isCompleted` = 1 THEN 'Completed' ELSE 'Pending' END")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `isDraft` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `checklist` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `attachmentPaths` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `todo_entries` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE `todo_entries` SET `createdAt` = `updatedAt` WHERE `createdAt` = 0")
            }
        }

        /** Expands the Documents module into a full digital vault: categories, folders,
         *  and a much richer set of fields per document (title, dates, tags, favorites, drafts). */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_categories` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`colorHex` TEXT NOT NULL, " +
                            "`icon` TEXT NOT NULL, " +
                            "`isBuiltIn` INTEGER NOT NULL DEFAULT 0, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_folders` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`parentFolderId` TEXT, " +
                            "`isFavorite` INTEGER NOT NULL DEFAULT 0, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )

                val now = System.currentTimeMillis()
                val builtInCategories = listOf(
                    Triple("doccat_personal", "Personal", "#E63950") to "personal",
                    Triple("doccat_education", "Education", "#4C6FFF") to "education",
                    Triple("doccat_banking", "Banking", "#2ECC71") to "banking",
                    Triple("doccat_vehicle", "Vehicle", "#FF9F45") to "vehicle",
                    Triple("doccat_employment", "Employment", "#7C5CFC") to "employment",
                    Triple("doccat_medical", "Medical", "#22C1C3") to "medical",
                    Triple("doccat_property", "Property", "#FFD24C") to "property",
                    Triple("doccat_others", "Others", "#9AA0A6") to "folder"
                )
                builtInCategories.forEach { (info, icon) ->
                    val (id, name, colorHex) = info
                    database.execSQL(
                        "INSERT OR IGNORE INTO `document_categories` (`id`, `name`, `colorHex`, `icon`, `isBuiltIn`, `updatedAt`) VALUES (?, ?, ?, ?, 1, ?)",
                        arrayOf(id, name, colorHex, icon, now)
                    )
                }

                database.execSQL("ALTER TABLE `documents` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE `documents` SET `title` = `docType` WHERE `title` = ''")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `categoryId` TEXT")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `folderId` TEXT")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `issueDate` INTEGER")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `expiryDate` INTEGER")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `issuedBy` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `isDraft` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE `documents` SET `createdAt` = `updatedAt` WHERE `createdAt` = 0")
                database.execSQL("ALTER TABLE `documents` ADD COLUMN `attachmentPaths` TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE `documents` SET `attachmentPaths` = `images`")
            }
        }

        private fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            SQLiteDatabase.loadLibs(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, "vaultra_vault.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
        }
    }
}
