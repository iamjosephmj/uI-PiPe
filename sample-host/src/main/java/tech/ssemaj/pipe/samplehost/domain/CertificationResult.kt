package tech.ssemaj.pipe.samplehost.domain

data class CertSummary(val subject: String, val issuer: String, val sha256: String)

/**
 * Outcome of the four independent checks. [challengeOk] is null when the leaf carries no
 * attestation extension (not applicable — degrades the badge, doesn't fail verification).
 */
data class CertificationResult(
    val signatureOk: Boolean,
    val challengeOk: Boolean?,
    val chainOk: Boolean,
    val hardwareBacked: Boolean,
    val certificates: List<CertSummary>,
) {
    val verified: Boolean get() = signatureOk && chainOk && challengeOk != false
}
