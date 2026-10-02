package com.drywall.calculator.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import com.drywall.calculator.data.local.entity.CuentaContable
import com.drywall.calculator.data.local.entity.FondoInversion
import com.drywall.calculator.data.local.entity.IncomeStatement
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FinancialPdfGenerator {

    private const val MARGIN = 40f
    private const val PAGE_WIDTH_A4 = 595
    private const val PAGE_HEIGHT_A4 = 842

    private const val COLOR_PRIMARY = "#1A237E"
    private const val COLOR_PRIMARY_LIGHT = "#E8EAF6"
    private const val COLOR_PRIMARY_DARK = "#0D1642"
    private const val COLOR_TEXT = "#212121"
    private const val COLOR_TEXT_SECONDARY = "#616161"
    private const val COLOR_TEXT_LIGHT = "#9E9E9E"
    private const val COLOR_DIVIDER = "#E0E0E0"
    private const val COLOR_BORDER = "#BDBDBD"
    private const val COLOR_ROW_ALT = "#F5F5F5"
    private const val COLOR_ROW_ALT_2 = "#FAFAFA"
    private const val COLOR_SUCCESS = "#2E7D32"
    private const val COLOR_SUCCESS_BG = "#E8F5E9"
    private const val COLOR_DANGER = "#C62828"
    private const val COLOR_DANGER_BG = "#FFEBEE"
    private const val COLOR_WHITE = "#FFFFFF"
    private const val COLOR_DEBE_HEADER = "#1565C0"
    private const val COLOR_HABER_HEADER = "#6A1B9A"
    private const val COLOR_DEBE_BG = "#E3F2FD"
    private const val COLOR_HABER_BG = "#F3E5F5"

    class Paints {
        val titlePaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor(COLOR_TEXT_SECONDARY); textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor(COLOR_TEXT); textSize = 9f; isAntiAlias = true
        }
        val headerTablePaint = Paint().apply {
            color = Color.WHITE; textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val totalPaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val totalBgPaint = Paint().apply { color = Color.parseColor(COLOR_PRIMARY_LIGHT); style = Paint.Style.FILL }
        val grandTotalPaint = Paint().apply {
            color = Color.WHITE; textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val grandTotalBgPaint = Paint().apply { color = Color.parseColor(COLOR_PRIMARY); style = Paint.Style.FILL }
        val positivePaint = Paint().apply {
            color = Color.parseColor(COLOR_SUCCESS); textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val negativePaint = Paint().apply {
            color = Color.parseColor(COLOR_DANGER); textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val tAccountTitlePaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val tAccountHeaderPaint = Paint().apply {
            color = Color.parseColor(COLOR_TEXT_SECONDARY); textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val tAccountBodyPaint = Paint().apply { color = Color.parseColor(COLOR_TEXT); textSize = 8f; isAntiAlias = true }
        val tAccountLinePaint = Paint().apply { color = Color.parseColor(COLOR_PRIMARY); strokeWidth = 1.5f }
        val tAccountTotalPaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val logoPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        val companyPaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val datePaint = Paint().apply { color = Color.parseColor(COLOR_TEXT_LIGHT); textSize = 9f; isAntiAlias = true }
        val footerPaint = Paint().apply {
            color = Color.parseColor(COLOR_TEXT_LIGHT); textSize = 7f
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val sectionLabelPaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val descriptionPaint = Paint().apply { color = Color.parseColor(COLOR_TEXT_SECONDARY); textSize = 7f; isAntiAlias = true }
        val aspectTitlePaint = Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY_DARK); textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
    }

    private class PdfState(
        val pdfDocument: PdfDocument,
        private val pageWidthPx: Float,
        private val pageHeightPx: Float,
        val paints: Paints
    ) {
        var currentPage: PdfDocument.Page
        var canvas: Canvas
        var y: Float = MARGIN
        private var pageNumber = 1
        private val contentWidth = pageWidthPx - 2 * MARGIN

        init {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_A4, PAGE_HEIGHT_A4, 1).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
        }

        fun ensureSpace(needed: Float, columns: List<Pair<String, Float>>? = null) {
            if (y + needed > pageHeightPx - 30f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_A4, PAGE_HEIGHT_A4, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                canvas.drawRect(0f, 0f, pageWidthPx, pageHeightPx, Paint().apply {
                    color = Color.WHITE; style = Paint.Style.FILL
                })
                y = MARGIN + 10f
                if (columns != null) {
                    y = drawTableHeader(columns)
                }
            }
        }

        fun drawTableHeader(columns: List<Pair<String, Float>>, bgColor: String = COLOR_PRIMARY): Float {
            val headerHeight = 20f
            canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + headerHeight, Paint().apply {
                color = Color.parseColor(bgColor); style = Paint.Style.FILL
            })
            var currentX = MARGIN + 4f
            for ((label, colWidth) in columns) {
                canvas.drawText(label, currentX, y + 14f, paints.headerTablePaint)
                currentX += colWidth
            }
            y += headerHeight
            return y
        }

        fun drawTableRow(
            values: List<Pair<String, Float>>, isAlt: Boolean,
            isBold: Boolean = false, isTotal: Boolean = false, isGrandTotal: Boolean = false,
            bgColor: String? = null
        ): Float {
            val rowHeight = 17f
            val bgPaint = when {
                bgColor != null -> Paint().apply { color = Color.parseColor(bgColor); style = Paint.Style.FILL }
                isGrandTotal -> paints.grandTotalBgPaint
                isTotal -> paints.totalBgPaint
                isAlt -> Paint().apply { color = Color.parseColor(COLOR_ROW_ALT); style = Paint.Style.FILL }
                else -> Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
            }
            canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + rowHeight, bgPaint)
            val textPaint = when {
                isGrandTotal -> paints.grandTotalPaint
                isTotal -> paints.totalPaint
                isBold -> Paint(paints.totalPaint).apply { textSize = 8.5f }
                else -> paints.bodyPaint
            }
            var currentX = MARGIN + 4f
            for ((value, colWidth) in values) {
                canvas.drawText(value, currentX, y + 12f, textPaint)
                currentX += colWidth
            }
            y += rowHeight
            return y
        }

        fun drawDetailRow(
            no: String, codigo: String, nombre: String, descripcion: String,
            subtipo: String, debeStr: String, haberStr: String,
            isAlt: Boolean, isDebe: Boolean = false, isHaber: Boolean = false,
            columns: List<Pair<String, Float>>
        ): Float {
            val rowHeight = 17f
            val bgColor = when {
                isDebe -> if (isAlt) "#EBF5FB" else "#F8FBFE"
                isHaber -> if (isAlt) "#F5EEF8" else "#FCF8FE"
                else -> if (isAlt) COLOR_ROW_ALT else COLOR_WHITE
            }
            canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + rowHeight, Paint().apply {
                color = Color.parseColor(bgColor); style = Paint.Style.FILL
            })
            var currentX = MARGIN + 4f
            canvas.drawText(no, currentX, y + 12f, paints.bodyPaint)
            currentX += columns[0].second
            val detallesText = "$codigo - $nombre"
            canvas.drawText(detallesText, currentX, y + 12f, paints.bodyPaint)
            currentX += columns[1].second
            if (columns.size > 2) {
                val subtipoLabel = subtipo.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                val descText = if (descripcion.isNotBlank()) descripcion else subtipoLabel
                canvas.drawText(descText, currentX, y + 12f, Paint(paints.descriptionPaint))
                currentX += columns[2].second
            }
            if (debeStr.isNotEmpty()) {
                canvas.drawText(debeStr, currentX + columns[3].second - paints.bodyPaint.measureText(debeStr), y + 12f,
                    Paint(paints.bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            }
            currentX += columns[3].second
            if (haberStr.isNotEmpty()) {
                canvas.drawText(haberStr, currentX + columns[4].second - paints.bodyPaint.measureText(haberStr), y + 12f,
                    Paint(paints.bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            }
            y += rowHeight
            return y
        }
    }

    private fun drawProfessionalHeader(
        state: PdfState, pageWidth: Float, context: Context,
        companyName: String, logoPath: String?, reportTitle: String, periodLabel: String
    ) {
        val paints = state.paints
        var headerBottom = state.y

        // Logo at the TOP RIGHT
        logoPath?.let { path ->
            try {
                val resolvedPath = ImageUtils.resolveUri(context, path) ?: path
                val uri = Uri.parse(resolvedPath)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = calculateInSampleSize(options, 80, 80)
                options.inJustDecodeBounds = false
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                bitmap?.let {
                    val logoWidth = 60f
                    val logoHeight = (it.height.toFloat() / it.width.toFloat()) * logoWidth
                    val logoX = pageWidth - MARGIN - logoWidth
                    state.canvas.drawBitmap(it, null, RectF(logoX, state.y, logoX + logoWidth, state.y + logoHeight), paints.logoPaint)
                    // If logo is present, we don't update headerBottom here yet, we wait for text
                }
            } catch (_: Exception) { }
        }

        // Company Name at the TOP LEFT
        paints.companyPaint.textAlign = Paint.Align.LEFT
        paints.companyPaint.textSize = 14f
        state.canvas.drawText(companyName.uppercase(), MARGIN, state.y + 15f, paints.companyPaint)
        
        state.y += 35f

        // Report Title
        state.canvas.drawText(reportTitle, MARGIN, state.y, paints.titlePaint)
        state.y += 18f

        val reportSubtitle = when {
            reportTitle.contains("COMPROBACI") -> "Balance General de Comprobaci\u00f3n por Saldos"
            reportTitle.contains("RESULTADO") -> "Estado de Resultados del Per\u00edodo"
            reportTitle.contains("FONDO") -> "Estado de Fondo de Inversi\u00f3n"
            reportTitle.contains("BALANCE") -> "Balance General / Estado de Situaci\u00f3n Financiera"
            else -> ""
        }
        if (reportSubtitle.isNotEmpty()) {
            state.canvas.drawText(reportSubtitle, MARGIN, state.y, paints.subtitlePaint)
            state.y += 14f
        }

        val thinLinePaint = Paint().apply { color = Color.parseColor(COLOR_PRIMARY); strokeWidth = 1.2f }
        state.canvas.drawLine(MARGIN, state.y, pageWidth - MARGIN, state.y, thinLinePaint)
        state.y += 12f

        state.canvas.drawText("Ejercicio: $periodLabel", MARGIN, state.y, Paint(paints.subtitlePaint).apply { textSize = 10f })
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        state.canvas.drawText("Emisi\u00f3n: $dateStr", pageWidth - MARGIN, state.y, paints.datePaint.apply { textAlign = Paint.Align.RIGHT })
        state.y += 14f

        state.canvas.drawLine(MARGIN, state.y, pageWidth - MARGIN, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 0.8f
        })
        state.y += 12f
    }

    private fun drawFooter(state: PdfState, pageWidth: Float, pageHeight: Float) {
        val footerY = pageHeight - 22f
        state.canvas.drawLine(MARGIN, footerY, pageWidth - MARGIN, footerY, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 0.5f
        })
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        state.canvas.drawText("Generado por Drywall Materials Calculator  |  $dateStr", pageWidth / 2, footerY + 10f, state.paints.footerPaint)
    }

    private fun drawSectionSeparator(state: PdfState, pageWidth: Float, label: String) {
        state.y += 8f
        state.canvas.drawLine(MARGIN, state.y, pageWidth - MARGIN, state.y, Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); strokeWidth = 1f
        })
        state.y += 12f
        state.canvas.drawText(label, MARGIN, state.y, state.paints.titlePaint)
        state.y += 14f
    }

    private fun formatMoney(value: Double, showSymbol: Boolean = false): String {
        val symbol = if (showSymbol) "$" else ""
        return "$symbol${String.format(Locale.US, "%,.2f", value)}"
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getMonthYearLabel(mes: Int, anio: Int): String {
        val months = listOf("", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        return "${months.getOrElse(mes) { "?" }} $anio"
    }

    fun generateBalanceComprobacion(
        context: Context, cuentas: List<CuentaContable>, mes: Int, anio: Int,
        companyName: String, logoPath: String?
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = PAGE_WIDTH_A4.toFloat()
        val pageHeight = PAGE_HEIGHT_A4.toFloat()
        val paints = Paints()
        val contentWidth = pageWidth - 2 * MARGIN
        val state = PdfState(pdfDocument, pageWidth, pageHeight, paints)

        val periodLabel = getMonthYearLabel(mes, anio)
        drawProfessionalHeader(state, pageWidth, context, companyName, logoPath, "BALANCE DE COMPROBACI\u00d3N", periodLabel)

        val columns = listOf(
            "No." to 25f, "Detalles" to contentWidth * 0.45f,
            "Descripci\u00f3n" to contentWidth * 0.20f,
            "Debe" to contentWidth * 0.16f, "Haber" to contentWidth * 0.16f
        )
        state.y = state.drawTableHeader(columns)

        val sortedCuentas = cuentas.sortedBy { it.codigo }
        val cuentasDebe = sortedCuentas.filter { it.saldoDebe > 0 }
        val cuentasHaber = sortedCuentas.filter { it.saldoHaber > 0 }

        var rowIdx = 0
        var totalDebe = 0.0
        var totalHaber = 0.0
        var firstDebeData = true
        var firstHaberData = true

        if (cuentasDebe.isNotEmpty()) {
            state.ensureSpace(30f)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 18f, Paint().apply {
                color = Color.parseColor(COLOR_DEBE_BG); style = Paint.Style.FILL
            })
            state.canvas.drawText("Cuentas con Saldo al DEBE", MARGIN + 4f, state.y + 13f, Paint(paints.sectionLabelPaint).apply {
                color = Color.parseColor(COLOR_DEBE_HEADER)
            })
            state.y += 18f

            for (cuenta in cuentasDebe) {
                state.ensureSpace(20f, columns)
                val debe = cuenta.saldoDebe
                totalDebe += debe
                val debeStr = formatMoney(debe, showSymbol = firstDebeData)
                state.drawDetailRow("${rowIdx + 1}", cuenta.codigo, cuenta.nombre, cuenta.descripcion,
                    cuenta.subtipo, debeStr, "", rowIdx % 2 == 0, isDebe = true, columns = columns)
                rowIdx++
                firstDebeData = false
            }
        }

        if (cuentasHaber.isNotEmpty()) {
            state.ensureSpace(30f)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 18f, Paint().apply {
                color = Color.parseColor(COLOR_HABER_BG); style = Paint.Style.FILL
            })
            state.canvas.drawText("Cuentas con Saldo al HABER", MARGIN + 4f, state.y + 13f, Paint(paints.sectionLabelPaint).apply {
                color = Color.parseColor(COLOR_HABER_HEADER)
            })
            state.y += 18f

            for (cuenta in cuentasHaber) {
                state.ensureSpace(20f, columns)
                val haber = cuenta.saldoHaber
                totalHaber += haber
                val haberStr = formatMoney(haber, showSymbol = firstHaberData)
                state.drawDetailRow("${rowIdx + 1}", cuenta.codigo, cuenta.nombre, cuenta.descripcion,
                    cuenta.subtipo, "", haberStr, rowIdx % 2 == 0, isHaber = true, columns = columns)
                rowIdx++
                firstHaberData = false
            }
        }

        state.y += 3f
        state.ensureSpace(100f, columns)
        state.y = state.drawTableHeader(columns)

        // SUMATORIA PARCIAL (No symbol here yet as per "sumatoria final" request)
        state.drawTableRow(listOf(
            "" to columns[0].second, "SUMATORIA PARCIAL" to columns[1].second, "" to columns[2].second,
            formatMoney(totalDebe, false) to columns[3].second, formatMoney(totalHaber, false) to columns[4].second
        ), false, isTotal = true)

        val aft = cuentas.sumOf { it.aft }
        val depAcum = cuentas.sumOf { it.depreciacionAcum }

        state.y += 1f
        state.drawTableRow(listOf(
            "" to columns[0].second, "AFT (Activo Fijo Tangible)" to columns[1].second, "" to columns[2].second,
            formatMoney(aft, false) to columns[3].second, "" to columns[4].second
        ), false, isBold = true, bgColor = COLOR_DEBE_BG)

        state.y += 1f
        state.drawTableRow(listOf(
            "" to columns[0].second, "Depreciaci\u00f3n Acumulada" to columns[1].second, "" to columns[2].second,
            "" to columns[3].second, formatMoney(depAcum, false) to columns[4].second
        ), false, isBold = true, bgColor = COLOR_HABER_BG)

        state.y += 3f
        val finalDebe = totalDebe + aft
        val finalHaber = totalHaber + depAcum
        state.drawTableRow(listOf(
            "" to columns[0].second, "SUMATORIA FINAL" to columns[1].second, "" to columns[2].second,
            formatMoney(finalDebe, true) to columns[3].second, formatMoney(finalHaber, true) to columns[4].second
        ), false, isGrandTotal = true)

        drawSectionSeparator(state, pageWidth, "CUENTAS T \u2013 DESGLOSE POR ASPECTO")

        val aspectos = listOf(
            CuentaContable.TIPO_ACTIVO to "ACTIVOS", CuentaContable.TIPO_PASIVO to "PASIVOS",
            CuentaContable.TIPO_PATRIMONIO to "PATRIMONIO", CuentaContable.TIPO_INGRESO to "INGRESOS",
            CuentaContable.TIPO_GASTO to "GASTOS"
        )

        for ((tipo, label) in aspectos) {
            val cuentasDelTipo = sortedCuentas.filter { it.tipo == tipo }
            if (cuentasDelTipo.isEmpty()) continue

            state.ensureSpace(100f)
            state.canvas.drawText("ASPECTO: $label", MARGIN, state.y, paints.aspectTitlePaint)
            state.y += 14f

            val tWidth = (contentWidth - 16f) / 2
            val tLeftX = MARGIN
            val tRightX = MARGIN + tWidth + 16f

            state.canvas.drawRect(tLeftX, state.y, tLeftX + tWidth, state.y + 18f, Paint().apply {
                color = Color.parseColor(COLOR_DEBE_BG); style = Paint.Style.FILL
            })
            state.canvas.drawRect(tRightX, state.y, tRightX + tWidth, state.y + 18f, Paint().apply {
                color = Color.parseColor(COLOR_HABER_BG); style = Paint.Style.FILL
            })

            val centerLeft = tLeftX + tWidth / 2
            val centerRight = tRightX + tWidth / 2
            val dHeader = Paint(paints.tAccountHeaderPaint).apply {
                color = Color.parseColor(COLOR_DEBE_HEADER); textAlign = Paint.Align.CENTER; textSize = 9f
            }
            val hHeader = Paint(paints.tAccountHeaderPaint).apply {
                color = Color.parseColor(COLOR_HABER_HEADER); textAlign = Paint.Align.CENTER; textSize = 9f
            }
            state.canvas.drawText("DEBE", centerLeft, state.y + 13f, dHeader)
            state.canvas.drawText("HABER", centerRight, state.y + 13f, hHeader)
            state.y += 18f

            var tDebeTotal = 0.0
            var tHaberTotal = 0.0
            var tRowIdx = 0

            for (cuenta in cuentasDelTipo) {
                state.ensureSpace(20f)
                val debeVal = cuenta.saldoDebe
                val haberVal = cuenta.saldoHaber
                tDebeTotal += debeVal
                tHaberTotal += haberVal

                if (tRowIdx % 2 == 0) {
                    state.canvas.drawRect(tLeftX, state.y, tLeftX + tWidth, state.y + 15f, Paint().apply {
                        color = Color.parseColor(COLOR_ROW_ALT_2); style = Paint.Style.FILL
                    })
                    state.canvas.drawRect(tRightX, state.y, tRightX + tWidth, state.y + 15f, Paint().apply {
                        color = Color.parseColor(COLOR_ROW_ALT_2); style = Paint.Style.FILL
                    })
                }

                val cuentaLabel = "${cuenta.codigo} ${cuenta.nombre}"
                if (debeVal > 0) {
                    val text = "$cuentaLabel: ${formatMoney(debeVal)}"
                    val tw = paints.tAccountBodyPaint.measureText(text)
                    val drawText = if (tw > tWidth - 6f) text.take(((tWidth - 6f) / paints.tAccountBodyPaint.textSize).toInt()) + "..." else text
                    state.canvas.drawText(drawText, tLeftX + 3f, state.y + 11f, paints.tAccountBodyPaint)
                }
                if (haberVal > 0) {
                    val text = "$cuentaLabel: ${formatMoney(haberVal)}"
                    val tw = paints.tAccountBodyPaint.measureText(text)
                    val drawText = if (tw > tWidth - 6f) text.take(((tWidth - 6f) / paints.tAccountBodyPaint.textSize).toInt()) + "..." else text
                    state.canvas.drawText(drawText, tRightX + 3f, state.y + 11f, paints.tAccountBodyPaint)
                }

                state.y += 15f
                tRowIdx++
            }

            state.canvas.drawLine(tLeftX, state.y, tLeftX + tWidth, state.y, paints.tAccountLinePaint)
            state.canvas.drawLine(tRightX, state.y, tRightX + tWidth, state.y, paints.tAccountLinePaint)
            state.y += 4f

            val tl = Paint(paints.tAccountTotalPaint).apply { textAlign = Paint.Align.LEFT; textSize = 8.5f }
            state.canvas.drawText("Saldo: ${formatMoney(tDebeTotal)}", tLeftX + 3f, state.y + 10f, tl)
            state.canvas.drawText("Saldo: ${formatMoney(tHaberTotal)}", tRightX + 3f, state.y + 10f, tl)
            state.y += 20f
        }

        drawFooter(state, pageWidth, pageHeight)
        pdfDocument.finishPage(state.currentPage)

        val fileName = "BALANCE_COMPROBACION_${mes}_${anio}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    fun generateEstadoResultado(
        context: Context, statement: IncomeStatement, companyName: String, logoPath: String?
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = PAGE_WIDTH_A4.toFloat()
        val pageHeight = PAGE_HEIGHT_A4.toFloat()
        val paints = Paints()
        val contentWidth = pageWidth - 2 * MARGIN
        val state = PdfState(pdfDocument, pageWidth, pageHeight, paints)

        val periodLabel = statement.periodLabel.ifBlank {
            val sdf = SimpleDateFormat("MMMM yyyy", Locale("es"))
            "${sdf.format(Date(statement.startDate))} - ${sdf.format(Date(statement.endDate))}"
        }
        drawProfessionalHeader(state, pageWidth, context, companyName, logoPath, "ESTADO DE RESULTADO", periodLabel)

        val columns = listOf("Concepto" to contentWidth * 0.60f, "Monto" to contentWidth * 0.40f)
        state.y = state.drawTableHeader(columns)

        fun drawLineItem(label: String, value: Double, isSubtotal: Boolean = false, indent: Boolean = false) {
            state.y += 2f
            state.ensureSpace(22f, columns)
            val xOff = if (indent) 12f else 0f
            val tp = if (isSubtotal) Paint(paints.totalPaint).apply { textSize = 9f } else Paint(paints.bodyPaint).apply { textSize = 9f }
            state.canvas.drawRect(MARGIN + xOff, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                color = if (isSubtotal) Color.parseColor(COLOR_PRIMARY_LIGHT) else Color.WHITE; style = Paint.Style.FILL
            })
            state.canvas.drawText(label, MARGIN + xOff + 4f, state.y + 12f, tp)
            val vs = formatMoney(value)
            val vw = tp.measureText(vs)
            state.canvas.drawText(vs, MARGIN + contentWidth - vw - 4f, state.y + 12f, tp)
            state.y += 17f
        }

        drawLineItem("(+) Ingresos por Ventas", statement.ventas)
        drawLineItem("(-) Costo de Ventas (CIV)", statement.costosVentas, indent = true)
        drawLineItem("UTILIDAD BRUTA", statement.utilidadBruta, isSubtotal = true)
        state.y += 4f
        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 0.5f
        })
        state.y += 4f
        drawLineItem("(-) Gastos Operativos", statement.gastosOperativos, indent = true)
        drawLineItem("UTILIDAD OPERATIVA (EBIT)", statement.utilidadOperativa, isSubtotal = true)
        state.y += 4f
        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 0.5f
        })
        state.y += 4f
        drawLineItem("(-) Gastos Financieros", statement.gastosFinancieros, indent = true)
        drawLineItem("UTILIDAD ANTES DE IMPUESTOS (UBT)", statement.utilidadAntesImpuestos, isSubtotal = true)
        state.y += 4f
        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 0.5f
        })
        state.y += 4f
        drawLineItem("(-) Impuesto sobre la Renta", statement.impuestos)

        state.y += 6f
        state.ensureSpace(30f, columns)
        val utilRowHeight = 24f
        val utilBgColor = if (statement.utilidadNeta >= 0) COLOR_SUCCESS_BG else COLOR_DANGER_BG
        state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + utilRowHeight, Paint().apply {
            color = Color.parseColor(utilBgColor); style = Paint.Style.FILL
        })
        state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + utilRowHeight, Paint().apply {
            color = Color.parseColor(if (statement.utilidadNeta >= 0) COLOR_SUCCESS else COLOR_DANGER)
            style = Paint.Style.STROKE; strokeWidth = 1.5f
        })
        val utilLP = Paint(paints.totalPaint).apply { textSize = 11f }
        val utilVP = Paint(if (statement.utilidadNeta >= 0) paints.positivePaint else paints.negativePaint).apply { textSize = 11f }
        state.canvas.drawText("UTILIDAD NETA DEL EJERCICIO", MARGIN + 6f, state.y + 17f, utilLP)
        val netStr = formatMoney(statement.utilidadNeta)
        val netW = utilVP.measureText(netStr)
        state.canvas.drawText(netStr, MARGIN + contentWidth - netW - 6f, state.y + 17f, utilVP)
        state.y += utilRowHeight + 12f

        state.ensureSpace(60f, columns)
        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 1f
        })
        state.y += 10f
        state.canvas.drawText("INDICADORES FINANCIEROS", MARGIN, state.y, paints.subtitlePaint)
        state.y += 14f

        val marginGross = if (statement.ventas > 0) (statement.utilidadBruta / statement.ventas * 100) else 0.0
        val marginOp = if (statement.ventas > 0) (statement.utilidadOperativa / statement.ventas * 100) else 0.0
        val marginNet = if (statement.ventas > 0) (statement.utilidadNeta / statement.ventas * 100) else 0.0

        val indCols = listOf("Indicador" to contentWidth * 0.55f, "Valor" to contentWidth * 0.45f)
        state.y = state.drawTableHeader(indCols, bgColor = "#455A64")

        val indicators = listOf(
            "Margen Bruto" to "${String.format(Locale.US, "%.1f%%", marginGross)}",
            "Margen Operativo" to "${String.format(Locale.US, "%.1f%%", marginOp)}",
            "Margen Neto" to "${String.format(Locale.US, "%.1f%%", marginNet)}",
            "Costo/Ventas" to "${if (statement.ventas > 0) String.format(Locale.US, "%.1f%%", statement.costosVentas / statement.ventas * 100) else "N/A"}",
            "Gastos Operativos/Ventas" to "${if (statement.ventas > 0) String.format(Locale.US, "%.1f%%", statement.gastosOperativos / statement.ventas * 100) else "N/A"}"
        )
        var indIdx = 0
        for ((indName, indValue) in indicators) {
            state.y += 2f
            state.ensureSpace(20f, indCols)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                color = if (indIdx % 2 == 0) Color.parseColor(COLOR_ROW_ALT) else Color.WHITE; style = Paint.Style.FILL
            })
            state.canvas.drawText(indName, MARGIN + 4f, state.y + 12f, paints.bodyPaint)
            state.canvas.drawText(indValue, MARGIN + indCols[0].second + 4f, state.y + 12f, Paint(paints.bodyPaint).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            state.y += 17f
            indIdx++
        }

        drawFooter(state, pageWidth, pageHeight)
        pdfDocument.finishPage(state.currentPage)

        val fileName = "ESTADO_RESULTADO_${statement.periodLabel.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    fun generateFondoInversion(
        context: Context, fondos: List<FondoInversion>, mes: Int, anio: Int,
        companyName: String, logoPath: String?
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = PAGE_WIDTH_A4.toFloat()
        val pageHeight = PAGE_HEIGHT_A4.toFloat()
        val paints = Paints()
        val contentWidth = pageWidth - 2 * MARGIN
        val state = PdfState(pdfDocument, pageWidth, pageHeight, paints)

        val periodLabel = getMonthYearLabel(mes, anio)
        drawProfessionalHeader(state, pageWidth, context, companyName, logoPath, "ESTADO DE FONDO DE INVERSI\u00d3N", periodLabel)

        val columns = listOf("Concepto" to contentWidth * 0.60f, "Monto" to contentWidth * 0.40f)

        var totalGeneralMat = 0.0; var totalGeneralMO = 0.0; var totalGeneralTrans = 0.0
        var totalGeneralEquip = 0.0; var totalGeneralOtros = 0.0; var totalGeneral = 0.0

        for (fondo in fondos) {
            state.ensureSpace(160f)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 20f, Paint().apply {
                color = Color.parseColor(COLOR_PRIMARY_LIGHT); style = Paint.Style.FILL
            })
            state.canvas.drawText("Proyecto: ${fondo.proyectoNombre}", MARGIN + 4f, state.y + 14f,
                Paint(paints.totalPaint).apply { textSize = 10f })
            state.y += 20f
            state.y = state.drawTableHeader(columns)

            val concepts = listOf(
                "Inversi\u00f3n en Materiales" to fondo.inversionMateriales,
                "Inversi\u00f3n en Mano de Obra" to fondo.inversionManoObra,
                "Inversi\u00f3n en Transporte" to fondo.inversionTransporte,
                "Inversi\u00f3n en Equipos" to fondo.inversionEquipos,
                "Inversi\u00f3n en Otros" to fondo.inversionOtros
            )
            var idx = 0
            for ((concept, amount) in concepts) {
                state.y += 2f
                state.ensureSpace(20f, columns)
                state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                    color = if (idx % 2 == 0) Color.parseColor(COLOR_ROW_ALT) else Color.WHITE; style = Paint.Style.FILL
                })
                state.canvas.drawText(concept, MARGIN + 4f, state.y + 12f, paints.bodyPaint)
                val vs = formatMoney(amount); val vw = paints.bodyPaint.measureText(vs)
                state.canvas.drawText(vs, MARGIN + contentWidth - vw - 4f, state.y + 12f, paints.bodyPaint)
                state.y += 17f; idx++
            }

            state.ensureSpace(24f, columns)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 20f, Paint().apply {
                color = Color.parseColor(COLOR_PRIMARY); style = Paint.Style.FILL
            })
            state.canvas.drawText("TOTAL INVERSI\u00d3N", MARGIN + 4f, state.y + 14f, paints.grandTotalPaint)
            val ts = formatMoney(fondo.totalInversion); val tw = paints.grandTotalPaint.measureText(ts)
            state.canvas.drawText(ts, MARGIN + contentWidth - tw - 4f, state.y + 14f, paints.grandTotalPaint)
            state.y += 20f

            if (fondo.notas.isNotBlank()) {
                state.y += 2f
                state.canvas.drawText("Notas: ${fondo.notas}", MARGIN + 4f, state.y + 10f, paints.descriptionPaint)
                state.y += 14f
            }
            state.y += 8f

            totalGeneralMat += fondo.inversionMateriales; totalGeneralMO += fondo.inversionManoObra
            totalGeneralTrans += fondo.inversionTransporte; totalGeneralEquip += fondo.inversionEquipos
            totalGeneralOtros += fondo.inversionOtros; totalGeneral += fondo.totalInversion
        }

        if (fondos.size > 1) {
            state.ensureSpace(120f)
            state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
                color = Color.parseColor(COLOR_PRIMARY); strokeWidth = 1.5f
            })
            state.y += 10f
            state.canvas.drawText("RESUMEN GENERAL DE INVERSIONES", MARGIN, state.y, paints.subtitlePaint)
            state.y += 16f
            state.y = state.drawTableHeader(columns)

            val summaryItems = listOf(
                "Total Inversi\u00f3n en Materiales" to totalGeneralMat,
                "Total Inversi\u00f3n en Mano de Obra" to totalGeneralMO,
                "Total Inversi\u00f3n en Transporte" to totalGeneralTrans,
                "Total Inversi\u00f3n en Equipos" to totalGeneralEquip,
                "Total Inversi\u00f3n en Otros" to totalGeneralOtros
            )
            var sIdx = 0
            for ((label, amount) in summaryItems) {
                state.y += 2f; state.ensureSpace(20f, columns)
                state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                    color = if (sIdx % 2 == 0) Color.parseColor(COLOR_ROW_ALT) else Color.WHITE; style = Paint.Style.FILL
                })
                state.canvas.drawText(label, MARGIN + 4f, state.y + 12f, paints.bodyPaint)
                val vs = formatMoney(amount); val vw = paints.bodyPaint.measureText(vs)
                state.canvas.drawText(vs, MARGIN + contentWidth - vw - 4f, state.y + 12f, paints.bodyPaint)
                state.y += 17f; sIdx++
            }

            state.ensureSpace(24f, columns)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 20f, Paint().apply {
                color = Color.parseColor(COLOR_PRIMARY); style = Paint.Style.FILL
            })
            state.canvas.drawText("TOTAL GENERAL DE INVERSIONES", MARGIN + 4f, state.y + 14f, paints.grandTotalPaint)
            val gs = formatMoney(totalGeneral); val gw = paints.grandTotalPaint.measureText(gs)
            state.canvas.drawText(gs, MARGIN + contentWidth - gw - 4f, state.y + 14f, paints.grandTotalPaint)
            state.y += 20f
        }

        state.y += 8f; state.ensureSpace(60f)
        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_DIVIDER); strokeWidth = 1f
        })
        state.y += 10f
        state.canvas.drawText("DISTRIBUCI\u00d3N PORCENTUAL", MARGIN, state.y, paints.subtitlePaint)
        state.y += 16f

        val pctCols = listOf("Concepto" to contentWidth * 0.45f, "Monto" to contentWidth * 0.28f, "%" to contentWidth * 0.27f)
        state.y = state.drawTableHeader(pctCols, bgColor = "#455A64")

        if (totalGeneral > 0) {
            val pctItems = listOf(
                "Materiales" to totalGeneralMat, "Mano de Obra" to totalGeneralMO,
                "Transporte" to totalGeneralTrans, "Equipos" to totalGeneralEquip, "Otros" to totalGeneralOtros
            )
            var pIdx = 0
            for ((label, amount) in pctItems) {
                state.y += 2f; state.ensureSpace(20f, pctCols)
                state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                    color = if (pIdx % 2 == 0) Color.parseColor(COLOR_ROW_ALT) else Color.WHITE; style = Paint.Style.FILL
                })
                state.canvas.drawText(label, MARGIN + 4f, state.y + 12f, paints.bodyPaint)
                val vs = formatMoney(amount); val vw = paints.bodyPaint.measureText(vs)
                state.canvas.drawText(vs, MARGIN + pctCols[0].second + pctCols[1].second - vw, state.y + 12f, paints.bodyPaint)
                val ps = "${String.format(Locale.US, "%.1f%%", amount / totalGeneral * 100)}"
                val pw = paints.bodyPaint.measureText(ps)
                state.canvas.drawText(ps, MARGIN + pctCols[0].second + pctCols[1].second + pctCols[2].second - pw - 4f, state.y + 12f,
                    Paint(paints.bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
                state.y += 17f; pIdx++
            }
        }

        drawFooter(state, pageWidth, pageHeight)
        pdfDocument.finishPage(state.currentPage)

        val fileName = "FONDO_INVERSION_${mes}_${anio}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }

    fun generateBalanceGeneral(
        context: Context, cuentas: List<CuentaContable>, mes: Int, anio: Int,
        companyName: String, logoPath: String?
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = PAGE_WIDTH_A4.toFloat()
        val pageHeight = PAGE_HEIGHT_A4.toFloat()
        val paints = Paints()
        val contentWidth = pageWidth - 2 * MARGIN
        val state = PdfState(pdfDocument, pageWidth, pageHeight, paints)

        val periodLabel = getMonthYearLabel(mes, anio)
        drawProfessionalHeader(state, pageWidth, context, companyName, logoPath, "BALANCE GENERAL", periodLabel)

        val columns = listOf("Cuenta" to contentWidth * 0.62f, "Saldo" to contentWidth * 0.38f)

        fun drawSectionTitle(title: String) {
            state.ensureSpace(30f, columns)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 18f, Paint().apply {
                color = Color.parseColor(COLOR_PRIMARY_LIGHT); style = Paint.Style.FILL
            })
            state.canvas.drawText(title, MARGIN + 4f, state.y + 13f, Paint(paints.sectionLabelPaint).apply { textSize = 9.5f })
            state.y += 18f
            state.y = state.drawTableHeader(columns)
        }

        fun drawAccountRow(cuenta: CuentaContable, idx: Int) {
            state.y += 2f; state.ensureSpace(20f, columns)
            val saldo = cuenta.saldoDebe - cuenta.saldoHaber
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                color = if (idx % 2 == 0) Color.parseColor(COLOR_ROW_ALT) else Color.WHITE; style = Paint.Style.FILL
            })
            state.canvas.drawText("${cuenta.codigo} ${cuenta.nombre}", MARGIN + 4f, state.y + 12f, paints.bodyPaint)
            val vs = formatMoney(saldo); val vw = paints.bodyPaint.measureText(vs)
            state.canvas.drawText(vs, MARGIN + contentWidth - vw - 4f, state.y + 12f, paints.bodyPaint)
            state.y += 17f
        }

        fun drawTotalRow(label: String, value: Double, isGrand: Boolean = false) {
            state.y += 2f; state.ensureSpace(22f, columns)
            val bg = if (isGrand) Paint().apply { color = Color.parseColor(COLOR_PRIMARY); style = Paint.Style.FILL }
            else Paint().apply { color = Color.parseColor(COLOR_PRIMARY_LIGHT); style = Paint.Style.FILL }
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 19f, bg)
            val tp = if (isGrand) Paint(paints.grandTotalPaint).apply { textSize = 10f }
            else Paint(paints.totalPaint).apply { textSize = 9.5f }
            state.canvas.drawText(label, MARGIN + 4f, state.y + 14f, tp)
            val vs = formatMoney(value); val vw = tp.measureText(vs)
            state.canvas.drawText(vs, MARGIN + contentWidth - vw - 4f, state.y + 14f, tp)
            state.y += 19f
        }

        val activosCorrientes = cuentas.filter { it.tipo == CuentaContable.TIPO_ACTIVO && it.subtipo == CuentaContable.SUBTIPO_ACTIVO_CORRIENTE }
        val activosNoCorrientes = cuentas.filter { it.tipo == CuentaContable.TIPO_ACTIVO && it.subtipo == CuentaContable.SUBTIPO_ACTIVO_NO_CORRIENTE }
        val pasivosCorrientes = cuentas.filter { it.tipo == CuentaContable.TIPO_PASIVO && it.subtipo == CuentaContable.SUBTIPO_PASIVO_CORRIENTE }
        val pasivosNoCorrientes = cuentas.filter { it.tipo == CuentaContable.TIPO_PASIVO && it.subtipo == CuentaContable.SUBTIPO_PASIVO_NO_CORRIENTE }
        val patrimonio = cuentas.filter { it.tipo == CuentaContable.TIPO_PATRIMONIO }

        state.canvas.drawText("ACTIVOS", MARGIN, state.y, paints.tAccountTitlePaint)
        state.y += 16f

        drawSectionTitle("ACTIVOS CORRIENTES")
        var idx = 0; for (cuenta in activosCorrientes) { drawAccountRow(cuenta, idx); idx++ }
        val totalActCorr = activosCorrientes.sumOf { it.saldoDebe - it.saldoHaber }
        drawTotalRow("Total Activos Corrientes", totalActCorr)
        state.y += 6f

        drawSectionTitle("ACTIVOS NO CORRIENTES")
        idx = 0; for (cuenta in activosNoCorrientes) { drawAccountRow(cuenta, idx); idx++ }
        val depAcum = cuentas.filter { it.nombre.contains("Depreciaci\u00f3n", ignoreCase = true) }.sumOf { it.saldoHaber - it.saldoDebe }
        if (depAcum != 0.0) {
            state.y += 2f; state.ensureSpace(20f, columns)
            state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 17f, Paint().apply {
                color = Color.parseColor(COLOR_HABER_BG); style = Paint.Style.FILL
            })
            state.canvas.drawText("(+) Depreciaci\u00f3n Acumulada (menos)", MARGIN + 4f, state.y + 12f,
                Paint(paints.bodyPaint).apply { color = Color.parseColor(COLOR_DANGER); typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) })
            val ds = formatMoney(-depAcum); val dw = paints.bodyPaint.measureText(ds)
            state.canvas.drawText(ds, MARGIN + contentWidth - dw - 4f, state.y + 12f,
                Paint(paints.bodyPaint).apply { color = Color.parseColor(COLOR_DANGER) })
            state.y += 17f
        }
        val totalActNoCorr = activosNoCorrientes.sumOf { it.saldoDebe - it.saldoHaber } - depAcum
        drawTotalRow("Total Activos No Corrientes", totalActNoCorr)
        state.y += 6f

        val totalActivos = totalActCorr + totalActNoCorr
        drawTotalRow("TOTAL ACTIVOS", totalActivos, isGrand = true)
        state.y += 10f

        state.canvas.drawLine(MARGIN, state.y, MARGIN + contentWidth, state.y, Paint().apply {
            color = Color.parseColor(COLOR_PRIMARY); strokeWidth = 1.2f
        })
        state.y += 12f

        state.canvas.drawText("PASIVOS Y PATRIMONIO", MARGIN, state.y, paints.tAccountTitlePaint)
        state.y += 16f

        drawSectionTitle("PASIVOS CORRIENTES")
        idx = 0; for (cuenta in pasivosCorrientes) { drawAccountRow(cuenta, idx); idx++ }
        val totalPasCorr = pasivosCorrientes.sumOf { it.saldoHaber - it.saldoDebe }
        drawTotalRow("Total Pasivos Corrientes", totalPasCorr)
        state.y += 6f

        drawSectionTitle("PASIVOS NO CORRIENTES")
        idx = 0; for (cuenta in pasivosNoCorrientes) { drawAccountRow(cuenta, idx); idx++ }
        val totalPasNoCorr = pasivosNoCorrientes.sumOf { it.saldoHaber - it.saldoDebe }
        drawTotalRow("Total Pasivos No Corrientes", totalPasNoCorr)
        state.y += 6f

        val totalPasivos = totalPasCorr + totalPasNoCorr
        drawTotalRow("TOTAL PASIVOS", totalPasivos)
        state.y += 10f

        drawSectionTitle("PATRIMONIO")
        idx = 0; for (cuenta in patrimonio) { drawAccountRow(cuenta, idx); idx++ }
        val totalPatrimonio = patrimonio.sumOf { it.saldoHaber - it.saldoDebe }
        drawTotalRow("TOTAL PATRIMONIO", totalPatrimonio)
        state.y += 10f

        val totalPasPat = totalPasivos + totalPatrimonio
        drawTotalRow("TOTAL PASIVOS + PATRIMONIO", totalPasPat, isGrand = true)

        state.y += 12f; state.ensureSpace(40f)
        val balanceCheck = totalActivos == totalPasPat
        val checkBg = if (balanceCheck) COLOR_SUCCESS_BG else COLOR_DANGER_BG
        val checkBc = if (balanceCheck) COLOR_SUCCESS else COLOR_DANGER
        val checkText = if (balanceCheck) "BALANCE CUADRADO \u2713" else "DESBALANCE DETECTADO"
        state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 22f, Paint().apply {
            color = Color.parseColor(checkBg); style = Paint.Style.FILL
        })
        state.canvas.drawRect(MARGIN, state.y, MARGIN + contentWidth, state.y + 22f, Paint().apply {
            color = Color.parseColor(checkBc); style = Paint.Style.STROKE; strokeWidth = 1.5f
        })
        state.canvas.drawText(checkText, MARGIN + contentWidth / 2, state.y + 16f, Paint(paints.totalPaint).apply {
            color = Color.parseColor(checkBc); textSize = 10f; textAlign = Paint.Align.CENTER
        })

        drawFooter(state, pageWidth, pageHeight)
        pdfDocument.finishPage(state.currentPage)

        val fileName = "BALANCE_GENERAL_${mes}_${anio}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        file.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.canonicalFile
    }
}
