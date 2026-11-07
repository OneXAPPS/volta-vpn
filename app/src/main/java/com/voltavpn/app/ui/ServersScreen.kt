package com.voltavpn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.R
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.Tier
import com.voltavpn.app.data.serverRanking

@Composable
fun ServersScreen(
    servers: List<Server>,
    selected: Server?,
    loading: Boolean,
    onSelect: (Server?) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    // One row per country (its best server); unreachable countries sink to the bottom.
    val perCountry = servers.groupBy { it.countryCode }
        .map { (_, list) -> list.sortedWith(serverRanking).first() }
        .sortedWith(compareBy<Server> { it.pingMs == -1 }.thenBy { it.tier.ordinal }.thenBy { it.displayName() })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.choose_location), fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ServerRow(
                    flag = "⚡", title = stringResource(R.string.fastest_server),
                    subtitle = stringResource(R.string.auto_pick), badge = null,
                    isSelected = selected == null, onClick = { onSelect(null) },
                )
            }
            items(perCountry, key = { it.countryCode }) { s ->
                ServerRow(
                    flag = s.flag, title = s.displayName(),
                    subtitle = when (s.pingMs) {
                        // Pings are only measured while the VPN is down (else they'd go through the tunnel).
                        null -> if (loading) stringResource(R.string.measuring) else "—"
                        -1 -> stringResource(R.string.offline)
                        else -> "${s.pingMs} ms"
                    } + if (s.unlocks.isNotEmpty()) " · " + stringResource(R.string.local_sites) else "",
                    badge = tierLabel(s), badgeColor = tierColor(s.tier),
                    isSelected = selected?.countryCode == s.countryCode,
                    dimmed = s.pingMs == -1,
                    onClick = { onSelect(s) },
                )
            }
            if (!loading && perCountry.isEmpty()) item {
                Text(stringResource(R.string.no_servers), color = VoltaColors.Muted, modifier = Modifier.padding(16.dp))
            }
            item { Text(stringResource(R.string.tier_legend), color = VoltaColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
fun tierLabel(s: Server): String = stringResource(
    when (s.tier) {
        Tier.PRIVATE -> R.string.badge_private
        Tier.NOLOG -> R.string.badge_nolog
        Tier.VERIFIED -> R.string.badge_verified
        Tier.COMMUNITY -> R.string.badge_community
    }
)

fun tierColor(t: Tier): Color = when (t) {
    Tier.PRIVATE -> VoltaColors.Bolt
    Tier.NOLOG -> Color(0xFF5BF0C9)
    Tier.VERIFIED -> VoltaColors.Connected
    Tier.COMMUNITY -> VoltaColors.Muted
}

@Composable
private fun ServerRow(
    flag: String, title: String, subtitle: String, badge: String?,
    isSelected: Boolean, onClick: () -> Unit,
    badgeColor: Color = VoltaColors.Muted, dimmed: Boolean = false,
) {
    // Kit pill: glowing cyan rim for the selected country, a quiet rim for the rest.
    GlowPill(
        Modifier.fillMaxWidth().alpha(if (dimmed) 0.5f else 1f),
        border = if (isSelected) KitBorders.Blue else listOf(Color(0xFF2A3A8C), Color(0xFF1C2766)),
        radius = 18.dp,
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flag, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = VoltaColors.Muted, fontSize = 13.sp)
            }
            if (badge != null) Text(badge, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Text("✓", color = Color(0xFF5BDCFF), fontWeight = FontWeight.Bold)
            }
        }
    }
}
