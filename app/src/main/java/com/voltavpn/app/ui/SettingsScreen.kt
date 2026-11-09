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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.BuildConfig
import com.voltavpn.app.R
import com.voltavpn.app.vpn.DnsMode
import com.voltavpn.app.vpn.ProtectionPrefs

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
            SectionHeader(stringResource(R.string.protect_title))
            Section(
                stringResource(R.string.killswitch_title), stringResource(R.string.killswitch_body),
                action = stringResource(R.string.open_settings),
            ) { open(context, Settings.ACTION_VPN_SETTINGS) }
            Section(
                stringResource(R.string.privatedns_title) + if (privateDns) "  ✓" else "",
                stringResource(if (privateDns) R.string.privatedns_on else R.string.privatedns_body),
                action = if (privateDns) null else stringResource(R.string.open_settings),
            ) { open(context, "android.settings.PRIVATE_DNS_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS) }
            DnsSection()
            Section(stringResource(R.string.https_title), stringResource(R.string.https_body))

            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.about_section))
            ExpandableSection(stringResource(R.string.community_title), stringResource(R.string.community_body))
            ExpandableSection(stringResource(R.string.riseup_title), stringResource(R.string.riseup_body))
            ExpandableSection(
                stringResource(R.string.privacy_title),
                stringResource(R.string.privacy_body) + "\n\n" + stringResource(R.string.privacy_servers),
            )
            ExpandableSection(stringResource(R.string.about_title), stringResource(R.string.about_body))
            ExpandableSection(stringResource(R.string.opensource_title), stringResource(R.string.opensource_body))
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
private fun SectionHeader(title: String) {
    Text(
        title, color = VoltaColors.Bolt, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

/** Which resolver the tunnel uses; applies from the next connection. */
@Composable
private fun DnsSection() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ProtectionPrefs.dnsMode(context)) }
    Surface(color = VoltaColors.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dns_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Column(Modifier.selectableGroup()) {
                for ((m, label, desc) in listOf(
                    Triple(DnsMode.STANDARD, R.string.dns_standard, R.string.dns_standard_desc),
                    Triple(DnsMode.MALWARE, R.string.dns_malware, R.string.dns_malware_desc),
                    Triple(DnsMode.TRACKERS, R.string.dns_trackers, R.string.dns_trackers_desc),
                )) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = mode == m, role = Role.RadioButton) {
                                mode = m; ProtectionPrefs.setDnsMode(context, m)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(label))
                            Text(stringResource(desc), color = VoltaColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Text(stringResource(R.string.dns_note), color = VoltaColors.Muted, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
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

/** Reference text: folded by default so the settings people change stay in view. */
@Composable
private fun ExpandableSection(title: String, body: String) {
    var expanded by remember { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    // The chevron is mirrored in right-to-left, so "points down" is the opposite rotation there.
    val openRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    Surface(
        onClick = { expanded = !expanded }, color = VoltaColors.Surface, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = state },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_chevron), contentDescription = null, tint = VoltaColors.Muted,
                    modifier = Modifier.rotate(if (expanded) openRotation else 0f),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(body, color = VoltaColors.Muted, fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
