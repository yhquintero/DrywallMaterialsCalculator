package com.drywall.common.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import com.drywall.common.security.SecureStorageUtils

object DatabaseBackupUtils {
    private const val TAG = "BackupUtils"
    private const val BUFFER_SIZE = 65536 // Reducido para evitar OOM (era 262144)
    private const val COMPRESSION_LEVEL = 6
    private const val MAX_ZIP_TOTAL_SIZE = 1024L * 1024 * 1024 // 1 GB
    private const val MAX_ENTRY_SIZE = 200L * 1024 * 1024 // 200 MB
    private const val MAX_ENTRY_COUNT = 5000
    private const val SECURE_STATE_PATH = "secure_state/prefs_state.json"

    private val SECURE_PREFS_FILES = listOf(
        "secure_licensing_prefs_v2",
        "trial_license_vault_v1",
        "permanent_license_vault",
        "trial_protection_vault",
        "secure_security_prefs"
    )

    fun isPathTraversalSafe(entryName: String): Boolean {
        if (entryName.contains("..")) return false
        if (entryName.startsWith("/")) return false
        val normalized = entryName.replace('\\', '/')
        return !(normalized.contains("../") || normalized.contains("/.."))
    }

    private fun validateAndResolve(baseDir: File, relativePath: String): File? {
        if (!isPathTraversalSafe(relativePath)) return null
        val resolved = File(baseDir, relativePath).canonicalFile
        val baseCanonical = baseDir.canonicalFile
        return if (resolved.startsWith(baseCanonical)) resolved else null
    }

    private fun captureSecurePrefsState(context: Context): String {
        val state = JSONObject()
        SECURE_PREFS_FILES.forEach { prefName ->
            try {
                val prefs = SecureStorageUtils.getEncryptedPrefs(context, prefName)
                val prefsObj = JSONObject()
                val all = prefs.all
                all.forEach { (key, value) ->
                    when (value) {
                        is String -> prefsObj.put(key, value)
                        is Boolean -> prefsObj.put(key, value)
                        is Long -> prefsObj.put(key, value)
                        is Int -> prefsObj.put(key, value)
                        is Float -> prefsObj.put(key, value)
                        is Set<*> -> {
                            val arr = JSONArray()
                            value.forEach { arr.put(it) }
                            prefsObj.put(key, arr)
                        }
                    }
                }
                state.put(prefName, prefsObj)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo capturar estado de $prefName: ${e.message}")
            }
        }
        return state.toString(2)
    }

    private fun restoreSecurePrefsState(context: Context, json: String) {
        try {
            val state = JSONObject(json)
            SECURE_PREFS_FILES.forEach { prefName ->
                if (!state.has(prefName)) return@forEach
                try {
                    val prefs = SecureStorageUtils.getEncryptedPrefs(context, prefName)
                    val prefsObj = state.getJSONObject(prefName)
                    val editor = prefs.edit()
                    prefsObj.keys().forEach { key ->
                        val value = prefsObj.get(key)
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Long -> editor.putLong(key, value)
                            is Int -> editor.putInt(key, value)
                            is Float -> editor.putFloat(key, value)
                            is JSONArray -> {
                                val set = mutableSetOf<String>()
                                for (i in 0 until value.length()) { set.add(value.getString(i)) }
                                editor.putStringSet(key, set)
                            }
                        }
                    }
                    editor.apply()
                    Log.i(TAG, "Preferencia segura restaurada: $prefName (${prefsObj.length()} claves)")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo restaurar $prefName: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando estado seguro", e)
        }
    }

    fun exportFullBackup(context: Context, destinationUri: Uri, preExportCheckpoint: (() -> Unit)? = null, onProgress: ((Int, Int, String) -> Unit)? = null) =
        exportFullBackup(context, destinationUri, getDefaultDbName(context), defaultSensitiveFiles(), null, "plain", preExportCheckpoint, onProgress)

    fun importFullBackup(context: Context, sourceUri: Uri, lenient: Boolean = false, onProgress: ((Int, Int, String) -> Unit)? = null) =
        importFullBackup(context, sourceUri, getDefaultDbName(context), defaultSensitiveFiles(), lenient, onProgress)

    fun getSuggestedFileName(isApp: Boolean = true): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return if (isApp) "Respaldo_Total_Drywall_$date.zip" else "KeyGen_Backup_$date.zip"
    }

    fun exportDatabaseWithRecovery(
        context: Context,
        destinationUri: Uri,
        dbName: String,
        sensitiveFiles: List<String> = defaultSensitiveFiles(),
        dbOverrideFile: File? = null,
        dbFormat: String = "plain",
        preExportCheckpoint: (() -> Unit)? = null,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): String {
        try {
            val dbFile = if (dbOverrideFile != null && dbOverrideFile.exists()) {
                dbOverrideFile
            } else {
                context.getDatabasePath(dbName)
            }

            if (!dbFile.exists()) {
                Log.w(TAG, "Archivo de base de datos no encontrado: ${dbFile.absolutePath}")
                return "Error: Archivo de base de datos no encontrado en ${dbFile.absolutePath}"
            }

            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (dbFormat == "plain" && (walFile.exists() || shmFile.exists())) {
                Log.i(TAG, "ADVERTENCIA: Se están exportando archivos WAL/SHM de una base de datos sin cifrar")
            }

            exportFullBackup(context, destinationUri, dbName, sensitiveFiles, dbOverrideFile, dbFormat, preExportCheckpoint, onProgress)
            return "Success"
        } catch (e: Exception) {
            Log.e(TAG, "Error exportando base de datos con recuperación", e)
            return "Error: ${e.message}"
        }
    }

    fun defaultSensitiveFiles(): List<String> = listOf(
        "secure_db_passphrase",
        "secure_licensing",
        "secure_remote_key",
        "secure_keygen",
        "secure_security" // MainActivity security prefs
    )

    private fun getDefaultDbName(context: Context): String {
        return when (context.packageName) {
            "com.drywall.calculator" -> "drywall_db"
            "com.drywall.keygen" -> "keygen_db"
            else -> "drywall_db"
        }
    }

    fun exportFullBackup(
        context: Context,
        destinationUri: Uri,
        dbName: String,
        sensitiveFiles: List<String> = defaultSensitiveFiles(),
        dbOverrideFile: File? = null,
        dbFormat: String = "plain",
        preExportCheckpoint: (() -> Unit)? = null,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ) {
        try {
            val filesToBackup = mutableListOf<Pair<File, String>>()
            val isOverrideEncrypted = dbFormat != "plain"

            val dbFile = if (dbOverrideFile != null && dbOverrideFile.exists()) {
                dbOverrideFile
            } else {
                context.getDatabasePath(dbName)
            }
            if (dbFile.exists()) {
                if (dbOverrideFile == null || isOverrideEncrypted) {
                    // WAL checkpoint: ensure all data is in the main DB file before reading
                    try {
                        preExportCheckpoint?.invoke()
                        Log.i(TAG, "WAL checkpoint completado antes de exportar")
                    } catch (e: Exception) {
                        Log.w(TAG, "WAL checkpoint falló, continuando con archivos actuales: ${e.message}")
                    }
                }

                filesToBackup.add(dbFile to "database/$dbName")

                if (dbOverrideFile == null || isOverrideEncrypted) {
                    // Only include WAL/SHM from the original encrypted DB
                    val walFile = File(dbFile.path + "-wal")
                    if (walFile.exists()) {
                        filesToBackup.add(walFile to "database/$dbName-wal")
                    }
                    val shmFile = File(dbFile.path + "-shm")
                    if (shmFile.exists()) {
                        filesToBackup.add(shmFile to "database/$dbName-shm")
                    }
                }
            }

            fun collectRecursive(folder: File, zipPath: String) {
                folder.listFiles()?.forEach { file ->
                    val name = file.name
                    val filePath = "$zipPath/$name"
                    if (file.isDirectory) {
                        // Exclude non-essential or large redundant directories
                        if ((name != "cache") && (name != "code_cache") && 
                            (name != "error_logs") && (name != "db_backups") && 
                            !Files.isSymbolicLink(file.toPath())) {
                            collectRecursive(file, filePath)
                        }
                    } else {
                        // Exclude log files and temporary backups to keep exports lightweight
                        // CRITICAL: Exclude .zip files to avoid nested backups and exponential size growth
                        if (!name.endsWith(".log") && 
                            !name.endsWith(".zip") && 
                            !name.contains("_pre_restore_") && 
                            !name.endsWith("-journal")) {
                            filesToBackup.add(file to filePath)
                        }
                    }
                }
            }

            collectRecursive(context.filesDir, "media")
            val imagesDir = File(context.getExternalFilesDir(null), "Pictures")
            if (imagesDir.exists()) collectRecursive(imagesDir, "external_media")

            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists()) {
                sharedPrefsDir.listFiles()?.forEach { file ->
                    val name = file.name
                    val isEncryptedPrefs = SECURE_PREFS_FILES.any { name.contains(it) }
                    if (!isEncryptedPrefs) {
                        filesToBackup.add(file to "prefs/$name")
                    }
                }
            }

            val totalFiles = filesToBackup.size
            val digest = MessageDigest.getInstance("SHA-256")
            val manifest = buildManifestJson(context, dbName, totalFiles, dbFormat)

            val secureStateJson = try { captureSecurePrefsState(context) } catch (e: Exception) { Log.w(TAG, "No se pudo capturar estado seguro: ${e.message}"); null }

            context.contentResolver.openOutputStream(destinationUri)?.use { rawOut ->
                BufferedOutputStream(rawOut, BUFFER_SIZE).use { bufferedOut ->
                    ZipOutputStream(bufferedOut).use { zipOut ->
                        zipOut.setLevel(COMPRESSION_LEVEL)

                        // Write manifest (excluded from integrity hash)
                        val manifestEntry = ZipEntry("manifest.json")
                        manifestEntry.time = System.currentTimeMillis()
                        zipOut.putNextEntry(manifestEntry)
                        zipOut.write(manifest.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()

                        if (secureStateJson != null) {
                            val stateEntry = ZipEntry(SECURE_STATE_PATH)
                            stateEntry.time = System.currentTimeMillis()
                            zipOut.putNextEntry(stateEntry)
                            zipOut.write(secureStateJson.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }

                        val buffer = ByteArray(BUFFER_SIZE)
                        filesToBackup.forEachIndexed { index, pair ->
                            val file = pair.first
                            val zipPath = pair.second
                            onProgress?.invoke(index, totalFiles, zipPath)

                            val entry = ZipEntry(zipPath)
                            entry.size = file.length()
                            entry.time = file.lastModified()
                            zipOut.putNextEntry(entry)

                            FileInputStream(file).use { fis ->
                                BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                                    var len: Int
                                    while (bis.read(buffer).also { len = it } > 0) {
                                        zipOut.write(buffer, 0, len)
                                        digest.update(buffer, 0, len)
                                    }
                                }
                            }
                            zipOut.closeEntry()
                        }

                        // Write integrity hash as final entry
                        val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                        val hashEntry = ZipEntry("backup.sha256")
                        hashEntry.time = System.currentTimeMillis()
                        zipOut.putNextEntry(hashEntry)
                        zipOut.write(hashHex.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()

                        zipOut.finish()
                    }
                }
            }
            Log.i(TAG, "Backup completado: $totalFiles archivos con integridad SHA-256")
        } catch (e: Exception) {
            Log.e(TAG, "Error en exportaci\u00f3n", e)
            throw e
        }
    }

    fun importFullBackup(
        context: Context,
        sourceUri: Uri,
        dbName: String,
        sensitiveFiles: List<String> = defaultSensitiveFiles(),
        lenient: Boolean = false,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ) {
        var processedCount = 0
        try {
            // Usar streams directamente de la URI en lugar de copiar el ZIP entero a cache (Ahorro de memoria)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                try {
                    // Backup current database before restoring
                    backupCurrentDatabase(context, dbName)
                    
                    // Borrar archivos auxiliares actuales para evitar conflictos de cifrado (WAL/SHM)
                    val dbFile = context.getDatabasePath(dbName)
                    listOf("-wal", "-shm", "-journal").forEach { suffix ->
                        val aux = File(dbFile.path + suffix)
                        if (aux.exists()) {
                            Log.i(TAG, "Limpiando archivo auxiliar previo a restauración: ${aux.name}")
                            aux.delete()
                        }
                    }

                    processedCount = extractBackupEntriesFromStream(context, input, dbName, sensitiveFiles, lenient, onProgress)

                    // Post-restore verification
                    if (!lenient) verifyRestoredDatabase(context, dbName)
                    
                    // Borrar archivos auxiliares post-restauración para forzar a SQLCipher a usar el archivo principal
                    listOf("-wal", "-shm", "-journal").forEach { suffix ->
                        val aux = File(dbFile.path + suffix)
                        if (aux.exists()) aux.delete()
                    }

                    Log.i(TAG, "Importación completada: $processedCount archivos")
                } catch (e: Exception) {
                    Log.e(TAG, "Error durante extracción del stream", e)
                    throw e
                }
            } ?: throw IOException("No se pudo abrir el stream de la URI de respaldo")
        } catch (e: Exception) {
            Log.e(TAG, "Error en importación", e)
            // Attempt to restore from backup if available
            if (!lenient) tryRestoreFromBackup(context, dbName)
            throw e
        }
    }

    private fun extractBackupEntriesFromStream(
        context: Context,
        inputStream: InputStream,
        dbName: String,
        sensitiveFiles: List<String>,
        lenient: Boolean = false,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): Int {
        var processedCount = 0
        var totalUncompressedSize = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var secureStateJson: String? = null

        ZipInputStream(BufferedInputStream(inputStream, BUFFER_SIZE)).use { zipIn ->
            var entry: ZipEntry? = null
            try {
                entry = zipIn.nextEntry
            } catch (e: Exception) {
                if (lenient) Log.e(TAG, "Error leyendo primera entrada del ZIP", e) else throw e
            }

            while (entry != null) {
                try {
                    val fileName = entry.name
                    if (fileName == "manifest.json" || fileName == "backup.sha256") {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    if (fileName == SECURE_STATE_PATH) {
                        val baos = ByteArrayOutputStream()
                        val sb = StringBuilder()
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            sb.append(String(buffer, 0, len))
                        }
                        secureStateJson = sb.toString()
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    processedCount++
                    if (!lenient) {
                        if (processedCount > MAX_ENTRY_COUNT) {
                            throw SecurityException("Demasiados archivos en el respaldo (máximo: $MAX_ENTRY_COUNT)")
                        }
                    }

                    if (isPathTraversalSafe(fileName) && !entry.isDirectory) {
                        onProgress?.invoke(processedCount, -1, fileName)

                        val destFile = when {
                            fileName.startsWith("database/") -> {
                                val dbPartName = fileName.substringAfter("database/")
                                val dbPath = context.getDatabasePath(dbName)
                                
                                when {
                                    dbPartName == dbName -> dbPath
                                    dbPartName == "$dbName-wal" -> File(dbPath.path + "-wal")
                                    dbPartName == "$dbName-shm" -> File(dbPath.path + "-shm")
                                    else -> dbPath.parentFile?.let { validateAndResolve(it, dbPartName) }
                                }
                            }
                            fileName.startsWith("media/") -> {
                                val relativePath = fileName.substringAfter("media/")
                                validateAndResolve(context.filesDir, relativePath)
                            }
                            fileName.startsWith("external_media/") -> {
                                val relativePath = fileName.substringAfter("external_media/")
                                val baseDir = File(context.getExternalFilesDir(null), "Pictures")
                                validateAndResolve(baseDir, relativePath)
                            }
                            fileName.startsWith("prefs/") -> {
                                val relativePath = fileName.substringAfter("prefs/")
                                val isEncryptedPrefs = SECURE_PREFS_FILES.any { relativePath.contains(it) }
                                if (!isEncryptedPrefs) {
                                    val baseDir = File(context.applicationInfo.dataDir, "shared_prefs")
                                    validateAndResolve(baseDir, relativePath)
                                } else {
                                    Log.d(TAG, "Omitiendo preferencia encriptada (restaurada vía JSON): $relativePath")
                                    null
                                }
                            }
                            else -> null
                        }

                        if (destFile != null) {
                            destFile.parentFile?.mkdirs()
                            try {
                                FileOutputStream(destFile).use { fos ->
                                    BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                        var len: Int
                                        while (zipIn.read(buffer).also { len = it } > 0) {
                                            bos.write(buffer, 0, len)
                                        }
                                        bos.flush()
                                    }
                                }
                            } catch (e: Exception) {
                                if (lenient) Log.e(TAG, "Error extrayendo $fileName", e) else throw e
                            }
                        }
                    }
                    zipIn.closeEntry()
                } catch (e: Exception) {
                    if (lenient) Log.e(TAG, "Error procesando entrada del ZIP", e) else throw e
                }
                
                if (processedCount % 50 == 0) {
                    System.gc()
                }
                
                try {
                    entry = zipIn.nextEntry
                } catch (e: Exception) {
                    if (lenient) entry = null else throw e
                }
            }
        }

        if (secureStateJson != null) {
            try {
                restoreSecurePrefsState(context, secureStateJson)
                Log.i(TAG, "Estado de preferencias seguras restaurado correctamente desde stream")
            } catch (e: Exception) {
                Log.w(TAG, "Error restaurando estado seguro desde stream: ${e.message}")
            }
        }

        return processedCount
    }

    private fun backupCurrentDatabase(context: Context, dbName: String) {
        try {
            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) {
                val backupDir = File(context.filesDir, "db_backups")
                backupDir.mkdirs()

                // Clean old backups (keep only last 3)
                val oldBackups = backupDir.listFiles { file -> file.name.startsWith("${dbName}_pre_restore_") }
                if (oldBackups != null && oldBackups.size >= 3) {
                    oldBackups.sortedBy { it.lastModified() }.take(oldBackups.size - 2).forEach { it.delete() }
                }

                val timestamp = System.currentTimeMillis()
                val backupFile = File(backupDir, "${dbName}_pre_restore_$timestamp.db")
                dbFile.inputStream().use { input ->
                    backupFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear backup de seguridad", e)
        }
    }

    private fun verifyRestoredDatabase(context: Context, dbName: String) {
        try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                Log.w(TAG, "Base de datos no existe después de la restauración")
                return
            }

            // Don't delete WAL file - let SQLite process it normally
            // Just verify the main DB file exists and has reasonable size
            if (dbFile.length() < 4096) {
                Log.w(TAG, "Base de datos restaurada parece incompleta: ${dbFile.length()} bytes")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error verificando base de datos restaurada", e)
        }
    }

    private fun tryRestoreFromBackup(context: Context, dbName: String) {
        try {
            val backupDir = File(context.filesDir, "db_backups")
            if (backupDir.exists()) {
                val backupFiles = backupDir.listFiles { file -> file.name.startsWith("$dbName-pre_restore_") }
                if (backupFiles != null && backupFiles.isNotEmpty()) {
                    val latestBackup = backupFiles.maxByOrNull { it.lastModified() }
                    if (latestBackup != null) {
                        Log.i(TAG, "Intentando restaurar desde backup: ${latestBackup.absolutePath}")
                        latestBackup.copyTo(context.getDatabasePath(dbName), overwrite = true)
                        Log.i(TAG, "Restauración desde backup completada")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando desde backup", e)
        }
    }

    private fun secureDelete(file: File) {
        try {
            if (!file.exists()) return
            val length = file.length()
            if (length > 0 && length < Int.MAX_VALUE) {
                val bufferSize = minOf(length.toInt(), 65536)
                val secureRandom = java.security.SecureRandom()

                // Pasada 1: Sobrescribir con ceros
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    val overwrite = ByteArray(bufferSize)
                    var remaining = length
                    while (remaining > 0) {
                        val writeSize = minOf(remaining, overwrite.size.toLong())
                        raf.write(overwrite, 0, writeSize.toInt())
                        remaining -= writeSize
                    }
                }

                // Pasada 2: Sobrescribir con unos (0xFF)
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    val pattern = ByteArray(bufferSize) { 0xFF.toByte() }
                    var remaining = length
                    while (remaining > 0) {
                        val writeSize = minOf(remaining, pattern.size.toLong())
                        raf.write(pattern, 0, writeSize.toInt())
                        remaining -= writeSize
                    }
                }

                // Pasada 3: Sobrescribir con datos aleatorios
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    val random = ByteArray(bufferSize)
                    var remaining = length
                    while (remaining > 0) {
                        secureRandom.nextBytes(random)
                        val writeSize = minOf(remaining, random.size.toLong())
                        raf.write(random, 0, writeSize.toInt())
                        remaining -= writeSize
                    }
                }

                // Pasada 4: Sobrescribir con ceros finales
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0)
                    val finalOverwrite = ByteArray(bufferSize)
                    var remaining = length
                    while (remaining > 0) {
                        val writeSize = minOf(remaining, finalOverwrite.size.toLong())
                        raf.write(finalOverwrite, 0, writeSize.toInt())
                        remaining -= writeSize
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Secure delete failed for ${file.path}", e)
            file.delete()
        }
    }

    private fun verifyBackupIntegrity(zipFile: File) {
        var expectedHash: String? = null
        val digest = MessageDigest.getInstance("SHA-256")
        var hashEntryFound = false

        try {
            // Pre-calculate hash of the backup content for comparison
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), BUFFER_SIZE)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "backup.sha256") {
                        val baos = ByteArrayOutputStream()
                        val hashBuffer = ByteArray(1024)
                        var hLen: Int
                        while (zipIn.read(hashBuffer).also { hLen = it } > 0) {
                            baos.write(hashBuffer, 0, hLen)
                        }
                        expectedHash = baos.toString("UTF-8").trim()
                        hashEntryFound = true
                    } else if (name != "manifest.json" && !entry.isDirectory) {
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            digest.update(buffer, 0, len)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (!hashEntryFound || expectedHash.isNullOrBlank()) {
                Log.w(TAG, "El respaldo no contiene hash de integridad — permitiendo por compatibilidad")
                return
            }

            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (computedHash != expectedHash) {
                Log.w(TAG, "Hash mismatch: expected=$expectedHash, computed=$computedHash")
                throw SecurityException("La integridad del respaldo no coincide.")
            }
            Log.i(TAG, "Integridad SHA-256 verificada correctamente")
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la verificación de integridad", e)
        }
    }

    /**
     * Calcula el hash SHA-256 de un archivo para detección de duplicados.
     */
    fun calculateFileHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            FileInputStream(file).use { fis ->
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    digest.update(buffer, 0, len)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.name + file.length() // Fallback a nombre+tamaño si falla el hash
        }
    }

    private fun extractBackupEntries(
        context: Context,
        zipFile: File,
        dbName: String,
        sensitiveFiles: List<String>,
        lenient: Boolean = false,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): Int {
        var processedCount = 0
        var totalUncompressedSize = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var secureStateJson: String? = null

        ZipInputStream(BufferedInputStream(zipFile.inputStream(), BUFFER_SIZE)).use { zipIn ->
            var entry: ZipEntry? = null
            try {
                entry = zipIn.nextEntry
            } catch (e: Exception) {
                if (lenient) Log.e(TAG, "Error leyendo primera entrada del ZIP", e) else throw e
            }

            while (entry != null) {
                try {
                    val fileName = entry.name
                    if (fileName == "manifest.json" || fileName == "backup.sha256") {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    if (fileName == SECURE_STATE_PATH) {
                        val sb = StringBuilder()
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            sb.append(String(buffer, 0, len))
                        }
                        secureStateJson = sb.toString()
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }

                    // Zip bomb protection: validate entry count and sizes
                    processedCount++
                    if (!lenient) {
                        if (processedCount > MAX_ENTRY_COUNT) {
                            throw SecurityException("Demasiados archivos en el respaldo (máximo: $MAX_ENTRY_COUNT)")
                        }
                        if (entry.size > MAX_ENTRY_SIZE) {
                            throw SecurityException("Archivo demasiado grande en el respaldo: $fileName (${entry.size} bytes)")
                        }
                        totalUncompressedSize += entry.size
                        if (totalUncompressedSize > MAX_ZIP_TOTAL_SIZE) {
                            throw SecurityException("El respaldo total excede el tamaño máximo permitido (${MAX_ZIP_TOTAL_SIZE / 1024 / 1024} MB)")
                        }
                    }

                    if (isPathTraversalSafe(fileName) && !entry.isDirectory) {
                        onProgress?.invoke(processedCount, -1, fileName)

                        val destFile = when {
                            fileName.startsWith("database/") -> {
                                val dbPartName = fileName.substringAfter("database/")
                                val dbPath = context.getDatabasePath(dbName)
                                val parentDir = dbPath.parentFile
                                
                                // Mapear correctamente drywall_db, drywall_db-wal, drywall_db-shm
                                when {
                                    dbPartName == dbName -> dbPath
                                    dbPartName == "$dbName-wal" -> File(dbPath.path + "-wal")
                                    dbPartName == "$dbName-shm" -> File(dbPath.path + "-shm")
                                    else -> parentDir?.let { validateAndResolve(it, dbPartName) }
                                }
                            }
                            fileName.startsWith("media/") -> {
                                val relativePath = fileName.substringAfter("media/")
                                validateAndResolve(context.filesDir, relativePath)
                            }
                            fileName.startsWith("external_media/") -> {
                                val relativePath = fileName.substringAfter("external_media/")
                                val baseDir = File(context.getExternalFilesDir(null), "Pictures")
                                validateAndResolve(baseDir, relativePath)
                            }
                            fileName.startsWith("prefs/") -> {
                                val relativePath = fileName.substringAfter("prefs/")
                                // Skip sensitive preferences (license, db passphrase, security keys)
                                val isEncryptedPrefs = SECURE_PREFS_FILES.any { relativePath.contains(it) }
                                if (!isEncryptedPrefs) {
                                    val baseDir = File(context.applicationInfo.dataDir, "shared_prefs")
                                    validateAndResolve(baseDir, relativePath)
                                } else {
                                    Log.i(TAG, "Omitiendo preferencia encriptada en restauración: $relativePath")
                                    null
                                }
                            }
                            else -> null
                        }

                        if (destFile != null) {
                            // Detección de duplicados para imágenes (sólo extraer si es nueva o diferente)
                            val isImage = fileName.contains("/Pictures/") || fileName.startsWith("external_media/")
                            var skipExtraction = false
                            
                            if (isImage && destFile.exists()) {
                                try {
                                    // Comparamos tamaños primero (rápido)
                                    if (destFile.length() == entry.size) {
                                        // Si el tamaño coincide, asumimos que es el mismo archivo 
                                        // para evitar el costo de calcular el hash de cada imagen
                                        skipExtraction = true
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error comparando duplicado para $fileName")
                                }
                            }

                            if (!skipExtraction) {
                                destFile.parentFile?.mkdirs()
                                try {
                                    FileOutputStream(destFile).use { fos ->
                                        BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                                            var len: Int
                                            while (zipIn.read(buffer).also { len = it } > 0) {
                                                bos.write(buffer, 0, len)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (lenient) Log.e(TAG, "Error extrayendo $fileName", e) else throw e
                                }
                            } else {
                                Log.d(TAG, "Omitiendo duplicado: $fileName")
                            }
                        }
                    }
                    zipIn.closeEntry()
                } catch (e: Exception) {
                    if (lenient) Log.e(TAG, "Error procesando entrada del ZIP", e) else throw e
                }
                
                try {
                    entry = zipIn.nextEntry
                } catch (e: Exception) {
                    if (lenient) {
                        Log.e(TAG, "Error leyendo siguiente entrada del ZIP", e)
                        entry = null
                    } else throw e
                }
            }
        }

        if (secureStateJson != null) {
            try {
                restoreSecurePrefsState(context, secureStateJson)
                Log.i(TAG, "Estado de preferencias seguras restaurado correctamente")
            } catch (e: Exception) {
                Log.w(TAG, "Error restaurando estado seguro: ${e.message}")
            }
        }

        return processedCount
    }

    private fun buildManifestJson(context: Context, dbName: String, totalFiles: Int, dbFormat: String = "plain"): String {
        return try {
            org.json.JSONObject().apply {
                put("app", context.packageName)
                put("version", 1)
                put("created", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                put("db", dbName)
                put("totalFiles", totalFiles)
                put("dbFormat", dbFormat)
            }.toString(2)
        } catch (e: Exception) {
            // Fallback to string building if JSONObject fails
            buildString {
                appendLine("{")
                appendLine("  \"app\": \"${context.packageName}\",")
                appendLine("  \"version\": 1,")
                appendLine("  \"created\": \"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())}\",")
                appendLine("  \"db\": \"$dbName\",")
                appendLine("  \"totalFiles\": $totalFiles,")
                appendLine("  \"dbFormat\": \"$dbFormat\"")
                appendLine("}")
            }
        }
    }
}
