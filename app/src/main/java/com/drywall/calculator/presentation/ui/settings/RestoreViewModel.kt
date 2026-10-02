package com.drywall.calculator.presentation.ui.settings

import android.content.Context
import android.content.Intent
import android.database.DatabaseErrorHandler
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SimpleSQLiteQuery
import com.drywall.calculator.data.local.AppDatabase
import com.drywall.calculator.data.local.entity.AppConfig
import com.drywall.calculator.data.local.entity.BankAccount
import com.drywall.calculator.data.local.entity.Client
import com.drywall.calculator.data.local.entity.CompanyProfile
import com.drywall.calculator.data.local.entity.CurrencyHistory
import com.drywall.calculator.data.local.entity.LaborPrice
import com.drywall.calculator.data.local.entity.Material
import com.drywall.calculator.data.local.entity.MaterialMeasurement
import com.drywall.calculator.data.local.entity.Project
import com.drywall.calculator.data.local.entity.ProjectPhoto
import com.drywall.calculator.data.local.entity.Provider
import com.drywall.calculator.data.local.entity.TaxSetting
import com.drywall.calculator.data.local.entity.UnitType
import com.drywall.calculator.data.local.entity.WorkDiary
import com.drywall.calculator.data.local.entity.Zone
import com.drywall.calculator.presentation.utils.BackupRecoveryUtils
import com.drywall.common.security.TrialProtectionManager
import com.drywall.common.security.SecurityUtils
import com.drywall.common.utils.DatabaseBackupUtils
import com.drywall.common.utils.ErrorTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase

data class MergeConflict(
    val type: String,
    val module: String,
    val identifier: String,
    val existingName: String,
    val newItem: Any
)

data class RestoreSummary(
    val version: Int,
    val newItems: List<Any>,
    val conflicts: List<MergeConflict>,
    val counts: Map<String, Int>
)

enum class RestoreMode { NONE, BACKUP, RESTORE }

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val db: AppDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val restoreMutex = Mutex()
    private val _summary = MutableStateFlow<RestoreSummary?>(null)
    val summary = _summary.asStateFlow()

    private val _currentDbVersion = MutableStateFlow(1)
    val currentDbVersion = _currentDbVersion.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _currentFileName = MutableStateFlow("")
    val currentFileName = _currentFileName.asStateFlow()

    private val _elapsedTime = MutableStateFlow("00:00:00.00")
    val elapsedTime = _elapsedTime.asStateFlow()

    private val _progressDetail = MutableStateFlow("")
    val progressDetail = _progressDetail.asStateFlow()

    private val _restoreMode = MutableStateFlow(RestoreMode.NONE)
    val restoreMode = _restoreMode.asStateFlow()

    private val _backupReportFile = MutableStateFlow<File?>(null)
    val backupReportFile = _backupReportFile.asStateFlow()

    private val _restoreResult = MutableSharedFlow<String>()
    val restoreResult = _restoreResult.asSharedFlow()

    private val _needRestart = MutableSharedFlow<Boolean>()
    val needRestart = _needRestart.asSharedFlow()

    private val _manualKey = MutableStateFlow("")
    val manualKey = _manualKey.asStateFlow()

    private var lastUpdateTimestamp = 0L

    companion object {
        private const val THROTTLE_MS = 100L
    }

    private fun updateProgressThrottled(progress: Float? = null, detail: String? = null, fileName: String? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastUpdateTimestamp >= THROTTLE_MS) {
            progress?.let { _progress.value = it }
            detail?.let { _progressDetail.value = it }
            fileName?.let { _currentFileName.value = it }
            lastUpdateTimestamp = now
        }
    }

    fun setManualKey(key: String) {
        _manualKey.value = key
    }

    private var currentBackupUri: Uri? = null
    private var backupManifestJson: String? = null

    private var timerJob: kotlinx.coroutines.Job? = null

    /**
     * Descifra la base de datos actual y devuelve un archivo temporal con la versión plana (sin cifrar).
     * Si falla, devuelve null.
     */
    private fun decryptDatabaseToTemp(context: Context): File? {
        val dbFile = context.getDatabasePath("drywall_db")
        if (!dbFile.exists() || dbFile.length() < 4096) {
            Log.w("RestoreVM", "No hay base de datos cifrada para descifrar")
            return null
        }

        val passphrase = AppDatabase.getDatabasePassphrase(context)
        val stableId = com.drywall.common.security.SecurityUtils.getDeviceId(context)
        val stablePassphrase = stableId.take(44).toByteArray(Charsets.UTF_8)

        val attempts = mutableListOf<Pair<String, ByteArray>>()
        attempts.add("Clave actual" to passphrase)
        attempts.add("ID de dispositivo estable" to stablePassphrase)
        attempts.add("Clave raw ISO-8859-1" to String(passphrase, Charsets.ISO_8859_1).toByteArray(Charsets.ISO_8859_1))
        attempts.add("ID raw ISO-8859-1" to String(stablePassphrase, Charsets.ISO_8859_1).toByteArray(Charsets.ISO_8859_1))

        for ((name, key) in attempts) {
            try {
                val tempPlain = File(context.cacheDir, "temp_plain_export_${System.nanoTime()}.db")
                SqlCipherDatabase.openDatabase(dbFile.absolutePath, key, null, SqlCipherDatabase.OPEN_READONLY, null, null).use { db ->
                    db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                    
                    tempPlain.absolutePath.let { path ->
                        db.rawExecSQL("ATTACH DATABASE '$path' AS plaintext KEY ''")
                        db.rawQuery("PRAGMA plaintext.journal_mode = OFF;", null).use { it.moveToFirst() }
                        db.rawQuery("SELECT sqlcipher_export('plaintext')", null).use { it.moveToFirst() }
                        db.rawExecSQL("DETACH DATABASE plaintext")
                    }
                }
                if (tempPlain.exists() && tempPlain.length() > 4096) {
                    Log.i("RestoreVM", "Base de datos descifrada con: $name (${tempPlain.length()} bytes)")
                    return tempPlain
                }
                tempPlain.delete()
                System.gc()
            } catch (e: Exception) {
                Log.d("RestoreVM", "Fallo con $name: ${e.message}")
            }
        }

        Log.e("RestoreVM", "No se pudo descifrar la BD con ninguna clave intentada")
        return null
    }

    private fun reEncryptDatabaseAfterRestore(context: Context): Boolean {
        return try {
            val dbFile = context.getDatabasePath("drywall_db")
            if (!dbFile.exists() || dbFile.length() < 4096) {
                Log.w("RestoreVM", "No hay base de datos para cifrar")
                return false
            }

            // IMPORTANTE: Borrar archivos auxiliares (WAL, SHM) antes de abrir.
            // Si el respaldo es plano, pero existen archivos WAL cifrados del sistema previo, 
            // SQLCipher fallará con "file is not a database" (code 26).
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val auxFile = File(dbFile.path + suffix)
                if (auxFile.exists()) {
                    Log.i("RestoreVM", "Borrando archivo auxiliar previo a re-cifrado: ${auxFile.name}")
                    auxFile.delete()
                }
            }

            val first16 = ByteArray(16)
            FileInputStream(dbFile).use { fis -> fis.read(first16) }
            val isPlain = String(first16, Charsets.US_ASCII).startsWith("SQLite format")
            if (!isPlain) {
                Log.i("RestoreVM", "La BD ya está cifrada, no necesita re-cifrado")
                return true
            }

            val passphrase = AppDatabase.getDatabasePassphrase(context)
            val tempEncrypted = File(context.cacheDir, "temp_reencrypted_${System.nanoTime()}.db")
            tempEncrypted.createNewFile()

            SqlCipherDatabase.openDatabase(dbFile.absolutePath, "".toByteArray(), null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { db ->
                db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                
                val passphraseString = String(passphrase, Charsets.UTF_8).replace("'", "''")
                db.rawExecSQL("ATTACH DATABASE '${tempEncrypted.absolutePath}' AS encrypted KEY '$passphraseString'")
                
                db.rawQuery("PRAGMA encrypted.journal_mode = OFF;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA encrypted.cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                
                db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                db.rawExecSQL("DETACH DATABASE encrypted")
            }

            System.gc()

            // Verificar que el archivo cifrado se creó correctamente
            if (!tempEncrypted.exists() || tempEncrypted.length() < 4096) {
                Log.e("RestoreVM", "Archivo cifrado inválido tras exportación: ${tempEncrypted.length()} bytes")
                tempEncrypted.delete()
                return false
            }

            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(dbFile.path + suffix).let { if (it.exists()) it.delete() }
            }

            System.gc()

            if (dbFile.delete()) {
                if (!tempEncrypted.renameTo(dbFile)) {
                    // Fallback: copiar si rename falla (por ejemplo, en algunos filesystems)
                    Log.w("RestoreVM", "Rename falló, intentando copia directa")
                    try {
                        tempEncrypted.inputStream().use { input ->
                            dbFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        tempEncrypted.delete()
                        Log.i("RestoreVM", "Base de datos re-cifrada via copia")
                        true
                    } catch (copyEx: Exception) {
                        Log.e("RestoreVM", "Error copiando BD cifrada", copyEx)
                        tempEncrypted.delete()
                        false
                    }
                } else {
                    Log.i("RestoreVM", "Base de datos re-cifrada exitosamente")
                    true
                }
            } else {
                Log.e("RestoreVM", "Error eliminando BD original plana")
                tempEncrypted.delete()
                false
            }
        } catch (e: Exception) {
            Log.e("RestoreVM", "Error re-cifrando BD", e)
            false
        }
    }

    init {
        viewModelScope.launch {
            try {
                // Delay slightly to allow MainActivityViewModel to finish its initial DB work
                // and avoid concurrency issues in SQLCipher's initialization
                kotlinx.coroutines.delay(500.milliseconds)
                withContext(Dispatchers.IO) {
                            val version = db.openHelper.readableDatabase.version
                    _currentDbVersion.value = version
                    Log.i("RestoreVM", "Current DB version (Room): $version, Adjusted: ${_currentDbVersion.value}")
                }
            } catch (e: Exception) {
                Log.e("RestoreVM", "Error getting current DB version", e)
            }
        }
    }

    private fun startTimer(startTime: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val current = System.currentTimeMillis() - startTime
                val h = current / 3600000
                val m = (current % 3600000) / 60000
                val s = (current % 60000) / 1000
                val ms = (current % 1000) / 10
                _elapsedTime.value = String.format(Locale.US, "%02d:%02d:%02d.%02d", h, m, s, ms)
                kotlinx.coroutines.delay(50)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Realiza la exportación del respaldo.
     * Intenta descifrar la BD para que el backup sea portable a otros dispositivos.
     * Si no se puede descifrar, exporta la BD cifrada (solo restaurable en este dispositivo).
     */
    fun performFullBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            if (!restoreMutex.tryLock()) return@launch
            try {
                _restoreMode.value = RestoreMode.BACKUP
                _isProcessing.value = true
                _progress.value = 0f
                _currentFileName.value = ""
                _progressDetail.value = "Iniciando exportación..."
                _backupReportFile.value = null

                val startTime = System.currentTimeMillis()
                startTimer(startTime)

                var decryptedDbFile: File? = null
                var dbFormat = "plain"
                withContext(Dispatchers.IO) {
                    _progressDetail.value = "Descifrando base de datos para portabilidad..."
                    decryptedDbFile = decryptDatabaseToTemp(context)

                    if (decryptedDbFile == null || !decryptedDbFile.exists()) {
                        Log.w("RestoreVM", "No se pudo descifrar la BD. Se exportará cifrada (solo restaurable en este dispositivo).")
                        dbFormat = "encrypted"
                    } else {
                        _progressDetail.value = "Reiniciando versión de base de datos a 1..."
                        try {
                            SqlCipherDatabase.openDatabase(decryptedDbFile!!.absolutePath, "".toByteArray(), null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { db ->
                                db.execSQL("PRAGMA user_version = 1")
                                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                            }
                            Log.i("RestoreVM", "Versión de BD reiniciada a 1 para exportación")
                        } catch (e: Exception) {
                            Log.w("RestoreVM", "No se pudo reiniciar versión de BD: ${e.message}")
                        }
                    }

                    _progressDetail.value = if (dbFormat == "plain") "Sincronizando base de datos..." else "Exportando base de datos cifrada..."

                    DatabaseBackupUtils.exportFullBackup(context, uri,
                        dbName = "drywall_db",
                        sensitiveFiles = DatabaseBackupUtils.defaultSensitiveFiles(),
                        dbOverrideFile = decryptedDbFile,
                        dbFormat = dbFormat,
                        preExportCheckpoint = {
                            try {
                                val wdb = db.openHelper.writableDatabase
                                // Checkpoint WAL to main file
                                wdb.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                                // Shrink database file by removing empty pages
                                wdb.query(SimpleSQLiteQuery("VACUUM"))
                                Log.i("RestoreVM", "WAL checkpoint y VACUUM completados exitosamente")
                            } catch (e: Exception) {
                                Log.w("RestoreVM", "Checkpoint/Vacuum falló, continuando: ${e.message}")
                            }
                        }
                    ) { current, total, fileName ->
                        val p = if (total > 0) (current + 1).toFloat() / total.toFloat() else 0f
                        updateProgressThrottled(progress = p, detail = fileName, fileName = fileName)
                    }

                    decryptedDbFile?.let { if (it.exists()) it.delete() }
                }

                stopTimer()
                val finalTime = _elapsedTime.value
                val report = buildString {
                    appendLine("=== REPORTE DE EXPORTACIÓN ===")
                    appendLine("Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("Estado: EXITOSO")
                    appendLine("Tiempo total: $finalTime")
                    appendLine("Archivo: ${uri.lastPathSegment ?: "backup.zip"}")
                    appendLine("==================================")
                }

                val reportFile = File(context.cacheDir, "BACKUP_REPORT_${System.currentTimeMillis()}.log")
                reportFile.writeText(report)
                _backupReportFile.value = reportFile.canonicalFile

                _restoreResult.emit("Respaldo exportado con éxito en $finalTime")
            } catch (e: Exception) {
                stopTimer()
                Log.e("RestoreVM", "Error backup", e)
                ErrorTracker.logError(context, "Exportación", "Fallo al exportar respaldo", e)
                _restoreResult.emit(e.message ?: "Fallo al exportar respaldo. Verifique el espacio disponible e intente de nuevo.")
            } finally {
                _isProcessing.value = false
                _progressDetail.value = ""
                restoreMutex.unlock()
            }
        }
    }

    fun startImportProcess(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        currentBackupUri = uri
        discoveredKey = null

        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.i("RestoreVM", "Permiso persistible obtenido para: $uri")
        } catch (e: Exception) {
            Log.w("RestoreVM", "No se pudo persistir permiso de URI (esperado si no es un DocumentProvider): ${e.message}")
        }

        viewModelScope.launch {
            if (!restoreMutex.tryLock()) {
                Log.w("RestoreVM", "Operación de restauración ya en curso. Ignorando...")
                return@launch
            }
            try {
                _restoreMode.value = RestoreMode.RESTORE
                _isProcessing.value = true
                _progress.value = 0f
                _currentFileName.value = ""
                _progressDetail.value = "Iniciando restauración..."
                backupManifestJson = null

                val startTime = System.currentTimeMillis()
                startTimer(startTime)

                val tempDbFile = withContext(Dispatchers.IO) {
                    BackupRecoveryUtils.extractDatabaseFromBackup(appContext, uri)
                }

                if (tempDbFile == null || !tempDbFile.exists()) {
                    val msg = "No se pudo leer el respaldo. Asegúrese de que el archivo no esté en uso por otra app o muévalo a la memoria interna."
                    ErrorTracker.logError(appContext, "Restauración", "Archivo temporal no creado: URI inaccesible o ZIP inválido")
                    stopTimer()
                    _restoreResult.emit(msg)
                    return@launch
                }

                _progressDetail.value = "Analizando datos del respaldo..."
                analyzeBackup(appContext, tempDbFile)
            } catch (e: Exception) {
                stopTimer()
                Log.e("RestoreVM", "Error extraction", e)
                ErrorTracker.logError(appContext, "Restauración", "Fallo en extracción ZIP", e)
                _restoreResult.emit(e.message ?: "El archivo de respaldo está dañado o es incompatible.")
                _progressDetail.value = ""
            } finally {
                _isProcessing.value = false
                restoreMutex.unlock()
            }
        }
    }

    

    private var discoveredKey: ByteArray? = null
    

    fun retryAnalysisWithManualKey(context: Context) {
        val uri = currentBackupUri ?: return
        val key = _manualKey.value
        if (key.isBlank()) return
        
        viewModelScope.launch {
            if (!restoreMutex.tryLock()) return@launch
            try {
                _isProcessing.value = true
                _progressDetail.value = "Reintentando con clave manual..."
                
                val tempDbFile = withContext(Dispatchers.IO) {
                    BackupRecoveryUtils.extractDatabaseFromBackup(context, uri)
                }
                
                if (tempDbFile != null && tempDbFile.exists()) {
                    analyzeBackup(context, tempDbFile, manualKey = key)
                }
            } catch (e: Exception) {
                _restoreResult.emit("Error con clave manual: ${e.message}")
            } finally {
                _isProcessing.value = false
                restoreMutex.unlock()
            }
        }
    }

    private suspend fun analyzeBackup(context: Context, tempDbFile: File, manualKey: String? = null) = withContext(Dispatchers.IO) {
        val tempDbPath = tempDbFile.path
        discoveredKey = null
        var backupDb: SqlCipherDatabase? = null
        try {
            if (!tempDbFile.exists()) {
                throw Exception("Error de sistema: El archivo temporal de restauración no se encuentra.")
            }

            val fileSize = tempDbFile.length()
            Log.i("RestoreVM", "Analizando respaldo: $tempDbPath (Tam: $fileSize bytes)")

            // Diagnostic: dump header bytes
            val fileHeader = ByteArray(64)
            FileInputStream(tempDbFile).use { fis -> fis.read(fileHeader) }
            val headerHex = fileHeader.joinToString("") { "%02x".format(it) }
            val headerAscii = String(fileHeader, Charsets.US_ASCII).replace(Regex("[^ -~]"), ".")
            Log.i("RestoreVM", "Header hex: $headerHex")
            Log.i("RestoreVM", "Header ASCII: $headerAscii")

            // Detectar formato desde el manifiesto primero
            val manifestDbFormat = backupManifestJson?.let { manifest ->
                Regex("\"dbFormat\"\\s*:\\s*\"([^\"]+)\"").find(manifest)?.groupValues?.getOrNull(1)
            }
            Log.i("RestoreVM", "Formato de BD en manifiesto: $manifestDbFormat")

            // Detectar si el archivo es SQLite plano (no cifrado)
            val first16 = fileHeader.copyOfRange(0, 16)
            val sqliteMagic = String(first16, Charsets.US_ASCII)
            val isPlainSqlite = sqliteMagic.startsWith("SQLite format")

            if (isPlainSqlite || manifestDbFormat == "plain") {
                // ... (mismo bloque que antes)
                try {
                    android.database.sqlite.SQLiteDatabase.openDatabase(
                        tempDbPath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                        object : DatabaseErrorHandler {
                            override fun onCorruption(dbObj: android.database.sqlite.SQLiteDatabase?) {
                                Log.w("RestoreVM", "Corrupción detectada durante análisis de respaldo sin cifrar.")
                            }
                        }
                    ).use {
                        it.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { c -> c.moveToFirst() }
                    }
                    Log.i("RestoreVM", "El respaldo es una base de datos sin cifrar. Abriendo con SQLCipher (clave vacía)...")
                    backupDb = SqlCipherDatabase.openDatabase(tempDbPath, ByteArray(0), null, SqlCipherDatabase.OPEN_READONLY, null, null)
                    backupDb.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                    backupDb.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                    discoveredKey = ByteArray(0)
                } catch (e: Exception) {
                    Log.e("RestoreVM", "Error abriendo BD plana", e)
                    throw Exception("El archivo de respaldo es una base de datos sin cifrar pero está corrupta o no es válida (${e.message}).")
                }
            } else {
                // La BD está cifrada - intentar con diferentes claves
                Log.i("RestoreVM", "Base de datos cifrada detectada. Probando claves de descifrado...")
                val attempts = mutableListOf<Pair<String, ByteArray>>()
                
                if (manualKey != null) {
                    attempts.add("Clave manual proporcionada" to manualKey.toByteArray(Charsets.UTF_8))
                    attempts.add("Clave manual (ISO-8859-1)" to manualKey.toByteArray(Charsets.ISO_8859_1))
                }

                val passphrase = AppDatabase.getDatabasePassphrase(context)
                val stableId = com.drywall.common.security.SecurityUtils.getDeviceId(context)
                val stablePassphrase = stableId.take(44).toByteArray(Charsets.UTF_8)
                val legacyId = com.drywall.common.security.SecurityUtils.getLegacyDeviceId(context)
                val legacyPassphrase = legacyId.toByteArray(Charsets.UTF_8)

                attempts.add("Clave actual del dispositivo" to passphrase)
                attempts.add("ID de dispositivo estable" to stablePassphrase)
                attempts.add("ID de dispositivo legacy" to legacyPassphrase)
                attempts.add("Clave raw ISO-8859-1" to String(passphrase, Charsets.ISO_8859_1).toByteArray(Charsets.ISO_8859_1))
                attempts.add("ID raw ISO-8859-1" to String(stablePassphrase, Charsets.ISO_8859_1).toByteArray(Charsets.ISO_8859_1))
                attempts.add("ID legacy ISO-8859-1" to String(legacyPassphrase, Charsets.ISO_8859_1).toByteArray(Charsets.ISO_8859_1))
                attempts.add("Clave vacía (Plana)" to ByteArray(0))

                var isKeyError = false

                for ((name, key) in attempts) {
                    try {
                        Log.d("RestoreVM", "Probando apertura con: $name")
                        backupDb = SqlCipherDatabase.openDatabase(tempDbPath, key, null, SqlCipherDatabase.OPEN_READONLY, null, null)
                        backupDb.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                        backupDb.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                        backupDb.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { it.moveToFirst() }
                        Log.i("RestoreVM", "Conexión exitosa con: $name")
                        discoveredKey = key
                        break
                    } catch (e: Exception) {
                        backupDb?.close()
                        backupDb = null
                        System.gc()
                        Log.d("RestoreVM", "Fallo con $name: ${e.message}")
                        val msg = e.message?.lowercase() ?: ""
                        if (msg.contains("file is not a database") || msg.contains("code 26") ||
                            msg.contains("authentication failed") || e is net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
                            isKeyError = true
                        }
                    }
                }

                if (backupDb == null) {
                    val headerHex2 = first16.joinToString("") { "%02x".format(it) }
                    val isLikelyEncrypted = fileSize >= 4096

                    val originHint = buildString {
                        backupManifestJson?.let { manifest ->
                            val appMatch = Regex("\"app\"\\s*:\\s*\"([^\"]+)\"").find(manifest)
                            val dbMatch = Regex("\"db\"\\s*:\\s*\"([^\"]+)\"").find(manifest)
                            val app = appMatch?.groupValues?.getOrNull(1) ?: ""
                            val db = dbMatch?.groupValues?.getOrNull(1) ?: ""
                            if (app.contains("keygen") || db == "keygen_db") {
                                append(" Este respaldo proviene de la app KeyGen, no de la Calculadora. ")
                                append("Las bases de datos usan claves de cifrado diferentes y no son intercambiables.")
                            } else if (!app.contains("calculator") && app.isNotEmpty()) {
                                append(" Este respaldo fue creado por una app diferente ($app) y no es compatible.")
                            }
                        }
                    }

                    // --- CAMBIO: Mensaje más claro y opción de recuperación ---
                    if (isKeyError || isLikelyEncrypted) {
                        val errorMsg = buildString {
                            append("El respaldo está cifrado con una clave incompatible o el archivo está dañado.")
                            append(" Tamaño: ${fileSize} bytes.")
                            append(originHint)
                            if (originHint.isEmpty()) {
                                append("\n\nSugerencias:")
                                append("\n1. Si el respaldo es de otro dispositivo, debe exportarlo como 'SIN CIFRAR' (formato portable) en el dispositivo de origen.")
                                append("\n2. Si está seguro de que los datos son correctos, use la opción de 'Sobrescritura Total' para intentar recuperar archivos e imágenes (aunque la base de datos sea ilegible).")
                            }
                        }
                        
                        // En lugar de lanzar excepción, creamos un resumen vacío para permitir la "Sobrescritura Total"
                        // que al menos recupera las imágenes del ZIP.
                        _summary.value = RestoreSummary(1, emptyList(), emptyList(), mapOf("Archivos/Imágenes" to 1))
                        _restoreResult.emit("ADVERTENCIA: $errorMsg")
                        return@withContext
                    } else {
                        throw Exception("El archivo de respaldo no parece ser una base de datos válida (header: ${headerHex2.substring(0, minOf(32, headerHex2.length))}..., tamaño: $fileSize bytes).")
                    }
                }
            }

            val currentDb = requireNotNull(backupDb) { "No se pudo inicializar la base de datos de respaldo" }

            _progressDetail.value = "Analizando versión..."
            // Leer versión desde el manifest del respaldo, no de la BD
            val backupVersion = try {
                backupManifestJson?.let { manifest ->
                    val json = org.json.JSONObject(manifest)
                    val v = json.optInt("version", 1)
                    if (v <= 0) 1 else v
                } ?: 1
            } catch (e: Exception) {
                1
            }
            val counts = mutableMapOf<String, Int>()

            _progressDetail.value = "Cargando clientes..."
            val backupClients = if (tableExists(currentDb, "clients")) {
                val list = loadClients(currentDb)
                if (list.isNotEmpty()) counts["Clientes"] = list.size
                list
            } else emptyList()

            _progressDetail.value = "Cargando proyectos..."
            val backupProjects = if (tableExists(currentDb, "projects")) {
                val list = loadProjects(currentDb)
                if (list.isNotEmpty()) counts["Obras"] = list.size
                list
            } else emptyList()

            _progressDetail.value = "Cargando materiales..."
            val backupMaterials = if (tableExists(currentDb, "materials")) {
                val list = loadMaterials(currentDb)
                if (list.isNotEmpty()) counts["Materiales"] = list.size
                list
            } else emptyList()

            _progressDetail.value = "Cargando cuentas bancarias..."
            val backupBankAccounts = if (tableExists(currentDb, "bank_accounts")) {
                val list = loadBankAccounts(currentDb)
                if (list.isNotEmpty()) counts["Cuentas"] = list.size
                list
            } else emptyList()

            _progressDetail.value = "Cargando proveedores..."
            val backupProviders = if (tableExists(currentDb, "providers")) {
                val list = loadProviders(currentDb)
                if (list.isNotEmpty()) counts["Proveedores"] = list.size
                list
            } else emptyList()

            val backupDiary = if (tableExists(currentDb, "work_diary")) {
                val list = loadDiary(currentDb)
                if (list.isNotEmpty()) counts["Bitácora"] = list.size
                list
            } else emptyList()

            val backupLabor = if (tableExists(currentDb, "labor_prices")) {
                val list = loadLaborPrices(currentDb)
                if (list.isNotEmpty()) counts["Precios MO"] = list.size
                list
            } else emptyList()

            val backupMeasurements = if (tableExists(currentDb, "material_measurements")) {
                val list = loadMeasurements(currentDb)
                if (list.isNotEmpty()) counts["Medidas"] = list.size
                list
            } else emptyList()

            val backupTaxes = if (tableExists(currentDb, "tax_settings")) {
                val list = loadTaxes(currentDb)
                if (list.isNotEmpty()) counts["Impuestos"] = list.size
                list
            } else emptyList()

            val backupUnitTypes = if (tableExists(currentDb, "unit_types")) {
                val list = loadUnitTypes(currentDb)
                if (list.isNotEmpty()) counts["Unidades"] = list.size
                list
            } else emptyList()

            val backupConfig = if (tableExists(currentDb, "app_config")) {
                val list = loadConfig(currentDb)
                if (list.isNotEmpty()) counts["Config"] = list.size
                list
            } else emptyList()

            val backupCompany = if (tableExists(currentDb, "company_profile")) {
                val list = loadCompany(currentDb)
                if (list.isNotEmpty()) counts["Empresa"] = 1
                list
            } else emptyList()

            val backupPhotos = if (tableExists(currentDb, "project_photos")) {
                val list = loadPhotos(currentDb)
                if (list.isNotEmpty()) counts["Fotos"] = list.size
                list
            } else emptyList()

            val backupHistory = if (tableExists(currentDb, "currency_history")) {
                val list = loadHistory(currentDb)
                if (list.isNotEmpty()) counts["Divisas"] = list.size
                list
            } else emptyList()

            val backupZones = if (tableExists(currentDb, "zones")) {
                val list = loadZones(currentDb)
                if (list.isNotEmpty()) counts["Zonas"] = list.size
                list
            } else emptyList()

            System.gc()

            _progressDetail.value = "Comparando con datos actuales..."
            val currentClients = db.clientDao().getAllClients().first()
            val currentProjects = db.projectDao().getAllProjects().first()
            val currentMaterials = db.materialDao().getAllMaterials().first()
            val currentBankAccounts = db.bankAccountDao().getAll().first()
            val currentProviders = db.providerDao().getAllProviders().first()

            System.gc()

            val conflicts = mutableListOf<MergeConflict>()
            val newItems = mutableListOf<Any>()

            backupClients.forEach { bc ->
                val existing = currentClients.find { it.ni == bc.ni }
                if (existing != null) conflicts.add(MergeConflict("Cliente", "Clientes", bc.ni, "${existing.name} ${existing.surnames}", bc))
                else newItems.add(bc)
            }

            backupProjects.forEach { bp ->
                val existing = currentProjects.find { it.name == bp.name }
                if (existing != null) conflicts.add(MergeConflict("Obra", "Obras", bp.name, existing.name, bp))
                else newItems.add(bp)
            }

            backupMaterials.forEach { bm ->
                val existing = currentMaterials.find { it.name == bm.name }
                if (existing != null) conflicts.add(MergeConflict("Material", "Catálogo", bm.name, existing.name, bm))
                else newItems.add(bm)
            }

            backupBankAccounts.forEach { ba ->
                val existing = currentBankAccounts.find { it.accountNumber == ba.accountNumber }
                if (existing != null) conflicts.add(MergeConflict("Cuenta", "Cuentas Bancarias", ba.accountNumber, ba.alias, ba))
                else newItems.add(ba)
            }

            backupProviders.forEach { bp ->
                val existing = currentProviders.find { it.name == bp.name }
                if (existing != null) conflicts.add(MergeConflict("Proveedor", "Proveedores", bp.name, existing.name, bp))
                else newItems.add(bp)
            }

            newItems.addAll(backupDiary)
            newItems.addAll(backupLabor)
            newItems.addAll(backupMeasurements)
            newItems.addAll(backupTaxes)
            newItems.addAll(backupUnitTypes)
            newItems.addAll(backupConfig)
            newItems.addAll(backupCompany)
            newItems.addAll(backupPhotos)
            newItems.addAll(backupHistory)
            newItems.addAll(backupZones)

            backupDb?.close()
            backupDb = null
            System.gc()

            _summary.value = RestoreSummary(backupVersion, newItems, conflicts, counts)
            _progress.value = 1f
        } catch (e: Exception) {
            Log.e("RestoreVM", "Error analysis", e)
            _restoreResult.emit(e.message ?: "Error desconocido al analizar el respaldo.")
        } finally {
            backupDb?.close()
            File(tempDbPath + "-wal").delete()
            File(tempDbPath + "-shm").delete()
            File(tempDbPath + "-journal").delete()
            if (tempDbFile.exists()) tempDbFile.delete()
            stopTimer()
            _isProcessing.value = false
            _progressDetail.value = if (_summary.value != null) "Análisis completado" else "Error en análisis"
        }
    }

    private fun tableExists(db: SqlCipherDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun executeRestore(context: Context, selectedConflicts: List<MergeConflict>, selectedNewItems: List<Any>, fullOverwrite: Boolean = false) {
        viewModelScope.launch {
            if (!restoreMutex.tryLock()) {
                Log.w("RestoreVM", "Operación en curso, espere a que termine la anterior.")
                return@launch
            }
            try {
                _isProcessing.value = true
                _progress.value = 0f
                _progressDetail.value = if (fullOverwrite) "Sobrescribiendo todo el sistema..." else "Restaurando datos seleccionados..."
                val startTime = System.currentTimeMillis()
                startTimer(startTime)

                withContext(Dispatchers.IO) {
                    if (fullOverwrite && currentBackupUri != null) {
                        // CASO 1: SOBRESCRITURA TOTAL DESDE ARCHIVO ZIP
                        // IMPORTANTE: Cerrar Room antes de sobrescribir archivos para evitar handles obsoletos
                        try {
                            AppDatabase.resetInstance()
                            Log.i("RestoreVM", "Instancia Room cerrada para restauración total desde ZIP")
                        } catch (e: Exception) {
                            Log.w("RestoreVM", "Error al cerrar Room (procediendo): ${e.message}")
                        }

                        val uri = requireNotNull(currentBackupUri)
                        
                        // Determinar si debemos usar el modo "lenient" (laxo) si el análisis previo falló
                        val isDamaged = _summary.value?.version == 0
                        
                        _progressDetail.value = "Restaurando archivos y configuraciones..."
                        DatabaseBackupUtils.importFullBackup(context, uri, lenient = isDamaged) { current, total, fileName ->
                            updateProgressThrottled(detail = "Restaurando: $fileName", fileName = fileName)
                        }
                        
                        // IMPORTANTE: Tras restaurar prefs_state.json, debemos resetear cualquier cache de claves
                        // para que la app lea la clave recién restaurada.
                        
                        // Si descubrimos una clave válida durante el análisis (o manual), 
                        // debemos descifrar y volver a cifrar con la clave de este dispositivo (que ahora es la misma)
                        if (discoveredKey != null) {
                            _progressDetail.value = "Sincronizando base de datos con sistema de seguridad..."
                            migrateRestoredDatabaseToCurrentKey(context, discoveredKey!!)
                        } else if (!isDamaged) {
                            _progressDetail.value = "Protegiendo base de datos restaurada..."
                            reEncryptDatabaseAfterRestore(context)
                        }
                        
                        if (!isDamaged) {
                            _progressDetail.value = "Verificando integridad de la base de datos..."
                            verifyDatabaseIntegrity(context)
                        } else {
                            _progressDetail.value = "Base de datos marcada como ilegible. Recuperación de archivos completada."
                        }
                        
                        System.gc()
                        
                        // AHORA SÍ, abrimos Room con la nueva base de datos para reparaciones finales
                        val freshDb = AppDatabase.getInstance(context)
                        _progressDetail.value = "Reparando rutas de imágenes (Clientes, Tarjetas, Datos)..."
                        Log.i("RestoreVM", "Iniciando reparación de rutas de imágenes con nueva instancia de DB")
                        repairImagePaths(context, freshDb)
                        
                        // Actualizar integridad de protección tras cambios en preferencias/DB
                        TrialProtectionManager.refreshStorageIntegrity(context)

                        _progress.value = 1f
                        Log.i("RestoreVM", "Restauración total completada exitosamente")
                        _progressDetail.value = "¡ÉXITO! Reiniciando para aplicar cambios..."
                        _restoreResult.emit("Sistema restaurado con éxito. La aplicación se reiniciará en breve.")
                        
                        kotlinx.coroutines.delay(2000)
                        _needRestart.emit(true)
                        return@withContext
                    }

                    // CASO 2: RESTAURACIÓN PARCIAL O SOBRESCRITURA SIN ZIP
                    val dbInstance = db

                    if (fullOverwrite) {
                        _progressDetail.value = "Limpiando datos actuales..."
                        dbInstance.clearAllTables()
                    }

                    val totalItems = selectedNewItems.size + selectedConflicts.size
                    var processed = 0

                    _progressDetail.value = "Guardando elementos nuevos..."
                    var failedCount = 0
                    selectedNewItems.forEach { item ->
                        val success = saveItem(dbInstance, item)
                        if (!success) failedCount++
                        processed++
                        updateProgressThrottled(
                            progress = processed.toFloat() / totalItems.toFloat(),
                            detail = "Guardando: ${getItemName(item)}"
                        )
                    }

                    _progressDetail.value = "Guardando elementos en conflicto..."
                    selectedConflicts.forEach { conflict ->
                        val success = saveItem(dbInstance, conflict.newItem)
                        if (!success) failedCount++
                        processed++
                        updateProgressThrottled(
                            progress = processed.toFloat() / totalItems.toFloat(),
                            detail = "Actualizando: ${conflict.existingName}"
                        )
                    }

                    if (failedCount > 0) {
                        throw Exception("$failedCount elementos fallaron al restaurar. Revise los logs para más detalles.")
                    }

                    _progressDetail.value = "Extrayendo archivos de medios del respaldo..."
                    extractMediaFromBackup(context, currentBackupUri)

                    _progressDetail.value = "Reparando rutas de imágenes..."
                    System.gc()
                    repairImagePaths(context, dbInstance)
                }

                stopTimer()
                _restoreResult.emit(
                    if (fullOverwrite) "Sistema sobreescrito completamente."
                    else "Restauración finalizada con éxito."
                )
                _summary.value = null
            } catch (e: Exception) {
                stopTimer()
                Log.e("RestoreVM", "Error execute", e)
                _restoreResult.emit("Fallo en la restauración. Verifique el archivo de respaldo e intente de nuevo.")
            } finally {
                _isProcessing.value = false
                _progressDetail.value = ""
                restoreMutex.unlock()
            }
        }
    }

    private fun migrateRestoredDatabaseToCurrentKey(context: Context, oldKey: ByteArray) {
        val dbFile = context.getDatabasePath("drywall_db")
        val currentKey = AppDatabase.getDatabasePassphrase(context)
        val tempPlain = File(context.cacheDir, "temp_migrate_plain.db")
        val tempEncrypted = File(context.cacheDir, "temp_migrate_encrypted.db")

        try {
            // Borrar archivos auxiliares para evitar conflictos de cifrado (WAL/SHM)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val auxFile = File(dbFile.path + suffix)
                if (auxFile.exists()) auxFile.delete()
            }

            if (tempPlain.exists()) tempPlain.delete()
            if (tempEncrypted.exists()) tempEncrypted.delete()
            
            // Asegurar que los archivos existan antes de ATTACH (algunas versiones de SQLCipher lo requieren)
            tempPlain.createNewFile()
            tempEncrypted.createNewFile()

            Log.d("RestoreVM", "Iniciando descifrado a temporal plano: ${tempPlain.absolutePath}")
            // 1. Descifrar con la clave antigua a un archivo plano
            SqlCipherDatabase.openDatabase(dbFile.absolutePath, oldKey, null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { db ->
                db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
                db.rawExecSQL("ATTACH DATABASE '${tempPlain.absolutePath}' AS plaintext KEY ''")
                db.rawQuery("PRAGMA plaintext.journal_mode = OFF;", null).use { it.moveToFirst() }
                db.rawQuery("SELECT sqlcipher_export('plaintext')", null).use { it.moveToFirst() }
                db.rawExecSQL("DETACH DATABASE plaintext")
            }

            if (!tempPlain.exists() || tempPlain.length() == 0L) {
                throw Exception("El archivo temporal plano no se creó correctamente o está vacío")
            }

            System.gc()

            Log.d("RestoreVM", "Iniciando re-cifrado con clave nueva")
            // 2. Volver a cifrar con la clave nueva
            SqlCipherDatabase.openDatabase(tempPlain.absolutePath, "".toByteArray(), null, SqlCipherDatabase.OPEN_READWRITE, null, null).use { db ->
                db.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                val currentKeyString = String(currentKey, Charsets.UTF_8).replace("'", "''")
                db.rawExecSQL("ATTACH DATABASE '${tempEncrypted.absolutePath}' AS encrypted KEY '$currentKeyString'")
                
                db.rawQuery("PRAGMA encrypted.journal_mode = OFF;", null).use { it.moveToFirst() }
                db.rawQuery("PRAGMA encrypted.cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                
                db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                db.rawExecSQL("DETACH DATABASE encrypted")
            }

            System.gc()

            // 3. Reemplazar original
            if (tempEncrypted.exists() && tempEncrypted.length() > 4096) {
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val auxFile = File(dbFile.path + suffix)
                    if (auxFile.exists()) auxFile.delete()
                }
                if (dbFile.exists()) dbFile.delete()
                
                if (!tempEncrypted.renameTo(dbFile)) {
                    tempEncrypted.inputStream().use { input ->
                        dbFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Log.i("RestoreVM", "Migración de clave completada con éxito")
            } else {
                throw Exception("El archivo cifrado temporal no es válido")
            }
        } catch (e: Exception) {
            Log.e("RestoreVM", "Fallo en migración de clave: ${e.message}", e)
        } finally {
            if (tempPlain.exists()) tempPlain.delete()
            if (tempEncrypted.exists()) tempEncrypted.delete()
        }
    }

    private fun getItemName(item: Any): String = when (item) {
        is Client -> "Cliente: ${item.name} ${item.surnames}"
        is Project -> "Obra: ${item.name}"
        is Material -> "Material: ${item.name}"
        is BankAccount -> "Cuenta: ${item.alias}"
        is Provider -> "Proveedor: ${item.name}"
        is WorkDiary -> "Bitácora: ${item.activity}"
        is LaborPrice -> "Precios mano de obra"
        is MaterialMeasurement -> "Medidas de materiales"
        is TaxSetting -> "Impuesto: ${item.taxType}"
        is AppConfig -> "Configuración de app"
        is CompanyProfile -> "Perfil de empresa"
        is ProjectPhoto -> "Foto de proyecto"
        is CurrencyHistory -> "Historial de divisas"
        is Zone -> "Zona: ${item.name}"
        else -> item.javaClass.simpleName
    }

    fun cancelRestore() {
        _summary.value = null
        viewModelScope.launch {
            _restoreResult.emit("Restauración cancelada.")
        }
    }

    fun clearBackupReport() {
        _backupReportFile.value = null
    }

    private suspend fun verifyDatabaseIntegrity(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("drywall_db")
                if (!dbFile.exists() || dbFile.length() < 4096) {
                    Log.w("RestoreVM", "Base de datos no existe o es muy pequeña")
                    return@withContext
                }

                val first16 = ByteArray(16)
                FileInputStream(dbFile).use { fis -> fis.read(first16) }
                val isPlain = String(first16, Charsets.US_ASCII).startsWith("SQLite format")

                if (isPlain) {
                    SqlCipherDatabase.openDatabase(
                        dbFile.absolutePath, "".toByteArray(), null,
                        SqlCipherDatabase.OPEN_READONLY, null, null
                    ).use { sqlDb ->
                        sqlDb.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                        sqlDb.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                        val cursor = sqlDb.rawQuery("PRAGMA quick_check(1)", null)
                        cursor.use { c ->
                            c.moveToFirst()
                            val result = c.getString(0)
                            if (result != "ok" && result != "1") {
                                Log.e("RestoreVM", "Integridad BD plana falló: $result")
                                throw Exception("La base de datos restaurada presenta inconsistencias.")
                            }
                            Log.i("RestoreVM", "Integridad BD plana verificada: $result")
                        }
                    }
                } else {
                    val passphrase = AppDatabase.getDatabasePassphrase(context)
                    val dbPath = dbFile.absolutePath
                    SqlCipherDatabase.openDatabase(dbPath, passphrase, null, SqlCipherDatabase.OPEN_READONLY, null, null).use { sqlDb ->
                        sqlDb.rawQuery("PRAGMA cipher_memory_limit = 134217728;", null).use { it.moveToFirst() }
                        sqlDb.rawQuery("PRAGMA cache_size = -8000;", null).use { it.moveToFirst() }
                        val cursor = sqlDb.rawQuery("PRAGMA quick_check(1)", null)
                        cursor.use { c ->
                            c.moveToFirst()
                            val result = c.getString(0)
                            if (result != "ok" && result != "1") {
                                Log.e("RestoreVM", "Integridad BD falló: $result")
                                throw Exception("La base de datos restaurada presenta inconsistencias. Intente restaurar de nuevo o contacte al soporte.")
                            }
                            Log.i("RestoreVM", "Integridad BD verificada: $result")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RestoreVM", "Error verificando integridad BD", e)
                throw e
            }
        }
    }

    private suspend fun repairImagePaths(context: Context, dbInstance: AppDatabase) {
        val currentFilesDir = context.filesDir.absolutePath
        withContext(Dispatchers.IO) {
            val clients = dbInstance.clientDao().getAllClients().first()
            Log.d("RestoreVM", "Reparando rutas para ${clients.size} clientes")
            clients.forEach { client ->
                val front = client.frontImageUri?.fixPath(currentFilesDir)
                val back = client.backImageUri?.fixPath(currentFilesDir)
                val sig = client.signatureImageUri?.fixPath(currentFilesDir)
                val bar = client.barcodeImageUri?.fixPath(currentFilesDir)
                val qr = client.qrCodeImageUri?.fixPath(currentFilesDir)
                val profile = client.profileImageUri?.fixPath(currentFilesDir)
                if (front != client.frontImageUri || back != client.backImageUri || sig != client.signatureImageUri ||
                    bar != client.barcodeImageUri || qr != client.qrCodeImageUri || profile != client.profileImageUri) {
                    dbInstance.clientDao().insertClient(client.copy(
                        frontImageUri = front, backImageUri = back, signatureImageUri = sig,
                        barcodeImageUri = bar, qrCodeImageUri = qr, profileImageUri = profile
                    ))
                }
            }

            val profile = dbInstance.companyDao().getCompanyProfile().first()
            profile?.let { p ->
                Log.d("RestoreVM", "Reparando rutas para perfil de empresa")
                val logo = p.logoUri?.fixPath(currentFilesDir)
                val sig = p.signatureUri?.fixPath(currentFilesDir)
                val photo = p.ownerPhotoUri?.fixPath(currentFilesDir)
                if (logo != p.logoUri || sig != p.signatureUri || photo != p.ownerPhotoUri) {
                    dbInstance.companyDao().upsert(p.copy(logoUri = logo, signatureUri = sig, ownerPhotoUri = photo))
                }
            }

            val accounts = dbInstance.bankAccountDao().getAll().first()
            Log.d("RestoreVM", "Reparando rutas para ${accounts.size} cuentas bancarias")
            accounts.forEach { account ->
                val card = account.cardImageUri?.fixPath(currentFilesDir)
                if (card != account.cardImageUri) {
                    dbInstance.bankAccountDao().insert(account.copy(cardImageUri = card))
                }
            }

            val photos = dbInstance.projectPhotoDao().getAllPhotos().first()
            Log.d("RestoreVM", "Reparando rutas para ${photos.size} fotos de proyectos")
            photos.forEach { photo ->
                val fixed = photo.photoUri.fixPath(currentFilesDir)
                if (fixed != photo.photoUri) {
                    dbInstance.projectPhotoDao().insert(photo.copy(photoUri = fixed))
                }
            }
        }
    }

    private fun String.fixPath(currentFilesDir: String): String {
        val filesMarker = "/files/"
        val idx = this.indexOf(filesMarker)
        if (idx == -1) return this
        val relativePath = this.substring(idx + filesMarker.length)
        return "$currentFilesDir/$relativePath"
    }

    private suspend fun extractMediaFromBackup(context: Context, backupUri: Uri?) = withContext(Dispatchers.IO) {
        if (backupUri == null) return@withContext
        try {
            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: java.util.zip.ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val fileName = entry.name
                        if (!entry.isDirectory && (fileName.startsWith("media/") || fileName.startsWith("external_media/"))) {
                            val destFile = if (fileName.startsWith("media/")) {
                                val relativePath = fileName.substringAfter("media/")
                                val target = java.io.File(context.filesDir, relativePath)
                                if (target.canonicalPath.startsWith(context.filesDir.canonicalPath)) target else null
                            } else {
                                val extDir = context.getExternalFilesDir(null)
                                if (extDir != null) {
                                    val relativePath = fileName.substringAfter("external_media/")
                                    val target = java.io.File(extDir, "Pictures/$relativePath")
                                    if (target.canonicalPath.startsWith(extDir.canonicalPath)) target else null
                                } else null
                            }
                            if (destFile == null) { zipIn.closeEntry(); entry = zipIn.nextEntry; continue }
                            destFile.parentFile?.mkdirs()
                            try {
                                java.io.FileOutputStream(destFile).use { fos ->
                                    java.io.BufferedOutputStream(fos).use { bos ->
                                        var len: Int
                                        while (zipIn.read(buffer).also { len = it } > 0) {
                                            bos.write(buffer, 0, len)
                                        }
                                        bos.flush()
                                    }
                                }
                                Log.d("RestoreVM", "Archivo de medios extraído: ${destFile.name}")
                            } catch (e: Exception) {
                                Log.w("RestoreVM", "No se pudo extraer $fileName: ${e.message}")
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            Log.i("RestoreVM", "Extracción de archivos de medios completada")
        } catch (e: Exception) {
            Log.w("RestoreVM", "Error extrayendo medios del respaldo: ${e.message}")
        }
    }

    private suspend fun saveItem(dbInstance: AppDatabase, item: Any): Boolean {
        return try {
            when (item) {
                is Client -> {
                    val existingByNi = dbInstance.clientDao().getAllClients().first().find { it.ni == item.ni }
                    if (existingByNi != null) {
                        dbInstance.clientDao().updateClient(item.copy(id = existingByNi.id))
                    } else {
                        val existingById = dbInstance.clientDao().getClientById(item.id)
                        if (existingById != null) {
                            dbInstance.clientDao().insertClient(item.copy(id = java.util.UUID.randomUUID().toString()))
                        } else {
                            dbInstance.clientDao().insertClient(item)
                        }
                    }
                    true
                }
                is Project -> {
                    val existingByName = dbInstance.projectDao().getProjectByName(item.name)
                    if (existingByName != null) {
                        dbInstance.projectDao().update(item.copy(id = existingByName.id))
                    } else {
                        val existingById = dbInstance.projectDao().getAllProjects().first().find { it.id == item.id }
                        if (existingById != null) {
                            dbInstance.projectDao().insert(item.copy(id = java.util.UUID.randomUUID().toString()))
                        } else {
                            dbInstance.projectDao().insert(item)
                        }
                    }
                    true
                }
                is Material -> {
                    val existingByName = dbInstance.materialDao().getMaterialByName(item.name)
                    if (existingByName != null) {
                        dbInstance.materialDao().updateMaterial(item.copy(id = existingByName.id))
                    } else {
                        val existingById = dbInstance.materialDao().getMaterialById(item.id)
                        if (existingById != null) {
                            dbInstance.materialDao().insertMaterial(item.copy(id = java.util.UUID.randomUUID().toString()))
                        } else {
                            dbInstance.materialDao().insertMaterial(item)
                        }
                    }
                    true
                }
                is Provider -> {
                    val existingByName = dbInstance.providerDao().getProviderByName(item.name)
                    if (existingByName != null) {
                        dbInstance.providerDao().update(item.copy(id = existingByName.id))
                    } else {
                        dbInstance.providerDao().insert(item.copy(id = 0))
                    }
                    true
                }
                is BankAccount -> {
                    val existingByNumber = dbInstance.bankAccountDao().getAll().first().find { it.accountNumber == item.accountNumber }
                    if (existingByNumber != null) {
                        dbInstance.bankAccountDao().update(item.copy(id = existingByNumber.id))
                    } else {
                        val existingById = dbInstance.bankAccountDao().getAll().first().find { it.id == item.id }
                        if (existingById != null) {
                            dbInstance.bankAccountDao().insert(item.copy(id = java.util.UUID.randomUUID().toString()))
                        } else {
                            dbInstance.bankAccountDao().insert(item)
                        }
                    }
                    true
                }
                is WorkDiary -> {
                    val existing = dbInstance.workDiaryDao().getAll().first().find { 
                        it.projectId == item.projectId && it.date == item.date && it.activity == item.activity 
                    }
                    if (existing != null) {
                        dbInstance.workDiaryDao().update(item.copy(id = existing.id))
                    } else {
                        dbInstance.workDiaryDao().insert(item.copy(id = 0))
                    }
                    true
                }
                is LaborPrice -> { 
                    val existing = dbInstance.laborPriceDao().getLaborPrices().first()
                    if (existing != null) {
                        dbInstance.laborPriceDao().upsert(item.copy(id = existing.id))
                    } else {
                        dbInstance.laborPriceDao().upsert(item)
                    }
                    true 
                }
                is MaterialMeasurement -> { 
                    val existing = dbInstance.materialMeasurementDao().getAllMeasurements().first().find { it.name == item.name }
                    if (existing != null) {
                        dbInstance.materialMeasurementDao().upsert(item.copy(id = existing.id))
                    } else {
                        dbInstance.materialMeasurementDao().upsert(item.copy(id = 0))
                    }
                    true 
                }
                is TaxSetting -> {
                    val existing = dbInstance.taxSettingDao().getTaxSetting().first()
                    if (existing != null) {
                        dbInstance.taxSettingDao().upsert(item.copy(id = existing.id))
                    } else {
                        dbInstance.taxSettingDao().upsert(item)
                    }
                    true 
                }
                is UnitType -> {
                    dbInstance.unitTypeDao().insertUnitType(item)
                    true
                }
                is AppConfig -> { 
                    val existing = dbInstance.appConfigDao().getConfig().first()
                    if (existing != null) {
                        dbInstance.appConfigDao().insert(item.copy(id = existing.id))
                    } else {
                        dbInstance.appConfigDao().insert(item)
                    }
                    true 
                }
                is CompanyProfile -> { 
                    val existing = dbInstance.companyDao().getCompanyProfile().first()
                    if (existing != null) {
                        dbInstance.companyDao().upsert(item.copy(id = existing.id))
                    } else {
                        dbInstance.companyDao().upsert(item)
                    }
                    true 
                }
                is ProjectPhoto -> { 
                    val existing = dbInstance.projectPhotoDao().getAllPhotos().first().find { 
                        it.projectId == item.projectId && it.photoUri == item.photoUri 
                    }
                    if (existing == null) {
                        dbInstance.projectPhotoDao().insert(item.copy(id = 0))
                    }
                    true 
                }
                is CurrencyHistory -> { 
                    val existing = dbInstance.currencyHistoryDao().getAllHistory().first().find {
                        it.day == item.day && it.month == item.month && it.year == item.year
                    }
                    if (existing != null) {
                        dbInstance.currencyHistoryDao().insert(item.copy(id = existing.id))
                    } else {
                        dbInstance.currencyHistoryDao().insert(item.copy(id = 0))
                    }
                    true 
                }
                is Zone -> { 
                    val existing = dbInstance.zoneDao().getZonesForProject(item.projectId).first().find { it.name == item.name }
                    if (existing != null) {
                        dbInstance.zoneDao().insertZone(item.copy(id = existing.id))
                    } else {
                        dbInstance.zoneDao().insertZone(item.copy(id = java.util.UUID.randomUUID().toString()))
                    }
                    true 
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("RestoreVM", "Error saving item: ${item.javaClass.simpleName}", e)
            false
        }
    }

    private fun loadClients(db: SqlCipherDatabase): List<Client> {
        val list = mutableListOf<Client>()
        db.query("clients", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Client(
                    id = cursor.safeString("id"), ni = cursor.safeString("ni"),
                    name = cursor.safeString("name"), surnames = cursor.safeString("surnames"),
                    fatherName = cursor.safeString("fatherName"), motherName = cursor.safeString("motherName"),
                    sex = cursor.safeString("sex"), birthDate = cursor.safeString("birthDate"),
                    expirationDate = cursor.safeString("expirationDate"), emissionDate = cursor.safeString("emissionDate"),
                    municipality = cursor.safeString("municipality"), province = cursor.safeString("province"),
                    address = cursor.safeString("address"), phone = cursor.safeString("phone"),
                    email = cursor.safeString("email"), registrationDate = cursor.safeLong("registrationDate"),
                    frontImageUri = cursor.safeString("frontImageUri"), backImageUri = cursor.safeString("backImageUri"),
                    tomo = cursor.safeString("tomo"), folio = cursor.safeString("folio"),
                    year = cursor.safeString("year"), civilRegistry = cursor.safeString("civilRegistry"),
                    signatureImageUri = cursor.safeString("signatureImageUri"), barcodeImageUri = cursor.safeString("barcodeImageUri"),
                    qrCodeImageUri = cursor.safeString("qrCodeImageUri"), profileImageUri = cursor.safeString("profileImageUri")
                ))
            }
        }
        return list
    }

    private fun loadProjects(db: SqlCipherDatabase): List<Project> {
        val list = mutableListOf<Project>()
        db.query("projects", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Project(
                    id = cursor.safeString("id"), name = cursor.safeString("name"),
                    clientName = cursor.safeString("clientName"), clientPhone = cursor.safeString("clientPhone"),
                    clientEmail = cursor.safeString("clientEmail"), address = cursor.safeString("address"),
                    totalAreaM2 = cursor.safeDouble("totalAreaM2"), height = cursor.safeDouble("height"),
                    length = cursor.safeDouble("length"), width = cursor.safeDouble("width"),
                    constructionType = cursor.safeString("constructionType"),
                    specialPartsJson = cursor.safeString("specialPartsJson"), specialRate = cursor.safeDouble("specialRate"),
                    specialCurrency = cursor.safeString("specialCurrency", "USD"),
                    status = cursor.safeString("status", "Pendiente"), date = cursor.safeLong("date")
                ))
            }
        }
        return list
    }

    private fun loadMaterials(db: SqlCipherDatabase): List<Material> {
        val list = mutableListOf<Material>()
        db.query("materials", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val providerIdIdx = cursor.getColumnIndex("providerId")
                list.add(Material(
                    id = cursor.safeString("id"), name = cursor.safeString("name"),
                    quantity = cursor.safeDouble("quantity"), purchasePrice = cursor.safeDouble("purchasePrice"),
                    salePrice = cursor.safeDouble("salePrice"), profitPercentage = cursor.safeDouble("profitPercentage"),
                    unitType = cursor.safeString("unitType", "unidad"),
                    providerId = if (providerIdIdx == -1 || cursor.isNull(providerIdIdx)) null else cursor.getInt(providerIdIdx)
                ))
            }
        }
        return list
    }

    private fun loadBankAccounts(db: SqlCipherDatabase): List<BankAccount> {
        val list = mutableListOf<BankAccount>()
        db.query("bank_accounts", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(BankAccount(
                    id = cursor.safeString("id"), alias = cursor.safeString("alias"),
                    bankName = cursor.safeString("bankName"), accountNumber = cursor.safeString("accountNumber"),
                    currency = cursor.safeString("currency"), mobileNumber = cursor.safeString("mobileNumber"),
                    accountType = cursor.safeString("accountType"), cardImageUri = cursor.safeString("cardImageUri")
                ))
            }
        }
        return list
    }

    private fun loadProviders(db: SqlCipherDatabase): List<Provider> {
        val list = mutableListOf<Provider>()
        db.query("providers", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Provider(
                    id = cursor.safeInt("id"), name = cursor.safeString("name"),
                    contactName = cursor.safeString("contactName"), phone = cursor.safeString("phone"),
                    email = cursor.safeString("email"), category = cursor.safeString("category"),
                    rating = cursor.safeInt("rating", 5), notes = cursor.safeString("notes")
                ))
            }
        }
        return list
    }

    private fun loadDiary(db: SqlCipherDatabase): List<WorkDiary> {
        val list = mutableListOf<WorkDiary>()
        db.query("work_diary", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(WorkDiary(id = cursor.safeInt("id"), projectId = cursor.safeString("projectId"),
                    date = cursor.safeLong("date"), activity = cursor.safeString("activity"),
                    progressPercent = cursor.safeInt("progressPercent"), weather = cursor.safeString("weather", "Soleado"),
                    observations = cursor.safeString("observations")))
            }
        }
        return list
    }

    private fun loadLaborPrices(db: SqlCipherDatabase): List<LaborPrice> {
        val list = mutableListOf<LaborPrice>()
        db.query("labor_prices", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(LaborPrice(id = cursor.safeInt("id"), interiorDrywall = cursor.safeDouble("interiorDrywall"),
                    exteriorDrywall = cursor.safeDouble("exteriorDrywall"), dropCeiling = cursor.safeDouble("dropCeiling"),
                    tileCeiling = cursor.safeDouble("tileCeiling"), profitPercent = cursor.safeDouble("profitPercent")))
            }
        }
        return list
    }

    private fun loadMeasurements(db: SqlCipherDatabase): List<MaterialMeasurement> {
        val list = mutableListOf<MaterialMeasurement>()
        db.query("material_measurements", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(MaterialMeasurement(
                    id = cursor.safeInt("id"),
                    name = cursor.safeString("name"),
                    width = cursor.safeDouble("width"),
                    height = cursor.safeDouble("height"),
                    thickness = cursor.safeDouble("thickness"),
                    length = cursor.safeDouble("length"),
                    unitSystem = cursor.safeString("unitSystem", "metric"),
                    colorHex = cursor.safeString("colorHex", "#6200EE")
                ))
            }
        }
        return list
    }

    private fun loadTaxes(db: SqlCipherDatabase): List<TaxSetting> {
        val list = mutableListOf<TaxSetting>()
        db.query("tax_settings", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(TaxSetting(id = cursor.safeInt("id"), country = cursor.safeString("country"),
                    taxType = cursor.safeString("taxType"), percentage = cursor.safeDouble("percentage")))
            }
        }
        return list
    }

    private fun loadUnitTypes(db: SqlCipherDatabase): List<UnitType> {
        val list = mutableListOf<UnitType>()
        db.query("unit_types", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(UnitType(
                    name = cursor.safeString("name"),
                    isCustom = cursor.safeInt("isCustom") == 1
                ))
            }
        }
        return list
    }

    private fun loadConfig(db: SqlCipherDatabase): List<AppConfig> {
        val list = mutableListOf<AppConfig>()
        db.query("app_config", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val darkModeIndex = cursor.getColumnIndex("isDarkMode")
                list.add(AppConfig(
                    id = cursor.safeInt("id"),
                    selectedShortcuts = loadStringList(cursor, "selectedShortcuts"),
                    dashboardShortcuts = loadStringList(cursor, "dashboardShortcuts"),
                    isDarkMode = if (darkModeIndex == -1 || cursor.isNull(darkModeIndex)) null else cursor.getInt(darkModeIndex) == 1,
                    decimalPrecision = cursor.safeInt("decimalPrecision", 2),
                    minProfitMargin = cursor.safeDouble("minProfitMargin", 5.0),
                    maxProfitMargin = cursor.safeDouble("maxProfitMargin", 500.0)
                ))
            }
        }
        return list
    }

    private fun loadStringList(cursor: android.database.Cursor, column: String): List<String> {
        val index = cursor.getColumnIndex(column)
        if (index == -1 || cursor.isNull(index)) return emptyList()
        val str = cursor.getString(index)
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            com.google.gson.Gson().fromJson(str, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun loadCompany(db: SqlCipherDatabase): List<CompanyProfile> {
        val list = mutableListOf<CompanyProfile>()
        db.query("company_profile", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(CompanyProfile(id = cursor.safeInt("id"), country = cursor.safeString("country"),
                    businessName = cursor.safeString("businessName"), logoUri = cursor.safeString("logoUri"),
                    address = cursor.safeString("address"), phone = cursor.safeString("phone"),
                    email = cursor.safeString("email"), bankType = cursor.safeString("bankType"),
                    accountNumber = cursor.safeString("accountNumber"), currencyType = cursor.safeString("currencyType"),
                    mobileBank = cursor.safeString("mobileBank"), signatureUri = cursor.safeString("signatureUri"),
                    ownerPhotoUri = cursor.safeString("ownerPhotoUri")))
            }
        }
        return list
    }

    private fun loadPhotos(db: SqlCipherDatabase): List<ProjectPhoto> {
        val list = mutableListOf<ProjectPhoto>()
        db.query("project_photos", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(ProjectPhoto(id = cursor.safeInt("id"), projectId = cursor.safeString("projectId"),
                    photoUri = cursor.safeString("photoUri"), date = cursor.safeLong("date"),
                    description = cursor.safeString("description")))
            }
        }
        return list
    }

    private fun loadHistory(db: SqlCipherDatabase): List<CurrencyHistory> {
        val list = mutableListOf<CurrencyHistory>()
        db.query("currency_history", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(CurrencyHistory(id = cursor.safeInt("id"), day = cursor.safeInt("day"),
                    month = cursor.safeInt("month"), year = cursor.safeInt("year"),
                    timestamp = cursor.safeLong("timestamp"), usdRate = cursor.safeDouble("usdRate"),
                    eurRate = cursor.safeDouble("eurRate"), mlcRate = cursor.safeDouble("mlcRate"),
                    cupRate = cursor.safeDouble("cupRate", 1.0),
                    cadRate = cursor.safeDouble("cadRate"), mexRate = cursor.safeDouble("mexRate"),
                    zelleRate = cursor.safeDouble("zelleRate"), claRate = cursor.safeDouble("claRate")
                ))
            }
        }
        return list
    }

    private fun loadZones(db: SqlCipherDatabase): List<Zone> {
        val list = mutableListOf<Zone>()
        db.query("zones", null, null, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Zone(id = cursor.safeString("id"), projectId = cursor.safeString("projectId"),
                    name = cursor.safeString("name"), areaM2 = cursor.safeDouble("areaM2")))
            }
        }
        return list
    }

    private fun android.database.Cursor.safeString(column: String, default: String = ""): String {
        val index = getColumnIndex(column)
        return if (index != -1 && !isNull(index)) getString(index) else default
    }
    private fun android.database.Cursor.safeLong(column: String, default: Long = 0L): Long {
        val index = getColumnIndex(column)
        return if (index != -1 && !isNull(index)) getLong(index) else default
    }
    private fun android.database.Cursor.safeDouble(column: String, default: Double = 0.0): Double {
        val index = getColumnIndex(column)
        return if (index != -1 && !isNull(index)) getDouble(index) else default
    }
    private fun android.database.Cursor.safeInt(column: String, default: Int = 0): Int {
        val index = getColumnIndex(column)
        return if (index != -1 && !isNull(index)) getInt(index) else default
    }
}