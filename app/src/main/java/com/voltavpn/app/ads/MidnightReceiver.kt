package com.voltavpn.app.ads

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import com.voltavpn.app.MainActivity
import com.voltavpn.app.R

/**
 * Fires around local midnight when the daily pass ends. It never cuts an active
 * connection; it only tells the user the next connect needs a new ad.
 */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_pass), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_volta_notice)
            .setContentTitle(context.getString(R.string.pass_expired_title))
            .setContentText(context.getString(R.string.pass_expired_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "daily_pass"
        private const val NOTIFICATION_ID = 7001

        /** Inexact alarm: no SCHEDULE_EXACT_ALARM permission needed. */
        fun schedule(context: Context, atMillis: Long) {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, MidnightReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC, atMillis, 15 * 60 * 1000L, pi)
        }
    }
}
