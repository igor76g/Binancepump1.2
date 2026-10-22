package com.example.binancepump

/**
 * Keeps a rolling (timestamp, price) history per symbol and computes
 * the price change in percent within the configured time window.
 */
class PriceTracker {

    private class Sample(val timeMs: Long, val price: Double)

    private val history = HashMap<String, ArrayDeque<Sample>>()

    fun add(symbol: String, price: Double, nowMs: Long, windowMs: Long) {
        val samples = history.getOrPut(symbol) { ArrayDeque() }
        samples.addLast(Sample(nowMs, price))
        val cutoff = nowMs - windowMs
        while (samples.isNotEmpty() && samples.first().timeMs < cutoff) {
            samples.removeFirst()
        }
    }

    /** Growth in % between the oldest sample in the window and the newest one, or null if not enough data. */
    fun changePercent(symbol: String): Double? {
        val samples = history[symbol] ?: return null
        if (samples.size < 2) return null
        val oldest = samples.first().price
        val newest = samples.last().price
        if (oldest <= 0.0) return null
        return (newest - oldest) / oldest * 100.0
    }

    /** Drop history for symbols that no longer pass the volume filter. */
    fun retainOnly(symbols: Set<String>) {
        history.keys.retainAll(symbols)
    }
}
