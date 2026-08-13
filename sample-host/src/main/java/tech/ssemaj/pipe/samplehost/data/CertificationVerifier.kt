package tech.ssemaj.pipe.samplehost.data

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import tech.ssemaj.pipe.samplehost.domain.CertSummary
import tech.ssemaj.pipe.samplehost.domain.CertificationResult

class CertificationVerifier(
    private val trustedRootKeysPem: List<String> = GOOGLE_ROOTS,
) {

    fun verify(nonce: ByteArray, signature: ByteArray, certChainDer: List<ByteArray>): CertificationResult {
        val factory = CertificateFactory.getInstance("X.509")
        val chain = certChainDer.map { factory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
        require(chain.isNotEmpty()) { "empty certificate chain" }
        val leaf = chain.first()

        val signatureOk = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(leaf.publicKey); update(nonce); verify(signature)
            }
        }.getOrDefault(false)

        val attestation = AttestationParser.parse(leaf)
        val challengeOk: Boolean? = attestation?.challenge?.contentEquals(nonce)

        val chainOk = runCatching {
            for (i in 0 until chain.size - 1) chain[i].verify(chain[i + 1].publicKey)
            chain.last().verify(chain.last().publicKey) // self-signed root
            true
        }.getOrDefault(false)

        val rootKeyB64 = Base64.getEncoder().encodeToString(chain.last().publicKey.encoded)
        val rootTrusted = trustedRootKeysPem.any { it.replace("\\s".toRegex(), "") == rootKeyB64 }
        val hardwareBacked = chainOk && rootTrusted && attestation != null && challengeOk == true &&
            (attestation.securityLevel == AttestationInfo.LEVEL_TEE ||
                attestation.securityLevel == AttestationInfo.LEVEL_STRONGBOX)

        val digest = MessageDigest.getInstance("SHA-256")
        val summaries = chain.map {
            CertSummary(
                subject = it.subjectX500Principal.name,
                issuer = it.issuerX500Principal.name,
                sha256 = digest.digest(it.encoded).joinToString("") { b -> "%02x".format(b) },
            )
        }
        return CertificationResult(signatureOk, challengeOk, chainOk, hardwareBacked, summaries)
    }

    companion object {
        /**
         * Google hardware-attestation root public keys (base64 SubjectPublicKeyInfo), from
         * developer.android.com/privacy-and-security/security-key-attestation:
         * the 2022 RSA root and the 2026 "Key Attestation CA1" EC root.
         */
        val GOOGLE_ROOTS: List<String> = listOf(
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS" +
                "Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6Wf" +
                "MgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKp" +
                "a73XpXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVW" +
                "utHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOd" +
                "T0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TV" +
                "B4HzWQgpZrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6" +
                "tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYf" +
                "CT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5UmAGMCAwEAAQ==",
            "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEI9ojcU7fPlsFCjxy6IRqzgeOoK0b+YsV9FPQywiyw8EQRTkJ9u3q" +
                "wfnI4DGoSLlBqClTXJfgfCcZvs60FikNMHnu4fkRzObfgDkU2KNXezT9/RQ+XvNslxPHrHCowhGr",
        )
    }
}
