package com.voltavpn.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.BuildConfig
import com.voltavpn.app.R

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val privateDns = remember { isPrivateDnsActive(context) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.protect_title), color = VoltaColors.Bolt, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Section(
                stringResource(R.string.killswitch_title), stringResource(R.string.killswitch_body),
                action = stringResource(R.string.open_settings),
            ) { open(context, Settings.ACTION_VPN_SETTINGS) }
            Section(
                stringResource(R.string.privatedns_title) + if (privateDns) "  ✓" else "",
                stringResource(if (privateDns) R.string.privatedns_on else R.string.privatedns_body),
                action = if (privateDns) null else stringResource(R.string.open_settings),
            ) { open(context, "android.settings.PRIVATE_DNS_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS) }
            Section(stringResource(R.string.https_title), stringResource(R.string.https_body))

            Spacer(Modifier.height(8.dp))
            Section(stringResource(R.string.privacy_title), stringResource(R.string.privacy_body))
            Section(stringResource(R.string.community_title), stringResource(R.string.community_body))
            Section(stringResource(R.string.about_title), stringResource(R.string.about_body))
            Section(stringResource(R.string.opensource_title), stringResource(R.string.opensource_body))
            Text(
                "Volta VPN ${BuildConfig.VERSION_NAME}", color = VoltaColors.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** True when Android already encrypts DNS (DNS-over-TLS) on some network. */
private fun isPrivateDnsActive(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    @Suppress("DEPRECATION")
    cm.allNetworks.any { cm.getLinkProperties(it)?.isPrivateDnsActive == true }
}.getOrDefault(false)

private fun open(context: Context, vararg actions: String) {
    for (action in actions) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

@Composable
private fun Section(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(color = VoltaColors.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = VoltaColors.Muted, fontSize = 14.sp)
            if (action != null) TextButton(onClick = onAction) { Text(action, color = VoltaColors.Bolt) }
        }
    }
    Spacer(Modifier.height(12.dp))
}
