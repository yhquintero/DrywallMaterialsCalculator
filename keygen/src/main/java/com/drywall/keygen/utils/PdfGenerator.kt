package com.drywall.keygen.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.drywall.keygen.data.IssuedLicense
import com.drywall.keygen.data.RateHistory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
        A4("A4", 595, 842),
        CARTA("Carta", 612, 792),
        OFICIO("Oficio", 612, 1008),
        LEGAL("Legal", 612, 1008)
    }

    fun generateEvolutionOnlyPdf(context: Context, rateHistory: List<RateHistory>, pageSize: PageSize = PageSize.A4) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 60f

        val margin = 50f
        val contentWidth = pageSize.widthPt - 2 * margin

        paint.textSize = 24f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(33, 150, 243)
        canvas.drawText("REPORTE DE EVOLUCIÓN DE TASAS", margin, y, paint)
        y += 40f

        paint.textSize = 14f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.BLACK
        canvas.drawText("HISTORIAL DE CAMBIOS (CUP)", margin, y, paint)
        y += 25f
        
        // Header
        paint.textSize = 10f; paint.isFakeBoldText = true
        canvas.drawText("No. Id.", margin, y, paint)
        canvas.drawText("Moneda", margin + 50f, y, paint)
        canvas.drawText("Tasa (CUP)", margin + 150f, y, paint)
        canvas.drawText("Fecha y Hora", margin + 300f, y, paint)
        y += 15f
        paint.isFakeBoldText = false
        canvas.drawLine(margin, y-5f, pageSize.widthPt - margin, y-5f, paint)

        rateHistory.reversed().forEachIndexed { index, hist ->
            if (y > pageSize.heightPt - margin) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
                
                // Header on new page
                paint.textSize = 10f; paint.isFakeBoldText = true
                canvas.drawText("No. Id.", margin, y, paint)
                canvas.drawText("Moneda", margin + 50f, y, paint)
                canvas.drawText("Tasa (CUP)", margin + 150f, y, paint)
                canvas.drawText("Fecha y Hora", margin + 300f, y, paint)
                y += 15f
                paint.isFakeBoldText = false
                canvas.drawLine(margin, y-5f, pageSize.widthPt - margin, y-5f, paint)
            }
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(hist.timestamp))
            canvas.drawText((index + 1).toString(), margin, y, paint)
            canvas.drawText(hist.currencyCode, margin + 50f, y, paint)
            canvas.drawText(String.format(Locale.US, "%.2f", hist.rate), margin + 150f, y, paint)
            canvas.drawText(dateStr, margin + 300f, y, paint)
            y += 15f
        }

        pdfDocument.finishPage(page)
        saveAndSharePdf(context, pdfDocument, "Evolucion_Tasas_Drywall.pdf")
    }

    fun generateInvoicePdf(context: Context, user: String, deviceId: String, plan: String, price: String, pageSize: PageSize = PageSize.A4) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // Company header
        paint.textSize = 32f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(33, 150, 243)
        canvas.drawText("YHQuintero Soluciones Profesionales", 50f, 60f, paint)
        paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.GRAY
        canvas.drawText("Generador de Licencias Profesionales", 50f, 90f, paint)
        
        // Invoice title
        paint.textSize = 24f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(33, 150, 243)
        canvas.drawText("FACTURA DE ACTIVACIÓN", 50f, 130f, paint)
        paint.textSize = 12f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.BLACK
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Fecha: $dateStr", 50f, 160f, paint)
        canvas.drawText("Factura #: DF-" + System.currentTimeMillis(), 50f, 180f, paint)

        // Customer details section
        paint.textSize = 16f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(33, 150, 243)
        canvas.drawText("INFORMACIÓN DEL CLIENTE", 50f, 220f, paint)
        paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.BLACK
        canvas.drawText("Cliente: $user", 50f, 250f, paint)
        canvas.drawText("ID Dispositivo: $deviceId", 50f, 270f, paint)
        canvas.drawText("Plan Solicitado: $plan", 50f, 290f, paint)

        // Payment details section
        paint.textSize = 16f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(33, 150, 243)
        canvas.drawText("DETALLES DEL PAGO", 50f, 340f, paint)
        paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.BLACK
        
        // Calculate expiration date based on plan
        val days = when (plan) {
            "1 Día Profesional" -> 1
            "1 Semana Profesional" -> 7
            "1 Mes Profesional" -> 30
            "1 Año Profesional" -> 365
            "2 Años Profesional" -> 730
            else -> 30
        }
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, days)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        val expiryDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(calendar.time)
        
        canvas.drawText("Monto: $price", 50f, 370f, paint)
        canvas.drawText("Expiración: $expiryDate", 50f, 390f, paint)
        canvas.drawText("Estado: Pendiente de Pago", 50f, 410f, paint)

        // Important information
        paint.textSize = 12f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.GRAY
        canvas.drawText("===========================================", 50f, 460f, paint)
        canvas.drawText("INFORMACIÓN IMPORTANTE:", 50f, 480f, paint)
        canvas.drawText("• Una vez confirmado el pago, se le enviará su", 50f, 500f, paint)
        canvas.drawText("  código de activación profesional por WhatsApp.", 50f, 515f, paint)
        canvas.drawText("• El código incluye su firma digital y", 50f, 530f, paint)
        canvas.drawText("  fecha de expiración.", 50f, 545f, paint)
        canvas.drawText("• Este documento sirve como comprobante de", 50f, 560f, paint)
        canvas.drawText("  su solicitud de licencia.", 50f, 575f, paint)
        canvas.drawText("===========================================", 50f, 590f, paint)

        // Footer
        paint.textSize = 10f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.GRAY
        canvas.drawText("YHQuintero - Soluciones Profesionales", 50f, 650f, paint)
        canvas.drawText("Yosvany Hernández Quintero | yhquintero@gmail.com", 50f, 670f, paint)
        canvas.drawText("Teléfono: +53 5 216 0801", 50f, 690f, paint)

        pdfDocument.finishPage(page)
        
        val extraText = "Hola $user, adjunto su factura de activación profesional para Drywall Pro.\n\nIncluye:\n• Código de activación con firma digital\n• Fecha de expiración: $expiryDate\n• Total a pagar: $price\n\nPor favor, confirme el pago para recibir su licencia."
        saveAndSharePdf(context, pdfDocument, "Factura_${user.replace(" ", "_")}.pdf", extraText)
    }

    fun generateFullHistoryPdf(context: Context, licenses: List<IssuedLicense>, pageSize: PageSize = PageSize.A4) {
        val pdfDocument = PdfDocument()
        val pageWidth = pageSize.widthPt
        val pageHeight = pageSize.heightPt
        val margin = 30f
        
        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(33, 150, 243)
            textAlign = Paint.Align.CENTER
        }
        val tableHeaderPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.WHITE
        }
        val contentPaint = Paint().apply {
            textSize = 9f
            color = android.graphics.Color.BLACK
        }
        val footerPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }

        // Agrupar por moneda (ej: de "50.00 USD" extrae "USD")
        val groupedLicenses = licenses.groupBy { 
            it.price.split(" ").getOrNull(1) ?: "CUP"
        }

        var globalPageCount = 0

        groupedLicenses.forEach { (currency, currencyLicenses) ->
            var idCounter = 1
            var currencyTotal = 0.0
            var currentItemIndex = 0
            
            while (currentItemIndex < currencyLicenses.size) {
                globalPageCount++
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, globalPageCount).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                var y = 50f
                var pageSubtotal = 0.0

                // Título de la sección por moneda
                canvas.drawText("CONTROL DE LICENCIAS - $currency", pageWidth / 2f, y, titlePaint)
                y += 25f
                
                contentPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("Generado el: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}", pageWidth / 2f, y, contentPaint)
                contentPaint.textAlign = Paint.Align.LEFT
                y += 35f

                // Encabezado de la tabla con fondo azul profesional
                paint.color = android.graphics.Color.rgb(33, 150, 243)
                canvas.drawRect(margin, y - 15f, pageWidth - margin, y + 12f, paint)
                
                // Definición de posiciones de columnas (Ajustadas para más espacio y mejores alineaciones)
                val c1 = margin + 5f      // No. Id.
                val c2 = margin + 45f     // Estado
                val c3 = margin + 105f    // Usuario
                val c4 = margin + 225f    // F. Creación
                val c5 = margin + 315f    // F. Expiración
                val c6 = margin + 405f    // ID Dispositivo
                val c7 = margin + 495f    // Monto
                
                canvas.drawText("No.", c1, y, tableHeaderPaint)
                canvas.drawText("Estado", c2, y, tableHeaderPaint)
                canvas.drawText("Usuario", c3, y, tableHeaderPaint)
                canvas.drawText("Creación", c4, y, tableHeaderPaint)
                canvas.drawText("Expiración", c5, y, tableHeaderPaint)
                canvas.drawText("Dispositivo", c6, y, tableHeaderPaint)
                canvas.drawText("Monto", c7, y, tableHeaderPaint)
                
                y += 25f

                val bottomLimit = pageHeight - 100f // Reserva para SubTotal, Total y Paginación

                while (currentItemIndex < currencyLicenses.size) {
                    val lic = currencyLicenses[currentItemIndex]
                    val amountText = lic.price.split(" ").getOrNull(0)?.replace(",", "") ?: "0"
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    
                    // Verificar si hay espacio para el siguiente registro y posibles totales
                    val isLastForCurrency = (currentItemIndex == (currencyLicenses.size - 1))
                    val spaceNeeded = if (isLastForCurrency) 70f else 25f
                    
                    if (y + spaceNeeded > bottomLimit) break

                    currencyTotal += amount
                    pageSubtotal += amount

                    // Estilo de filas alternas para mejor lectura
                    if (idCounter % 2 == 0) {
                        paint.color = android.graphics.Color.rgb(245, 245, 245)
                        canvas.drawRect(margin, y - 15f, pageWidth - margin, y + 10f, paint)
                    }

                    // Línea separadora horizontal entre filas
                    paint.color = android.graphics.Color.LTGRAY
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(margin, y + 10f, pageWidth - margin, y + 10f, paint)

                    canvas.drawText(idCounter.toString(), c1, y, contentPaint)
                    
                    // Estado con indicadores visuales
                    val statusText = if (lic.isPaid) "PAGADO" else "PENDIENTE"
                    paint.color = if (lic.isPaid) android.graphics.Color.rgb(76, 175, 80) else android.graphics.Color.rgb(255, 152, 0)
                    paint.isFakeBoldText = true
                    paint.textSize = 8f
                    canvas.drawText(statusText, c2, y, paint)
                    paint.isFakeBoldText = false
                    paint.textSize = 9f
                    
                    val gson = com.google.gson.Gson()
                    val licenseInfo = try { gson.fromJson(lic.licenseJson, com.drywall.keygen.LicenseInfo::class.java) } catch (e: Exception) { null }
                    
                    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val creationStr = if (licenseInfo != null) sdfDate.format(Date(licenseInfo.creationDate)) else sdfDate.format(Date(lic.dateIssued))
                    val expiryStr = if (licenseInfo != null) sdfDate.format(Date(licenseInfo.expiryDate)) else "N/A"

                    canvas.drawText(lic.userName.take(20), c3, y, contentPaint)
                    canvas.drawText(creationStr, c4, y, contentPaint)
                    canvas.drawText(expiryStr, c5, y, contentPaint)
                    canvas.drawText(lic.deviceId.take(15), c6, y, contentPaint)
                    
                    contentPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(lic.price, pageWidth - margin - 5f, y, contentPaint)
                    contentPaint.textAlign = Paint.Align.LEFT

                    y += 22f
                    idCounter++
                    currentItemIndex++
                }

                // Dibujar SubTotal de la hoja actual
                canvas.drawLine(margin, bottomLimit, pageWidth - margin, bottomLimit, footerPaint)
                footerPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("SubTotal Página:", c7 - 20f, bottomLimit + 20f, footerPaint)
                canvas.drawText(String.format(Locale.US, "%.2f %s", pageSubtotal, currency), pageWidth - margin - 5f, bottomLimit + 20f, footerPaint)
                
                // Si es la última página de esta moneda, dibujar el Total General
                if (currentItemIndex == currencyLicenses.size) {
                    footerPaint.color = android.graphics.Color.rgb(33, 150, 243)
                    canvas.drawText("TOTAL GENERAL ($currency):", c7 - 20f, bottomLimit + 45f, footerPaint)
                    canvas.drawText(String.format(Locale.US, "%.2f %s", currencyTotal, currency), pageWidth - margin - 5f, bottomLimit + 45f, footerPaint)
                    footerPaint.color = android.graphics.Color.BLACK
                }
                footerPaint.textAlign = Paint.Align.LEFT

                // Paginación al pie de la página
                canvas.drawText("Página $globalPageCount", pageWidth / 2f, pageHeight - 25f, contentPaint)
                pdfDocument.finishPage(page)
            }
        }

        if (licenses.isEmpty()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawText("No se encontraron registros de licencias.", pageWidth / 2f, pageHeight / 2f, titlePaint)
            pdfDocument.finishPage(page)
        }

        saveAndSharePdf(context, pdfDocument, "Historial_Licencias.pdf")
    }

    fun generateIncomePdf(context: Context, totals: Map<String, Double>, totalPaid: Int, rateHistory: List<RateHistory>, pageSize: PageSize = PageSize.A4) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 60f

        // Company header
        val margin = 50f
        val contentWidth = pageSize.widthPt - 2 * margin

        // Company header
        paint.textSize = 28f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(76, 175, 80)
        canvas.drawText("DRYWALL PRO YHQUINTERO", margin, y, paint)
        y += 35f

        paint.textSize = 20f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.rgb(76, 175, 80)
        canvas.drawText("REPORTE INTEGRAL DE CONTROL", margin, y, paint)
        y += 35f

        paint.textSize = 14f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.BLACK
        canvas.drawText("RESUMEN DE INGRESOS", margin, y, paint)
        y += 30f
        paint.isFakeBoldText = false
        canvas.drawText("Licencias Cobradas: $totalPaid", margin, y, paint)
        y += 25f

        totals.forEach { (curr, amount) ->
            canvas.drawText("Total $curr: ${String.format(Locale.US, "%.2f", amount)}", margin, y, paint)
            y += 25f
        }
        y += 35f

        paint.textSize = 16f; paint.isFakeBoldText = true
        canvas.drawText("HISTORIAL DE TASAS DE CAMBIO (CUP)", margin, y, paint)
        y += 30f
        
        // Header
        paint.textSize = 11f; paint.isFakeBoldText = true
        canvas.drawText("No. Id.", margin, y, paint)
        canvas.drawText("Moneda", margin + 50f, y, paint)
        canvas.drawText("Tasa (CUP)", margin + 150f, y, paint)
        canvas.drawText("Fecha y Hora", margin + 300f, y, paint)
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawLine(margin, y-8f, pageSize.widthPt - margin, y-8f, paint)

        rateHistory.reversed().take(50).forEachIndexed { index, hist ->
            if (y > pageSize.heightPt - margin - 50f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
                
                // Header on new page
                paint.textSize = 11f; paint.isFakeBoldText = true
                canvas.drawText("No. Id.", margin, y, paint)
                canvas.drawText("Moneda", margin + 50f, y, paint)
                canvas.drawText("Tasa (CUP)", margin + 150f, y, paint)
                canvas.drawText("Fecha y Hora", margin + 300f, y, paint)
                y += 18f
                paint.isFakeBoldText = false
                canvas.drawLine(margin, y-8f, pageSize.widthPt - margin, y-8f, paint)
            }
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(hist.timestamp))
            canvas.drawText((index + 1).toString(), margin, y, paint)
            canvas.drawText(hist.currencyCode, margin + 50f, y, paint)
            canvas.drawText(String.format(Locale.US, "%.2f", hist.rate), margin + 150f, y, paint)
            canvas.drawText(dateStr, margin + 300f, y, paint)
            y += 18f
        }

        // Footer
        paint.textSize = 10f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.GRAY
        canvas.drawText("Generado el: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}", margin, pageSize.heightPt - margin - 20f, paint)
        canvas.drawText("DRYWALL PRO YHQUINTERO - Soluciones Profesionales", margin, pageSize.heightPt - margin, paint)

        pdfDocument.finishPage(page)
        saveAndSharePdf(context, pdfDocument, "Reporte_Integral_Drywall.pdf")
    }

    fun saveAndSharePdf(context: Context, pdfDocument: PdfDocument, fileName: String, extraText: String? = null) {
        val pdfDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DrywallPro/PDFs")
        if (!pdfDir.exists()) pdfDir.mkdirs()
        
        val file = File(pdfDir, fileName)
        try {
            FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.canonicalFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                val bodyText = extraText ?: "Adjunto el reporte: $fileName\n\nGenerado por Drywall Pro YHQUINTERO\nFecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}"
                putExtra(Intent.EXTRA_TEXT, bodyText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir PDF"))
        } catch (e: Exception) { 
            Toast.makeText(context, "Error al guardar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        } finally { 
            pdfDocument.close() 
        }
    }
}
