package com.drywall.common.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorTracker {
    private const val LOG_DIR = "error_logs"
    private const val TAG = "ErrorTracker"

    /**
     * Inicializa el rastreador de errores global para capturar crashes inesperados.
     */
    fun init(context: Context, appName: String) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(context, throwable, appName)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Guarda el detalle del error en un archivo local.
     */
    fun logError(context: Context, module: String, message: String, exception: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = """
            --- ERROR EN MÓDULO: $module ---
            Fecha: $timestamp
            Mensaje: $message
            Stacktrace: ${exception?.let { getStackTrace(it) } ?: "N/D"}
            --------------------------------
            
        """.trimIndent()
        
        try {
            val logFile = File(getLogFolder(context), "runtime_errors.log")
            FileOutputStream(logFile, true).use { it.write(logEntry.toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo escribir el log manual", e)
        }
    }

    private fun saveCrashLog(context: Context, throwable: Throwable, appName: String) {
        val date = Date()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(date)
        val displayDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
        val fileName = "CRASH_$timestamp.log"
        
        val sanitizedMessage = throwable.localizedMessage
            ?.replace(Regex("[a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+"), "[EMAIL]")
            ?.replace(Regex("\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}"), "[CARD]")
            ?.replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "[TOKEN]")
            ?: "Error desconocido"

        val report = """
            === REPORTE DE FALLO CRITICO ===
            App: $appName
            Fecha: $displayDate
            Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})
            
            CAUSA DEL ERROR:
            $sanitizedMessage
            
            STACKTRACE COMPLETO:
            ${getStackTrace(throwable)}
            
            SISTEMA:
            RAM: ${getAvailableMemory(context)}
            ==============================================
        """.trimIndent()

        try {
            val logFile = File(getLogFolder(context), fileName)
            FileOutputStream(logFile).use { it.write(report.toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving crash log", e)
        }
    }

    fun hasLogs(context: Context): Boolean {
        val folder = getLogFolder(context)
        return (folder.listFiles()?.size ?: 0) > 0
    }

    fun getAllLogs(context: Context): String {
        val sb = StringBuilder()
        sb.append("--- COMPILACIÓN DE LOGS DE DIAGNÓSTICO ---\n\n")
        
        val folder = getLogFolder(context)
        folder.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
            sb.append("ARCHIVO: ${file.name}\n")
            sb.append(file.readText())
            sb.append("\n\n")
        }
        
        if (sb.length < 50) return ""
        return sb.toString()
    }

    fun clearLogs(context: Context) {
        getLogFolder(context).listFiles()?.forEach { it.delete() }
    }

    private fun getLogFolder(context: Context): File {
        val folder = File(context.filesDir, LOG_DIR)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    private fun getAvailableMemory(context: Context): String {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            "${memInfo.availMem / (1024 * 1024)} MB disponibles"
        } catch (e: Exception) { "N/D" }
    }
}
