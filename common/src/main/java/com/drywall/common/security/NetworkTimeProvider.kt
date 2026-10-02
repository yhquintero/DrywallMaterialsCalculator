package com.drywall.common.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object NetworkTimeProvider {
    private const val TAG = "NetworkTimeProvider"
    private const val MAX_SYNC_AGE_MS = 3600_000L // 1 hour
    private const val MAX_TIME_DRIFT_MS = 300_000L // 5 minutes

    private data class TimeState(val syncedTime: Long, val lastSyncUnix: Long)

    private val timeApis = listOf(
        TimeApiSource("https://www.google.com/generate_204", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://www.google.com", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://timeapi.io/api/Time/current/zone?timeZone=UTC", parseMethod = ParseMethod.JSON),
        TimeApiSource("https://www.cloudflare.com", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://www.facebook.com", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://www.microsoft.com", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://www.apple.com", parseMethod = ParseMethod.HEADER),
        TimeApiSource("https://www.bing.com", parseMethod = ParseMethod.HEADER),
    )

    private data class TimeApiSource(val url: String, val parseMethod: ParseMethod)
    private enum class ParseMethod { HEADER, JSON }

    private val timeState = AtomicReference(TimeState(0L, 0L))
    private var lastUsedServer = "Ninguno"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun getSyncedTime(): Long {
        val state = timeState.get()
        if (state.syncedTime == 0L) return System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - state.lastSyncUnix
        if (elapsed > MAX_SYNC_AGE_MS) return System.currentTimeMillis()
        return state.syncedTime + elapsed
    }

    fun isSynchronized(): Boolean = timeState.get().syncedTime != 0L
    fun getLastUsedServer(): String = lastUsedServer

    fun resetSync() {
        timeState.set(TimeState(0L, 0L))
        lastUsedServer = "Ninguno"
    }

    suspend fun syncWithInternet(): Result<Long> = withContext(Dispatchers.IO) {
        val systemTime = System.currentTimeMillis()
        for (source in timeApis) {
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "DrywallCalculator/1.0")
                    .header("Accept", "*/*")
                    .header("Connection", "close")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val headerDate = response.header("Date")?.let { parseHttpDate(it) } ?: 0L

                    if (headerDate > 1_600_000_000_000L && source.parseMethod == ParseMethod.HEADER) {
                        if (kotlin.math.abs(headerDate - systemTime) > MAX_TIME_DRIFT_MS) {
                            Log.w(TAG, "Time drift too large from ${source.url}: ${headerDate - systemTime}ms")
                            return@use null
                        }
                        timeState.set(TimeState(headerDate, System.currentTimeMillis()))
                        lastUsedServer = source.url
                        return@withContext Result.success(headerDate)
                    }

                    if (response.isSuccessful && source.parseMethod == ParseMethod.JSON) {
                        val body = response.body.string()
                        val unixTime = parseUnixTime(body)
                        if (unixTime != null) {
                            val timeInMillis = if (unixTime > 10_000_000_000L) unixTime else unixTime * 1000L
                            if (timeInMillis > 1_600_000_000_000L) {
                                if (kotlin.math.abs(timeInMillis - systemTime) > MAX_TIME_DRIFT_MS) {
                                    Log.w(TAG, "Time drift too large from ${source.url}: ${timeInMillis - systemTime}ms")
                                    return@use null
                                }
                                timeState.set(TimeState(timeInMillis, System.currentTimeMillis()))
                                lastUsedServer = source.url
                                return@withContext Result.success(timeInMillis)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next source
            }
        }
        return@withContext Result.failure(Exception("No se pudo conectar con ningún servidor de tiempo confiable"))
    }

    private fun parseHttpDate(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
            sdf.isLenient = false
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseUnixTime(json: String): Long? {
        return try {
            val keys = listOf("\"unixtime\":", "\"unixTime\":", "\"epoch\":", "\"milliSeconds\":")
            for (key in keys) {
                val start = json.indexOf(key)
                if (start != -1) {
                    val valueStart = start + key.length
                    val valueEnd = json.indexOfAny(charArrayOf(',', '}', ' '), valueStart)
                    if (valueEnd != -1) {
                        val value = json.substring(valueStart, valueEnd).replace("\"", "").trim()
                        val longValue = value.toDoubleOrNull()?.toLong() ?: continue
                        if ((key == "\"milliSeconds\":") && (longValue < 1_000_000_000L)) continue
                        return longValue
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
