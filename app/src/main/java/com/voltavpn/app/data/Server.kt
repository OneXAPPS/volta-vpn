package com.voltavpn.app.data

import org.json.JSONObject
import java.util.Locale

/**
 * How much the user can trust whoever runs the server.
 * PRIVATE: our own no-log servers. NOLOG: an audited no-log provider (Riseup).
 * VERIFIED: public volunteer server that passed our leak / geo / TLS checks (operator may
 * still keep logs). COMMUNITY: public, untested.
 */
enum class Tier { PRIVATE, NOLOG, VERIFIED, COMMUNITY }

data class Server(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val ip: String,
    /** Round-trip time measured from this phone; null = not measured yet, -1 = unreachable. */
    val pingMs: Int?,
    val speedMbps: Double,
    val score: Long,
    val tier: Tier,
    val config: String,
    /** Local / government sites the backend confirmed this server opens (geo-fenced from abroad). */
    val unlocks: List<String> = emptyList(),
    /** Where this phone must fetch its own anonymous client certificate (Riseup), if any. */
    val certUrl: String? = null,
) {
    val flag: String
        get() = if (countryCode.length != 2) "🌐" else countryCode.uppercase()
            .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
            .joinToString("")

    /** Country name in the phone's language ("اليابان"), not VPN Gate's English label. */
    fun displayName(locale: Locale = Locale.getDefault()): String =
        Locale("", countryCode).getDisplayCountry(locale).takeIf { it.isNotBlank() && it != countryCode }
            ?: countryName.substringBefore(" (")

    /** TCP port to probe for reachability: the OpenVPN TCP port, else SoftEther's 443. */
    val probePort: Int
        get() {
            val remote = config.lineSequence().firstOrNull { it.startsWith("remote ") }?.split(' ')
            val tcp = config.lineSequence().any { it.trim().startsWith("proto tcp") }
            return if (tcp) remote?.getOrNull(2)?.toIntOrNull() ?: 443 else 443
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("cc", countryCode).put("country", countryName).put("ip", ip)
        .put("mbps", speedMbps).put("score", score).put("tier", tier.name).put("config", config)
        .put("unlocks", org.json.JSONArray(unlocks)).put("cert_url", certUrl)

    companion object {
        fun fromJson(o: JSONObject) = Server(
            id = o.getString("id"),
            countryCode = o.getString("cc"),
            countryName = o.getString("country"),
            ip = o.getString("ip"),
            pingMs = null,
            speedMbps = o.optDouble("mbps", 0.0),
            score = o.optLong("score", 0),
            tier = when {
                o.has("tier") -> runCatching { Tier.valueOf(o.getString("tier").uppercase()) }
                    .getOrDefault(Tier.COMMUNITY)
                o.optBoolean("verified") -> Tier.VERIFIED
                else -> Tier.COMMUNITY
            },
            config = o.getString("config"),
            unlocks = o.optJSONArray("unlocks")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
            certUrl = o.optString("cert_url").takeIf { it.startsWith("https://") },
        )
    }
}

/** Best first: most trusted tier, then real measured ping, then VPN Gate's score. */
val serverRanking: Comparator<Server> =
    compareBy<Server> { it.tier.ordinal }
        .thenBy { it.pingMs?.takeIf { p -> p >= 0 } ?: Int.MAX_VALUE }
        .thenByDescending { it.score }
