package com.drywall.keygen.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LicenseSaver {

    private const val TAG = "LicenseSaver"

    private fun getLicensesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Licencias_Generadas")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveLicense(context: Context, userName: String, deviceId: String, plan: String, price: String, licenseJson: String) {
        try {
            if (userName.isBlank() || deviceId.isBlank() || plan.isBlank() || price.isBlank() || licenseJson.isBlank()) {
                Log.e(TAG, "Error: Datos inválidos para guardar licencia")
                return
            }
            
            val dir = getLicensesDir(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeName = userName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "Licencia_${safeName}_${timestamp}.json")

            val jsonObject = JSONObject().apply {
                put("usuario", userName)
                put("dispositivo", deviceId)
                put("plan", plan)
                put("precio", price)
                put("fechaGeneracion", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
                put("codigoLicencia", JSONObject(licenseJson))
            }

            file.writeText(jsonObject.toString(2))
            Log.i(TAG, "Licencia guardada en: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando licencia: ${e.message}", e)
        }
    }

    fun getSavedLicensesCount(context: Context): Int {
        return try {
            getLicensesDir(context).listFiles()?.filter { it.extension == "json" }?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
