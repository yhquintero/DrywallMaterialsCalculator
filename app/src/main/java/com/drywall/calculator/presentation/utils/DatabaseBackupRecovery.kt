package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log
import java.io.File

object DatabaseBackupRecovery {
    private const val TAG = "DatabaseBackupRecovery"

    fun performDatabaseBackupRecovery(
        context: Context,
        destination: String
    ): String {
        try {
            Log.i(TAG, "Iniciando recuperación de respaldo de base de datos (optimizada)")
            
            val destinationFile = File(context.filesDir, destination)
            OptimizedDatabaseBackup.backupDatabaseOnly(context, destinationFile, "plain")
            Log.i(TAG, "Proceso de recuperación optimizado completado con éxito")
            return "Éxito: Respaldo creado en ${destinationFile.absolutePath}"
        } catch (e: Exception) {
            Log.e(TAG, "Error en recuperación de respaldo optimizada", e)
            return "Error: ${e.message}"
        }
    }
}
