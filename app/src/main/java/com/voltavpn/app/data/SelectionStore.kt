package com.voltavpn.app.data

import android.content.Context
import org.json.JSONObject

/** What must survive an app restart: the user's pick and what they've already been told. */
class SelectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("selection", Context.MODE_PRIVATE)

    /** Picked country code; null = automatic. */
    var country: String?
        get() = prefs.getString(KEY_COUNTRY, null)
        set(value) = prefs.edit().putString(KEY_COUNTRY, value).apply()

    /**
     * Server of the last successful connection, kept whole: if the UI restarts mid-session it
     * may no longer be in the list, yet we must still name it, show its tier and return to it.
     */
    var lastServer: Server?
        get() = prefs.getString(KEY_LAST_SERVER, null)?.let { saved ->
            runCatching { Server.fromJson(JSONObject(saved)) }.getOrNull()
        }
        set(value) = prefs.edit().putString(KEY_LAST_SERVER, value?.toJson()?.toString()).apply()

    var volunteerNoticeSeen: Boolean
        get() = prefs.getBoolean(KEY_VOLUNTEER_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_VOLUNTEER_SEEN, value).apply()

    /** The one-time tip about Android's "Block connections without VPN" was shown. */
    var lockdownTipSeen: Boolean
        get() = prefs.getBoolean(KEY_LOCKDOWN_TIP, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCKDOWN_TIP, value).apply()

    private companion object {
        const val KEY_COUNTRY = "country"
        const val KEY_LAST_SERVER = "last_server"
        const val KEY_VOLUNTEER_SEEN = "volunteer_seen"
        const val KEY_LOCKDOWN_TIP = "lockdown_tip_seen"
    }
}
