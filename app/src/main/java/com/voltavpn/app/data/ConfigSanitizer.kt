package com.voltavpn.app.data

/**
 * Server configs come from outside (VPN Gate volunteers, Riseup, our own servers), so only an
 * ALLOWLIST of plain connection settings reaches the engine. Anything else is dropped:
 * scripts, plugins, file writes, proxies, routes around the tunnel, DNS changes, compression,
 * static keys, TLS downgrades, and any directive OpenVPN adds in the future.
 * Applied again to every config at connect time (see vpn/ConnectionGuard).
 */
object ConfigSanitizer {
    private val allowed = setOf(
        // Where and how to connect.
        "client", "tls-client", "pull", "dev", "dev-type", "proto", "remote", "remote-random",
        "resolv-retry", "nobind", "float", "connect-retry", "connect-retry-max",
        "connect-timeout", "server-poll-timeout", "explicit-exit-notify",
        // Tunnel behaviour.
        "persist-key", "persist-tun", "keepalive", "ping", "ping-restart", "reneg-sec",
        "tun-mtu", "mssfix", "sndbuf", "rcvbuf", "topology", "mute-replay-warnings",
        // Cryptography: what the server must prove, and which algorithms may be used.
        "cipher", "data-ciphers", "data-ciphers-fallback", "ncp-ciphers", "auth",
        "tls-cipher", "tls-ciphersuites", "tls-groups", "tls-version-min", "key-direction",
        "remote-cert-tls", "verify-x509-name",
        // Logging verbosity (no files: "log" and "status" stay blocked).
        "verb", "mute",
    )

    /** Key and certificate material, passed through untouched. */
    private val dataBlocks = setOf(
        "ca", "cert", "key", "extra-certs", "tls-auth", "tls-crypt", "tls-crypt-v2",
        "crl-verify", "peer-fingerprint",
    )

    fun sanitize(config: String): String = buildString {
        // Inside an inline block: its closing tag, and whether its content is kept.
        var closing: String? = null
        var keep = false
        for (raw in config.lineSequence()) {
            val line = raw.trim()
            if (closing != null) {
                if (keep) appendLine(line)
                if (line.equals(closing, ignoreCase = true)) closing = null
                continue
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("<")) {
                val tag = line.removePrefix("</").removePrefix("<").substringBefore('>').trim().lowercase()
                when {
                    // <connection> holds ordinary directives, filtered line by line like the rest.
                    tag == "connection" -> appendLine(line)
                    line.startsWith("</") -> if (tag in dataBlocks) appendLine(line)
                    else -> {
                        // Unknown blocks (e.g. <http-proxy-user-pass>) are dropped with their content.
                        keep = tag in dataBlocks
                        if (keep) appendLine(line)
                        // A block opened and closed on the same line has nothing more to skip.
                        if (!line.endsWith("</$tag>", ignoreCase = true)) closing = "</$tag>"
                    }
                }
                continue
            }
            val directive = line.substringBefore(' ').substringBefore('\t').lowercase()
            if (directive in allowed) appendLine(line)
        }
    }
}
