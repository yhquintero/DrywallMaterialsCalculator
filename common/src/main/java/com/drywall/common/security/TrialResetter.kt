package com.drywall.common.security

import android.content.Context
import android.util.Log

/**
 * Utility to perform deep reset of trial state and license management.
 * Used by the Cleaner app to manage the main app's licensing state.
 */
object TrialResetter {
    private const val TAG = "TrialResetter"
    
    // Preferences names from LicensingManager
    private const val TRIAL_VAULT_NAME = "trial_license_vault_v2"
    private const val CONSUMED_VAULT_NAME = "permanent_license_vault_v3"
    private const val MAIN_PREFS_NAME = "secure_licensing_prefs_v3"

    // Trial keys (must match LicensingManager exactly)
    private const val KEY_TRIAL_USED = "trial_used_permanently"
    private const val KEY_TRIAL_ACTIVE = "trial_currently_active"
    private const val KEY_TRIAL_START = "trial_start_time"
    private const val KEY_TRIAL_LAST_VALIDATION = "trial_last_validation"
    private const val KEY_TRIAL_CONSUMED_RECORD = "trial_consumed_record"
    private const val KEY_LICENSE = "license_data"
    private const val KEY_PROFESSIONAL_LICENSE_ACTIVATED = "professional_license_ever_activated"

    fun deepReset(context: Context): Boolean {
        return try {
            Log.i(TAG, "Starting deep reset of trial state...")
            
            val markerRemoved = DeviceTrialMarker.removeTrialMarker(context)
            Log.i(TAG, "Device marker removed: $markerRemoved")
            
            val trialVault = SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
            val consumedVault = SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)
            val mainPrefs = SecureStorageUtils.getEncryptedPrefs(context, MAIN_PREFS_NAME)
            
            trialVault.edit().clear().commit()
            consumedVault.edit().clear().commit()
            mainPrefs.edit().clear().commit()
            
            Log.i(TAG, "Preferences cleared")
            
            context.deleteSharedPreferences(TRIAL_VAULT_NAME)
            context.deleteSharedPreferences(CONSUMED_VAULT_NAME)
            context.deleteSharedPreferences(MAIN_PREFS_NAME)
            
            Log.i(TAG, "Trial reset completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during deep reset: ${e.message}")
            false
        }
    }

    /**
     * Reset trial completely and activate a fresh 7-day trial.
     * Clears all old trial data, removes DeviceMarker, then sets up a new trial.
     */
    fun resetAndActivateTrial(context: Context): Boolean {
        return try {
            Log.i(TAG, "Resetting and activating fresh trial...")

            // 1. Remove DeviceMarker
            DeviceTrialMarker.removeTrialMarker(context)

            // 2. Clear all old trial data
            val trialVault = SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
            val consumedVault = SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)
            val mainPrefs = SecureStorageUtils.getEncryptedPrefs(context, MAIN_PREFS_NAME)

            trialVault.edit().clear().commit()
            consumedVault.edit().clear().commit()
            mainPrefs.edit().apply {
                remove(KEY_LICENSE)
                remove(KEY_PROFESSIONAL_LICENSE_ACTIVATED)
            }.commit()

            // 3. Delete and recreate to clear EncryptedSharedPreferences
            context.deleteSharedPreferences(TRIAL_VAULT_NAME)
            context.deleteSharedPreferences(CONSUMED_VAULT_NAME)
            context.deleteSharedPreferences(MAIN_PREFS_NAME)

            // 4. Activate fresh trial with current time
            val now = System.currentTimeMillis()
            val freshTrialVault = SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
            val freshConsumedVault = SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)

            freshTrialVault.edit()
                .putBoolean(KEY_TRIAL_USED, true)
                .putBoolean(KEY_TRIAL_ACTIVE, true)
                .putLong(KEY_TRIAL_START, now)
                .putLong(KEY_TRIAL_LAST_VALIDATION, now)
                .commit()

            freshConsumedVault.edit()
                .putBoolean(KEY_TRIAL_USED, true)
                .putBoolean(KEY_TRIAL_ACTIVE, true)
                .putLong(KEY_TRIAL_START, now)
                .putLong(KEY_TRIAL_LAST_VALIDATION, now)
                .commit()

            // 5. Store DeviceMarker for the new trial
            DeviceTrialMarker.storeTrialMarker(context, now)

            Log.i(TAG, "Fresh trial activated successfully. 7 days from now.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting and activating trial: ${e.message}")
            false
        }
    }

    /**
     * Remove the professional license from the main app.
     * After this, the app will show as having no license.
     */
    fun removeProfessionalLicense(context: Context): Boolean {
        return try {
            Log.i(TAG, "Removing professional license...")

            val mainPrefs = SecureStorageUtils.getEncryptedPrefs(context, MAIN_PREFS_NAME)
            mainPrefs.edit().apply {
                remove(KEY_LICENSE)
                remove(KEY_PROFESSIONAL_LICENSE_ACTIVATED)
            }.commit()

            // Also try to delete the encrypted prefs file
            try {
                context.deleteSharedPreferences(MAIN_PREFS_NAME)
            } catch (_: Exception) { }

            Log.i(TAG, "Professional license removed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing professional license: ${e.message}")
            false
        }
    }

    /**
     * Remove professional license AND activate a fresh trial.
     * User goes from licensed -> trial mode with 7 new days.
     */
    fun removeLicenseAndActivateTrial(context: Context): Boolean {
        return try {
            Log.i(TAG, "Removing license and activating fresh trial...")

            // 1. Remove license
            removeProfessionalLicense(context)

            // 2. Remove DeviceMarker
            DeviceTrialMarker.removeTrialMarker(context)

            // 3. Clear old trial state
            val trialVault = SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
            val consumedVault = SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)

            trialVault.edit().clear().commit()
            consumedVault.edit().clear().commit()

            context.deleteSharedPreferences(TRIAL_VAULT_NAME)
            context.deleteSharedPreferences(CONSUMED_VAULT_NAME)
            context.deleteSharedPreferences(MAIN_PREFS_NAME)

            // 4. Activate fresh trial
            val now = System.currentTimeMillis()
            val freshTrialVault = SecureStorageUtils.getEncryptedPrefs(context, TRIAL_VAULT_NAME)
            val freshConsumedVault = SecureStorageUtils.getEncryptedPrefs(context, CONSUMED_VAULT_NAME)

            freshTrialVault.edit()
                .putBoolean(KEY_TRIAL_USED, true)
                .putBoolean(KEY_TRIAL_ACTIVE, true)
                .putLong(KEY_TRIAL_START, now)
                .putLong(KEY_TRIAL_LAST_VALIDATION, now)
                .commit()

            freshConsumedVault.edit()
                .putBoolean(KEY_TRIAL_USED, true)
                .putBoolean(KEY_TRIAL_ACTIVE, true)
                .putLong(KEY_TRIAL_START, now)
                .putLong(KEY_TRIAL_LAST_VALIDATION, now)
                .commit()

            DeviceTrialMarker.storeTrialMarker(context, now)

            Log.i(TAG, "License removed and fresh trial activated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error removing license and activating trial: ${e.message}")
            false
        }
    }
}
