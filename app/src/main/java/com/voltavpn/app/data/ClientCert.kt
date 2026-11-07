package com.voltavpn.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Providers like Riseup give every client its own short-lived anonymous certificate
 * (nothing ties it to a person). We fetch one per phone, never share it, and refresh it
 * daily, exactly like the provider's own app.
 */
object ClientCert {
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val KEY = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", RegexOption.DOT_MATCHES_ALL)
    private val CERT = Regex("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL)

    /** The server with its client key/cert appended when it needs one; unchanged otherwise. */
    suspend fun attach(context: Context, server: Server): Server {
        val url = server.certUrl ?: return server
        val pem = withContext(Dispatchers.IO) { cached(context, url) }
        val key = KEY.find(pem)?.value ?: error("no key")
        val cert = CERT.find(pem)?.value ?: error("no cert")
        return server.copy(config = server.config + "<key>\n$key\n</key>\n<cert>\n$cert\n</cert>\n")
    }

    private fun cached(context: Context, url: String): String {
        val file = File(context.noBackupFilesDir, "client-" + url.hashCode() + ".pem")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS) return file.readText()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val pem = try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
        file.writeText(pem)
        return pem
    }
}
