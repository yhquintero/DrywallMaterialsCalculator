package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log
import com.drywall.calculator.presentation.utils.AppManager
import com.drywall.calculator.presentation.utils.BackupRecoveryUtils
import com.drywall.calculator.utils.security.LicensingManager

object BackupRecoveryAssistant {
    private const val TAG = "BackupRecoveryAssistant"
    private const val SECURE_DB_PASSPHRASE = "secure_db_passphrase"

    fun performSafeBackup(
        context: Context,
        destinationUri: android.net.Uri,
        forceRecovery: Boolean = false
    ): String {
        try {
            Log.i(TAG, "Iniciando backup seguro - Forzar recuperación: $forceRecovery")
            
            if (forceRecovery) {
                Log.i(TAG, "Limpiando datos de seguridad para recuperación forzada")
                cleanupSecurityData(context)
            }

            val backupPath = BackupRecoveryUtils.performDatabaseBackupRecovery(context, destinationUri)
            Log.i(TAG, "Backup seguro completado: $backupPath")
            return backupPath
        } catch (e: Exception) {
            Log.e(TAG, "Error en backup seguro", e)
            val contextRef = AppManager.getAppContext()
            val manualPath = BackupRecoveryUtils.performDatabaseBackupRecovery(contextRef, destinationUri)
            return "Backup con recuperación manual completado: $manualPath"
        }
    }

    private fun cleanupSecurityData(context: Context) {
        try {
            val legacyPrefs = context.getSharedPreferences("secure_licensing_prefs", Context.MODE_PRIVATE)
            val currentPrefs = context.getSharedPreferences("secure_licensing_prefs_v2", Context.MODE_PRIVATE)
            
            legacyPrefs.edit().clear().apply()
            currentPrefs.edit().clear().apply()
            
            Log.i(TAG, "Datos de seguridad limpiados")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando seguridad", e)
        }
    }
}