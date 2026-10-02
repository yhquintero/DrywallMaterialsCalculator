package com.drywall.common.utils

import android.content.Context
import android.util.Log

@Suppress("DEPRECATION")
object DatabaseRecoveryUtils {
    private const val TAG = "DatabaseRecoveryUtils"

    fun forceReinitializeLicensingSecurity(context: Context) {
        try {
            val legacyPrefsName = "secure_licensing_prefs"
            val currentPrefsName = "secure_licensing_prefs_v2"
            
            val legacyPrefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
            val currentPrefs = context.getSharedPreferences(currentPrefsName, Context.MODE_PRIVATE)
            
            val legacyData = mutableMapOf<String, Any?>()
            for (key in legacyPrefs.all.keys) {
                when (val value = legacyPrefs.getString(key, null)) {
                    null -> legacyData[key] = null
                    else -> {
                        legacyData[key] = value
                    }
                }
            }
            
            Log.i(TAG, "Legacy security data found: ${legacyData.size} entries")
            
            currentPrefs.edit().clear().apply()
            
            Log.i(TAG, "Security preferences cleared - will be recreated on next app use")
        } catch (e: Exception) {
            Log.e(TAG, "Error during security recovery", e)
            throw RuntimeException("Failed to recover security system", e)
        }
    }

    fun forceReinitializeKeygenSecurity(context: Context) {
        try {
            val legacyPrefsName = "secure_keygen_prefs"
            val currentPrefsName = "secure_keygen_keys_v2"
            
            val legacyPrefs = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
            val currentPrefs = context.getSharedPreferences(currentPrefsName, Context.MODE_PRIVATE)
            
            val legacyData = legacyPrefs.all
            if (legacyData.isEmpty()) {
                Log.i(TAG, "No legacy keygen security data found")
                return
            }
            
            Log.i(TAG, "Legacy keygen data found: ${legacyData.size} entries")
            
            currentPrefs.edit().clear().apply()
            
            val newMasterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val newPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "temp_key_for_migration",
                newMasterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            Log.i(TAG, "New security infrastructure created")
        } catch (e: Exception) {
            Log.e(TAG, "Error during keygen security recovery", e)
            throw RuntimeException("Failed to recover keygen security system", e)
        }
    }

    fun attemptKeyReencryption(context: Context) {
        try {
            val prefs = context.getSharedPreferences("temp_key_for_migration", Context.MODE_PRIVATE)
            
            if (prefs.all.isEmpty()) {
                Log.i(TAG, "No temporary key found, generating new key...")
                generateNewKeyPair(context)
            } else {
                Log.i(TAG, "Temporary key exists, migrating data...")
                migrateDataToNewSecurity(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during key re-encryption", e)
            generateNewKeyPair(context)
        }
    }

    private fun generateNewKeyPair(context: Context) {
        try {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(4096, java.security.SecureRandom())
            val keyPair = keyPairGenerator.generateKeyPair()
            
            val tempPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "temp_key_for_migration",
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            Log.i(TAG, "Generated new RSA-4096 key pair for database encryption")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate new key pair", e)
        }
    }

    private fun migrateDataToNewSecurity(context: Context) {
        try {
            val tempPrefs = context.getSharedPreferences("temp_key_for_migration", Context.MODE_PRIVATE)
            val currentLicensingPrefs = context.getSharedPreferences("secure_licensing_prefs_v2", Context.MODE_PRIVATE)
            
            tempPrefs.all.forEach { (key, value) ->
                if (key != "temp_key") {
                    when (value) {
                        is String -> currentLicensingPrefs.edit().putString(key, value).apply()
                        is Boolean -> currentLicensingPrefs.edit().putBoolean(key, value).apply()
                        is Long -> currentLicensingPrefs.edit().putLong(key, value).apply()
                        is Float -> currentLicensingPrefs.edit().putFloat(key, value).apply()
                    }
                }
            }
            
            tempPrefs.edit().clear().apply()
            Log.i(TAG, "Data migration completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating data", e)
        }
    }
}