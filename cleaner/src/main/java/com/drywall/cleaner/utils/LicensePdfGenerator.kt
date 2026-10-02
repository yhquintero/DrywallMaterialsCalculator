package com.drywall.cleaner.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LicensePdfGenerator {
    private const val TAG = "LicensePdfGenerator"

    fun generateActionReport(context: Context, actionName: String, result: Boolean): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Background
        canvas.drawColor(Color.WHITE)

        // Title
        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Reporte de Auditoría de Licencia", 50f, 50f, paint)

        // Details
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date())

        var y = 100f
        canvas.drawText("Fecha: $dateStr", 50f, y, paint); y += 30f
        canvas.drawText("Acción Realizada: $actionName", 50f, y, paint); y += 30f
        
        paint.color = if (result) Color.parseColor("#2E7D32") else Color.RED
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Resultado: ${if (result) "EXITOSO" else "FALLIDO"}", 50f, y, paint)
        
        paint.color = Color.DKGRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        y += 50f
        canvas.drawText("Este reporte fue generado por el Administrador de Seguridad (Cleaner).", 50f, y, paint)

        pdfDocument.finishPage(page)

        val fileName = "AUDIT_${actionName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        return try {
            file.outputStream().use { 
                pdfDocument.writeTo(it)
            }
            pdfDocument.close()
            Log.i(TAG, "Reporte PDF generado: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error generando PDF", e)
            pdfDocument.close()
            null
        }
    }
}
