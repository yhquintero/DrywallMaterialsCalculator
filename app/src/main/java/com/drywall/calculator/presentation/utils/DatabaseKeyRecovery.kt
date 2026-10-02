package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.drywall.calculator.presentation.utils.AppManager

object DatabaseKeyRecovery {
    private const val TAG = "DatabaseKeyRecovery"

    fun forceKeyRecovery(context: Context, targetPrefName: String): Boolean {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val testPrefs = EncryptedSharedPreferences.create(
                context,
                targetPrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            Log.i(TAG, "Key recovery exitoso para: $targetPrefName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Key recovery falló para: $targetPrefName", e)
            tryReinitKeyInfrastructure(context, targetPrefName)
            return false
        }
    }

    private fun tryReinitKeyInfrastructure(context: Context, targetPrefName: String): Boolean {
        try {
            val currentPrefs = context.getSharedPreferences(targetPrefName, Context.MODE_PRIVATE)
            currentPrefs.edit().clear().apply()

            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val reinitPrefs = EncryptedSharedPreferences.create(
                context,
                targetPrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            reinitPrefs.edit().apply()
            Log.i(TAG, "Nueva infraestructura de clave creada para: $targetPrefName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Fallo la recreación de infraestructura", e)
            return false
        }
    }
}