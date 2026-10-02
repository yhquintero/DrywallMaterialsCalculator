package com.drywall.common.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.KeyStore

object SecureStorageUtils {
    private const val TAG = "SecureStorageUtils"

    @Suppress("DEPRECATION")
    fun getEncryptedPrefs(context: Context, fileName: String): SharedPreferences {
        val masterKeyAlias = MasterKey.DEFAULT_MASTER_KEY_ALIAS
        
        fun createPrefs(): SharedPreferences {
            val masterKey = MasterKey.Builder(context, masterKeyAlias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        return try {
            createPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "Fallo inicial en EncryptedSharedPreferences para $fileName: ${e.message}", e)
            
            try {
                Log.w(TAG, "Intentando recuperación Nivel 1: eliminando archivo $fileName")
                context.deleteSharedPreferences(fileName)
                createPrefs()
            } catch (e2: Exception) {
                Log.e(TAG, "Fallo Nivel 1. Intentando recuperación Nivel 2: Resetear MasterKey alias $masterKeyAlias", e2)
                try {
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)
                    keyStore.deleteEntry(masterKeyAlias)
                    
                    context.deleteSharedPreferences(fileName)
                    createPrefs()
                } catch (e3: Exception) {
                    Log.e(TAG, "FATAL: Error irrecuperable en almacenamiento seguro para $fileName", e3)
                    throw SecurityException("Almacenamiento seguro no disponible tras múltiples intentos de recuperación.", e3)
                }
            }
        }
    }
}
