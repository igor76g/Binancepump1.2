package com.example.binancepump

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Binance USDT-M Futures PUBLIC REST API client.
 * No API key is needed: all endpoints used here are public market data.
 *
 *   GET /fapi/v1/exchangeInfo   - list of symbols
 *   GET /fapi/v1/ticker/24hr    - 24h statistics (quoteVolume)
 *   GET /fapi/v1/ticker/price   - latest prices
 *
 * All calls are blocking: call them from a background thread (Dispatchers.IO).
 */
object BinanceApi {
    private const val BASE_URL = "https://fapi.binance.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    private fun get(path: String): String {
        val request = Request.Builder().url(BASE_URL + path).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Binance HTTP ${response.code} for $path")
            }
            return response.body?.string() ?: throw IOException("Empty response for $path")
        }
    }

    /** Currently trading USDT-margined perpetual futures symbols (e.g. BTCUSDT). */
    @Throws(IOException::class)
    fun fetchUsdtPerpetualSymbols(): Set<String> {
        val root = JSONObject(get("/fapi/v1/exchangeInfo"))
        val symbols = root.getJSONArray("symbols")
        val result = HashSet<String>()
        for (i in 0 until symbols.length()) {
            val s = symbols.getJSONObject(i)
            if (s.optString("quoteAsset") == "USDT" &&
                s.optString("contractType") == "PERPETUAL" &&
                s.optString("status") == "TRADING"
            ) {
                result.add(s.getString("symbol"))
            }
        }
        return result
    }

    /** Map of symbol -> 24h quote volume in USDT. */
    @Throws(IOException::class)
    fun fetchQuoteVolumes(): Map<String, Double> {
        val array = JSONArray(get("/fapi/v1/ticker/24hr"))
        val result = HashMap<String, Double>(array.length() * 2)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val volume = item.optString("quoteVolume").toDoubleOrNull() ?: continue
            result[item.getString("symbol")] = volume
        }
        return result
    }

    /** Map of symbol -> last price. */
    @Throws(IOException::class)
    fun fetchPrices(): Map<String, Double> {
        val array = JSONArray(get("/fapi/v1/ticker/price"))
        val result = HashMap<String, Double>(array.length() * 2)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val price = item.optString("price").toDoubleOrNull() ?: continue
            result[item.getString("symbol")] = price
        }
        return result
    }
}
