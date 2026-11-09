package com.voltavpn.app.vpn

import com.voltavpn.app.data.ConfigSanitizer
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.Connection

/**
 * Last line of defence, applied to every profile right before the engine starts, whatever
 * list it came from: the signed list, the cache on disk, or VPN Gate directly. Either the
 * profile is made safe here, or the connection is refused.
 */
object ConnectionGuard {
    class UnsafeConfig(reason: String) : SecurityException(reason)

    /** Broken or no encryption: refuse rather than give a false sense of protection. */
    private val WEAK_CIPHERS = listOf("NONE", "BF-", "DES", "RC2", "RC4", "CAST")
    private val WEAK_AUTH = setOf("NONE", "MD4", "MD5")

    /**
     * Start of ISRG Root X1 (Let's Encrypt), the CA in VPN Gate configs. Trusting it with no
     * name check accepts a certificate for ANY domain, so anyone on the path (an ISP, a
     * public Wi-Fi) could pose as the server with a free certificate for their own site.
     */
    private const val ISRG_ROOT_X1 = "MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAw"
    /** The name every real VPN Gate server presents (CN=opengw.net, *.opengw.net). */
    private const val VPNGATE_CERT_NAME = "opengw.net"

    /**
     * Pushed by the server, these would route traffic around the tunnel ("route ... net_gateway"),
     * or replace our DNS. The tunnel already carries everything, so nothing is lost.
     */
    private val PULL_FILTERS = listOf(
        // Last, so they win over anything in the config: no TLS older than 1.2, and no
        // compression (it leaks information about encrypted content, as in VORACLE).
        "tls-version-min 1.2",
        "allow-compression no",
        "pull-filter ignore \"route \"",
        "pull-filter ignore \"route-ipv6 \"",
        "pull-filter ignore \"redirect-private\"",
        "pull-filter ignore \"dhcp-option\"",
        "pull-filter ignore \"block-outside-dns\"",
    )

    fun enforce(p: VpnProfile, rawConfig: String) {
        if (p.mAuthenticationType == VpnProfile.TYPE_STATICKEYS) throw UnsafeConfig("static key, no server certificate")
        val cipher = p.mCipher.orEmpty().uppercase()
        if (WEAK_CIPHERS.any { cipher.startsWith(it) }) throw UnsafeConfig("weak cipher $cipher")
        if (p.mAuth.orEmpty().uppercase() in WEAK_AUTH) throw UnsafeConfig("weak auth ${p.mAuth}")
        p.mUseLzo = false

        // The server must prove it holds a server certificate, not a client one from the same CA.
        p.mExpectTLSCert = true
        if (rawConfig.contains(ISRG_ROOT_X1) && !p.hasNameCheck()) {
            p.mCheckRemoteCN = true
            p.mRemoteCN = VPNGATE_CERT_NAME
            p.mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_RDN
        }

        // Every app, all traffic, through the tunnel: nothing excluded, no app may bypass it.
        p.mExcludedRoutes = ""
        p.mExcludedRoutesv6 = ""
        p.mAllowedAppsVpn.clear()
        p.mAllowedAppsVpnAreDisallowed = true
        p.mAllowAppVpnBypass = false
        // Don't tell the server the phone's model, OS version or network details.
        p.mPushPeerInfo = false

        for (conn in p.mConnections) {
            conn.mProxyType = Connection.ProxyType.NONE
            conn.mUseProxyAuth = false
            conn.mCustomConfiguration = ConfigSanitizer.sanitize(conn.mCustomConfiguration.orEmpty())
        }
        // Options the parser couldn't map are passed raw to the engine: filter them too.
        p.mCustomConfigOptions = ConfigSanitizer.sanitize(p.mCustomConfigOptions.orEmpty()) +
            PULL_FILTERS.joinToString("\n", postfix = "\n")
        p.mUseCustomConfig = true
    }

    private fun VpnProfile.hasNameCheck(): Boolean =
        (mCheckRemoteCN && !mRemoteCN.isNullOrEmpty()) || mCheckPeerFingerprint
}
