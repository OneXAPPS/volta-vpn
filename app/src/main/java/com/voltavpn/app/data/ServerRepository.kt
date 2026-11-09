package com.voltavpn.app.data

import android.content.Context
import android.util.Base64
import com.voltavpn.app.BuildConfig
import com.voltavpn.app.pass.DailyPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Loads the server list. Preferred source is the signed list built by backend/
 * (servers already tested for leaks, real geo-location and MITM). Until that is
 * deployed, falls back to VPN Gate's public API, marking entries unverified.
 */
class ServerRepository(private val context: Context) {
    /** HTTP Date of the last response (0 = none); read on the same IO thread that set it. */
    private var lastResponseDate = 0L
    private val cache = File(context.filesDir, "servers.json")

    suspend fun load(forceRefresh: Boolean = false): List<Server> = withContext(Dispatchers.IO) {
        if (!forceRefresh) readCache(maxAgeMs = CACHE_MAX_AGE_MS)?.let { return@withContext it }
        val fresh = runCatching { fetch() }.getOrNull().orEmpty()
        if (fresh.isNotEmpty()) {
            writeCache(fresh)
            fresh
        } else {
            readCache(maxAgeMs = Long.MAX_VALUE).orEmpty()
        }
    }

    private fun fetch(): List<Server> {
        // Mirrors of the same signed file; if one host is blocked in a country, try the next.
        for (url in BuildConfig.SERVER_LIST_URLS.split(',').filter { it.isNotBlank() }) {
            runCatching { fetchSigned(url) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Blocked in some countries (e.g. answered by an ISP filter page): yields no servers.
        return fetchVpnGate()
    }

    private fun fetchSigned(url: String): List<Server> {
        val body = JSONObject(httpGet(url))
        val payload = Base64.decode(body.getString("payload"), Base64.DEFAULT)
        val sig = Base64.decode(body.getString("sig"), Base64.DEFAULT)
        // Any of the pinned keys: the current one, plus a spare kept offline for rotation.
        val ok = BuildConfig.SERVER_LIST_PUBKEY.split(',').map { it.trim() }.filter { it.isNotEmpty() }.any { pub ->
            runCatching {
                val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(pub, Base64.DEFAULT)))
                Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(payload); verify(sig) }
            }.getOrDefault(false)
        }
        check(ok) { "server list signature invalid" }
        val list = JSONObject(payload.decodeToString())
        val generatedMs = list.getLong("generated") * 1000
        // A genuine but old list (replayed by someone on the path) would send people to servers
        // that may have gone bad since: refuse anything older than the newest list already
        // accepted, or much older than the backend's hourly schedule.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        check(generatedMs >= prefs.getLong(KEY_NEWEST, 0)) { "server list older than one already accepted" }
        // The HTTPS server's own clock, not the phone's (which may be set wrong either way).
        val now = lastResponseDate.takeIf { it > 0 } ?: System.currentTimeMillis()
        check(now - generatedMs <= MAX_LIST_AGE_MS) { "server list expired" }
        prefs.edit().putLong(KEY_NEWEST, generatedMs).apply()
        DailyPass.observeTrustedTime(context, generatedMs)
        val arr = list.getJSONArray("servers")
        return List(arr.length()) { Server.fromJson(arr.getJSONObject(it)) }
    }

    private fun fetchVpnGate(): List<Server> {
        val csv = httpGet(VPNGATE_API)
        val servers = mutableListOf<Server>()
        for (line in csv.lineSequence()) {
            if (line.isBlank() || line.startsWith("*") || line.startsWith("#")) continue
            val f = line.split(',')
            if (f.size < 15) continue
            val config = runCatching {
                Base64.decode(f.last(), Base64.DEFAULT).decodeToString()
            }.getOrNull() ?: continue
            if (!config.contains("remote ")) continue
            servers += Server(
                id = f[0],
                countryCode = f[6],
                countryName = f[5],
                ip = f[1],
                pingMs = null,
                speedMbps = (f[4].toDoubleOrNull() ?: 0.0) / 1_000_000,
                score = f[2].toLongOrNull() ?: 0,
                tier = Tier.COMMUNITY,
                config = ConfigSanitizer.sanitize(config),
            )
        }
        // Keep the best few per country so the list stays small and fast.
        return servers.groupBy { it.countryCode }
            .flatMap { (_, list) -> list.sortedWith(serverRanking).take(PER_COUNTRY) }
            .sortedWith(serverRanking)
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "VoltaVPN/${BuildConfig.VERSION_NAME}")
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            // HTTP Date header gives a clock the user can't change (anti clock-tamper).
            lastResponseDate = conn.date
            if (conn.date > 0) DailyPass.observeTrustedTime(context, conn.date)
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(maxAgeMs: Long): List<Server>? = runCatching {
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > maxAgeMs) return null
        val arr = JSONArray(cache.readText())
        List(arr.length()) { Server.fromJson(arr.getJSONObject(it)) }
    }.getOrNull()

    private fun writeCache(servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        cache.writeText(arr.toString())
    }

    companion object {
        // HTTPS only: over plain HTTP anyone on the path (ISP, public Wi-Fi) could swap in
        // configs for their own server, and the phone would connect to it.
        private const val VPNGATE_API = "https://www.vpngate.net/api/iphone/"
        // Backend refreshes hourly; public servers come and go, so don't keep a list longer.
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L
        private const val PER_COUNTRY = 6
        // The backend publishes hourly; a day and a half covers several missed runs.
        private const val MAX_LIST_AGE_MS = 36 * 60 * 60 * 1000L
        private const val PREFS = "server_list"
        private const val KEY_NEWEST = "newest_generated"
    }
}
