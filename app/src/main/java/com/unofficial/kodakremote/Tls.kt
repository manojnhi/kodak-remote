package com.unofficial.kodakremote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * TLS with a client certificate that lives in the Android Keystore.
 * Android TVs use self-signed certificates, so the server certificate is not chain-validated;
 * it is authenticated by the pairing code (the code is bound to both public keys).
 */
object Tls {
    private const val ALIAS = "kodak_remote_client"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureIdentity() {
        if (keyStore().containsAlias(ALIAS)) return
        val now = Date()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS
            )
            .setCertificateSubject(X500Principal("CN=KodakRemoteUnofficial"))
            .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
            .setCertificateNotBefore(now)
            .setCertificateNotAfter(Date(now.time + 20L * 365 * 24 * 3600 * 1000))
            .build()
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    fun clientCert(): X509Certificate {
        ensureIdentity()
        return keyStore().getCertificate(ALIAS) as X509Certificate
    }

    private val keyManager = object : X509KeyManager {
        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String> = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            keyStore().getCertificateChain(ALIAS)?.map { it as X509Certificate }?.toTypedArray()
        override fun getPrivateKey(alias: String?): PrivateKey? = keyStore().getKey(ALIAS, null) as? PrivateKey
    }

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** Blocking. Call from Dispatchers.IO. */
    fun connect(host: String, port: Int, readTimeoutMs: Int): SSLSocket {
        ensureIdentity()
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
