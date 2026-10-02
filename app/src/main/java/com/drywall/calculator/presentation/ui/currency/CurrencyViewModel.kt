package com.drywall.calculator.presentation.ui.currency

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.calculator.data.local.entity.CurrencyHistory
import com.drywall.calculator.data.repository.CurrencyRepository
import com.drywall.common.utils.CurrencyScraper
import com.drywall.common.utils.ScrapeResult
import com.drywall.common.utils.WebViewScraper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CurrencyViewModel @Inject constructor(
    private val repository: CurrencyRepository,
    private val application: Application
) : ViewModel() {

    private val _isScraping = MutableStateFlow(false)
    val isScraping = _isScraping.asStateFlow()

    private val _lastScrapedTimestamp = MutableStateFlow("")
    val lastScrapedTimestamp = _lastScrapedTimestamp.asStateFlow()

    private val _lastScrapeError = MutableStateFlow<String?>(null)
    val lastScrapeError = _lastScrapeError.asStateFlow()

    val history: StateFlow<List<CurrencyHistory>> = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestRate: StateFlow<CurrencyHistory?> = repository.getLatestRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateRates(
        usd: Double, 
        eur: Double, 
        mlc: Double, 
        cup: Double,
        cad: Double = 0.0,
        mex: Double = 0.0,
        zelle: Double = 0.0,
        cla: Double = 0.0
    ) {
        viewModelScope.launch {
            val last = latestRate.value
            
            val hasChanged = last == null || 
                last.usdRate != usd || last.eurRate != eur || last.mlcRate != mlc ||
                last.cadRate != cad || last.mexRate != mex || last.zelleRate != zelle || last.claRate != cla

            if (hasChanged) {
                val newEntry = CurrencyHistory.fromNow(usd, eur, mlc, cup, cad, mex, zelle, cla)
                repository.insert(newEntry)
            }
        }
    }

    private val _scrapeSuccess = MutableSharedFlow<Unit>()
    val scrapeSuccess = _scrapeSuccess.asSharedFlow()

    private fun isValidScrapedRate(currency: String, rate: Double?): Boolean {
        if (rate == null || rate <= 0) return false
        val minRates = mapOf(
            "USD" to 100.0, "EUR" to 100.0, "MLC" to 50.0,
            "CAD" to 50.0, "MEX" to 5.0, "ZELLE" to 100.0, "CLA" to 50.0
        )
        return rate >= (minRates[currency] ?: 1.0)
    }

    private fun applyRates(result: ScrapeResult, timestamp: String) {
        val usd = if (isValidScrapedRate("USD", result.rates["USD"])) result.rates["USD"]!! else latestRate.value?.usdRate ?: 0.0
        val eur = if (isValidScrapedRate("EUR", result.rates["EUR"])) result.rates["EUR"]!! else latestRate.value?.eurRate ?: 0.0
        val mlc = if (isValidScrapedRate("MLC", result.rates["MLC"])) result.rates["MLC"]!! else latestRate.value?.mlcRate ?: 0.0
        val cad = if (isValidScrapedRate("CAD", result.rates["CAD"])) result.rates["CAD"]!! else latestRate.value?.cadRate ?: 0.0
        val mex = if (isValidScrapedRate("MEX", result.rates["MEX"])) result.rates["MEX"]!! else latestRate.value?.mexRate ?: 0.0
        val zelle = if (isValidScrapedRate("ZELLE", result.rates["ZELLE"])) result.rates["ZELLE"]!! else latestRate.value?.zelleRate ?: 0.0
        val cla = if (isValidScrapedRate("CLA", result.rates["CLA"])) result.rates["CLA"]!! else latestRate.value?.claRate ?: 0.0

        updateRates(usd, eur, mlc, 1.0, cad, mex, zelle, cla)
        _lastScrapedTimestamp.value = timestamp
        _scrapeSuccess.tryEmit(Unit)
    }

    fun fetchRates() {
        if (_isScraping.value) return
        viewModelScope.launch {
            _isScraping.value = true
            _lastScrapeError.value = null
            try {
                // 1. Try OkHttp-based scraping first (fast)
                var result = withContext(Dispatchers.IO) {
                    CurrencyScraper.fetchRates()
                }

                // 2. If incomplete (<5 rates), try WebView (handles Cloudflare)
                if (!result.success || result.rates.size < 5) {
                    Log.d("CurrencyViewModel", "Scraping parcial (${result.rates.size} rates), intentando WebView...")
                    val wvResult = WebViewScraper(application).fetchRates()
                    if (wvResult.success && wvResult.rates.isNotEmpty()) {
                        // Merge: WebView as base, complement with existing data
                        val merged = wvResult.rates.toMutableMap()
                        result.rates.forEach { (k, v) ->
                            if (!merged.containsKey(k) && isValidScrapedRate(k, v)) {
                                merged[k] = v
                            }
                        }
                        result = ScrapeResult(rates = merged, success = true, timestamp = wvResult.timestamp)
                    }
                }

                if (result.success && result.rates.isNotEmpty()) {
                    applyRates(result, result.timestamp)
                } else {
                    _lastScrapeError.value = result.errorMessage ?: "No se pudieron obtener las tasas"
                    Log.w("CurrencyViewModel", "Scraping failed: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                _lastScrapeError.value = "Error de conexión: ${e.message}"
                Log.e("CurrencyViewModel", "Error scraping rates", e)
            } finally {
                _isScraping.value = false
            }
        }
    }
}
