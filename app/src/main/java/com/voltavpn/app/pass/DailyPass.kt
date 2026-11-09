package com.voltavpn.app.pass

import android.content.Context
import java.util.Calendar

/**
 * One rewarded ad unlocks connecting until the next local midnight.
 *
 * Clock-tamper protection: we remember the latest time we have ever seen (device
 * clock or a server's HTTP Date). If the device clock is later found to be behind
 * that, it was turned back, and the pass is revoked.
 */
object DailyPass {
    private const val PREFS = "daily_pass"
    private const val KEY_VALID_UNTIL = "valid_until"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val TOLERANCE_MS = 10 * 60 * 1000L

    fun isActive(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val lastSeen = p.getLong(KEY_LAST_SEEN, 0)
        if (now + TOLERANCE_MS < lastSeen) {
            p.edit().remove(KEY_VALID_UNTIL).apply()
            return false
        }
        if (now > lastSeen) p.edit().putLong(KEY_LAST_SEEN, now).apply()
        return now < p.getLong(KEY_VALID_UNTIL, 0)
    }

    fun validUntil(context: Context): Long = prefs(context).getLong(KEY_VALID_UNTIL, 0)

    fun grant(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_VALID_UNTIL, nextMidnight(now))
            .putLong(KEY_LAST_SEEN, maxOf(now, prefs(context).getLong(KEY_LAST_SEEN, 0)))
            .apply()
    }

    fun observeTrustedTime(context: Context, trustedNow: Long) {
        val p = prefs(context)
        if (trustedNow > p.getLong(KEY_LAST_SEEN, 0)) p.edit().putLong(KEY_LAST_SEEN, trustedNow).apply()
    }

    fun nextMidnight(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
