package tech.ssemaj.pipe.samplehost.data

import java.security.cert.X509Certificate

internal data class AttestationInfo(val challenge: ByteArray, val securityLevel: Int) {
    companion object {
        const val LEVEL_SOFTWARE = 0
        const val LEVEL_TEE = 1
        const val LEVEL_STRONGBOX = 2
    }
}

internal object AttestationParser {
    const val OID = "1.3.6.1.4.1.11129.2.1.17"

    /** Reads the attestation extension from [leaf]; null when absent or malformed. */
    fun parse(leaf: X509Certificate): AttestationInfo? {
        val extension = leaf.getExtensionValue(OID) ?: return null
        // getExtensionValue wraps the payload in an extra OCTET STRING.
        val inner = runCatching { Der.parse(extension, 0) }.getOrNull() ?: return null
        if (inner.tag != 0x04) return null
        return parseKeyDescription(inner.value)
    }

    /** [keyDescriptionDer] is the KeyDescription SEQUENCE (Android Key Attestation schema). */
    fun parseKeyDescription(keyDescriptionDer: ByteArray): AttestationInfo? = runCatching {
        val seq = Der.parse(keyDescriptionDer, 0)
        if (seq.tag != 0x30) return null
        val fields = Der.children(seq.value)
        if (fields.size < 5) return null
        val securityLevel = fields[1].takeIf { it.tag == 0x0A }?.value?.singleOrNull()?.toInt() ?: return null
        val challenge = fields[4].takeIf { it.tag == 0x04 }?.value ?: return null
        AttestationInfo(challenge, securityLevel)
    }.getOrNull()
}
