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
        if (BuildConfig.SERVER_LIST_URL.isNotEmpty()) {
            runCatching { fetchSigned() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Blocked in some countries (e.g. answered by an ISP filter page): yields no servers.
        return fetchVpnGate()
    }

    private fun fetchSigned(): List<Server> {
        val body = JSONObject(httpGet(BuildConfig.SERVER_LIST_URL))
        val payload = Base64.decode(body.getString("payload"), Base64.DEFAULT)
        val sig = Base64.decode(body.getString("sig"), Base64.DEFAULT)
        val key = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(BuildConfig.SERVER_LIST_PUBKEY, Base64.DEFAULT))
        )
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(key); update(payload); verify(sig)
        }
        check(ok) { "server list signature invalid" }
        val list = JSONObject(payload.decodeToString())
        DailyPass.observeTrustedTime(context, list.getLong("generated") * 1000)
        val arr = list.getJSONArray("servers")
        return List(arr.length()) { Server.fromJson(arr.getJSONObject(it), verifiedDefault = true) }
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
                pingMs = f[3].toIntOrNull() ?: 999,
                speedMbps = (f[4].toDoubleOrNull() ?: 0.0) / 1_000_000,
                score = f[2].toLongOrNull() ?: 0,
                verified = false,
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
            if (conn.date > 0) DailyPass.observeTrustedTime(context, conn.date)
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(maxAgeMs: Long): List<Server>? = runCatching {
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > maxAgeMs) return null
        val arr = JSONArray(cache.readText())
        List(arr.length()) { Server.fromJson(arr.getJSONObject(it), verifiedDefault = false) }
    }.getOrNull()

    private fun writeCache(servers: List<Server>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        cache.writeText(arr.toString())
    }

    companion object {
        private const val VPNGATE_API = "http://www.vpngate.net/api/iphone/"
        private const val CACHE_MAX_AGE_MS = 3 * 60 * 60 * 1000L
        private const val PER_COUNTRY = 6
    }
}
