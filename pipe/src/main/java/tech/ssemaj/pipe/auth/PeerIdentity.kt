package tech.ssemaj.pipe.auth

/**
 * A cryptographically verified peer. Built only by the library — from a binder uid
 * (provider side) or a resolved ComponentName (host side) — never from self-reported data.
 * [signingCertSha256] is lowercase hex and includes the full rotation lineage.
 */
data class PeerIdentity(
    val uid: Int,
    val packages: List<String>,
    val signingCertSha256: List<String>,
)
