package com.drywall.calculator.utils.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object TrialAdminManager {
    fun getDeviceAdminComponent(context: Context): ComponentName {
        return ComponentName(context, TrialDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val adminComponent = getDeviceAdminComponent(context)
            val pm = context.packageManager
            val componentEnabled = pm.getComponentEnabledSetting(adminComponent)
            componentEnabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
            componentEnabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } catch (_: Exception) { false }
    }

    fun requestDeviceAdminActivation(context: Context) {
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getDeviceAdminComponent(context))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "La protección de administrador de dispositivo previene la desinstalación no autorizada de la aplicación.")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
