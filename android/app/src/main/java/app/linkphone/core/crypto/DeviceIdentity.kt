package app.linkphone.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class IdentityUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Phone identity: a non-exportable P-256 key in AndroidKeyStore. The public key
 * is shared at pairing; the PC verifies a signed nonce on every reconnect.
 */
class DeviceIdentity {
    companion object {
        private const val TAG = "Identity"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "linkphone.identity.v1"
    }

    fun hasKey(): Boolean = runCatching {
        val store = loadStore()
        store.containsAlias(KEY_ALIAS) && store.getCertificate(KEY_ALIAS) != null
    }.getOrDefault(false)

    fun ensureKey(): ByteArray {
        try {
            val store = loadStore()
            if (!store.containsAlias(KEY_ALIAS) || store.getCertificate(KEY_ALIAS) == null) {
                generateKey()
            }
            val certificate = loadStore().getCertificate(KEY_ALIAS)
                ?: throw IdentityUnavailableException("Identity certificate missing")
            return certificate.publicKey.encoded
                ?: throw IdentityUnavailableException("Identity public key missing")
        } catch (error: IdentityUnavailableException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Unable to load identity", error)
            throw IdentityUnavailableException("Unable to load the phone identity key", error)
        }
    }

    fun publicKeySpkiBase64(): String =
        Base64.getEncoder().encodeToString(ensureKey())

    fun fingerprintHex(): String = sha256Hex(ensureKey())

    fun sign(payload: String): ByteArray {
        try {
            val privateKey = loadStore().getKey(KEY_ALIAS, null) as? PrivateKey
                ?: throw IdentityUnavailableException("Identity private key missing")
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(payload.toByteArray(Charsets.UTF_8))
            return signature.sign()
        } catch (error: IdentityUnavailableException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Signing failed", error)
            throw IdentityUnavailableException("Phone identity key could not sign the challenge", error)
        }
    }

    fun deleteKey() {
        runCatching {
            val store = loadStore()
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun loadStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generateKey() {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        generator.generateKeyPair()
    }
}
