package com.drywall.common.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ScrapedRate(
    val currencyCode: String,
    val rate: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScrapeResult(
    val rates: Map<String, Double>,
    val timestamp: String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
    val success: Boolean,
    val errorMessage: String? = null
)

object CurrencyScraper {

    private const val ELTOQUE_URL = "https://eltoque.com/tasas-de-cambio-cuba"
    private const val SOLUCIONES_URL = "https://solucionescuba.com/"
    private const val TASAS_API_URL = "https://solucionescuba.com/tasas.php"
    private const val DIRECTORIO_CUBANO_URL = "https://www.directoriocubano.info/cadeca/"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    private const val CONNECT_TIMEOUT = 20000L
    private const val READ_TIMEOUT = 20000L
    private const val MAX_ATTEMPTS = 2

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun fetchRates(): ScrapeResult = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        var bestResult = emptyMap<String, Double>()

        repeat(MAX_ATTEMPTS) { attempt ->
            // Source 1: ElToque (primary - most reliable)
            try {
                val rates = fetchFromEltoque()
                if (rates.size > bestResult.size) bestResult = rates
                if (rates.size >= 7) {
                    return@withContext ScrapeResult(rates = rates, success = true)
                }
            } catch (e: Exception) {
                lastError = e
            }

            // Source 2: SolucionesCuba API (JSON)
            try {
                val rates = fetchFromTasasApi()
                if (rates.size > bestResult.size) bestResult = rates
                if (rates.size >= 7) {
                    return@withContext ScrapeResult(rates = rates, success = true)
                }
            } catch (e: Exception) {
                lastError = e
            }

            // Source 3: Directorio Cubano
            try {
                val rates = fetchFromDirectorioCubano()
                if (rates.size > bestResult.size) bestResult = rates
                if (rates.size >= 7) {
                    return@withContext ScrapeResult(rates = rates, success = true)
                }
            } catch (e: Exception) {
                lastError = e
            }

            if (bestResult.isNotEmpty()) {
                return@withContext ScrapeResult(rates = bestResult, success = true)
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                Thread.sleep(2000)
            }
        }

        if (bestResult.isNotEmpty()) {
            return@withContext ScrapeResult(rates = bestResult, success = true)
        }

        ScrapeResult(
            rates = emptyMap(),
            success = false,
            errorMessage = translateError(lastError)
        )
    }

    private fun translateError(e: Exception?): String {
        if (e == null) return "Error desconocido al obtener tasas"
        val message = e.message ?: ""
        return when {
            message.contains("Status:", ignoreCase = true) -> "Servidor retornó error: ${message.substringAfter("Status:")}"
            message.contains("Connection reset", ignoreCase = true) -> "Conexión restablecida"
            message.contains("timeout", ignoreCase = true) -> "Tiempo de espera agotado"
            message.contains("Unable to resolve host", ignoreCase = true) -> "Sin conexión a internet"
            message.contains("Cloudflare", ignoreCase = true) -> "Bloqueado por Cloudflare"
            else -> "Error de red: ${e.localizedMessage}"
        }
    }

    private fun fetchWithClient(url: String, accept: String = "text/html"): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("Referer", "https://www.google.com/")
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Status: ${response.code} ${response.message}")
            }
            val body = response.body.string()
            if (body.isBlank()) throw Exception("Respuesta del servidor vacía")
            body
        }
    }

    private fun isValidRate(currency: String, rate: Double): Boolean {
        if (rate <= 0) return false
        val minRates = mapOf(
            "USD" to 100.0, "EUR" to 100.0, "MLC" to 50.0,
            "CAD" to 50.0, "MEX" to 5.0, "ZELLE" to 100.0, "CLA" to 50.0
        )
        return rate >= (minRates[currency] ?: 1.0)
    }

    // ============================================================
    // SOURCE 1: ElToque (PRIMARY)
    // ============================================================
    private fun fetchFromEltoque(): Map<String, Double> {
        val html = fetchWithClient(ELTOQUE_URL)
        if (html.contains("cloudflare", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true)) {
            throw Exception("Bloqueo de Cloudflare en ElToque")
        }
        val doc = Jsoup.parse(html)
        val result = mutableMapOf<String, Double>()

        // Strategy 1: Parse structured currency cards/boxes
        parseEltoqueStructuredElements(doc, result)
        if (result.size >= 5) return result

        // Strategy 2: Parse tables with informal market data
        parseEltoqueTables(doc, result)
        if (result.size >= 5) return result

        // Strategy 3: Parse full page text with regex
        parseEltoquePlainText(doc, result)
        return result
    }

    private fun parseEltoqueStructuredElements(doc: Document, result: MutableMap<String, Double>) {
        // Try various selectors that ElToque might use
        val selectors = listOf(
            "div[class*='currency']",
            "div[class*='rate']",
            "div[class*='exchange']",
            "div[class*='tasa']",
            "div[class*='price']",
            "div[class*='card']",
            "div[class*='item']",
            "div[class*='row']",
            "article",
            "section"
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            for (element in elements) {
                extractRatesFromText(element.text(), result)
                if (result.size >= 5) return
            }
        }
    }

    private fun parseEltoqueTables(doc: Document, result: MutableMap<String, Double>) {
        val tables = doc.select("table")
        for (table in tables) {
            val rows = table.select("tr")
            for (row in rows) {
                extractRatesFromText(row.text(), result)
            }
            if (result.size >= 5) return
        }
    }

    private fun parseEltoquePlainText(doc: Document, result: MutableMap<String, Double>) {
        val allText = doc.text()
        extractRatesFromText(allText, result)
    }

    // ============================================================
    // SOURCE 2: SolucionesCuba API
    // ============================================================
    private fun fetchFromTasasApi(): Map<String, Double> {
        val json = fetchWithClient(TASAS_API_URL, "application/json")
        val result = mutableMapOf<String, Double>()

        val apiToCode = mapOf(
            "USD" to "USD",
            "ECU" to "EUR",
            "MLC" to "MLC"
        )

        for ((apiKey, currencyCode) in apiToCode) {
            val rate = extractJsonDouble(json, apiKey)
            if (rate != null && isValidRate(currencyCode, rate)) {
                result[currencyCode] = rate
            }
        }
        return result
    }

    // ============================================================
    // SOURCE 3: Directorio Cubano
    // ============================================================
    private fun fetchFromDirectorioCubano(): Map<String, Double> {
        val html = fetchWithClient(DIRECTORIO_CUBANO_URL)
        if (html.contains("cloudflare", ignoreCase = true) ||
            html.contains("Checking your browser", ignoreCase = true)) {
            throw Exception("Bloqueo de Cloudflare en Directorio Cubano")
        }
        val doc = Jsoup.parse(html)
        val result = mutableMapOf<String, Double>()

        val tables = doc.select("table")
        for (table in tables) {
            val rows = table.select("tr")
            if (rows.size < 3) continue

            val headerRow = rows.first() ?: continue
            val headerCells = headerRow.select("th, td")
            var cupColIndex = -1
            for ((idx, cell) in headerCells.withIndex()) {
                val cellText = cell.text().trim().uppercase(Locale.ROOT)
                if (cellText == "CUP") {
                    cupColIndex = idx
                    break
                }
            }
            if (cupColIndex < 0) continue

            val currencyMap = mapOf(
                "USD" to "USD", "DÓLAR" to "USD", "DOLAR" to "USD",
                "EUR" to "EUR", "EURO" to "EUR",
                "MLC" to "MLC", "CAD" to "CAD",
                "MXN" to "MEX", "MEXICANO" to "MEX",
                "ZELLE" to "ZELLE", "CLA" to "CLA", "CHAMBA" to "CLA"
            )

            for (i in 1 until rows.size) {
                val row = rows[i]
                val cells = row.select("td, th")
                if (cells.size <= cupColIndex) continue

                val labelCell = cells[0].text().trim().uppercase(Locale.ROOT)
                val rateCell = cells[cupColIndex].text().trim()

                val code = currencyMap[labelCell]
                if (code != null) {
                    val rateStr = rateCell.replace(",", "").replace("CUP", "").trim()
                    val rate = rateStr.toDoubleOrNull() ?: continue
                    if (isValidRate(code, rate)) {
                        result[code] = rate
                    }
                }
            }
        }
        return result
    }

    // ============================================================
    // SHARED PARSING UTILITIES
    // ============================================================

    private fun extractRatesFromText(text: String, result: MutableMap<String, Double>) {
        if (text.isBlank()) return

        data class CurrencyPattern(val code: String, val patterns: List<Regex>)

        val currencyPatterns = listOf(
            CurrencyPattern("USD", listOf(
                Regex("""1\s*USD\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""USD\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""1\s*D[ÓO]LAR(?:ES)?\s*[=\s:]*\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                Regex("""D[ÓO]LAR(?:ES)?\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("EUR", listOf(
                Regex("""1\s*EUR\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""EUR\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""1\s*EURO(?:S)?\s*[=\s:]*\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                Regex("""EURO(?:S)?\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("MLC", listOf(
                Regex("""1\s*MLC\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""MLC\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""MLC\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("CAD", listOf(
                Regex("""1\s*CAD\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""CAD\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""1\s*D[ÓO]LAR(?:ES)?\s*CANADIENSES?\s*[=\s:]*\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                Regex("""CAD\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("MEX", listOf(
                Regex("""1\s*MXN\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""MXN\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""1\s*PESO(?:S)?\s*MEXICANO(?:S)?\s*[=\s:]*\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                Regex("""MXN\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("ZELLE", listOf(
                Regex("""1\s*USD\s*\(?\s*Zelle\s*\)?\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""Zelle\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""Zelle\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            )),
            CurrencyPattern("CLA", listOf(
                Regex("""1\s*CLA\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""CLA\s*[=\s:]*\s*([\d.,]+)\s*CUP""", RegexOption.IGNORE_CASE),
                Regex("""Chamba\s*[=\s:]*\s*([\d.,]+)""", RegexOption.IGNORE_CASE),
                Regex("""CLA\s*[=\s:]+([\d.,]+)""", RegexOption.IGNORE_CASE)
            ))
        )

        for (cp in currencyPatterns) {
            if (result.containsKey(cp.code)) continue
            for (pattern in cp.patterns) {
                val match = pattern.find(text)
                if (match != null) {
                    val rateStr = match.groupValues[1].replace(",", "").trim()
                    val rate = rateStr.toDoubleOrNull()
                    if (rate != null && isValidRate(cp.code, rate)) {
                        result[cp.code] = rate
                        break
                    }
                }
            }
        }
    }

    private fun extractJsonDouble(json: String, key: String): Double? {
        val regex = Regex(""""$key"\s*:\s*(\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
