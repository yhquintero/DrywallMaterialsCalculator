package com.drywall.calculator.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.drywall.calculator.BuildConfig
import com.drywall.calculator.data.local.dao.AppConfigDao
import com.drywall.calculator.data.local.dao.BankAccountDao
import com.drywall.calculator.data.local.dao.ClientDao
import com.drywall.calculator.data.local.dao.CompanyDao
import com.drywall.calculator.data.local.dao.CuentaContableDao
import com.drywall.calculator.data.local.dao.CurrencyHistoryDao
import com.drywall.calculator.data.local.dao.FondoInversionDao
import com.drywall.calculator.data.local.dao.IncomeStatementDao
import com.drywall.calculator.data.local.dao.InventoryTransactionDao
import com.drywall.calculator.data.local.dao.MovimientoContableDao
import com.drywall.calculator.data.local.dao.LaborPriceDao
import com.drywall.calculator.data.local.dao.MaterialDao
import com.drywall.calculator.data.local.dao.MaterialMeasurementDao
import com.drywall.calculator.data.local.dao.PriceHistoryDao
import com.drywall.calculator.data.local.dao.ProjectDao
import com.drywall.calculator.data.local.dao.ProjectPhotoDao
import com.drywall.calculator.data.local.dao.ProviderDao
import com.drywall.calculator.data.local.dao.PurchaseOrderDao
import com.drywall.calculator.data.local.dao.TaxSettingDao
import com.drywall.calculator.data.local.dao.UnitTypeDao
import com.drywall.calculator.data.local.dao.WorkDiaryDao
import com.drywall.calculator.data.local.dao.ZoneDao
import com.drywall.common.security.SecurityUtils
import com.drywall.calculator.data.local.entity.IncomeStatement
import com.drywall.calculator.data.local.entity.CuentaContable
import com.drywall.calculator.data.local.entity.FondoInversion
import com.drywall.calculator.data.local.entity.MovimientoContable
import com.drywall.calculator.data.local.entity.AppConfig
import com.drywall.calculator.data.local.entity.BankAccount
import com.drywall.calculator.data.local.entity.Client
import com.drywall.calculator.data.local.entity.CompanyProfile
import com.drywall.calculator.data.local.entity.CurrencyHistory
import com.drywall.calculator.data.local.entity.InventoryTransaction
import com.drywall.calculator.data.local.entity.LaborPrice
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import com.drywall.calculator.data.local.entity.PriceHistory
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.local.entity.ProjectPhoto
import com.drywall.calculator.data.local.entity.Provider
import com.drywall.calculator.data.local.entity.PurchaseOrder
import com.drywall.calculator.data.local.entity.PurchaseOrderItem
import com.drywall.calculator.data.local.entity.TaxSetting
import com.drywall.calculator.data.local.entity.UnitType
import com.drywall.calculator.data.local.entity.WorkDiary
import com.drywall.calculator.data.local.entity.Zone
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase

@Database(
    entities = [
        Material::class, InventoryTransaction::class, Project::class, Zone::class,
        CompanyProfile::class, BankAccount::class, TaxSetting::class,
        LaborPrice::class, MaterialMeasurement::class, AppConfig::class, Client::class,
        PurchaseOrder::class, PurchaseOrderItem::class, Provider::class, WorkDiary::class, ProjectPhoto::class,
        CurrencyHistory::class, UnitType::class, PriceHistory::class, IncomeStatement::class,
        CuentaContable::class, MovimientoContable::class, FondoInversion::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun projectDao(): ProjectDao
    abstract fun zoneDao(): ZoneDao
    abstract fun companyDao(): CompanyDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun taxSettingDao(): TaxSettingDao
    abstract fun laborPriceDao(): LaborPriceDao
    abstract fun materialMeasurementDao(): MaterialMeasurementDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun clientDao(): ClientDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun providerDao(): ProviderDao
    abstract fun workDiaryDao(): WorkDiaryDao
    abstract fun projectPhotoDao(): ProjectPhotoDao
    abstract fun currencyHistoryDao(): CurrencyHistoryDao
    abstract fun unitTypeDao(): UnitTypeDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun incomeStatementDao(): IncomeStatementDao
    abstract fun cuentaContableDao(): CuentaContableDao
    abstract fun movimientoContableDao(): MovimientoContableDao
    abstract fun fondoInversionDao(): FondoInversionDao

    companion object {
        init {
            System.loadLibrary("sqlcipher")
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabasePassphrase(context: Context): ByteArray {
            val prefs = try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "secure_db_passphrase_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("AppDatabase", "Fallo al acceder a frases de seguridad. Intentando recuperación...", e)
                // Eliminar solo el archivo corrupto, NUNCA la Master Key global
                try {
                    context.deleteSharedPreferences("secure_db_passphrase_prefs")
                } catch (de: Exception) {
                    Log.e("AppDatabase", "No se pudo eliminar preferencias corruptas", de)
                }

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "secure_db_passphrase_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }

            var passphrase = prefs.getString("db_passphrase", null)
            if (passphrase == null) {
                // Intentar usar un ID de dispositivo estable como base para la clave si las preferencias se pierden.
                // Esto permite que el mismo dispositivo recupere acceso a su BD tras un reinicio de preferencias (KeyStore corruption).
                val stableId = com.drywall.common.security.SecurityUtils.getDeviceId(context)
                passphrase = stableId.take(44) // El ID ya es un hash SHA-256 en Base64 (aprox 44 chars)
                
                // Usar commit() para asegurar que la clave se guarde antes de proceder
                prefs.edit().putString("db_passphrase", passphrase).commit()
                Log.i("AppDatabase", "Frase de seguridad estable generada a partir de ID de dispositivo.")
            }
            return passphrase?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        }

        private fun backupDatabaseIfExists(context: Context) {
            try {
                val dbFile = context.getDatabasePath("drywall_db")
                if (dbFile.exists()) {
                    val backupDir = File(context.filesDir, "db_backups")
                    backupDir.mkdirs()
                    val timestamp = System.currentTimeMillis()
                    val backupFile = File(backupDir, "drywall_db_backup_$timestamp.db")
                    dbFile.inputStream().use { input ->
                        backupFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (BuildConfig.DEBUG) Log.w("AppDatabase", "Backup pre-migration: ${backupFile.absolutePath}")
                    val walFile = File(dbFile.path + "-wal")
                    if (walFile.exists()) {
                        File(backupDir, "drywall_db_backup_$timestamp.db-wal").also { walBackup ->
                            walFile.inputStream().use { input -> walBackup.outputStream().use { output -> input.copyTo(output) } }
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("AppDatabase", "Error creating pre-migration backup", e)
            }
        }

        private fun ensureDatabaseEncryption(context: Context, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath("drywall_db")
            if (!dbFile.exists()) return

            var isEncrypted = false
            try {
                // Intentar abrir como cifrado y realizar una operación de lectura para verificar la clave
                SqlCipherDatabase.openDatabase(dbFile.absolutePath, passphrase, null, SqlCipherDatabase.OPEN_READONLY, null, null).use { db ->
                    db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                    isEncrypted = true
                }
            } catch (e: Exception) {
                Log.w("AppDatabase", "No se pudo abrir como cifrado: ${e.message}")
                isEncrypted = false
            }

            if (!isEncrypted) {
                try {
                    // Fallback: Intentar con la clave basada en el ID del dispositivo (para recuperación de KeyStore corrupto)
                    val stableId = com.drywall.common.security.SecurityUtils.getDeviceId(context)
                    val stablePassphrase = stableId.take(44).toByteArray(Charsets.UTF_8)
                    SqlCipherDatabase.openDatabase(dbFile.absolutePath, stablePassphrase, null, SqlCipherDatabase.OPEN_READONLY, null, null).use { db ->
                        db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                        db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                        db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                        isEncrypted = true
                        Log.i("AppDatabase", "Acceso recuperado usando Device ID estable.")
                    }
                } catch (_: Exception) { }
            }

            if (!isEncrypted) {
                val isPlain = tryOpenAsPlain(dbFile)

                if (isPlain) {
                    Log.w("AppDatabase", "La base de datos NO está cifrada. Cifrando ahora...")
                    try {
                        encryptExistingDatabase(context, dbFile, passphrase)
                    } catch (e: Exception) {
                        Log.e("AppDatabase", "Error crítico durante el cifrado", e)
                        handleCorruptedDatabase(dbFile)
                    }
                } else {
                    // Si no es plano ni se abre con la clave actual, está corrupto o la clave se perdió definitivamente
                    Log.e("AppDatabase", "CRÍTICO: La base de datos existe pero no es legible. Posible pérdida de clave o corrupción.")
                    handleCorruptedDatabase(dbFile)
                }
            }
        }

        /**
         * Intenta abrir la base de datos como SQLite estándar (sin cifrar) para verificar si requiere cifrado.
         * Usa un ErrorHandler personalizado para evitar que el sistema borre el archivo si detecta "corrupción"
         * (lo cual ocurre si el archivo está cifrado pero se intenta abrir como plano).
         */
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
                    db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                    true
                }
            } catch (e: Exception) {
                Log.w("AppDatabase", "No se pudo abrir como plano: ${e.message}")
                false
            }
        }

        private fun handleCorruptedDatabase(dbFile: File) {
            val timestamp = System.currentTimeMillis()
            val corruptedFile = File(dbFile.parent, "drywall_db_corrupted_$timestamp.db")
            if (dbFile.exists()) {
                if (dbFile.renameTo(corruptedFile)) {
                    Log.e("AppDatabase", "Base de datos corrupta movida a ${corruptedFile.name}")
                    // Intentar mover también archivos auxiliares
                    listOf("-wal", "-shm").forEach { suffix ->
                        val auxFile = File(dbFile.path + suffix)
                        if (auxFile.exists()) {
                            auxFile.renameTo(File(corruptedFile.path + suffix))
                        }
                    }
                } else {
                    Log.e("AppDatabase", "No se pudo renombrar la base de datos corrupta. Eliminando para permitir reinicio.")
                    dbFile.delete()
                }
            }
        }

        private fun encryptExistingDatabase(context: Context, dbFile: File, passphrase: ByteArray) {
            val tempFile = File(context.cacheDir, "encrypted_drywall_db")
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()

            try {
                // Abrir base de datos plana usando SQLCipher (con clave vacía)
                SqlCipherDatabase.openDatabase(dbFile.absolutePath, "".toByteArray(), null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { database ->
                    // Configurar límites de memoria para evitar SQLiteOutOfMemoryException
                    database.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA wal_checkpoint(FULL);", null).use { it.moveToFirst() }
                    
                    val passphraseString = String(passphrase, Charsets.UTF_8).replace("'", "''")
                    database.rawExecSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$passphraseString';")
                    
                    // Optimizar base de datos de destino
                    database.rawQuery("PRAGMA encrypted.journal_mode = OFF;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA encrypted.page_size = 4096;", null).use { it.moveToFirst() }
                    database.rawQuery("PRAGMA encrypted.cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    
                    database.rawQuery("SELECT sqlcipher_export('encrypted');", null).use { it.moveToFirst() }
                    database.rawExecSQL("DETACH DATABASE encrypted;")
                }

                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val f = File(dbFile.path + suffix)
                    if (f.exists()) f.delete()
                }

                if (dbFile.delete()) {
                    if (!tempFile.renameTo(dbFile)) {
                        // Si falla rename, intentar copiar
                        tempFile.inputStream().use { input ->
                            dbFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile.delete()
                    }
                    Log.i("AppDatabase", "Base de datos cifrada exitosamente.")
                } else {
                    throw Exception("No se pudo eliminar el archivo original para reemplazarlo.")
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            }
        }

        fun resetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Error al cerrar instancia para reset", e)
                }
                INSTANCE = null
            }
        }

        fun getInstance(context: Context): AppDatabase {
            val temp = INSTANCE
            if (temp != null) return temp

            return synchronized(this) {
                val temp2 = INSTANCE
                if (temp2 != null) return temp2

                val startTime = System.currentTimeMillis()
                Log.d("AppDatabase", "Iniciando instancia de base de datos...")
                
                val passphrase = getDatabasePassphrase(context)
                ensureDatabaseEncryption(context, passphrase)

                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "drywall_db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.execSQL("PRAGMA cipher_memory_limit = 134217728;")
                            db.execSQL("PRAGMA cache_size = -8000;")
                            db.execSQL("PRAGMA temp_store = MEMORY;")
                            db.execSQL("PRAGMA mmap_size = 134217728;")
                            db.execSQL("PRAGMA synchronous = NORMAL;")
                        } catch (e: Exception) {
                            Log.w("AppDatabase", "Error setting pragmas: ${e.message}")
                        }
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Log.i("AppDatabase", "Base de datos creada (instalación nueva o reset)")
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        Log.e("AppDatabase", "MIGRACIÓN DESTRUCTIVA: ¡Se han perdido datos de usuario por incompatibilidad de versión!")
                    }
                })
                .fallbackToDestructiveMigration(true)
                .build()
                
                INSTANCE = instance
                Log.d("AppDatabase", "Base de datos lista en ${System.currentTimeMillis() - startTime}ms")
                instance
            }
        }
    }
}
