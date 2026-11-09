package com.voltavpn.app.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import com.voltavpn.app.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Detects a copy of Volta that was modified and re-signed (the usual way fake VPN apps
 * carrying malware are made), or that is being debugged / hooked while running.
 *
 * A tampered app can't be trusted with the user's traffic, so it refuses to connect.
 * This raises the cost of an attack; no client-side check can make one impossible.
 * Debug builds are never checked.
 */
object Integrity {
    /** Libraries of hooking toolkits, as they appear in the process's memory map. */
    private val HOOK_MARKERS = listOf("frida", "xposed", "lspatch", "substrate")

    fun isCompromised(context: Context): Boolean {
        if (BuildConfig.DEBUG) return false
        return runCatching {
            resigned(context) || debuggable(context) || beingDebugged() || hooked()
        }.getOrDefault(false)
    }

    /** Signed with a key other than ours. Skipped while SIGNER_SHA256 is empty (see app/build.gradle.kts). */
    private fun resigned(context: Context): Boolean {
        val expected = BuildConfig.SIGNER_SHA256.split(',')
            .map { it.replace(":", "").trim().lowercase() }
            .filter { it.isNotEmpty() }
        if (expected.isEmpty()) return false
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return true
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        val md = MessageDigest.getInstance("SHA-256")
        return certs.none { cert -> md.digest(cert.toByteArray()).joinToString("") { "%02x".format(it) } in expected }
    }

    /** A release build is never debuggable; this flag only appears when the APK was rebuilt. */
    private fun debuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun beingDebugged(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        // A native debugger / tracer (gdb, strace, frida in ptrace mode).
        val tracer = File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("TracerPid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
        }
        return tracer != null && tracer != 0
    }

    private fun hooked(): Boolean {
        val injected = File("/proc/self/maps").useLines { lines ->
            lines.any { line -> HOOK_MARKERS.any { line.contains(it, ignoreCase = true) } }
        }
        if (injected) return true
        return runCatching { Class.forName("de.robv.android.xposed.XposedBridge"); true }.getOrDefault(false)
    }
}
