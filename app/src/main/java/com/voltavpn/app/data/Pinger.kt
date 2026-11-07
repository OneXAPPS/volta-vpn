package com.voltavpn.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures each server from this phone with a TCP handshake (no root / ICMP needed).
 * A server that doesn't answer is marked -1 and skipped when connecting, so dead
 * public servers no longer cost the user a 20-second timeout.
 * Must run while the VPN is down, or it would measure through the tunnel.
 */
object Pinger {
    private const val TIMEOUT_MS = 2500
    private val parallel = Semaphore(16)

    suspend fun measure(servers: List<Server>): List<Server> = withContext(Dispatchers.IO) {
        coroutineScope {
            servers.map { s ->
                async { parallel.withPermit { s.copy(pingMs = rtt(s.ip, s.probePort)) } }
            }.awaitAll()
        }
    }

    private fun rtt(ip: String, port: Int): Int = try {
        Socket().use { socket ->
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
            ((System.nanoTime() - start) / 1_000_000).toInt()
        }
    } catch (_: Exception) {
        -1
    }
}
