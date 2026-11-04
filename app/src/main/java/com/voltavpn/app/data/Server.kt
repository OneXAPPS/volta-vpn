package com.voltavpn.app.data

import org.json.JSONObject

data class Server(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val ip: String,
    val pingMs: Int,
    val speedMbps: Double,
    val score: Long,
    /** true = passed the backend's leak / geo / MITM checks (signed list). */
    val verified: Boolean,
    val config: String,
) {
    val flag: String
        get() = if (countryCode.length != 2) "🌐" else countryCode.uppercase()
            .map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
            .joinToString("")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("cc", countryCode).put("country", countryName).put("ip", ip)
        .put("ping", pingMs).put("mbps", speedMbps).put("score", score)
        .put("verified", verified).put("config", config)

    companion object {
        fun fromJson(o: JSONObject, verifiedDefault: Boolean) = Server(
            id = o.getString("id"),
            countryCode = o.getString("cc"),
            countryName = o.getString("country"),
            ip = o.getString("ip"),
            pingMs = o.optInt("ping", 999),
            speedMbps = o.optDouble("mbps", 0.0),
            score = o.optLong("score", 0),
            verified = o.optBoolean("verified", verifiedDefault),
            config = o.getString("config"),
        )
    }
}

/** Best first: verified servers, then VPN Gate score (which weighs speed, ping and uptime). */
val serverRanking: Comparator<Server> =
    compareByDescending<Server> { it.verified }.thenByDescending { it.score }.thenBy { it.pingMs }
