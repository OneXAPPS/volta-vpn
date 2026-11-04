package com.voltavpn.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltavpn.app.BuildConfig
import com.voltavpn.app.R

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section(stringResource(R.string.about_title), stringResource(R.string.about_body))
            Section(stringResource(R.string.privacy_title), stringResource(R.string.privacy_body))
            Section(stringResource(R.string.community_title), stringResource(R.string.community_body))
            Section(stringResource(R.string.opensource_title), stringResource(R.string.opensource_body))
            Text(
                "Volta VPN ${BuildConfig.VERSION_NAME}", color = VoltaColors.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Surface(color = VoltaColors.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = VoltaColors.Muted, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
}
