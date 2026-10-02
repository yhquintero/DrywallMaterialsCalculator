package com.drywall.calculator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.*

object ImageUtils {
    private const val TAG = "ImageUtils"
    /**
     * Converts a stored path (relative or legacy absolute) to an absolute path.
     * Legacy absolute paths like "/data/.../files/clientes/ni/img.jpg" have the
     * subdirectory structure preserved (not just the filename).
     */
    private fun sanitizePath(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._\\-/]"), "_")
            .replace("..", "_")
            .take(255)
    }

    fun resolveUri(context: Context, storedPath: String?): String? {
        if (storedPath == null) return null
        
        // Extraer el path real sin parámetros de consulta (cache busting)
        val realPath = if (storedPath.contains("?")) storedPath.substringBefore("?") else storedPath
        
        val sanitized = sanitizePath(realPath)
        val filesDir = context.filesDir.absolutePath
        val filesMarker = "/files/"
        return if (sanitized.startsWith("/")) {
            val idx = sanitized.indexOf(filesMarker)
            if (idx == -1) sanitized
            else "$filesDir/${sanitized.substring(idx + filesMarker.length)}"
        } else {
            "$filesDir/$sanitized"
        }
    }

    fun cropAndSaveImage(context: Context, sourceBitmap: Bitmap, fileNamePrefix: String): String? {
        val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        
        // Optimización: Redimensionar si es muy grande antes de recortar para ahorrar memoria
        val maxDimension = 1200
        val scale = if (sourceBitmap.width > maxDimension || sourceBitmap.height > maxDimension) {
            maxDimension.toFloat() / maxOf(sourceBitmap.width, sourceBitmap.height)
        } else 1.0f
        
        val workingBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(sourceBitmap, (sourceBitmap.width * scale).toInt(), (sourceBitmap.height * scale).toInt(), true)
        } else sourceBitmap

        val width = workingBitmap.width
        val height = workingBitmap.height
        val cropWidth = (width * 0.85).toInt()
        val cropHeight = (height * 0.85).toInt()
        val startX = (width - cropWidth) / 2
        val startY = (height - cropHeight) / 2
        
        val croppedBitmap = Bitmap.createBitmap(workingBitmap, startX, startY, cropWidth, cropHeight)
        
        return try {
            FileOutputStream(file).use { out ->
                // Calidad 75 es suficiente para documentos y ahorra mucho espacio
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            if (workingBitmap != sourceBitmap) workingBitmap.recycle()
            if (croppedBitmap != workingBitmap) croppedBitmap.recycle()
            fileName
        } catch (e: Exception) {
            null
        }
    }

    fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Optimización: Cargar versión reducida si es gigante
            val maxDim = 1600
            var sampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / sampleSize >= maxDim && halfWidth / sampleSize >= maxDim) {
                    sampleSize *= 2
                }
            }
            
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            
            // Reabrir stream
            val imageStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(imageStream, null, options)
            imageStream.close()

            if (bitmap == null) return null

            // Corregir rotación EXIF automáticamente
            val rotation = getExifRotation(context, uri)
            if (rotation != 0) {
                val rotated = rotateBitmap(bitmap, rotation.toFloat())
                bitmap.recycle()
                return rotated
            }
            
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error convertiendo URI a Bitmap", e)
            null
        }
    }

    fun getExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): String? {
        val file = File(context.filesDir, fileName)
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    fun copyUriToPath(context: Context, uri: Uri, fileNamePrefix: String): String? {
        val fileName = "${fileNamePrefix}_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        return try {
            val bitmap = uriToBitmap(context, uri) ?: return null
            FileOutputStream(file).use { out ->
                // Siempre comprimir para asegurar tamaño razonable
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()
            fileName
        } catch (e: Exception) {
            null
        }
    }
}
