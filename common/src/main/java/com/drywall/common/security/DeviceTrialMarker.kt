package com.drywall.common.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object DeviceTrialMarker {
    private const val TAG = "DeviceTrialMarker"
    private const val MARKER_DIR = "CalculadoraDrywall"
    private const val MARKER_PREFIX = "trial_"
    private const val MARKER_EXTENSION = ".dat"

    fun getDeviceFingerprint(context: Context): String {
        val components = mutableListOf<String>()
        try {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
            components.add("aid:$androidId")
        } catch (_: Exception) { }
        components.add("board:${Build.BOARD}")
        components.add("bootloader:${Build.BOOTLOADER}")
        components.add("device:${Build.DEVICE}")
        components.add("hardware:${Build.HARDWARE}")
        components.add("manufacturer:${Build.MANUFACTURER}")
        components.add("model:${Build.MODEL}")
        components.add("product:${Build.PRODUCT}")
        components.add("display:${Build.DISPLAY}")
        components.add("fingerprint:${Build.FINGERPRINT}")
        try {
            val serial = Build::class.java.getField("SERIAL").get(null) as? String ?: ""
            if (serial.isNotEmpty() && serial != "unknown") {
                components.add("serial:$serial")
            }
        } catch (_: Exception) { }
        val combined = components.joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getMarkerFileName(fingerprint: String): String {
        return "$MARKER_PREFIX$fingerprint$MARKER_EXTENSION"
    }

    fun hasTrialMarker(context: Context): Boolean {
        return try {
            val fingerprint = getDeviceFingerprint(context)
            val markerName = getMarkerFileName(fingerprint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(markerName, "%$MARKER_DIR%")
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val markerFile = java.io.File(downloadsDir, "$MARKER_DIR/$markerName")
                markerFile.exists()
            }
        } catch (e: Exception) { false }
    }

    fun storeTrialMarker(context: Context, trialStartTime: Long): Boolean {
        return try {
            val fingerprint = getDeviceFingerprint(context)
            val markerName = getMarkerFileName(fingerprint)
            val rawContent = buildString {
                append("f="); append(fingerprint.take(16))
                append("|t="); append(trialStartTime.toString())
                append("|a="); append(System.currentTimeMillis().toString())
                append("|m="); append(Build.MANUFACTURER.take(8))
                append("|v="); append(Build.VERSION.RELEASE.take(4))
            }
            val content = obfuscateContent(rawContent, fingerprint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, markerName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$MARKER_DIR")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    val updateValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    context.contentResolver.update(uri, updateValues, null, null)
                    true
                } else false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val markerDir = java.io.File(downloadsDir, MARKER_DIR)
                if (!markerDir.exists()) markerDir.mkdirs()
                val markerFile = java.io.File(markerDir, markerName)
                markerFile.writeText(content)
                true
            }
        } catch (e: Exception) { false }
    }

    fun removeTrialMarker(context: Context): Boolean {
        return try {
            val fingerprint = getDeviceFingerprint(context)
            val markerName = getMarkerFileName(fingerprint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(markerName, "%$MARKER_DIR%")
                val deleted = context.contentResolver.delete(collection, selection, selectionArgs)
                deleted > 0
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val markerFile = java.io.File(downloadsDir, "$MARKER_DIR/$markerName")
                markerFile.delete()
            }
        } catch (e: Exception) { false }
    }

    fun getTrialStartTimeFromMarker(context: Context): Long {
        return try {
            val fingerprint = getDeviceFingerprint(context)
            val markerName = getMarkerFileName(fingerprint)
            val content = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(markerName, "%$MARKER_DIR%")
                var fileUri: Uri? = null
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        fileUri = Uri.withAppendedPath(collection, id.toString())
                    }
                }
                fileUri?.let { uri -> context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val markerFile = java.io.File(downloadsDir, "$MARKER_DIR/$markerName")
                if (markerFile.exists()) markerFile.readText() else null
            }
            if (content != null) {
                val deobfuscated = deobfuscateContent(content, fingerprint)
                deobfuscated?.split("|")?.find { it.startsWith("t=") }?.substringAfter("t=")?.toLongOrNull() ?: 0L
            } else 0L
        } catch (_: Exception) { 0L }
    }

    private fun obfuscateContent(content: String, fingerprint: String): String {
        val key = fingerprint.toByteArray(Charsets.UTF_8)
        val data = content.toByteArray(Charsets.UTF_8)
        val result = ByteArray(data.size)
        for (i in data.indices) { result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun deobfuscateContent(obfuscated: String, fingerprint: String): String? {
        return try {
            val key = fingerprint.toByteArray(Charsets.UTF_8)
            val data = Base64.decode(obfuscated, Base64.DEFAULT)
            val result = ByteArray(data.size)
            for (i in data.indices) { result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
            String(result, Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
}
