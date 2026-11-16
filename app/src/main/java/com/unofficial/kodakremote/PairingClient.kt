package com.unofficial.kodakremote

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(@StringRes val res: Int, cause: Throwable? = null, info: String? = null) : Exception(cause) {
    /** Technical reason, shown in the error dialog so failures can be diagnosed. */
    val detail: String = info ?: cause?.let { it.javaClass.simpleName + ": " + it.message } ?: ""
}

/**
 * Android TV Remote v2 pairing (TV port 6467).
 * 1) begin(): TLS connect + handshake messages -> the TV shows a 6-character code
 * 2) finish(code): sends the secret derived from both public keys + the code
 */
class PairingClient {
    private var socket: SSLSocket? = null
    private var serverCert: X509Certificate? = null
    private var step = "connect"

    private fun send(inner: ByteArray) {
        val body = Pb.int(1, 2) + Pb.int(2, 200) + inner   // protocol_version=2, status=OK
        val out = socket!!.outputStream
        out.write(Pb.varint(body.size.toLong()) + body)
        out.flush()
    }

    private fun expect(field: Int) {
        val fields = Pb.parse(Pb.readFrame(socket!!.inputStream))
        val status = fields.firstOrNull { it.num == 2 }?.long ?: 200L
        val info = "step=$step status=$status expected=$field got=" + fields.map { it.num }
        if (status == 402L) throw PairingException(R.string.err_pair_wrong_code, null, info)
        if (status != 200L) throw PairingException(R.string.err_pair_refused, null, info)
        if (fields.none { it.num == field }) throw PairingException(R.string.err_pair_refused, null, info)
    }

    private inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (e: PairingException) {
            close(); throw e
        } catch (e: SocketTimeoutException) {
            close(); throw PairingException(R.string.err_pair_timeout, e, "step=$step " + e.javaClass.simpleName + ": " + e.message)
        } catch (e: IOException) {
            close(); throw PairingException(R.string.err_network, e, "step=$step " + e.javaClass.simpleName + ": " + e.message)
        }
    }

    suspend fun begin(host: String, clientName: String) = withContext(Dispatchers.IO) {
        close()
        guard {
            step = "tls-connect:6467"
            val s = Tls.connect(host, 6467, 15000)
            socket = s
            serverCert = s.session.peerCertificates[0] as X509Certificate
            step = "pairing-request"
            send(Pb.msg(10, Pb.str(1, "atvremote"), Pb.str(2, clientName)))          // pairing_request
            expect(11)                                                                // pairing_request_ack
            step = "options"
            send(Pb.msg(20, Pb.msg(1, Pb.int(1, 3), Pb.int(2, 6)), Pb.int(3, 1)))   // options: hex, 6 symbols, role=input
            expect(20)
            step = "configuration"
            send(Pb.msg(30, Pb.msg(1, Pb.int(1, 3), Pb.int(2, 6)), Pb.int(2, 1)))   // configuration
            expect(31)                                                                // TV now shows the code
        }
    }

    suspend fun finish(rawCode: String) = withContext(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        if (!Regex("^[0-9A-F]{6}$").matches(code)) throw PairingException(R.string.err_pair_wrong_code)
        guard {
            step = "secret"
            val cPub = Tls.clientCert().publicKey as RSAPublicKey
            val sPub = serverCert?.publicKey as? RSAPublicKey ?: throw PairingException(R.string.err_pair_refused)
            val md = MessageDigest.getInstance("SHA-256")
            md.update(strip(cPub.modulus.toByteArray()))
            md.update(strip(cPub.publicExponent.toByteArray()))
            md.update(strip(sPub.modulus.toByteArray()))
            md.update(strip(sPub.publicExponent.toByteArray()))
            md.update(hex(code.substring(2)))
            val digest = md.digest()
            if (digest[0] != hex(code.substring(0, 2))[0]) throw PairingException(R.string.err_pair_wrong_code)
            send(Pb.msg(40, Pb.bytes(1, digest)))                                     // pairing_secret
            expect(41)                                                                // pairing_secret_ack
            close()
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun strip(b: ByteArray): ByteArray {
        var i = 0
        while (i < b.size - 1 && b[i] == 0.toByte()) i++
        return b.copyOfRange(i, b.size)
    }

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
