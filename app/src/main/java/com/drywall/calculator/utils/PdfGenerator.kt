package com.drywall.calculator.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import com.drywall.calculator.data.local.entity.IncomeStatement
import com.drywall.calculator.presentation.ui.incomestatement.IncomeStatementUtils
import com.drywall.calculator.utils.security.ConsumedLicense
import com.drywall.calculator.utils.security.LicenseInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class PdfGenerator @Inject constructor() {
    enum class PageSize(val label: String, val widthPt: Int, val heightPt: Int) {
        A4("A4", 595, 842),
        CARTA("Carta", 612, 792),
        OFICIO("Oficio", 612, 1008),
        LEGAL("Legal", 612, 1008)
    }

    companion object {
        private const val TAG = "PdfGenerator"
        private var logoCache: Pair<String, Bitmap>? = null

        fun generateInventorySummary(
            context: Context,
            materials: List<com.drywall.calculator.data.local.entity.Material>,
            pageSize: PageSize = PageSize.A4
        ): File {
            val pdfDocument = PdfDocument()
            val pageWidth = pageSize.widthPt.toFloat()
            val pageHeight = pageSize.heightPt.toFloat()
            val margin = 50f
            val contentWidth = pageWidth - 2 * margin
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isFakeBoldText = true }
            val headerPaint = Paint().apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; isFakeBoldText = true }
            val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
            val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

            canvas.drawText("RESUMEN DE INVENTARIO", margin, 40f, titlePaint)
            canvas.drawText("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", margin, 60f, bodyPaint)

            var y = 90f
            canvas.drawText("Material", margin, y, headerPaint)
            canvas.drawText("Stock", margin + contentWidth * 0.5f, y, headerPaint)
            canvas.drawText("Precio", margin + contentWidth * 0.75f, y, headerPaint)
            y += 5f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 15f

            for (material in materials) {
                canvas.drawText(material.name, margin, y, bodyPaint)
                canvas.drawText("${String.format("%.1f", material.quantity)} ${material.unitType}", margin + contentWidth * 0.5f, y, bodyPaint)
                canvas.drawText("$${String.format("%.2f", material.salePrice)}", margin + contentWidth * 0.75f, y, bodyPaint)
                y += 18f
                if (y > pageHeight - 62f) break
            }

            pdfDocument.finishPage(page)
            val fileName = "RESUMEN_INVENTARIO_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            file.outputStream().use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            return file.canonicalFile
        }

        fun generateIncomeStatementPdf(
            context: Context,
            statement: IncomeStatement,
            pageSize: PageSize = PageSize.A4
        ): File {
            val pdfDocument = PdfDocument()
            val pageWidth = pageSize.widthPt.toFloat()
            val pageHeight = pageSize.heightPt.toFloat()
            val margin = 50f
            val contentWidth = pageWidth - 2 * margin
            
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isFakeBoldText = true }
            val subtitlePaint = Paint().apply { color = Color.DKGRAY; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
            val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
            val resultPaint = Paint().apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
            val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

            canvas.drawText("ESTADO DE RESULTADO", margin, 50f, titlePaint)
            canvas.drawText("Empresa: Drywall Materials Calculator", margin, 75f, bodyPaint)
            canvas.drawText("Fecha de Emisión: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", margin, 90f, bodyPaint)
            
            var y = 120f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 25f

            canvas.drawText("INFORMACIÓN DEL PERÍODO", margin, y, subtitlePaint)
            y += 20f
            canvas.drawText("Tipo de Período: ${IncomeStatement.PERIOD_LABELS[statement.periodType] ?: statement.periodType}", margin, y, bodyPaint)
            y += 15f
            val dateRange = "${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(statement.startDate))} - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(statement.endDate))}"
            canvas.drawText("Rango de Fechas: $dateRange", margin, y, bodyPaint)
            
            y += 30f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 25f

            fun drawFinancialRow(label: String, value: Double, isTotal: Boolean = false) {
                if (isTotal) {
                    canvas.drawLine(margin + contentWidth * 0.4f, y - 12f, pageWidth - margin, y - 12f, dividerPaint)
                    canvas.drawText(label, margin + 20f, y, resultPaint)
                    val valueStr = IncomeStatementUtils.formatCurrency(value, statement.moneda)
                    val valueWidth = resultPaint.measureText(valueStr)
                    canvas.drawText(valueStr, pageWidth - margin - valueWidth, y, resultPaint)
                    y += 25f
                } else {
                    canvas.drawText(label, margin + 20f, y, bodyPaint)
                    val valueStr = IncomeStatementUtils.formatCurrency(value, statement.moneda)
                    val valueWidth = bodyPaint.measureText(valueStr)
                    canvas.drawText(valueStr, pageWidth - margin - valueWidth, y, bodyPaint)
                    y += 18f
                }
            }

            drawFinancialRow("(+) VENTAS", statement.ventas)
            drawFinancialRow("(-) Costos de Ventas", statement.costosVentas)
            drawFinancialRow("UTILIDAD BRUTA", statement.utilidadBruta, true)
            drawFinancialRow("(-) Gastos Operativos", statement.gastosOperativos)
            drawFinancialRow("UTILIDAD OPERATIVA", statement.utilidadOperativa, true)
            drawFinancialRow("(-) Gastos Financieros", statement.gastosFinancieros)
            drawFinancialRow("UTILIDAD ANTES DE IMPUESTOS", statement.utilidadAntesImpuestos, true)
            drawFinancialRow("(-) Impuestos", statement.impuestos)
            
            y += 5f
            val finalResultPaint = Paint(resultPaint).apply { textSize = 14f; color = if (statement.utilidadNeta >= 0) Color.parseColor("#2E7D32") else Color.RED }
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 25f
            canvas.drawText("UTILIDAD NETA", margin, y, finalResultPaint)
            val netStr = IncomeStatementUtils.formatCurrency(statement.utilidadNeta, statement.moneda)
            val netWidth = finalResultPaint.measureText(netStr)
            canvas.drawText(netStr, pageWidth - margin - netWidth, y, finalResultPaint)

            pdfDocument.finishPage(page)
            val fileName = "ESTADO_RESULTADO_${statement.periodLabel.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            file.outputStream().use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            return file.canonicalFile
        }

        fun generateProjectsSummary(context: Context, projects: List<com.drywall.calculator.data.local.entity.Project>, pageSize: PageSize = PageSize.A4): File {
            val pdfDocument = PdfDocument()
            val pageWidth = pageSize.widthPt.toFloat()
            val margin = 50f
            val contentWidth = pageWidth - 2 * margin
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
            val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
            canvas.drawText("RESUMEN DE OBRAS", margin, 40f, titlePaint)
            var y = 80f
            for (project in projects) {
                canvas.drawText("${project.name} - Area: ${project.totalAreaM2} m2", margin, y, bodyPaint)
                y += 18f
            }
            pdfDocument.finishPage(page)
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RESUMEN_OBRAS_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            return file.canonicalFile
        }

        fun generateClientsSummary(context: Context, clients: List<com.drywall.calculator.data.local.entity.Client>, pageSize: PageSize = PageSize.A4): File {
            val pdfDocument = PdfDocument()
            val pageWidth = pageSize.widthPt.toFloat()
            val margin = 50f
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
            val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
            canvas.drawText("RESUMEN DE CLIENTES", margin, 40f, titlePaint)
            var y = 80f
            for (client in clients) {
                canvas.drawText("${client.name} ${client.surnames}", margin, y, bodyPaint)
                y += 18f
            }
            pdfDocument.finishPage(page)
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RESUMEN_CLIENTES_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            return file.canonicalFile
        }

        fun generateConsumedLicenseSummary(context: Context, consumedHistory: List<ConsumedLicense>, pageSize: PageSize = PageSize.A4): File {
            val pdfDocument = PdfDocument()
            val pageWidth = pageSize.widthPt.toFloat()
            val margin = 50f
            val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
            val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 10f }
            canvas.drawText("RESUMEN DE LICENCIAS", margin, 40f, titlePaint)
            var y = 80f
            for (license in consumedHistory) {
                canvas.drawText("${license.user} - Razon: ${license.reason}", margin, y, bodyPaint)
                y += 18f
            }
            pdfDocument.finishPage(page)
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "RESUMEN_LICENCIAS_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            return file.canonicalFile
        }
    }

    fun generateIdCard(
        context: Context,
        companyName: String,
        logoPath: String?,
        ownerPhotoPath: String?,
        signaturePath: String?,
        ownerName: String,
        role: String = "ESPECIALISTA EN DRYWALL",
        pageSize: PageSize = PageSize.A4
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = pageSize.widthPt.toFloat()
        val pageHeight = pageSize.heightPt.toFloat()
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }
        paint.color = Color.WHITE; canvas.drawRect(0f, 0f, pageWidth, pageHeight, paint)
        paint.color = Color.BLACK; paint.textSize = 24f; canvas.drawText(ownerName.uppercase(), 50f, 100f, paint)
        paint.textSize = 14f; canvas.drawText(role, 50f, 130f, paint)
        canvas.drawText(companyName, 50f, 160f, paint)
        pdfDocument.finishPage(page)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CARNET_${ownerName.replace(" ", "_")}.pdf")
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    fun generateDocument(
        context: Context,
        docType: DocType,
        companyName: String,
        logoPath: String?,
        projectName: String,
        clientName: String,
        totalM2: Double,
        items: List<InvoiceItem>,
        totalCost: Double,
        taxInfo: String = "",
        measurementsInfo: Map<String, Double> = emptyMap(),
        bankInfo: Map<String, String> = emptyMap(),
        grandTotal: Double? = null,
        constructionType: String = "",
        decimalPrecision: Int = 4,
        specialData: String? = null,
        pageSize: PageSize = PageSize.A4
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = pageSize.widthPt.toFloat()
        val pageHeight = pageSize.heightPt.toFloat()
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 12f }
        canvas.drawText(docType.name, 50f, 50f, paint)
        canvas.drawText("Obra: $projectName", 50f, 80f, paint)
        canvas.drawText("Cliente: $clientName", 50f, 100f, paint)
        var y = 140f
        for (item in items) {
            canvas.drawText("${item.name} - ${item.totalQty} ${item.unitType}", 50f, y, paint)
            y += 15f
            if (y > pageHeight - 50f) break
        }
        pdfDocument.finishPage(page)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DOCUMENTO_${projectName.replace(" ", "_")}.pdf")
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    fun generateLicenseHistoryReport(
        context: Context,
        companyName: String,
        logoPath: String?,
        currentLicense: LicenseInfo?,
        isLicenseValid: Boolean,
        history: List<ConsumedLicense>,
        pageSize: PageSize = PageSize.A4
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = pageSize.widthPt.toFloat()
        val pageHeight = pageSize.heightPt.toFloat()
        val margin = 50f
        val contentWidth = pageWidth - 2 * margin

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isFakeBoldText = true }
        val subtitlePaint = Paint().apply { color = Color.DKGRAY; textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 9f }
        val smallPaint = Paint().apply { color = Color.DKGRAY; textSize = 8f }
        val statusValidPaint = Paint().apply { color = Color.parseColor("#2E7D32"); textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val statusInvalidPaint = Paint().apply { color = Color.RED; textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        val thickDividerPaint = Paint().apply { color = Color.DKGRAY; strokeWidth = 1f }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        var currentPage = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, currentPage).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var y = 40f

        var logo: Bitmap? = null
        if (!logoPath.isNullOrBlank()) {
            logo = loadSecureBitmap(context, logoPath, 80, 80)
        }

        if (logo != null) {
            val logoWidth = 40f
            val logoHeight = 40f * logo.height / logo.width
            canvas.drawBitmap(logo, null, RectF(margin, y - 5f, margin + logoWidth, y - 5f + logoHeight), null)
            canvas.drawText(companyName, margin + logoWidth + 10f, y + 10f, titlePaint)
            canvas.drawText("Reporte de Historial de Licencias", margin + logoWidth + 10f, y + 28f, smallPaint)
        } else {
            canvas.drawText("HISTORIAL DE LICENCIAS", margin, y + 10f, titlePaint)
            canvas.drawText(companyName, margin, y + 30f, subtitlePaint)
        }

        y += if (logo != null) 55f else 50f
        canvas.drawText("Fecha de Emisión: ${dateTimeFormat.format(Date())}", margin, y, smallPaint)
        y += 5f
        canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
        y += 20f

        if (currentLicense != null) {
            canvas.drawText("ESTADO DE LICENCIA ACTUAL", margin, y, subtitlePaint)
            y += 18f
            canvas.drawText("Usuario: ${currentLicense.user}", margin, y, bodyPaint)
            y += 14f
            canvas.drawText("Tipo: ${currentLicense.type}", margin, y, bodyPaint)
            y += 14f
            canvas.drawText("Vencimiento: ${dateTimeFormat.format(Date(currentLicense.expiryDate))}", margin, y, bodyPaint)
            y += 14f
            val statusText = if (isLicenseValid) "VALIDA" else "EXPIRADA"
            val statusPaint = if (isLicenseValid) statusValidPaint else statusInvalidPaint
            canvas.drawText("Estado: $statusText", margin, y, statusPaint)
            y += 20f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 20f
        }

        canvas.drawText("HISTORIAL DE USO (${history.size} registros)", margin, y, subtitlePaint)
        y += 20f

        val colUser = margin
        val colStatus = margin + contentWidth * 0.35f
        val colConsumeDate = margin + contentWidth * 0.58f
        val colExpiry = margin + contentWidth * 0.78f

        canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
        y += 4f
        canvas.drawText("Usuario", colUser, y, headerPaint)
        canvas.drawText("Estado", colStatus, y, headerPaint)
        canvas.drawText("Consumo", colConsumeDate, y, headerPaint)
        canvas.drawText("Vencimiento", colExpiry, y, headerPaint)
        y += 4f
        canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
        y += 14f

        val rowHeight = 16f
        val bottomMargin = 60f

        for (record in history) {
            if (y + rowHeight > pageHeight - bottomMargin) {
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                pdfDocument.finishPage(page)
                currentPage++
                pageInfo = PdfDocument.PageInfo.Builder(pageSize.widthPt, pageSize.heightPt, currentPage).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                y = 40f
                canvas.drawText("HISTORIAL DE LICENCIAS (cont.)", margin, y, subtitlePaint)
                y += 20f
                canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
                y += 4f
                canvas.drawText("Usuario", colUser, y, headerPaint)
                canvas.drawText("Estado", colStatus, y, headerPaint)
                canvas.drawText("Consumo", colConsumeDate, y, headerPaint)
                canvas.drawText("Vencimiento", colExpiry, y, headerPaint)
                y += 4f
                canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
                y += 14f
            }

            canvas.drawText(record.user, colUser, y, bodyPaint)

            val isTrial = record.signature == com.drywall.calculator.utils.security.LicensingManager.TRIAL_SIGNATURE
            val recordStatusPaint = when {
                record.reason.contains("Violación") -> statusInvalidPaint
                record.reason == "Activa" -> statusValidPaint
                else -> bodyPaint
            }
            val statusLabel = if (isTrial) "PRUEBA - ${record.reason}" else record.reason
            canvas.drawText(statusLabel, colStatus, y, recordStatusPaint)

            val consumeDateStr = if (record.consumptionDate > 1000000000000L) dateFormat.format(Date(record.consumptionDate)) else "N/A"
            canvas.drawText(consumeDateStr, colConsumeDate, y, bodyPaint)

            val expiryDateStr = if (record.expiryDate > 1000000000000L) dateFormat.format(Date(record.expiryDate)) else "N/A"
            canvas.drawText(expiryDateStr, colExpiry, y, bodyPaint)

            y += rowHeight
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 2f
        }

        y += 15f
        canvas.drawLine(margin, y, pageWidth - margin, y, thickDividerPaint)
        y += 15f
        val footerPaint = Paint().apply { color = Color.LTGRAY; textSize = 8f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Documento generado por Drywall Materials Calculator - $companyName", pageWidth / 2, y, footerPaint)
        y += 10f
        canvas.drawText(dateTimeFormat.format(Date()), pageWidth / 2, y, footerPaint)

        pdfDocument.finishPage(page)
        val fileName = "HISTORIAL_LICENCIAS_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    private fun drawTextAdaptable(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxWidth: Float, minSize: Float = 8f): Float {
        val originalSize = paint.textSize
        var currentSize = originalSize
        while (paint.measureText(text) > maxWidth && currentSize > minSize) { currentSize -= 0.5f; paint.textSize = currentSize }
        canvas.drawText(text, x, y, paint)
        val finalSize = paint.textSize
        paint.textSize = originalSize
        return finalSize
    }

    private fun loadSecureBitmap(context: Context, path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val resolvedPath = com.drywall.calculator.utils.ImageUtils.resolveUri(context, path) ?: path
            val uri = Uri.parse(resolvedPath)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) { null }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) { inSampleSize *= 2 }
        }
        return inSampleSize
    }

    data class InvoiceItem(val name: String, val qtyPerM2: Double, val totalQty: Double, val unitPrice: Double, val totalPrice: Double, val unitType: String = "unidad", val groupName: String? = null, val segLength: Double = 0.0, val segWidth: Double = 0.0, val segRepetitions: Int = 0)
    enum class DocType { QUOTATION, PURCHASE_ORDER, FINAL_INVOICE, MEASUREMENTS_REPORT, ID_CARD }
}
