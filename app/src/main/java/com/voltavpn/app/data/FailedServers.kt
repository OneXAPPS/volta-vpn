package com.voltavpn.app.data

import android.content.Context

/** Servers that just failed to connect are skipped for a while instead of retried at once. */
class FailedServers(context: Context) {
    private val prefs = context.getSharedPreferences("failed_servers", Context.MODE_PRIVATE)

    fun mark(id: String) {
        prefs.edit().putLong(id, System.currentTimeMillis()).apply()
    }

    fun isRecentlyFailed(id: String): Boolean =
        System.currentTimeMillis() - prefs.getLong(id, 0) < COOLDOWN_MS

    fun clear(id: String) {
        prefs.edit().remove(id).apply()
    }

    companion object {
        private const val COOLDOWN_MS = 3 * 60 * 60 * 1000L
    }
}
