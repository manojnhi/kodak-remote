package com.unofficial.kodakremote

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

/**
 * TLS with a self-signed client certificate.
 * The key pair is a normal software RSA key stored in the app-private folder (the Android Keystore
 * RSA key failed inside Conscrypt's TLS handshake on some phones).
 * Android TVs use self-signed certificates, so the server certificate is not chain-validated;
 * it is authenticated by the pairing code (the code is bound to both public keys).
 */
object Tls {
    private const val ALIAS = "kodak_remote_client"

    private var dir: File? = null
    private var key: PrivateKey? = null
    private var cert: X509Certificate? = null

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    @Synchronized
    private fun identity() {
        if (key != null && cert != null) return
        val d = dir ?: throw IllegalStateException("Tls.init not called")
        val keyFile = File(d, "client_key.pk8")
        val certFile = File(d, "client_cert.der")
        if (keyFile.exists() && certFile.exists()) {
            try {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = certFile.inputStream().use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
                }
                return
            } catch (e: Exception) {
                // damaged files: fall through and create a new identity
            }
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val c = SelfSignedCert.generate(kp, "KodakRemoteUnofficial")
        keyFile.writeBytes(kp.private.encoded)
        certFile.writeBytes(c.encoded)
        key = kp.private
        cert = c
    }

    fun clientCert(): X509Certificate {
        identity()
        return cert!!
    }

    private val keyManager = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String> = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
            identity()
            return arrayOf(cert!!)
        }
        override fun getPrivateKey(alias: String?): PrivateKey? {
            identity()
            return key
        }
    }

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** Blocking. Call from Dispatchers.IO. */
    fun connect(host: String, port: Int, readTimeoutMs: Int): SSLSocket {
        identity()
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(arrayOf<KeyManager>(keyManager), arrayOf<TrustManager>(trustAll), null)
        val s = ctx.socketFactory.createSocket() as SSLSocket
        try {
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = readTimeoutMs
            s.startHandshake()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
        return s
    }
}