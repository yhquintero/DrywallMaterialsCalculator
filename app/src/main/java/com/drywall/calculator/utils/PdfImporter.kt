package com.drywall.calculator.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.drywall.calculator.presentation.ui.calculator.ConstructionBlock
import com.drywall.calculator.presentation.ui.calculator.ConstructionSegment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

object PdfImporter {

    private val HEADER_KEYWORDS = listOf(
        "DESCRIPCIÓN", "SEGMENTO", "SUBTOTAL", "ÁREA TOTAL", "REPORTE",
        "DETALLE", "MEDIDAS", "TÉCNICAS", "DATOS", "ADICIONALES",
        "CONT...", "LARGO", "ANCHO", "REP.", "ÁREA", "TOTAL GENERAL",
        "RESUMEN", "EMPRESA", "PROYECTO", "FECHA", "CLIENTE", "TIPO DE",
        "NOMBRE:", "DATOS DE", "FIRMA", "No.",
        "构造", "段号", "部件", "型号", "技术", "参数", "汇总", "工程",
        "项目", "日期", "客户", "公司", "名称:", "签名", "编号",
        "AREA", "TOTAL", "REPT", "UNIT", "PRICE", "COST"
    )

    private fun isHeaderOrFooterLine(text: String): Boolean {
        val upper = text.uppercase().trim()
        return upper.length < 2 || HEADER_KEYWORDS.any { upper.contains(it) }
    }

    private fun isNumericToken(s: String): Boolean {
        val cleaned = s.replace(",", ".")
        return cleaned.toDoubleOrNull() != null
    }

    private fun tryParseJsonBlocks(rawJson: String): List<ConstructionBlock>? {
        val json = rawJson.trim()
        if (json.isBlank()) return null
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<ConstructionBlock>>() {}.type
            val imported: List<ConstructionBlock> = com.google.gson.Gson().fromJson(json, type)
            if (imported.isNotEmpty()) imported else null
        } catch (e: Exception) {
            android.util.Log.e("PdfImporter", "JSON parse failed", e)
            null
        }
    }

    suspend fun extractBlocksFromPdf(context: Context, uri: Uri): List<ConstructionBlock> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        try {
            val blocks = mutableListOf<ConstructionBlock>()
            val renderer = PdfRenderer(pfd)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            var currentBlock: ConstructionBlock? = null
            val segments = mutableListOf<ConstructionSegment>()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                    val matrix = android.graphics.Matrix()
                    matrix.setScale(3f, 3f)
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    bitmap.recycle()

                    val fullText = result.text.trim()

                    // === STRATEGY 1: Hidden JSON markers ===
                    val jsonResult = tryExtractHiddenJson(fullText)
                    if (jsonResult != null) {
                        recognizer.close()
                        renderer.close()
                        return jsonResult.map { b ->
                            b.copy(
                                id = if (b.id.isBlank()) UUID.randomUUID().toString() else b.id,
                                segments = b.segments.map { s ->
                                    if (s.id.isBlank()) s.copy(id = UUID.randomUUID().toString()) else s
                                }
                            )
                        }
                    }

                    // === STRATEGY 2: OCR-based line parsing ===
                    val lines = result.textBlocks
                        .flatMap { it.lines }
                        .sortedBy { it.boundingBox?.top ?: 0 }

                    for (line in lines) {
                        val text = line.text.trim()
                        if (text.isBlank()) continue

                        // Skip known header/footer lines
                        if (isHeaderOrFooterLine(text)) continue

                        // Pattern A: "BLOQUE #N: Name" block headers
                        if (text.contains("BLOQUE #", ignoreCase = true)) {
                            if (currentBlock != null && segments.isNotEmpty()) {
                                blocks.add(currentBlock.copy(segments = segments.toList()))
                                segments.clear()
                            }
                            val name = text.substringAfter(":", "").trim().ifBlank { text.substringAfter("#").trim() }
                            currentBlock = ConstructionBlock(name = name.ifBlank { "Bloque Importado" })
                            continue
                        }

                        // Pattern B: Technical format "Name [L:x.xx W:x.xx R:xx]"
                        if (text.contains("[L:", ignoreCase = true) && text.contains("W:", ignoreCase = true)) {
                            try {
                                val name = text.substringBefore("[").trim()
                                val length = text.substringAfter("L:").substringBefore(" ").replace(",", ".").toDoubleOrNull() ?: 0.0
                                val width = text.substringAfter("W:").substringBefore(" ").replace(",", ".").toDoubleOrNull() ?: 0.0
                                val reps = text.substringAfter("R:").substringBefore("]").toIntOrNull() ?: 1

                                if (currentBlock == null) {
                                    currentBlock = ConstructionBlock(name = "Bloque Importado")
                                }

                                segments.add(ConstructionSegment(
                                    name = name.ifBlank { "Segmento" },
                                    length = length,
                                    width = width,
                                    repetitions = reps
                                ))
                                continue
                            } catch (e: Exception) {
                                android.util.Log.e("PdfImporter", "Error parsing technical line: $text")
                            }
                        }

                        // Pattern C: "×" separator format "Name 1.20×2.40"
                        if (text.contains("×") && !isHeaderOrFooterLine(text)) {
                            try {
                                val parts = text.split("\\s+".toRegex())
                                val dimIdx = parts.indexOfFirst { it.contains("×") }
                                if (dimIdx != -1) {
                                    val dims = parts[dimIdx].split("×")
                                    val length = dims[0].replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val width = if (dims.size > 1) dims[1].replace(",", ".").toDoubleOrNull() ?: 0.0 else 0.0
                                    val name = if (dimIdx > 0) parts.subList(0, dimIdx).joinToString(" ").trim() else "Segmento"
                                    val reps = if (dimIdx + 1 < parts.size) {
                                        parts[dimIdx + 1].replace("x", "").toIntOrNull() ?: 1
                                    } else 1

                                    if (currentBlock == null) {
                                        currentBlock = ConstructionBlock(name = "Importado de PDF")
                                    }

                                    segments.add(ConstructionSegment(
                                        name = name.ifBlank { "Segmento" },
                                        length = length,
                                        width = width,
                                        repetitions = reps
                                    ))
                                    continue
                                }
                            } catch (_: Exception) {}
                        }

                        // Pattern D: MEASUREMENTS_REPORT table format
                        // Lines like: "1 Pared Norte 3.50 2.40 2 8.40 8.40"
                        val parsed = parseTableRow(text)
                        if (parsed != null) {
                            if (currentBlock == null) {
                                currentBlock = ConstructionBlock(name = "Importado de PDF")
                            }
                            segments.add(parsed)
                            continue
                        }

                        // Pattern E: Group header detection for MEASUREMENTS_REPORT
                        // Uppercase text lines like "PARED NORTE" (block group names)
                        val groupBlock = tryParseGroupHeader(text)
                        if (groupBlock != null) {
                            if (currentBlock != null && segments.isNotEmpty()) {
                                blocks.add(currentBlock.copy(segments = segments.toList()))
                                segments.clear()
                            }
                            currentBlock = groupBlock
                            continue
                        }

                        // Pattern F: Legacy table row with unit markers (m², ft², etc.)
                        val legacyParsed = parseLegacyTableRow(text)
                        if (legacyParsed != null) {
                            if (currentBlock == null) {
                                currentBlock = ConstructionBlock(name = "Importado de PDF")
                            }
                            segments.add(legacyParsed)
                            continue
                        }

                        // Pattern G: Fallback - detect lines with exactly 2-3 decimal numbers (dimensions)
                        // E.g., "Pared Norte 3.50 2.40" or "3.50 2.40 2"
                        val fallbackParsed = parseFallbackDimensionLine(text)
                        if (fallbackParsed != null) {
                            if (currentBlock == null) {
                                currentBlock = ConstructionBlock(name = "Importado de PDF")
                            }
                            segments.add(fallbackParsed)
                            continue
                        }
                    }
                } finally {
                    page.close()
                }
            }

            // Add last block
            currentBlock?.let {
                if (segments.isNotEmpty()) {
                    blocks.add(it.copy(segments = segments.toList()))
                }
            }

            recognizer.close()
            renderer.close()
            return blocks
        } finally {
            pfd.close()
        }
    }

    private fun tryExtractHiddenJson(fullText: String): List<ConstructionBlock>? {
        val markers = listOf("---DRYWALL_IMPORT_START---", "DRYWALL_JSON_DATA:")
        val hasMarker = markers.any { fullText.contains(it, ignoreCase = true) }
        if (!hasMarker) return null

        return try {
            val jsonPart = when {
                fullText.contains("---DRYWALL_IMPORT_START---", ignoreCase = true) -> {
                    val start = fullText.substringAfter("---DRYWALL_IMPORT_START---", fullText.substringAfter("---DRYWALL_IMPORT_START---"))
                    val end = if (start.contains("---DRYWALL_IMPORT_END---")) {
                        start.substringBefore("---DRYWALL_IMPORT_END---")
                    } else {
                        start
                    }
                    end.replace("\n", "").replace("\r", "").replace(" ", "").trim()
                }
                else -> {
                    val raw = fullText.substringAfter("DRYWALL_JSON_DATA:").trim()
                    val lastBracket = raw.lastIndexOf("]")
                    if (lastBracket != -1) raw.substring(0, lastBracket + 1) else raw
                }
            }

            val result = tryParseJsonBlocks(jsonPart)
            if (result != null) return result

            // OCR may have split the JSON across lines incorrectly; try rebuilding from full text
            val arrayStart = fullText.indexOf("[")
            val arrayEnd = fullText.lastIndexOf("]")
            if (arrayStart != -1 && arrayEnd > arrayStart) {
                val rebuilt = fullText.substring(arrayStart, arrayEnd + 1)
                    .replace("\n", "").replace("\r", "").replace(" ", "").trim()
                tryParseJsonBlocks(rebuilt)
            } else null
        } catch (e: Exception) {
            android.util.Log.e("PdfImporter", "Error extracting hidden JSON", e)
            null
        }
    }

    /**
     * Parses a MEASUREMENTS_REPORT table row:
     * "1 Pared Norte 3.50 2.40 2 8.40 8.40"
     * Format: COUNT NAME L W R AREA TOTAL
     */
    private fun parseTableRow(text: String): ConstructionSegment? {
        val parts = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.size < 4) return null

        val firstNum = parts[0].toIntOrNull() ?: return null

        var lastTextIdx = 0
        for (idx in 1 until parts.size) {
            val cleaned = parts[idx].replace(",", ".")
            val isDash = parts[idx] == "-"
            val isNum = cleaned.toDoubleOrNull() != null
            if (!isNum && !isDash) {
                lastTextIdx = idx
            }
        }

        if (lastTextIdx == 0) return null

        val name = parts.subList(1, lastTextIdx + 1).joinToString(" ").trim()
        if (name.isBlank()) return null

        val numbers = parts.subList(lastTextIdx + 1, parts.size).map { token ->
            if (token == "-") 0.0 else token.replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        if (numbers.isEmpty()) return null

        return when {
            numbers.size >= 5 -> {
                ConstructionSegment(
                    name = name,
                    length = numbers[0],
                    width = numbers[1],
                    repetitions = numbers[2].toInt().coerceAtLeast(1)
                )
            }
            numbers.size >= 3 -> {
                ConstructionSegment(
                    name = name,
                    length = numbers[0],
                    width = numbers[1],
                    repetitions = numbers[2].toInt().coerceAtLeast(1)
                )
            }
            numbers.size == 2 -> {
                ConstructionSegment(
                    name = name,
                    length = numbers[0],
                    width = 1.0,
                    repetitions = numbers[1].toInt().coerceAtLeast(1)
                )
            }
            numbers.size == 1 -> {
                val area = numbers[0]
                if (area > 0) {
                    ConstructionSegment(name = name, length = area, width = 1.0, repetitions = 1)
                } else null
            }
            else -> null
        }
    }

    /**
     * Detects group/block header lines in MEASUREMENTS_REPORT format.
     * These are uppercase text lines like "PARED NORTE" that are not table headers.
     */
    private fun tryParseGroupHeader(text: String): ConstructionBlock? {
        val upper = text.uppercase().trim()
        if (upper.length < 2 || upper.length > 80) return null

        val hasLetters = upper.any { it.isLetter() }
        if (!hasLetters) return null

        if (isHeaderOrFooterLine(text)) return null

        val mostlyLetters = upper.count { it.isLetter() || it == ' ' || it == ':' || it == '#' || it == '-' } > upper.length * 0.7
        if (!mostlyLetters) return null

        return ConstructionBlock(name = text.trim())
    }

    private fun parseLegacyTableRow(text: String): ConstructionSegment? {
        val parts = text.split(" ")
        val unitIdx = parts.indexOfFirst {
            it.equals("m²", true) || it.equals("ft²", true) ||
                it.equals("m2", true) || it.equals("ft2", true) ||
                it.equals("unidad", true) || it.equals("kg", true)
        }

        if (unitIdx == -1 || parts.size <= unitIdx + 2) return null

        val qtyPerM2 = parts[unitIdx + 1].replace(",", ".").toDoubleOrNull() ?: 0.0
        val totalQty = parts[unitIdx + 2].replace(",", ".").toDoubleOrNull() ?: 0.0

        if (qtyPerM2 <= 0 || totalQty <= 0) return null

        val repetitions = (totalQty / qtyPerM2 + 0.1).toInt().coerceAtLeast(1)
        val startIdx = if (parts[0].toIntOrNull() != null) 1 else 0
        val name = if (unitIdx > startIdx) {
            parts.subList(startIdx, unitIdx).joinToString(" ")
        } else "Segmento"

        if (name.isBlank()) return null
        if (isHeaderOrFooterLine(name)) return null

        return ConstructionSegment(
            name = name,
            length = qtyPerM2,
            width = 1.0,
            repetitions = repetitions
        )
    }

    /**
     * Fallback parser for lines with dimension-like numbers.
     * E.g., "Pared Norte 3.50 2.40" or "3.50 2.40 2"
     */
    private fun parseFallbackDimensionLine(text: String): ConstructionSegment? {
        val parts = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.size < 2 || parts.size > 6) return null

        val numbers = mutableListOf<Double>()
        val textParts = mutableListOf<String>()

        for (part in parts) {
            val cleaned = part.replace(",", ".")
            val num = cleaned.toDoubleOrNull()
            if (num != null && num > 0 && num < 1000) {
                numbers.add(num)
            } else {
                textParts.add(part)
            }
        }

        if (numbers.size !in 2..3) return null

        val name = if (textParts.isNotEmpty()) textParts.joinToString(" ") else "Segmento"
        if (isHeaderOrFooterLine(name)) return null

        return when (numbers.size) {
            2 -> ConstructionSegment(
                name = name,
                length = numbers[0],
                width = numbers[1],
                repetitions = 1
            )
            3 -> ConstructionSegment(
                name = name,
                length = numbers[0],
                width = numbers[1],
                repetitions = numbers[2].toInt().coerceAtLeast(1)
            )
            else -> null
        }
    }
}
