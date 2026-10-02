package com.drywall.keygen

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drywall.common.utils.CurrencyScraper
import com.drywall.common.utils.ScrapeResult
import com.drywall.common.utils.WebViewScraper
import com.drywall.keygen.data.IssuedLicense
import com.drywall.keygen.data.KeygenDatabase
import com.drywall.keygen.data.RateHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.drywall.keygen.utils.LicenseSaver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeygenViewModel(application: Application) : AndroidViewModel(application) {
    private val db = KeygenDatabase.getDatabase(application)
    private val dao = db.licenseDao()

    companion object {
        private const val TAG = "KeygenViewModel"
    }

    private val _isScraping = MutableStateFlow(false)
    val isScraping = _isScraping.asStateFlow()

    private val _lastScrapedTimestamp = MutableStateFlow("")
    val lastScrapedTimestamp = _lastScrapedTimestamp.asStateFlow()

    private val _currentRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentRates = _currentRates.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _scrapeSuccess = MutableStateFlow(false)
    val scrapeSuccess = _scrapeSuccess.asStateFlow()

    fun clearError() { _error.value = null }
    fun clearSuccess() { _scrapeSuccess.value = false }

    val licenses = dao.getAllLicenses().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rateHistory = dao.getRateHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun logRateChange(currency: String, rate: Double) {
        viewModelScope.launch {
            val history = rateHistory.value
            val lastForCurrency = history.filter { it.currencyCode == currency }.maxByOrNull { it.timestamp }
            
            if (lastForCurrency == null || lastForCurrency.rate != rate) {
                dao.insertRateHistory(RateHistory(currencyCode = currency, rate = rate))
            }
        }
    }

    fun registerLicenseRequest(userName: String, deviceId: String, plan: String, price: String) {
        viewModelScope.launch {
            try {
                // Validate input data
                if (userName.isBlank() || deviceId.isBlank() || plan.isBlank() || price.isBlank()) {
                    _error.value = "Todos los campos son requeridos"
                    return@launch
                }
                
                if (userName.length > 100) {
                    _error.value = "El nombre del usuario no puede exceder 100 caracteres"
                    return@launch
                }
                
                if (deviceId.length > 100) {
                    _error.value = "El ID del dispositivo no puede exceder 100 caracteres"
                    return@launch
                }
                
                // Validate price format
                val pricePattern = "^\\d+\\.\\d{2}(\\s+[A-Z]+)?$".toRegex()
                if (!pricePattern.matches(price)) {
                    _error.value = "Formato de precio inválido. Use formato: 0.00"
                    return@launch
                }
                
                val license = IssuedLicense(
                    userName = userName.trim(),
                    deviceId = deviceId.trim(),
                    planType = plan,
                    price = price,
                    isPaid = false
                )
                dao.insertLicense(license)
            } catch (e: Exception) {
                _error.value = "Error al registrar licencia: ${e.message}"
                Log.e(TAG, "Error registering license", e)
            }
        }
    }

    fun markAsPaid(license: IssuedLicense, licenseJson: String) {
        viewModelScope.launch {
            try {
                // Validate licenseJson
                if (licenseJson.isBlank()) {
                    _error.value = "El JSON de la licencia no puede estar vacío"
                    return@launch
                }
                
                if (licenseJson.length > 10000) {
                    _error.value = "El JSON de la licencia es demasiado grande"
                    return@launch
                }
                
                // Validate that licenseJson contains essential fields
                try {
                    val json = org.json.JSONObject(licenseJson)
                    if (!json.has("user") || !json.has("deviceId") || 
                        !json.has("expiryDate") || !json.has("signature")) {
                        _error.value = "El JSON de la licencia no contiene los campos requeridos"
                        return@launch
                    }
                } catch (e: Exception) {
                    _error.value = "El formato del JSON de licencia no es válido"
                    return@launch
                }
                
                dao.updateLicense(license.copy(isPaid = true, licenseJson = licenseJson))
                LicenseSaver.saveLicense(
                    getApplication(),
                    license.userName,
                    license.deviceId,
                    license.planType,
                    license.price,
                    licenseJson
                )
            } catch (e: Exception) {
                _error.value = "Error al marcar como pagada: ${e.message}"
                Log.e(TAG, "Error marking as paid", e)
            }
        }
    }

    fun deleteLicense(license: IssuedLicense) {
        viewModelScope.launch {
            // Validate license before deletion
            if (license.id <= 0) {
                throw IllegalArgumentException("ID de licencia inválido")
            }
            
            if (license.userName.isBlank() || license.deviceId.isBlank()) {
                throw IllegalArgumentException("La licencia no puede ser eliminada sin usuario o dispositivo válidos")
            }
            
            dao.deleteLicense(license)
        }
    }

    private fun isValidScrapedRate(currency: String, rate: Double?): Boolean {
        if (rate == null || rate <= 0) return false
        val minRates = mapOf(
            "USD" to 100.0, "EUR" to 100.0, "MLC" to 50.0,
            "CAD" to 50.0, "MEX" to 5.0, "ZELLE" to 100.0, "CLA" to 50.0
        )
        return rate >= (minRates[currency] ?: 1.0)
    }

    fun fetchRates() {
        if (_isScraping.value) return
        viewModelScope.launch {
            _isScraping.value = true
            _error.value = null
            _scrapeSuccess.value = false
            try {
                // 1. Try OkHttp-based scraping first (fast)
                var result = withContext(Dispatchers.IO) {
                    CurrencyScraper.fetchRates()
                }

                // 2. If incomplete (<5 rates), try WebView (handles Cloudflare)
                if (!result.success || result.rates.size < 5) {
                    Log.d(TAG, "Scraping parcial (${result.rates.size} rates), intentando WebView...")
                    val wvResult = WebViewScraper(getApplication()).fetchRates()
                    if (wvResult.success && wvResult.rates.isNotEmpty()) {
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
                    _currentRates.value = result.rates
                    _lastScrapedTimestamp.value = result.timestamp
                    _scrapeSuccess.value = true

                    val logCodes = listOf("USD", "EUR", "MLC", "CAD", "MEX", "ZELLE", "CLA")
                    logCodes.forEach { code ->
                        val rate = result.rates[code]
                        if (rate != null && isValidScrapedRate(code, rate)) {
                            logRateChange(code, rate)
                        }
                    }
                } else {
                    _error.value = result.errorMessage ?: "No se pudieron obtener las tasas"
                    Log.e(TAG, "Error fetching rates: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                _error.value = "Error de conexión: ${e.message}"
                Log.e(TAG, "Error scraping rates", e)
            } finally {
                _isScraping.value = false
            }
        }
    }
}
