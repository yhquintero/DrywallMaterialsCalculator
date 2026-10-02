package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

object OptimizedDatabaseBackup {
    private const val TAG = "OptimizedDatabaseBackup"

    fun backupDatabaseOnly(context: Context, destination: File, format: String): String {
        try {
            val dbFile = prepareDatabaseForBackup(context)
            val manifest = createOptimizedManifest(format, dbFile.length())
            createOptimizedZip(dbFile, destination, listOf(dbFile), manifest, 9)
            return "Éxito: ${destination.name}"
        } catch (e: Exception) {
            Log.e(TAG, "Error creating optimized backup", e)
            throw RuntimeException("Fallo creando backup: ${e.message}", e)
        }
    }

    private fun prepareDatabaseForBackup(context: Context): File {
        val dbFile = context.getDatabasePath("drywall_db")
        if (!dbFile.exists()) {
            context.openOrCreateDatabase("drywall_db", Context.MODE_PRIVATE, null).close()
        }

        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        walFile.delete()
        shmFile.delete()

        return dbFile
    }

private fun createOptimizedManifest(format: String, dbSize: Long): String {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val manifest = JSONObject()
        manifest.put("app", "com.drywall.calculator")
        manifest.put("version", 1)
        manifest.put("created", date)
        manifest.put("db", "drywall_db")
        manifest.put("totalFiles", 1)
        manifest.put("dbFormat", format)
        manifest.put("dbSize", dbSize)

        return manifest.toString(2)
    }

    private fun createOptimizedZip(
        dbFile: File,
        destinationFile: File,
        allFiles: List<File>,
        manifest: String,
        compressionLevel: Int
    ) {
        FileOutputStream(destinationFile).use { fos ->
            ZipOutputStream(fos).use { zipOut ->
                zipOut.setLevel(compressionLevel)

                val manifestEntry = ZipEntry("manifest.json")
                manifestEntry.time = System.currentTimeMillis()
                zipOut.putNextEntry(manifestEntry)
                zipOut.write(manifest.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry("database/drywall_db"))
                dbFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()

                val hashEntry = ZipEntry("backup.sha256")
                hashEntry.time = System.currentTimeMillis()
                zipOut.putNextEntry(hashEntry)
                zipOut.write(computeOptimizedSha256(allFiles).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                Log.i(TAG, "Archivo ZIP optimizado creado: ${destinationFile.absolutePath}")
            }
        }
    }

    private fun computeOptimizedSha256(files: List<File>): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        files.forEach { file ->
            if (file.exists()) {
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fis.read(buffer).also { read = it } > 0) {
                        md.update(buffer, 0, read)
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
