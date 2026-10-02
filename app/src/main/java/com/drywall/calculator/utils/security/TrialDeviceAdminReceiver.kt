package com.drywall.calculator.utils.security

import com.drywall.common.security.TrialProtectionManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TrialDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "TrialDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin ACTIVADO - Protección de desinstalación habilitada")
        TrialProtectionManager.refreshStorageIntegrity(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin DESACTIVADO - Verificando período de prueba...")
        TrialProtectionManager.onDeviceAdminDeactivated(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val isActive = try {
            val vault = com.drywall.common.security.SecureStorageUtils.getEncryptedPrefs(context, "trial_license_vault_v1")
            vault?.getBoolean("trial_currently_active", false) ?: false
        } catch (_: Exception) { false }

        val hasTrialBeenUsed = try {
            val vault = com.drywall.common.security.SecureStorageUtils.getEncryptedPrefs(context, "trial_license_vault_v1")
            vault?.getBoolean("trial_used_permanently", false) ?: false
        } catch (_: Exception) { false }

        val hasLicense = try {
            LicensingManager.isLicenseValid(context)
        } catch (_: Exception) { false }

        return if (isActive || (hasTrialBeenUsed && !hasLicense)) {
            "ADVERTENCIA: Desactivar el administrador del dispositivo bloqueará permanentemente la aplicación durante el período de prueba. Esta acción NO se puede deshacer."
        } else {
            "Si desactiva el administrador, la protección de la aplicación se reducirá."
        }
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.i(TAG, "Contraseña del dispositivo cambiada")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completado - verificando integridad")
            TrialProtectionManager.onBootCompleted(context)
        }
    }
}
