package com.drywall.keygen.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.database.DatabaseErrorHandler
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.drywall.keygen.security.KeyGenSecurity
import com.drywall.keygen.BuildConfig
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase
import java.io.File
import java.security.SecureRandom
import java.util.Base64

@Database(entities = [IssuedLicense::class, RateHistory::class], version = 1, exportSchema = false)
abstract class KeygenDatabase : RoomDatabase() {
    abstract fun licenseDao(): LicenseDao

    companion object {
        private const val TAG = "KeygenDatabase"

        init {
            System.loadLibrary("sqlcipher")
        }

        @Volatile
        private var INSTANCE: KeygenDatabase? = null

        private fun getDatabasePassphrase(context: Context): ByteArray {
            val prefs = KeyGenSecurity.getEncryptedPrefs(context, "secure_db_passphrase_prefs")
            var passphrase = prefs.getString("db_passphrase", null)
            if (passphrase == null) {
                val random = SecureRandom()
                val bytes = ByteArray(32)
                random.nextBytes(bytes)
                passphrase = Base64.getEncoder().encodeToString(bytes)
                prefs.edit().putString("db_passphrase", passphrase).commit()
            }
            return passphrase.toByteArray(Charsets.UTF_8)
        }

        private fun backupDatabaseIfExists(context: Context) {
            try {
                val dbFile = context.getDatabasePath("keygen_db")
                if (dbFile.exists()) {
                    val backupDir = File(context.filesDir, "db_backups")
                    backupDir.mkdirs()

                    // Clean old backups (keep only last 3)
                    val oldBackups = backupDir.listFiles { file -> file.name.startsWith("keygen_db_backup_") }
                    if (oldBackups != null && oldBackups.size >= 3) {
                        oldBackups.sortedBy { it.lastModified() }.take(oldBackups.size - 2).forEach { it.delete() }
                    }

                    val timestamp = System.currentTimeMillis()
                    val backupFile = File(backupDir, "keygen_db_backup_$timestamp.db")
                    dbFile.inputStream().use { input ->
                        backupFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (_: Exception) {
                // Silent fail for backup
            }
        }

        private fun ensureDatabaseEncryption(context: Context, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath("keygen_db")
            if (!dbFile.exists()) return

            var isEncrypted = false
            try {
                // Try to open with the passphrase using SQLCipher (using 6-arg signature to avoid ambiguity)
                SqlCipherDatabase.openDatabase(dbFile.absolutePath, passphrase, null, SqlCipherDatabase.OPEN_READONLY, null, null).use {
                    it.version
                    isEncrypted = true
                }
            } catch (_: Exception) {
                isEncrypted = false
            }

            if (!isEncrypted) {
                val isPlain = tryOpenAsPlain(dbFile)

                if (isPlain) {
                    Log.w(TAG, "Database is NOT encrypted. Encrypting now...")
                    encryptExistingDatabase(context, dbFile, passphrase)
                } else {
                    Log.e(TAG, "CRITICAL: Database key lost or file corrupted. Resetting database to restore app functionality.")
                    val corruptedFile = File(dbFile.path + ".corrupted_${System.currentTimeMillis()}")
                    dbFile.renameTo(corruptedFile)
                    // Also delete side files
                    listOf("-wal", "-shm").forEach { suffix ->
                        File(dbFile.path + suffix).delete()
                    }
                }
            }
        }

        private fun tryOpenAsPlain(dbFile: File): Boolean {
            return try {
                SqlCipherDatabase.openDatabase(
                    dbFile.absolutePath,
                    "".toByteArray(),
                    null,
                    SqlCipherDatabase.OPEN_READONLY,
                    null,
                    null
                ).use { db ->
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun encryptExistingDatabase(context: Context, dbFile: File, passphrase: ByteArray) {
            val tempFile = File(context.cacheDir, "encrypted_keygen_db")
            if (tempFile.exists()) tempFile.delete()

            try {
                // Open unencrypted with SQLCipher using empty password
                SqlCipherDatabase.openDatabase(dbFile.absolutePath, "".toByteArray(), null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { database ->
                    // Configurar límites de memoria
                    database.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA wal_checkpoint(FULL);", null).use { it.moveToFirst() }
                    
                    val passphraseString = String(passphrase, Charsets.UTF_8).replace("'", "''")
                    database.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$passphraseString';")
                    
                    database.rawQuery("PRAGMA encrypted.journal_mode = OFF;", null).use { it.moveToFirst() }
                    database.rawQuery("SELECT sqlcipher_export('encrypted');", null).use { it.moveToFirst() }
                    database.rawExecSQL("DETACH DATABASE encrypted;")
                }

                // Remove side files for clean transition
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    File(dbFile.path + suffix).let { if (it.exists()) it.delete() }
                }

                if (dbFile.delete()) {
                    if (!tempFile.renameTo(dbFile)) {
                        tempFile.inputStream().use { input ->
                            dbFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        tempFile.delete()
                    }
                    Log.i(TAG, "Database successfully encrypted.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during database encryption", e)
                if (tempFile.exists()) tempFile.delete()
            }
        }

        fun getDatabase(context: Context): KeygenDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getDatabasePassphrase(context)
                ensureDatabaseEncryption(context, passphrase)
                
                backupDatabaseIfExists(context)
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KeygenDatabase::class.java,
                    "keygen_db"
                )
                .openHelperFactory(factory)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.query(SimpleSQLiteQuery("PRAGMA cipher_memory_limit = 134217728;")).close()
                        db.query(SimpleSQLiteQuery("PRAGMA cache_size = -8000;")).close()
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
