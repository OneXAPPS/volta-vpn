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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.R
import com.voltavpn.app.data.Server
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
    // One row per country (its best server) keeps the list short and simple.
    val perCountry = servers.groupBy { it.countryCode }
        .map { (_, list) -> list.sortedWith(serverRanking).first() }
        .sortedBy { it.countryName }

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
                Row2(
                    flag = "⚡", title = stringResource(R.string.fastest_server),
                    subtitle = stringResource(R.string.auto_pick), badge = null,
                    isSelected = selected == null, onClick = { onSelect(null) },
                )
            }
            items(perCountry, key = { it.countryCode }) { s ->
                Row2(
                    flag = s.flag, title = s.countryName,
                    subtitle = "${s.pingMs} ms · ${"%.1f".format(s.speedMbps)} Mbps",
                    badge = if (s.verified) stringResource(R.string.badge_verified) else stringResource(R.string.badge_community),
                    badgeColor = if (s.verified) VoltaColors.Connected else VoltaColors.Muted,
                    isSelected = selected?.countryCode == s.countryCode, onClick = { onSelect(s) },
                )
            }
            if (!loading && perCountry.isEmpty()) item {
                Text(stringResource(R.string.no_servers), color = VoltaColors.Muted, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun Row2(
    flag: String, title: String, subtitle: String, badge: String?,
    isSelected: Boolean, onClick: () -> Unit, badgeColor: Color = VoltaColors.Muted,
) {
    Surface(
        color = if (isSelected) VoltaColors.SurfaceHigh else VoltaColors.Surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                Text("✓", color = VoltaColors.Bolt, fontWeight = FontWeight.Bold)
            }
        }
    }
}
