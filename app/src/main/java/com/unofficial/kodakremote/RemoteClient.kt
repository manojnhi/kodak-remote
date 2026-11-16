package com.unofficial.kodakremote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.net.ssl.SSLSocket

/** Android TV Remote v2 control connection (TV port 6466). Local network only. */
class RemoteClient(private val scope: CoroutineScope) {
    enum class State { Disconnected, Connecting, Ready }

    private val _state = MutableStateFlow(State.Disconnected)
    val state: StateFlow<State> = _state

    private var job: Job? = null
    private var socket: SSLSocket? = null
    @Volatile private var out: OutputStream? = null
    private val writeLock = Mutex()

    fun connect(host: String) {
        if (_state.value != State.Disconnected) return
        _state.value = State.Connecting
        job = scope.launch(Dispatchers.IO) {
            try {
                val s = Tls.connect(host, 6466, 30000)
                socket = s
                out = s.outputStream
                val input = s.inputStream
                while (isActive) {
                    val fields = Pb.parse(Pb.readFrame(input))
                    for (f in fields) {
                        when (f.num) {
                            1 -> write(configureReply())
                            2 -> {
                                write(Pb.msg(2, Pb.int(1, 622)))
                                _state.value = State.Ready
                            }
                            8 -> {
                                val v = Pb.parse(f.bytes ?: ByteArray(0)).firstOrNull { it.num == 1 }?.long ?: 0L
                                write(Pb.msg(9, Pb.int(1, v)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // connection ended; state is reset below
            } finally {
                runCatching { socket?.close() }
                socket = null
                out = null
                _state.value = State.Disconnected
            }
        }
    }

    fun disconnect() {
        runCatching { socket?.close() }
        job?.cancel()
        job = null
        _state.value = State.Disconnected
    }

    suspend fun sendKey(code: Int): Boolean {
        if (_state.value != State.Ready) return false
        return try {
            write(Pb.msg(10, Pb.int(1, code), Pb.int(2, 3)))   // key_inject: key_code, direction=SHORT
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun configureReply(): ByteArray = Pb.msg(
        1,
        Pb.int(1, 622),
        Pb.msg(
            2,
            Pb.str(1, "KodakRemote"), Pb.str(2, "Unofficial"), Pb.int(3, 1), Pb.str(4, "1"),
            Pb.str(5, "com.unofficial.kodakremote"), Pb.str(6, "1.0")
        )
    )

    private suspend fun write(payload: ByteArray) {
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                val o = out ?: throw IOException("not connected")
                o.write(Pb.varint(payload.size.toLong()) + payload)
                o.flush()
            }
        }
    }
}
