package com.unofficial.kodakremote

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Check { Pending, Running, Ok, Fail }

data class CompatState(
    val running: Boolean = false,
    val done: Boolean = false,
    val wifi: Check = Check.Pending,
    val tvFound: Check = Check.Pending,
    val ports: Check = Check.Pending,
    val ir: Check = Check.Pending,
    val wifiPossible: Boolean = false,
    val host: String? = null,
    val detail: String = "",
)

enum class PairStage { Idle, Starting, AwaitCode, Verifying, Done, Failed }

data class PairUi(
    val stage: PairStage = PairStage.Idle,
    val host: String = "",
    val errorRes: Int? = null,
    val detail: String = "",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val ctx = app

    val settings: StateFlow<AppSettings?> =
        prefs.flow.stateIn<AppSettings?>(viewModelScope, SharingStarted.Eagerly, null)

    val discovery = TvDiscovery(app)
    val remote = RemoteClient(viewModelScope)
    val ir = IrController(app)
    private val pairing = PairingClient()

    val pair = MutableStateFlow(PairUi())
    val compat = MutableStateFlow(CompatState())
    val online = MutableStateFlow<Boolean?>(null)
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    private var pendingName: String? = null

    init {
        Tls.init(app)
        // Online/offline polling + auto-connect for the paired TV (local network only).
        viewModelScope.launch {
            settings.filterNotNull().map { it.tvHost }.distinctUntilChanged().collectLatest { host ->
                if (host == null) {
                    online.value = null
                    remote.disconnect()
                } else {
                    remote.disconnect()
                    while (isActive) {
                        val up = Net.canConnect(host, 6466, 1500)
                        online.value = up
                        if (up && settings.value?.mode == "wifi" && remote.state.value == RemoteClient.State.Disconnected) {
                            remote.connect(host)
                        }
                        delay(5000)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ keys
    fun press(key: RemoteKey) {
        val s = settings.value ?: return
        if (s.mode == "ir") {
            testIr(key)
            return
        }
        val host = s.tvHost
        viewModelScope.launch {
            if (host == null) {
                messages.emit(R.string.msg_not_paired)
            } else if (!remote.sendKey(key.androidCode)) {
                messages.emit(R.string.msg_retry)
                remote.connect(host)
            }
        }
    }

    fun testIr(key: RemoteKey) {
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val code = s.irCodes[key.name]
            when {
                !ir.available -> messages.emit(R.string.ir_no_blaster)
                code == null -> messages.emit(R.string.ir_code_unset)
                else -> {
                    ir.send(s.irAddress, code)
                    messages.emit(R.string.ir_sent)
                }
            }
        }
    }

    fun saveIrCode(key: RemoteKey, code: Int?) {
        viewModelScope.launch { prefs.updateIrCode(key.name, code) }
    }

    fun saveIrAddress(address: Int) {
        viewModelScope.launch { prefs.set(PK.irAddress, address) }
    }

    // ------------------------------------------------------------ settings
    fun finishOnboarding() { viewModelScope.launch { prefs.set(PK.onboarded, true) } }
    fun setMode(mode: String) { viewModelScope.launch { prefs.set(PK.mode, mode) } }
    fun setHaptics(on: Boolean) { viewModelScope.launch { prefs.set(PK.haptics, on) } }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            prefs.set(PK.language, tag)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun unpair() {
        viewModelScope.launch {
            remote.disconnect()
            prefs.remove(PK.tvHost, PK.tvName)
        }
    }

    // ------------------------------------------------------------ discovery + pairing
    fun startScan() { discovery.start(viewModelScope) }

    fun startPairing(host: String, name: String?) {
        pendingName = name
        pair.value = PairUi(PairStage.Starting, host)
        viewModelScope.launch {
            try {
                pairing.begin(host, "Kodak Remote (Unofficial)")
                pair.value = PairUi(PairStage.AwaitCode, host)
            } catch (e: PairingException) {
                pair.value = PairUi(PairStage.Failed, host, e.res, e.detail)
            }
        }
    }

    fun submitCode(code: String) {
        val host = pair.value.host
        pair.value = PairUi(PairStage.Verifying, host)
        viewModelScope.launch {
            try {
                pairing.finish(code)
                prefs.set(PK.tvHost, host)
                prefs.set(PK.tvName, pendingName ?: host)
                pair.value = PairUi(PairStage.Done, host)
            } catch (e: PairingException) {
                pair.value = PairUi(PairStage.Failed, host, e.res, e.detail)
            }
        }
    }

    fun cancelPairing() {
        pairing.close()
        pair.value = PairUi()
    }

    fun resetPair() { pair.value = PairUi() }

    // ------------------------------------------------------------ compatibility check
    fun runCompat(manualHost: String?) {
        viewModelScope.launch {
            var c = CompatState(running = true, wifi = Check.Running)
            compat.value = c
            val wifi = Net.isWifi(ctx)
            c = c.copy(wifi = if (wifi) Check.Ok else Check.Fail, tvFound = Check.Running)
            compat.value = c

            var host: String? = manualHost?.trim()?.takeIf { Net.ipv4.matches(it) }
            if (wifi && host == null) {
                discovery.start(viewModelScope)
                var waited = 0
                while (waited < 16 && discovery.devices.value.isEmpty()) {
                    delay(500)
                    waited++
                }
                val list = discovery.devices.value
                host = (list.firstOrNull { it.looksKodak } ?: list.firstOrNull())?.host
                discovery.stop()
            }
            c = c.copy(tvFound = if (host != null) Check.Ok else Check.Fail, host = host, ports = Check.Running)
            compat.value = c

            var portsOk = false
            var p1 = false
            var p2 = false
            if (wifi && host != null) {
                p1 = Net.canConnect(host, 6466)
                p2 = Net.canConnect(host, 6467)
                portsOk = p1 && p2
            }
            val info = "wifi=$wifi host=$host port6466=$p1 port6467=$p2 services=" + discovery.devices.value.size
            c = c.copy(ports = if (portsOk) Check.Ok else Check.Fail, ir = if (ir.available) Check.Ok else Check.Fail, detail = info)
            compat.value = c.copy(running = false, done = true, wifiPossible = wifi && host != null && portsOk, detail = info)
        }
    }

    override fun onCleared() {
        remote.disconnect()
        discovery.stop()
        pairing.close()
    }
}