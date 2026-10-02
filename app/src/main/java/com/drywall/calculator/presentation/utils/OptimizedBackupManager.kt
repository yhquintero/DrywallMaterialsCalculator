package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log
import java.io.File

object OptimizedBackupManager {
    private const val TAG = "OptimizedBackupManager"
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024

    fun createBackupPayload(context: Context, basePath: String): List<File> {
        val essentialFiles = mutableListOf<File>()
        
        essentialFiles.addAll(optimizeBackupCollection(
            filterMediaFiles(context),
            filterPreferenceFiles(context), 
            filterEssentialDataFiles(context)
        ))
        
        Log.i(TAG, "Optimized backup payload: ${essentialFiles.size} files selected")
        return essentialFiles
    }

    private fun optimizeBackupCollection(
        mediaFiles: List<File>,
        preferenceFiles: List<File>,
        essentialFiles: List<File>
    ): List<File> {
        val result = mutableListOf<File>()
        
        mediaFiles.filter { !it.name.startsWith(".", ignoreCase = true) }.forEach { file ->
            if (file.length() <= MAX_FILE_SIZE) {
                result.add(file)
            }
        }
        
        preferenceFiles.forEach { result.add(it) }
        result.addAll(essentialFiles)
        
        return result
    }

    private fun filterMediaFiles(context: Context): List<File> {
        val mediaFiles = mutableListOf<File>()
        
        val mediaDir = File(context.filesDir, "media")
        if (mediaDir.exists()) {
            addMediaFiles(mediaDir, mediaFiles)
        }
        
        val externalImagesDir = File(context.getExternalFilesDir(null), "Pictures")
        if (externalImagesDir.exists()) {
            addMediaFiles(externalImagesDir, mediaFiles)
        }
        
        return mediaFiles
    }

    private fun addMediaFiles(dir: File, fileList: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addMediaFiles(file, fileList)
            } else {
                val ext = file.extension.lowercase()
                if (ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")) {
                    fileList.add(file)
                }
            }
        }
    }

    private fun filterPreferenceFiles(context: Context): List<File> {
        val result = mutableListOf<File>()
        
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        sharedPrefsDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".xml")) {
                result.add(file)
            }
        }
        
        return result
    }

    private fun filterEssentialDataFiles(context: Context): List<File> {
        val essentialFiles = mutableListOf<File>()
        
        val essentialDir = File(context.applicationInfo.dataDir, "databases")
        essentialDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("keygen_") || file.name == "keygen_db") {
                essentialFiles.add(file)
            }
        }
        
        return essentialFiles
    }
}
