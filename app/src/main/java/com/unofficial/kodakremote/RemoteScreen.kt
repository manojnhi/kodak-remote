package com.unofficial.kodakremote

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val RedKey = Color(0xFFD32F2F)
private val GreenKey = Color(0xFF388E3C)
private val YellowKey = Color(0xFFFBC02D)
private val BlueKey = Color(0xFF1976D2)

@Composable
fun RemoteScreen(
    vm: AppViewModel,
    s: AppSettings,
    onConnect: () -> Unit,
    onIr: () -> Unit,
    onSettings: () -> Unit,
    onCompat: () -> Unit,
) {
    val online by vm.online.collectAsStateWithLifecycle()
    val rstate by vm.remote.state.collectAsStateWithLifecycle()
    val isIr = s.mode == "ir"
    val press: (RemoteKey) -> Unit = { vm.press(it) }

    val statusText: String
    val dot: Color
    when {
        isIr && vm.ir.available -> { statusText = stringResource(R.string.ir_ready); dot = Good }
        isIr -> { statusText = stringResource(R.string.ir_no_blaster); dot = Danger }
        s.tvHost == null -> { statusText = stringResource(R.string.not_paired); dot = Muted }
        online == false -> { statusText = stringResource(R.string.offline); dot = Danger }
        rstate == RemoteClient.State.Ready -> { statusText = stringResource(R.string.online); dot = Good }
        else -> { statusText = stringResource(R.string.connecting); dot = Warn }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.tvName ?: stringResource(R.string.tv_default_name),
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, color = Muted, fontSize = 13.sp)
                    }
                }
                FilterChip(
                    selected = !isIr, onClick = { vm.setMode("wifi") },
                    label = { Text(stringResource(R.string.wifi_mode)) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = isIr, onClick = { vm.setMode("ir") },
                    label = { Text(stringResource(R.string.ir_mode)) }
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onConnect) { Text(stringResource(R.string.connect_tv)) }
                TextButton(onClick = onIr) { Text(stringResource(R.string.ir_profile)) }
                TextButton(onClick = onCompat) { Text(stringResource(R.string.compat_title)) }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_title)) }
            }

            if (!isIr && s.tvHost == null) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.pair_tv_prompt), color = Color.White)
                        Button(onClick = onConnect) { Text(stringResource(R.string.go_pair)) }
                    }
                }
            }

            // ---- power / mute / input
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                KeyButton(
                    label = "", description = stringResource(RemoteKey.POWER.desc),
                    onPress = { press(RemoteKey.POWER) },
                    modifier = Modifier.size(64.dp), container = Danger, shape = CircleShape,
                    icon = { PowerGlyph(Color.White) }
                )
                RK(RemoteKey.MUTE, press, Modifier.width(104.dp).height(52.dp), fontSize = 13.sp)
                RK(RemoteKey.SOURCE, press, Modifier.width(104.dp).height(52.dp), fontSize = 13.sp)
            }

            // ---- home/back/menu/settings
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(RemoteKey.HOME, RemoteKey.BACK, RemoteKey.MENU, RemoteKey.SETTINGS).forEach {
                    RK(it, press, Modifier.weight(1f).height(52.dp), fontSize = 12.sp)
                }
            }

            // ---- d-pad
            Box(Modifier.size(250.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                RK(RemoteKey.OK, press, Modifier.size(84.dp), shape = CircleShape, container = Accent, fontSize = 18.sp)
                RK(RemoteKey.UP, press, Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(64.dp), shape = CircleShape, repeat = true, fontSize = 20.sp)
                RK(RemoteKey.DOWN, press, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(64.dp), shape = CircleShape, repeat = true, fontSize = 20.sp)
                RK(RemoteKey.LEFT, press, Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(64.dp), shape = CircleShape, repeat = true, fontSize = 20.sp)
                RK(RemoteKey.RIGHT, press, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(64.dp), shape = CircleShape, repeat = true, fontSize = 20.sp)
            }

            // ---- volume / info-guide-exit / channel
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Rocker(stringResource(R.string.vol_label), RemoteKey.VOL_UP, RemoteKey.VOL_DOWN, press)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RK(RemoteKey.INFO, press, Modifier.fillMaxWidth().height(52.dp), fontSize = 12.sp)
                    RK(RemoteKey.GUIDE, press, Modifier.fillMaxWidth().height(52.dp), fontSize = 12.sp)
                    RK(RemoteKey.EXIT, press, Modifier.fillMaxWidth().height(52.dp), fontSize = 12.sp)
                }
                Rocker(stringResource(R.string.ch_label), RemoteKey.CH_UP, RemoteKey.CH_DOWN, press)
            }

            // ---- media
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RemoteKey.PREV, RemoteKey.REWIND, RemoteKey.PLAY_PAUSE, RemoteKey.FFWD, RemoteKey.NEXT).forEach {
                    RK(it, press, Modifier.weight(1f).height(52.dp), fontSize = 12.sp)
                }
            }

            // ---- colour keys
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RK(RemoteKey.RED, press, Modifier.weight(1f).height(40.dp), container = RedKey)
                RK(RemoteKey.GREEN, press, Modifier.weight(1f).height(40.dp), container = GreenKey)
                RK(RemoteKey.YELLOW, press, Modifier.weight(1f).height(40.dp), container = YellowKey)
                RK(RemoteKey.BLUE, press, Modifier.weight(1f).height(40.dp), container = BlueKey)
            }

            // ---- number pad (subtitle / 0 / audio on the bottom row)
            val rows = listOf(
                listOf(RemoteKey.N1, RemoteKey.N2, RemoteKey.N3),
                listOf(RemoteKey.N4, RemoteKey.N5, RemoteKey.N6),
                listOf(RemoteKey.N7, RemoteKey.N8, RemoteKey.N9),
                listOf(RemoteKey.SUBTITLE, RemoteKey.N0, RemoteKey.AUDIO),
            )
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.forEach { k ->
                        val small = k == RemoteKey.SUBTITLE || k == RemoteKey.AUDIO
                        RK(k, press, Modifier.weight(1f).height(60.dp), fontSize = if (small) 13.sp else 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.unofficial), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Rocker(title: String, up: RemoteKey, down: RemoteKey, onKey: (RemoteKey) -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(38.dp)).background(Panel).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RK(up, onKey, Modifier.size(64.dp), shape = CircleShape, repeat = true, fontSize = 24.sp)
        Text(title, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 14.dp))
        RK(down, onKey, Modifier.size(64.dp), shape = CircleShape, repeat = true, fontSize = 24.sp)
    }
}
