package tech.ssemaj.pipe.sampleprovider.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Generates a throwaway P-256 signing key in AndroidKeyStore with the host's nonce as
 * attestation challenge, signs the nonce, returns the attestation chain, deletes the key.
 * Falls back to an unattested key when the device can't attest ([Issued.attested] = false).
 */
class KeystoreRepository {

    data class Issued(val signature: ByteArray, val certChainDer: List<ByteArray>, val attested: Boolean)

    fun signWithAttestedKey(challenge: ByteArray): Issued {
        val alias = "pipe-cert-" + challenge.take(8).joinToString("") { "%02x".format(it) }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        try {
            val attested = generateKey(alias, challenge)
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(entry.privateKey); update(challenge); sign()
            }
            val chain = keyStore.getCertificateChain(alias).map { it.encoded }
            return Issued(signature, chain, attested)
        } finally {
            runCatching { keyStore.deleteEntry(alias) }
        }
    }

    /** Returns true when the key carries an attestation challenge. */
    private fun generateKey(alias: String, challenge: ByteArray): Boolean {
        fun spec(withAttestation: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .apply { if (withAttestation) setAttestationChallenge(challenge) }
                .build()

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        return try {
            generator.initialize(spec(withAttestation = true))
            generator.generateKeyPair()
            true
        } catch (e: Exception) { // ProviderException/StrongBoxUnavailable on non-attesting devices
            generator.initialize(spec(withAttestation = false))
            generator.generateKeyPair()
            false
        }
    }

    private companion object { const val ANDROID_KEYSTORE = "AndroidKeyStore" }
}
