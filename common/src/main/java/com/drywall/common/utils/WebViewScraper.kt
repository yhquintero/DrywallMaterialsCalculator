package com.drywall.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

class WebViewScraper(private val context: Context) {

    companion object {
        private const val ELTOQUE_URL = "https://eltoque.com/tasas-de-cambio-cuba"
        private const val DIRECTORIO_CUBANO_URL = "https://www.directoriocubano.info/cadeca/"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val JS_EXTRACT_ELTOQUE = """
        (function() {
            var result = {};
            var text = document.body ? document.body.textContent : '';

            if (text.indexOf('Checking your browser') !== -1 ||
                text.indexOf('Just a moment') !== -1 ||
                text.indexOf('Enable JavaScript') !== -1 ||
                text.length < 100) {
                return null;
            }

            var currencyPatterns = [
                {code: 'USD', patterns: [
                    /1\s*USD\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /USD\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /1\s*D[OÓ]LAR(?:ES)?\s*[=:\s]*\s*([\d,.]+)/i,
                    /D[OÓ]LAR(?:ES)?\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'EUR', patterns: [
                    /1\s*EUR\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /EUR\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /1\s*EURO(?:S)?\s*[=:\s]*\s*([\d,.]+)/i,
                    /EURO(?:S)?\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'MLC', patterns: [
                    /1\s*MLC\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /MLC\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /MLC\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'CAD', patterns: [
                    /1\s*CAD\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /CAD\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /1\s*D[OÓ]LAR(?:ES)?\s*CANADIENSES?\s*[=:\s]*\s*([\d,.]+)/i,
                    /CAD\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'MXN', patterns: [
                    /1\s*MXN\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /MXN\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /1\s*PESO(?:S)?\s*MEXICANO(?:S)?\s*[=:\s]*\s*([\d,.]+)/i,
                    /MXN\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'ZELLE', patterns: [
                    /1\s*USD\s*\(?\s*Zelle\s*\)?\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /Zelle\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /Zelle\s*[=:]+\s*([\d,.]+)/i
                ]},
                {code: 'CLA', patterns: [
                    /1\s*CLA\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /CLA\s*[=:\s]*\s*([\d,.]+)\s*CUP/i,
                    /Chamba\s*[=:\s]*\s*([\d,.]+)/i,
                    /CLA\s*[=:]+\s*([\d,.]+)/i
                ]}
            ];

            var minRates = {USD: 100, EUR: 100, MLC: 50, CAD: 50, MXN: 5, ZELLE: 100, CLA: 50};

            currencyPatterns.forEach(function(cp) {
                for (var i = 0; i < cp.patterns.length; i++) {
                    var m = text.match(cp.patterns[i]);
                    if (m) {
                        var v = parseFloat(m[1].replace(/,/g, ''));
                        if (!isNaN(v) && v >= (minRates[cp.code] || 1)) {
                            var key = cp.code === 'MXN' ? 'MEX' : cp.code;
                            result[key] = v;
                            break;
                        }
                    }
                }
            });

            return Object.keys(result).length > 0 ? JSON.stringify(result) : null;
        })();
    """.trimIndent()

    private val JS_EXTRACT_DIRECTORIO = """
        (function() {
            var result = {};
            var text = document.body ? document.body.textContent : '';

            if (text.indexOf('Checking your browser') !== -1 ||
                text.indexOf('Just a moment') !== -1 ||
                text.length < 100) {
                return null;
            }

            var minRates = {USD: 100, EUR: 100, MLC: 50, CAD: 50, MXN: 5, ZELLE: 100, CLA: 50};
            var currencyMap = {
                'USD': 'USD', 'DÓLAR': 'USD', 'DOLAR': 'USD',
                'EUR': 'EUR', 'EURO': 'EUR',
                'MLC': 'MLC', 'CAD': 'CAD',
                'MXN': 'MEX', 'ZELLE': 'ZELLE', 'CLA': 'CLA'
            };

            var tables = document.querySelectorAll('table');
            tables.forEach(function(table) {
                var headerText = table.textContent || '';
                if (headerText.indexOf('CUP') === -1) return;

                var rows = table.querySelectorAll('tr');
                if (rows.length < 3) return;

                var headerCells = rows[0].querySelectorAll('th, td');
                var cupCol = -1;
                for (var h = 0; h < headerCells.length; h++) {
                    if (headerCells[h].textContent.trim().toUpperCase() === 'CUP') {
                        cupCol = h;
                        break;
                    }
                }
                if (cupCol < 0) return;

                for (var r = 1; r < rows.length; r++) {
                    var cells = rows[r].querySelectorAll('td');
                    if (cells.length <= cupCol) continue;

                    var label = cells[0].textContent.trim().toUpperCase();
                    var rateText = cells[cupCol].textContent.trim().replace(/,/g, '').replace(/CUP/g, '').trim();
                    var rate = parseFloat(rateText);

                    var code = currencyMap[label];
                    if (code && !isNaN(rate) && rate >= (minRates[code] || 1)) {
                        var key = code === 'MXN' ? 'MEX' : code;
                        result[key] = rate;
                    }
                }
            });

            return Object.keys(result).length > 0 ? JSON.stringify(result) : null;
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchRates(): ScrapeResult = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ScrapeResult>()

        val allRates = mutableMapOf<String, Double>()
        var completedLoads = 0
        val totalUrls = 2

        fun tryComplete() {
            completedLoads++
            if (completedLoads >= totalUrls && !deferred.isCompleted) {
                if (allRates.isNotEmpty()) {
                    deferred.complete(ScrapeResult(rates = allRates.toMap(), success = true))
                } else {
                    deferred.complete(ScrapeResult(
                        rates = emptyMap(), success = false,
                        errorMessage = "No se encontraron tasas en las páginas"
                    ))
                }
            }
        }

        fun extractRatesFromView(view: WebView, jsCode: String) {
            view.evaluateJavascript(jsCode) { json ->
                if (json != null && json != "null" && json.length > 2) {
                    try {
                        val raw = JSONObject(json)
                        val keys = raw.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = raw.getDouble(key)
                            if (!allRates.containsKey(key)) {
                                allRates[key] = value
                            }
                        }
                    } catch (_: Exception) {}
                }
                tryComplete()
            }
        }

        fun createWebView(): WebView {
            return WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/125.0.6422.165 Mobile Safari/537.36"
                }
            }
        }

        val webView1 = createWebView()
        webView1.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                extractRatesFromView(view, JS_EXTRACT_ELTOQUE)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { tryComplete() }
            }
        }
        webView1.loadUrl(ELTOQUE_URL)

        val webView2 = createWebView()
        webView2.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                extractRatesFromView(view, JS_EXTRACT_DIRECTORIO)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { tryComplete() }
            }
        }
        webView2.loadUrl(DIRECTORIO_CUBANO_URL)

        val result = withTimeoutOrNull(TIMEOUT_SECONDS.seconds) {
            deferred.await()
        } ?: ScrapeResult(
            rates = allRates.toMap().ifEmpty { emptyMap() },
            success = allRates.isNotEmpty(),
            errorMessage = if (allRates.isEmpty()) "Tiempo de espera agotado ($TIMEOUT_SECONDS s)" else null
        )

        try {
            webView1.stopLoading()
            webView1.destroy()
        } catch (_: Exception) {}
        try {
            webView2.stopLoading()
            webView2.destroy()
        } catch (_: Exception) {}

        result
    }

    private suspend fun <T> withTimeoutOrNull(
        timeout: kotlin.time.Duration,
        block: suspend kotlinx.coroutines.CoroutineScope.() -> T
    ): T? = kotlinx.coroutines.withTimeoutOrNull(timeout.inWholeMilliseconds, block)
}
