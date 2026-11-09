package com.voltavpn.app.data

import android.content.Context

/**
 * Servers that just failed to connect go to the back of the queue for a while. The backend
 * re-tests the list hourly, so a longer cooldown would outlive what we actually know.
 *
 * Also keeps a small success / failure tally per server on this phone, so one that keeps
 * failing here ranks below an equally fast one that works. Stays on the device.
 */
class FailedServers(context: Context) {
    private val prefs = context.getSharedPreferences("failed_servers", Context.MODE_PRIVATE)
    private val stats = context.getSharedPreferences("server_stats", Context.MODE_PRIVATE)

    fun mark(id: String) {
        prefs.edit().putLong(id, System.currentTimeMillis()).apply()
        record(id, success = false)
    }

    fun isRecentlyFailed(id: String): Boolean =
        System.currentTimeMillis() - prefs.getLong(id, 0) < COOLDOWN_MS

    fun clear(id: String) {
        prefs.edit().remove(id).apply()
    }

    fun recordSuccess(id: String) = record(id, success = true)

    /** 0..1, starting at 0.5 for a server never tried (Laplace smoothing). */
    fun reliability(id: String): Double {
        val ok = stats.getInt("ok_$id", 0)
        val bad = stats.getInt("bad_$id", 0)
        return (ok + 1.0) / (ok + bad + 2.0)
    }

    private fun record(id: String, success: Boolean) {
        var ok = stats.getInt("ok_$id", 0) + if (success) 1 else 0
        var bad = stats.getInt("bad_$id", 0) + if (success) 0 else 1
        // Recent behaviour matters most: halve old history once it grows.
        if (ok + bad > MAX_HISTORY) {
            ok /= 2
            bad /= 2
        }
        stats.edit().putInt("ok_$id", ok).putInt("bad_$id", bad).apply()
    }

    companion object {
        private const val COOLDOWN_MS = 60 * 60 * 1000L
        private const val MAX_HISTORY = 20
    }
}
