package com.voltavpn.app.vpn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Which DNS resolver the tunnel uses. All are encrypted-capable public resolvers reached
 * inside the tunnel; the filtering ones also block known malicious or tracking domains
 * before a connection is even made. Zero app size: it's just a different IP.
 */
enum class DnsMode(val primary: String, val secondary: String) {
    STANDARD("1.1.1.1", "1.0.0.1"),            // Cloudflare: fast, no logging of IPs
    MALWARE("1.1.1.2", "1.0.0.2"),             // Cloudflare for Families: blocks malware & phishing
    TRACKERS("94.140.14.14", "94.140.15.15"),  // AdGuard DNS: blocks ads, trackers & malware
}

object ProtectionPrefs {
    private const val PREFS = "protection"

    fun dnsMode(context: Context): DnsMode = runCatching {
        DnsMode.valueOf(prefs(context).getString("dns", DnsMode.MALWARE.name)!!)
    }.getOrDefault(DnsMode.MALWARE)

    fun setDnsMode(context: Context, mode: DnsMode) {
        prefs(context).edit().putString("dns", mode.name).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What the outside world sees right now: the exit IP and its country. */
data class ExitInfo(val ip: String, val country: String)

object ExitCheck {
    /** Asked through the tunnel, so it reports the VPN's exit, not the phone's real IP. */
    suspend fun fetch(): ExitInfo? = withContext(Dispatchers.IO) {
        for (url in listOf("https://api.country.is/", "https://ipinfo.io/json")) {
            val info = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                try {
                    val o = JSONObject(conn.inputStream.bufferedReader().readText())
                    ExitInfo(o.getString("ip"), o.getString("country").uppercase())
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }
}
