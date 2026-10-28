package app.linkphone.core.crypto

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Pins the exact leaf certificate shown in the pairing QR. Hostname verification
 * is intentionally disabled: on a LAN the certificate fingerprint is the PC's
 * identity and the host/IP is only a route to it.
 */
class PinnedTrustManager(private val expectedFingerprintHex: String) : X509TrustManager {
    private val expected = expectedFingerprintHex.lowercase().toByteArray(Charsets.US_ASCII)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("PC presented no certificate")
        val actual = sha256Hex(leaf.encoded).toByteArray(Charsets.US_ASCII)
        if (!MessageDigest.isEqual(expected, actual)) {
            throw CertificateException("PC certificate fingerprint does not match the paired fingerprint")
        }
        leaf.checkValidity()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not used by LinkPhone")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object PinnedTls {
    fun client(fingerprintHex: String): OkHttpClient {
        val trustManager = PinnedTrustManager(fingerprintHex)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .sslSocketFactory(context.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun close(client: OkHttpClient) {
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }
}
