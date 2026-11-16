package com.unofficial.kodakremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredTv(val name: String, val host: String, val port: Int) {
    val looksKodak: Boolean get() = name.contains("kodak", ignoreCase = true)
}

object Net {
    fun isWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun canConnect(host: String, port: Int, timeoutMs: Int = 1500): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            } catch (e: IOException) {
                false
            }
        }

    val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
}

/** mDNS discovery of Android TV Remote v2 services (_androidtvremote2._tcp) on the local Wi-Fi. */
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _devices = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val devices: StateFlow<List<DiscoveredTv>> = _devices
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var timeoutJob: Job? = null

    fun start(scope: CoroutineScope, timeoutMs: Long = 10_000) {
        stop()
        _devices.value = emptyList()
        _scanning.value = true
        lock = wifi.createMulticastLock("kodak_remote").apply {
            setReferenceCounted(false)
            acquire()
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(this@TvDiscovery) { pending.add(info) }
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _devices.update { list -> list.filterNot { it.name == info.serviceName } }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { _scanning.value = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        try {
            nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            _scanning.value = false
        }
        timeoutJob = scope.launch {
            delay(timeoutMs)
            stop()
        }
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    done()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val h = serviceInfo.host
                    if (h is Inet4Address) {
                        val tv = DiscoveredTv(serviceInfo.serviceName, h.hostAddress ?: "", 6466)
                        _devices.update { list -> if (list.any { it.host == tv.host }) list else list + tv }
                    }
                    done()
                }
                private fun done() {
                    synchronized(this@TvDiscovery) { resolving = false }
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            resolving = false
        }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { lock?.takeIf { it.isHeld }?.release() }
        lock = null
        _scanning.value = false
        timeoutJob?.cancel()
    }
}
