package tech.ssemaj.pipe.samplehost

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.AttestationParser
import tech.ssemaj.pipe.samplehost.data.CertificationVerifier

/** Builds root -> leaf chains mirroring what AndroidKeyStore attestation returns. */
class CertificationVerifierTest {

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun tlv(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte(), value.size.toByte()) + value

    private fun keyDescription(challenge: ByteArray, level: Int): ByteArray = tlv(0x30,
        tlv(0x02, byteArrayOf(100)) + tlv(0x0A, byteArrayOf(level.toByte())) +
        tlv(0x02, byteArrayOf(100)) + tlv(0x0A, byteArrayOf(level.toByte())) +
        tlv(0x04, challenge) + tlv(0x04, ByteArray(0)) + tlv(0x30, ByteArray(0)) + tlv(0x30, ByteArray(0)))

    private fun cert(
        subject: String, subjectKey: KeyPair, issuer: String, issuerKey: KeyPair,
        attestationExt: ByteArray? = null,
    ): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            X500Name("CN=$issuer"), BigInteger.valueOf(System.nanoTime()),
            Date(System.currentTimeMillis() - 86_400_000), Date(System.currentTimeMillis() + 86_400_000),
            X500Name("CN=$subject"), subjectKey.public,
        )
        if (attestationExt != null) {
            // KeyDescription is a SEQUENCE; addExtension DER-encodes it and adds the OCTET STRING wrapper.
            builder.addExtension(
                ASN1ObjectIdentifier(AttestationParser.OID), false,
                ASN1Primitive.fromByteArray(attestationExt),
            )
        }
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun sign(nonce: ByteArray, key: KeyPair): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(nonce); sign() }

    @Test fun happyPath_teeChain_verifies() {
        val nonce = ByteArray(32) { it.toByte() }
        val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val verifier = CertificationVerifier(trustedRootKeysPem = listOf(pem(rootCert)))
        val result = verifier.verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.signatureOk); assertEquals(true, result.challengeOk)
        assertTrue(result.chainOk); assertTrue(result.hardwareBacked); assertTrue(result.verified)
        assertEquals(2, result.certificates.size)
    }

    @Test fun wrongChallenge_failsChallengeCheckOnly() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(ByteArray(32) { 9 }, level = 1))
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.signatureOk); assertEquals(false, result.challengeOk); assertFalse(result.verified)
    }

    @Test fun brokenChain_failsChainCheck() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair(); val stranger = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Stranger", stranger, keyDescription(nonce, level = 1)) // not signed by root
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertFalse(result.chainOk); assertFalse(result.verified)
    }

    @Test fun unknownRoot_degradesToSoftwareNotFailure() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val result = CertificationVerifier(trustedRootKeysPem = emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.verified); assertFalse(result.hardwareBacked)
    }

    @Test fun noAttestationExtension_challengeNotApplicable() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, attestationExt = null)
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertEquals(null, result.challengeOk); assertTrue(result.verified); assertFalse(result.hardwareBacked)
    }

    @Test fun badSignature_fails() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val result = CertificationVerifier(emptyList())
            .verify(nonce, ByteArray(70), listOf(leafCert.encoded, rootCert.encoded))
        assertFalse(result.signatureOk); assertFalse(result.verified)
    }

    private fun pem(cert: X509Certificate): String =
        java.util.Base64.getEncoder().encodeToString(cert.publicKey.encoded)
}
