package com.drywall.calculator.presentation.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object EmergencyBackupManager {
    private const val TAG = "EmergencyBackupManager"

    fun createEmergencyBackup(context: Context): String {
        try {
            Log.i(TAG, "Creando copia de emergencia de seguridad")
            
            val backupResult = BackupRecoveryUtils.performDatabaseBackupRecovery(context, Uri.parse("emergency_backup.db"))
            Log.i(TAG, "Copia de emergencia creada: $backupResult")
            return backupResult
        } catch (e: Exception) {
            Log.e(TAG, "Error creando copia de emergencia", e)
            return "Error: ${e.message}"
        }
    }

    fun attemptFullRecovery(context: Context): String {
        try {
            Log.i(TAG, "Intentando recuperación completa")
            
            val recoveryResult = BackupRecoveryAssistant.performSafeBackup(context, Uri.parse("full_recovery.zip"), forceRecovery = true)
            Log.i(TAG, "Recuperación completa completada: $recoveryResult")
            return recoveryResult
        } catch (e: Exception) {
            Log.e(TAG, "Error en recuperación completa", e)
            return "Error en recuperación: ${e.message}"
        }
    }

    fun checkAndRecreateDatabase(context: Context): String {
        try {
            Log.i(TAG, "Verificando estado de la base de datos")
            
            val dbFile = context.getDatabasePath("drywall_db")
            Log.i(TAG, "Estado de la BD - Existe: ${dbFile.exists()}, Longitud: ${dbFile.length()}")
            
            if (!dbFile.exists() || dbFile.length() == 0L) {
                val recreatedPath = recreateDatabase(context)
                Log.i(TAG, "BD recreada: $recreatedPath")
                return "BD recreada en: $recreatedPath"
            }
            
            return "BD válida, no requiere recreación"
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando BD", e)
            return "Error: ${e.message}"
        }
    }

    private fun recreateDatabase(context: Context): String {
        try {
            val backupDir = File(context.filesDir, "db_backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            
            val backupFileName = "emergency_backup_${System.currentTimeMillis()}.db"
            val backupFile = File(backupDir, backupFileName)
            
            val dbFile = context.getDatabasePath("drywall_db")
            if (dbFile.exists()) {
                dbFile.copyTo(backupFile, overwrite = true)
                dbFile.delete()
                Log.i(TAG, "Archivo de base de datos archivado: ${backupFile.absolutePath}")
            }
            
            context.openOrCreateDatabase("drywall_db", Context.MODE_PRIVATE, null).close()
            val newPath = dbFile.absolutePath
            
            Log.i(TAG, "Nueva base de datos creada: $newPath")
            return newPath
        } catch (e: Exception) {
            Log.e(TAG, "Error recreando BD", e)
            throw RuntimeException("Fallo recreando BD: ${e.message}", e)
        }
    }
}
