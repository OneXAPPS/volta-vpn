package com.voltavpn.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A quick look before starting the VPN engine (an idea from Psiphon, which races servers):
 * a VPN Gate server answers TLS on its TCP port with the certificate every real one has
 * (CN=opengw.net). If it doesn't, the address has changed hands or the server is down, and
 * the engine would only waste its 15 s timeout on it.
 *
 * Only an ordering hint: a server that fails here is tried last, never refused, and the engine
 * still does the real certificate check on every connection.
 */
object ServerProbe {
    private const val TIMEOUT_MS = 4000
    private const val VPNGATE_NAME = "opengw.net"
    /** Start of ISRG Root X1, the CA in every VPN Gate config (see vpn/ConnectionGuard). */
    private const val ISRG_ROOT_X1 = "MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAw"

    /** true = genuine VPN Gate server answering; false = not; null = can't tell for this server. */
    suspend fun check(server: Server): Boolean? = withContext(Dispatchers.IO) {
        if (ISRG_ROOT_X1 !in server.config) return@withContext null
        val (host, port) = tcpRemote(server.config) ?: return@withContext null
        runCatching {
            Socket().use { raw ->
                raw.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                raw.soTimeout = TIMEOUT_MS
                // System CAs only (as everywhere in Volta), then the name every VPN Gate server has.
                (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, VPNGATE_NAME, port, false).use { tls ->
                        (tls as SSLSocket).startHandshake()
                        HttpsURLConnection.getDefaultHostnameVerifier().verify(VPNGATE_NAME, tls.session)
                    }
            }
        }.getOrDefault(false)
    }

    /** The list, verified servers first, then those we can't check, then the ones that failed. */
    suspend fun order(servers: List<Server>): List<Server> = coroutineScope {
        val results = servers.map { async { check(it) } }.awaitAll()
        servers.indices.sortedBy { i -> when (results[i]) { true -> 0; null -> 1; false -> 2 } }.map { servers[it] }
    }

    private fun tcpRemote(config: String): Pair<String, Int>? {
        val globalTcp = config.lineSequence().any { it.trim().startsWith("proto tcp") }
        for (line in config.lineSequence()) {
            val f = line.trim().split(Regex("\\s+"))
            if (f.firstOrNull() != "remote" || f.size < 3) continue
            val proto = f.getOrNull(3)
            if (proto?.startsWith("tcp") == true || (proto == null && globalTcp)) {
                f[2].toIntOrNull()?.let { return f[1] to it }
            }
        }
        return null
    }
}
