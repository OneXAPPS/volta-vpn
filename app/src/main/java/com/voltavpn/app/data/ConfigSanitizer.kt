package com.voltavpn.app.data

/**
 * Public VPN servers hand us raw OpenVPN configs. Strip every directive that could
 * run code, write files, or weaken our own leak protection before the engine sees it.
 */
object ConfigSanitizer {
    private val blocked = setOf(
        "script-security", "up", "down", "route-up", "route-pre-down", "ipchange",
        "tls-verify", "client-connect", "client-disconnect", "learn-address",
        "auth-user-pass-verify", "plugin", "setenv", "setenv-safe", "management",
        "management-hold", "log", "log-append", "status", "writepid", "daemon",
        "chroot", "user", "group", "cd", "redirect-private", "dhcp-option",
        "block-outside-dns", "pull-filter", "route-nopull", "allow-pull-fqdn",
    )

    fun sanitize(config: String): String = buildString {
        var inBlock = false
        for (raw in config.lineSequence()) {
            val line = raw.trim()
            // Inline blocks like <ca>...</ca> are passed through untouched.
            if (line.startsWith("<") && !line.startsWith("</")) inBlock = true
            if (inBlock) {
                appendLine(line)
                if (line.startsWith("</")) inBlock = false
                continue
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            val directive = line.substringBefore(' ').substringBefore('\t').lowercase()
            if (directive in blocked) continue
            appendLine(line)
        }
    }
}
