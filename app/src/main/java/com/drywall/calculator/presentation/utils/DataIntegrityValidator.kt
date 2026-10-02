package com.drywall.calculator.presentation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import com.google.gson.Gson

object DataIntegrityValidator {
    private const val TAG = "DataIntegrityValidator"
    private const val VALIDATION_TIMEOUT_MS = 30000L

    fun validateIntegrity(databasePath: String, validationFingerprint: String?): Boolean {
        try {
            Log.i(TAG, "Validando integridad: $databasePath")
            val startTime = System.currentTimeMillis()

            val result = try {
                val file = File(databasePath)
                if (!file.exists() || file.length() == 0L) {
                    Log.w(TAG, "Archivo de base de datos inválido o vacío")
                    false
                } else {
                    val computedHash = computeFileChecksum(file)
                    val isValid = computedHash.equals(validationFingerprint, ignoreCase = true)
                    Log.i(TAG, "Integridad ${if (isValid) "OK" else "fallida"}: almacenado=$validationFingerprint, calculado=$computedHash")
                    isValid
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculando checksum", e)
                false
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > VALIDATION_TIMEOUT_MS) {
                Log.w(TAG, "Validación excedió timeout: ${elapsed}ms")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error catastrófico en validación de integridad", e)
            return false
        }
    }

    fun validateDatabase(context: Context, backupChecksum: String? = null): Pair<Boolean, String> {
        try {
            Log.i(TAG, "Validando estado de base de datos...")

            val databasePath = context.getDatabasePath("drywall_db")
            Log.i(TAG, "Ruta BD: ${databasePath.absolutePath}, existe: ${databasePath.exists()}, longitud: ${databasePath.length()}")

            if (!databasePath.exists()) {
                Log.i(TAG, "BD no encontrada - se requiere nueva")
                return Pair(false, "Base de datos no existe - se requiere una nueva")
            }

            if (databasePath.length() == 0L) {
                Log.w(TAG, "BD vacía - se requiere una nueva")
                return Pair(false, "Base de datos vacía - se requiere una nueva")
            }

            val checksumValid = if (backupChecksum != null) validateIntegrity(databasePath.absolutePath, backupChecksum) else true

            if (!checksumValid) {
                Log.w(TAG, "Checksum inválido, intentando regenerar...")
                val regenerated = regenerateDatabaseContent(context, databasePath)
                return Pair(regenerated, if (regenerated) "BD regenerada" else "Fallo en regenerar BD")
            }

            Log.i(TAG, "BD válida verificada")
            return Pair(true, "Base de datos válida")
        } catch (e: Exception) {
            Log.e(TAG, "Error validando estado de base de datos", e)
            return Pair(false, "Error validando BD: ${e.message}")
        }
    }

    fun validateLicensingData(context: Context): Pair<Boolean, String> {
        try {
            Log.i(TAG, "Validando datos de licencia...")

            val prefs = context.getSharedPreferences("secure_licensing_prefs_v2", Context.MODE_PRIVATE)
            val license = prefs.getString("license_data", null)

            if (license.isNullOrEmpty()) {
                Log.i(TAG, "No hay licencia almacenada")
                return Pair(false, "Licencia no encontrada")
            }

            try {
                com.google.gson.Gson().fromJson(license, Map::class.java)
                Log.i(TAG, "Licencia válida verificada")
                return Pair(true, "Licencia válida")
            } catch (_: Exception) {
                Log.w(TAG, "Licencia inválida - intentando limpieza...")
                prefs.edit().remove("license_data").apply()
                return Pair(false, "Licencia eliminada - se requiere una nueva")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validando datos de licencia", e)
            return Pair(false, "Error validando licencia: ${e.message}")
        }
    }

    private fun computeFileChecksum(file: File): String {
        try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val inputStream = FileInputStream(file)
            try {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                    md.update(buffer, 0, bytesRead)
                }
                return md.digest().joinToString("") { "%02x".format(it) }
            } finally {
                inputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando checksum", e)
            throw RuntimeException("Fallo calculando checksum", e)
        }
    }

    private fun regenerateDatabaseContent(context: Context, databasePath: File): Boolean {
        try {
            val backupFileName = "backup_${System.currentTimeMillis()}.db"
            val backupDir = File(context.filesDir, "db_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val backupFile = File(backupDir, backupFileName)
            if (databasePath.renameTo(backupFile)) {
                Log.i(TAG, "BD restaurada a: ${backupFile.absolutePath}")

                context.openOrCreateDatabase("drywall_db", Context.MODE_PRIVATE, null).close()
                Log.i(TAG, "Nueva BD creada en: ${databasePath.absolutePath}")

                return true
            } else {
                Log.w(TAG, "Renombrar archivo falló")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error regenerando contenido de BD", e)
            return false
        }
    }
}