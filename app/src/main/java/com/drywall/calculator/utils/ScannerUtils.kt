package com.drywall.calculator.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Utility class for processing images and extracting text and barcodes using Google ML Kit.
 * Handles ID cards (front and back) and Bank Cards for automatic data entry.
 */
object ScannerUtils {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_PDF417)
            .build(),
    )

    data class IdCardData(
        val ni: String? = null,
        val name: String? = null,
        val surnames: String? = null,
        val country: String? = null,
        val father: String? = null,
        val mother: String? = null,
        val sex: String? = null,
        val birthDate: String? = null,
        val expiryDate: String? = null,
        val emissionDate: String? = null,
        val municipality: String? = null,
        val province: String? = null,
        val civilRegistry: String? = null,
        val tomo: String? = null,
        val folio: String? = null,
        val year: String? = null,
        val address: String? = null,
        val qrData: String? = null,
        val barcodeData: String? = null,
        val rawText: String? = null,
        val imageUri: String? = null,
        val profileImageUri: String? = null,
        val signatureImageUri: String? = null,
        val barcodeImageUri: String? = null,
        val qrCodeImageUri: String? = null,
    )

    suspend fun processFront(bitmap: Bitmap): IdCardData {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = textRecognizer.process(image).await()
        val text = result.text
        
        return IdCardData(
            rawText = text,
            ni = findNi(text),
            name = findName(text),
            surnames = findSurnames(text),
            country = if (text.contains("CUBA", ignoreCase = true)) "Cuba" else null,
            sex = findSex(text),
            birthDate = findBirthDate(text),
            expiryDate = findExpiryDate(text),
            emissionDate = findEmissionDate(text),
            father = findFather(text),
            mother = findMother(text),
            tomo = findTomo(text),
            folio = findFolio(text),
            year = findYear(text),
        )
    }

    suspend fun processBack(bitmap: Bitmap): IdCardData {
        val image = InputImage.fromBitmap(bitmap, 0)
        val barcodes = barcodeScanner.process(image).await()
        val qr = barcodes.find { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
        val barcode = barcodes.find { it.format != Barcode.FORMAT_QR_CODE }?.rawValue
        
        val textResult = textRecognizer.process(image).await()
        val text = textResult.text

        return IdCardData(
            qrData = qr,
            barcodeData = barcode ?: barcodes.firstOrNull { it.format != Barcode.FORMAT_QR_CODE }?.rawValue,
            address = findResidence(text),
            civilRegistry = findRegistry(text),
            municipality = findMunicipality(text),
            province = findProvince(text),
            rawText = text,
        )
    }

    private fun findNi(text: String): String? {
        val regex = Regex("\\b\\d{11}\\b")
        return regex.find(text)?.value
    }

    private fun findName(text: String): String? {
        val lines = text.split("\n")
        val index = lines.indexOfFirst { it.contains("NOMBRE", ignoreCase = true) || it.contains("FIRST NAME", ignoreCase = true) }
        if (index != -1 && index + 1 < lines.size) {
            val nameLine = lines[index + 1].trim()
            if (nameLine.isNotEmpty()) return fixAccents(nameLine)
        }
        val possible = lines.find { it.length > 2 && it.all { c -> c.isLetter() || c.isWhitespace() } && it == it.uppercase() && !it.contains("CUBA") && !it.contains("NOMBRE") }
        return possible?.let { fixAccents(it) }
    }

    private fun findSurnames(text: String): String? {
        val lines = text.split("\n")
        val index = lines.indexOfFirst { it.contains("APELLIDOS", ignoreCase = true) || it.contains("LAST NAME", ignoreCase = true) }
        if (index != -1 && index + 1 < lines.size) {
            val surnameLine = lines[index + 1].trim()
            if (surnameLine.isNotEmpty()) return fixAccents(surnameLine)
        }
        val possible = lines.filter { it.length > 2 && it.all { c -> c.isLetter() || c.isWhitespace() } && it == it.uppercase() && !it.contains("APELLIDOS") }
            .getOrNull(1)
        return possible?.let { fixAccents(it) }
    }

    private fun fixAccents(text: String): String {
        return text.uppercase()
            .replace("A'", "Á")
            .replace("E'", "É")
            .replace("I'", "Í")
            .replace("O'", "Ó")
            .replace("U'", "Ú")
            .replace("N'", "Ñ")
            .replace("  ", " ")
            .trim()
    }

    private fun findSex(text: String): String? {
        if (text.contains(" F ", ignoreCase = false) || text.endsWith(" F")) return "F"
        if (text.contains(" M ", ignoreCase = false) || text.endsWith(" M")) return "M"
        return null
    }

    private fun findBirthDate(text: String): String? {
        val ni = findNi(text)
        if (ni != null && ni.length == 11) {
            val yy = ni.substring(0, 2)
            val mm = ni.substring(2, 4)
            val dd = ni.substring(4, 6)
            val century = when(ni.substring(6, 7).toInt()) {
                in 6..8 -> "20"
                else -> "19"
            }
            return "$dd/$mm/$century$yy"
        }
        return null
    }

    private fun findExpiryDate(text: String): String? {
        val regex = Regex("(Vence|Vencimiento|EXP|VENC)\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(2)
    }

    private fun findEmissionDate(text: String): String? {
        val regex = Regex("(Emisión|FECHA|EMISION)\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(2)
    }

    private fun findResidence(text: String): String? {
        val regex = Regex("(RESIDENCIA|DOMICILIO|CALLE)\\s*:?\\s*([\\s\\S]+?)(?=PADRE|MADRE|TOMO|FOLIO|REGISTRO|MUNICIPIO|\\n\\s*\\n|$)", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        var res = match?.groupValues?.get(2)?.trim()?.replace("\n", " ")?.replace("\\s+".toRegex(), " ")
        if (match?.groupValues?.get(1)?.uppercase() == "CALLE" && res != null) {
            res = "CALLE $res"
        }
        return res
    }

    private fun findMunicipality(text: String): String? {
        val regex = Regex("MUNICIPIO\\s*:?\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun findProvince(text: String): String? {
        val regex = Regex("PROVINCIA\\s*:?\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun findFather(text: String): String? {
        val regex = Regex("PADRE\\s*:?\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun findMother(text: String): String? {
        val regex = Regex("MADRE\\s*:?\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    private fun findTomo(text: String): String? {
        val regex = Regex("Tomo\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)
    }

    private fun findFolio(text: String): String? {
        val regex = Regex("(Folio|Folie)\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(2)
    }

    private fun findYear(text: String): String? {
        val regex = Regex("(Año|Añe|Anio|Year)\\s*:?\\s*(\\d{2,4})", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(2)
    }

    private fun findRegistry(text: String): String? {
        val regex = Regex("(Registro\\s*Civil|REGISTRO)\\s*:?\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(2)?.trim()
    }

    suspend fun processBankCard(bitmap: Bitmap): IdCardData {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = textRecognizer.process(image).await()
        val text = result.text
        return IdCardData(
            rawText = text,
            ni = findCardNumber(text),
            name = findBankName(text),
            country = findCurrency(text) ?: if (text.contains("CUBA", ignoreCase = true)) "CUP" else null
        )
    }

    private fun findCardNumber(text: String): String? {
        val regex = Regex("\\b(?:\\d{4}[ -]?){3}\\d{4}\\b")
        return regex.find(text)?.value?.replace("[ -]".toRegex(), "")
    }

    private fun findBankName(text: String): String? {
        val banks = listOf(
            "BANDEC", "BANMET", "BPA", "BANCO METROPOLITANO", 
            "BANCO DE CREDITO Y COMERCIO", "BANCO POPULAR DE AHORRO",
            "BANCO EXTERIOR", "VISA", "MASTERCARD"
        )
        for (bank in banks) {
            if (text.contains(bank, ignoreCase = true)) {
                return when(bank.uppercase()) {
                    "BANCO DE CREDITO Y COMERCIO" -> "BANDEC"
                    "BANCO POPULAR DE AHORRO" -> "BPA"
                    "BANCO METROPOLITANO" -> "BANMET"
                    else -> bank
                }
            }
        }
        return null
    }

    private fun findCurrency(text: String): String? {
        val currencies = listOf("CUP", "USD", "MLC", "EUR")
        for (curr in currencies) {
            if (text.contains(curr, ignoreCase = true)) return curr
        }
        if (text.contains("PESO", ignoreCase = true)) return "CUP"
        if (text.contains("DOLLAR", ignoreCase = true)) return "USD"
        return null
    }
}
