package com.drywall.calculator.presentation.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.drywall.common.utils.DatabaseBackupUtils
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupRecoveryUtils {
    private const val TAG = "BackupRecoveryUtils"

    fun performDatabaseBackupRecovery(context: Context, destination: Uri): String {
        return try {
            DatabaseBackupUtils.exportDatabaseWithRecovery(
                context,
                destination,
                "drywall_db"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en performDatabaseBackupRecovery", e)
            "Error: ${e.message}"
        }
    }

    fun performDatabaseBackupRecovery(context: Context, destination: String): String {
        return try {
            val file = File(context.filesDir, destination)
            val uri = Uri.fromFile(file)
            performDatabaseBackupRecovery(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error en performDatabaseBackupRecovery string", e)
            "Error: ${e.message}"
        }
    }

    fun performSecureBackupRecovery(context: Context, identifier: String) {
        Log.i(TAG, "Iniciando recuperación de respaldo seguro para: $identifier")
        // Implementación básica o placeholder si no hay lógica específica requerida
    }

    fun extractAnalysisData(context: Context, uri: Uri): Pair<File?, String?> {
        var tempDb: File? = null
        var manifest: String? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "database/drywall_db" || entry.name == "database/keygen_db" -> {
                                val file = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.db")
                                FileOutputStream(file).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                                tempDb = file
                            }
                            entry.name == "manifest.json" -> {
                                val baos = ByteArrayOutputStream()
                                zipIn.copyTo(baos)
                                manifest = baos.toString("UTF-8")
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo datos de análisis", e)
        }
        return Pair(tempDb, manifest)
    }

    fun extractDatabaseFromBackup(context: Context, uri: Uri): File? {
        return extractAnalysisData(context, uri).first
    }

    fun createOptimizedZip(
        dbFile: File,
        destinationFile: File,
        manifest: String,
        compressionLevel: Int
    ) {
        FileOutputStream(destinationFile).use { fos ->
            ZipOutputStream(fos).use { zipOut ->
                zipOut.setLevel(compressionLevel)

                val manifestEntry = ZipEntry("manifest.json")
                manifestEntry.time = System.currentTimeMillis()
                zipOut.putNextEntry(manifestEntry)
                zipOut.write(manifest.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("database/drywall_db"))
                dbFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()

                val hashEntry = ZipEntry("backup.sha256")
                hashEntry.time = System.currentTimeMillis()
                zipOut.putNextEntry(hashEntry)
                zipOut.write(computeSha256(dbFile).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                Log.i(TAG, "Archivo ZIP creado: ${destinationFile.absolutePath}")
            }
        }
    }

    private fun computeSha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        if (file.exists()) {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var readCount: Int
                while (fis.read(buffer).also { readCount = it } > 0) {
                    md.update(buffer, 0, readCount)
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
