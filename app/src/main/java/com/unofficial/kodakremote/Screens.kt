package com.unofficial.kodakremote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ScreenFrame(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backDesc = stringResource(R.string.nav_back)
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backDesc }) {
                Text("◀", fontSize = 18.sp)
            }
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        val m = if (scroll) Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        else Modifier.fillMaxWidth().weight(1f)
        Column(m.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun InfoCard(text: String, color: Color = Panel) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Color.White, modifier = Modifier.padding(14.dp))
    }
}

// ------------------------------------------------------------------ onboarding
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val titles = listOf(R.string.onb1_title, R.string.onb2_title, R.string.onb3_title)
    val bodies = listOf(R.string.onb1_body, R.string.onb2_body, R.string.onb3_body)
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(titles[page]), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(bodies[page]), color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) Accent else Muted)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (pager.currentPage < 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.skip)) }
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.next))
                }
            }
        } else {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.check_compat))
            }
        }
    }
}

// ------------------------------------------------------------------ compatibility check
@Composable
fun CompatScreen(vm: AppViewModel, onBack: () -> Unit, onPair: () -> Unit, onUseIr: () -> Unit) {
    val c by vm.compat.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf("") }
    ScreenFrame(stringResource(R.string.compat_title), onBack) {
        OutlinedTextField(
            value = ip, onValueChange = { ip = it.filter { ch -> ch.isDigit() || ch == '.' }.take(15) },
            label = { Text(stringResource(R.string.compat_manual_hint)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(onClick = { vm.runCompat(ip) }, enabled = !c.running, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.compat_run))
        }
        CheckRow(stringResource(R.string.chk_wifi), c.wifi)
        CheckRow(stringResource(R.string.chk_tv), c.tvFound)
        CheckRow(stringResource(R.string.chk_ports), c.ports)
        CheckRow(stringResource(R.string.chk_ir), c.ir)
        if (c.done) {
            if (c.wifiPossible) {
                InfoCard(stringResource(R.string.compat_ok), Color(0xFF1B3A22))
                Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_pair)) }
            } else {
                InfoCard(stringResource(R.string.compat_unsupported), Color(0xFF4A1A1A))
            }
            OutlinedButton(onClick = onUseIr, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.use_ir))
            }
        }
        InfoCard(stringResource(R.string.no_cloud))
    }
}

@Composable
private fun CheckRow(label: String, state: Check) {
    val (glyph, color) = when (state) {
        Check.Pending -> "•" to Muted
        Check.Running -> "…" to Warn
        Check.Ok -> "✓" to Good
        Check.Fail -> "✗" to Danger
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(glyph, color = color, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Text(label, color = Color.White)
    }
}

// ------------------------------------------------------------------ connect / pair
@Composable
fun ConnectScreen(vm: AppViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val devices by vm.discovery.devices.collectAsStateWithLifecycle()
    val scanning by vm.discovery.scanning.collectAsStateWithLifecycle()
    val pair by vm.pair.collectAsStateWithLifecycle()
    var ip by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.startScan() }
    DisposableEffect(Unit) { onDispose { vm.discovery.stop() } }
    LaunchedEffect(pair.stage) {
        if (pair.stage == PairStage.Done) {
            vm.resetPair()
            onDone()
        }
    }

    ScreenFrame(stringResource(R.string.connect_title), onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.startScan() }, enabled = !scanning) { Text(stringResource(R.string.scan)) }
            Spacer(Modifier.width(12.dp))
            if (scanning) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scanning), color = Muted)
            }
        }
        if (devices.isNotEmpty()) {
            Text(stringResource(R.string.devices_found), color = Muted)
            devices.forEach { d ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    modifier = Modifier.fillMaxWidth().clickable { vm.startPairing(d.host, d.name) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, color = Color.White, fontWeight = FontWeight.Bold)
                            if (d.looksKodak) {
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.badge_kodak), color = Accent, fontSize = 12.sp)
                            }
                        }
                        Text(d.host, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        } else if (!scanning) {
            InfoCard(stringResource(R.string.no_devices))
            InfoCard(stringResource(R.string.compat_unsupported), Color(0xFF4A1A1A))
        }
        OutlinedTextField(
            value = ip, onValueChange = { ip = it.filter { ch -> ch.isDigit() || ch == '.' }.take(15) },
            label = { Text(stringResource(R.string.manual_ip)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(
            onClick = { vm.startPairing(ip.trim(), null) },
            enabled = Net.ipv4.matches(ip.trim()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.pair)) }
    }

    PairDialog(vm, pair)
}

@Composable
private fun PairDialog(vm: AppViewModel, pair: PairUi) {
    if (pair.stage == PairStage.Idle || pair.stage == PairStage.Done) return
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { vm.cancelPairing() },
        confirmButton = {
            when (pair.stage) {
                PairStage.AwaitCode -> TextButton(onClick = { vm.submitCode(code) }, enabled = code.length == 6) {
                    Text(stringResource(R.string.submit))
                }
                PairStage.Failed -> TextButton(onClick = { vm.cancelPairing() }) { Text(stringResource(R.string.close)) }
                else -> {}
            }
        },
        dismissButton = {
            if (pair.stage == PairStage.AwaitCode) {
                TextButton(onClick = { vm.cancelPairing() }) { Text(stringResource(R.string.cancel)) }
            }
        },
        title = { Text(stringResource(R.string.connect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (pair.stage) {
                    PairStage.Starting -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.pair_starting))
                    }
                    PairStage.AwaitCode -> {
                        Text(stringResource(R.string.pair_code_title))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { v ->
                                code = v.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(6)
                            },
                            label = { Text(stringResource(R.string.pair_code_hint)) },
                            singleLine = true
                        )
                    }
                    PairStage.Verifying -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.pair_verifying))
                    }
                    PairStage.Failed -> {
                        Text(stringResource(pair.errorRes ?: R.string.err_network))
                        Text(stringResource(R.string.compat_unsupported), color = Warn)
                    }
                    else -> {}
                }
            }
        }
    )
}

// ------------------------------------------------------------------ IR profile
@Composable
fun IrScreen(vm: AppViewModel, s: AppSettings, onBack: () -> Unit) {
    var addr by remember { mutableStateOf(s.irAddress.toString(16).uppercase()) }
    ScreenFrame(stringResource(R.string.ir_title), onBack, scroll = false) {
        if (!vm.ir.available) InfoCard(stringResource(R.string.ir_no_blaster), Color(0xFF4A1A1A))
        Text(stringResource(R.string.ir_help), color = Muted, fontSize = 13.sp)
        OutlinedTextField(
            value = addr,
            onValueChange = { v ->
                addr = v.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(4)
                vm.saveIrAddress(addr.toIntOrNull(16) ?: 0)
            },
            label = { Text(stringResource(R.string.ir_address)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.weight(1f)) {
            items(RemoteKey.entries.toList(), key = { it.name }) { key ->
                IrKeyRow(vm, key, s.irCodes[key.name])
            }
        }
    }
}

@Composable
private fun IrKeyRow(vm: AppViewModel, key: RemoteKey, initial: Int?) {
    var text by remember { mutableStateOf(initial?.toString(16)?.uppercase() ?: "") }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(key.desc), color = Color.White, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(2)
                vm.saveIrCode(key, text.toIntOrNull(16))
            },
            placeholder = { Text("hex") },
            singleLine = true, modifier = Modifier.width(90.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { vm.testIr(key) }) { Text(stringResource(R.string.ir_test)) }
    }
}

// ------------------------------------------------------------------ settings
@Composable
fun SettingsScreen(vm: AppViewModel, s: AppSettings, onBack: () -> Unit, onCompat: () -> Unit) {
    ScreenFrame(stringResource(R.string.settings_title), onBack) {
        Text(stringResource(R.string.language), color = Muted)
        listOf("en" to R.string.english, "hi" to R.string.hindi).forEach { (tag, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setLanguage(tag) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = s.language == tag, onClick = { vm.setLanguage(tag) })
                Text(stringResource(label), color = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.haptics), color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = s.haptics, onCheckedChange = { vm.setHaptics(it) })
        }
        if (s.tvHost != null) {
            InfoCard((s.tvName ?: "") + "  " + s.tvHost)
            OutlinedButton(onClick = { vm.unpair() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.unpair))
            }
        }
        OutlinedButton(onClick = onCompat, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.run_compat_again))
        }
        Text(stringResource(R.string.about), color = Muted)
        InfoCard(stringResource(R.string.app_description))
        InfoCard(stringResource(R.string.no_cloud))
    }
}
