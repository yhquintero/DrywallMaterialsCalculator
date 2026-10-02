package com.drywall.calculator

import android.app.Application
import android.util.Log
import com.drywall.common.utils.ErrorTracker
import com.drywall.calculator.utils.security.LicensingManager
import com.drywall.calculator.presentation.utils.BackupRecoveryUtils
import com.drywall.calculator.presentation.utils.DatabaseKeyRecovery
import com.drywall.calculator.presentation.utils.AppManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class DrywallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorTracker.init(this, "Calculadora Drywall")
        AppManager.initialize(this)
        
        // Ejecutar inicializaciones pesadas en segundo plano para evitar ANR al inicio
        GlobalScope.launch(Dispatchers.IO) {
            try {
                LicensingManager.init(this@DrywallApplication)
                // Warm up database to move SQLCipher heavy init to background
                com.drywall.calculator.data.local.AppDatabase.getInstance(this@DrywallApplication)
                BackupRecoveryUtils.performSecureBackupRecovery(this@DrywallApplication, "")
                DatabaseKeyRecovery.forceKeyRecovery(this@DrywallApplication, "secure_licensing_prefs_v2")
                DatabaseKeyRecovery.forceKeyRecovery(this@DrywallApplication, "secure_keygen_keys_v2")
            } catch (e: Exception) {
                Log.e("DrywallApp", "Error en inicialización background", e)
            }
        }
    }
}
