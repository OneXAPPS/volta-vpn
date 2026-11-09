package com.voltavpn.app.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.R
import com.voltavpn.app.data.Server
import com.voltavpn.app.data.Tier
import com.voltavpn.app.data.bestOfCountry

@Composable
fun ServersScreen(
    servers: List<Server>,
    selected: Server?,
    loading: Boolean,
    cooledCountries: Set<String>,
    onSelect: (Server?) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    // One row per country (its best reachable server). Ones that should work come first,
    // grouped by trust and fastest first within each group.
    val perCountry = remember(servers, cooledCountries) {
        servers.groupBy { it.countryCode }
            .mapNotNull { (_, list) -> list.bestOfCountry() }
            .sortedWith(
                compareBy<Server> { it.pingMs == -1 }
                    .thenBy { it.countryCode in cooledCountries }
                    .thenBy { it.tier.ordinal }
                    .thenBy { it.pingMs ?: Int.MAX_VALUE }
                    .thenBy { it.displayName() }
            )
    }
    var showTierInfo by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Open on the current pick when it would otherwise start below the fold.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        val index = perCountry.indexOfFirst { it.countryCode == selected?.countryCode } + 1
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (index >= lastVisible && index > 0) listState.scrollToItem(index)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.choose_location), fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showTierInfo = true }) {
                Icon(painterResource(R.drawable.ic_info), stringResource(R.string.tier_info_title))
            }
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) {
                Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ServerRow(
                    flag = "⚡", title = stringResource(R.string.best_server),
                    subtitle = stringResource(R.string.auto_pick), badge = null,
                    isSelected = selected == null, onClick = { onSelect(null) },
                )
            }
            items(perCountry, key = { it.countryCode }) { s ->
                val cooled = s.countryCode in cooledCountries
                val status = when {
                    s.pingMs == -1 -> stringResource(R.string.offline)
                    cooled -> stringResource(R.string.cooldown_hint)
                    // Pings are only measured while the VPN is down (else they'd go through the tunnel).
                    s.pingMs == null -> if (loading) stringResource(R.string.measuring) else "—"
                    else -> formatPing(s.pingMs)
                }
                ServerRow(
                    flag = s.flag, title = s.displayName(),
                    subtitle = status + if (s.unlocks.isNotEmpty()) " · " + stringResource(R.string.local_sites) else "",
                    badge = tierLabel(s), badgeColor = tierColor(s.tier),
                    isSelected = selected?.countryCode == s.countryCode,
                    unavailable = s.pingMs == -1 || cooled,
                    onClick = { onSelect(s) },
                )
            }
            if (!loading && perCountry.isEmpty()) item {
                Text(stringResource(R.string.no_servers_list), color = VoltaColors.Muted, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (showTierInfo) TierInfoDialog(servers.map { it.tier }.toSet()) { showTierInfo = false }
}

/** Explains only the tiers actually on offer, so it never describes servers that don't exist. */
@Composable
private fun TierInfoDialog(tiers: Set<Tier>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.tier_info_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (tier in Tier.entries.filter { it in tiers }) {
                    Column {
                        Text(stringResource(tierLabelRes(tier)), color = tierColor(tier), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(stringResource(tierDescriptionRes(tier)), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
    )
}

private fun tierLabelRes(t: Tier) = when (t) {
    Tier.PRIVATE -> R.string.badge_private
    Tier.NOLOG -> R.string.badge_nolog
    Tier.VERIFIED -> R.string.badge_verified
    Tier.COMMUNITY -> R.string.badge_community
}

private fun tierDescriptionRes(t: Tier) = when (t) {
    Tier.PRIVATE -> R.string.tier_private_desc
    Tier.NOLOG -> R.string.tier_nolog_desc
    Tier.VERIFIED -> R.string.tier_verified_desc
    Tier.COMMUNITY -> R.string.tier_community_desc
}

@Composable
fun tierLabel(s: Server): String = stringResource(tierLabelRes(s.tier))

fun tierColor(t: Tier): Color = when (t) {
    Tier.PRIVATE -> VoltaColors.TierVolta
    Tier.NOLOG -> VoltaColors.TierNoLog
    Tier.VERIFIED -> VoltaColors.TierTested
    Tier.COMMUNITY -> VoltaColors.TierUntested
}

@Composable
private fun ServerRow(
    flag: String, title: String, subtitle: String, badge: String?,
    isSelected: Boolean, onClick: () -> Unit,
    badgeColor: Color = VoltaColors.Muted, unavailable: Boolean = false,
) {
    Surface(
        selected = isSelected, onClick = onClick,
        color = if (isSelected) VoltaColors.SurfaceHigh else VoltaColors.Surface,
        contentColor = VoltaColors.Text,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Only the flag fades: dimming the text would drop it below readable contrast.
            Text(
                flag, fontSize = 26.sp,
                modifier = Modifier.alpha(if (unavailable) 0.5f else 1f).clearAndSetSemantics { },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (unavailable) VoltaColors.Muted else VoltaColors.Text)
                Text(subtitle, color = VoltaColors.Muted, fontSize = 13.sp)
            }
            if (badge != null) Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Text("✓", color = VoltaColors.Bolt, fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}
