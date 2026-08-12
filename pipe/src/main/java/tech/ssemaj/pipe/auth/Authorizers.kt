package tech.ssemaj.pipe.auth

import tech.ssemaj.pipe.core.PipeRequest

/** Allows peers whose signing lineage intersects the local app's signing certs. */
class SameSigningKeyAuthorizer(localCertsSha256: Set<String>) : EmbedAuthorizer {
    private val local = localCertsSha256.map { it.lowercase() }.toSet()

    override fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision {
        if (peer.signingCertSha256.isEmpty()) return AuthDecision.Deny("peer has no readable signing certs")
        val match = peer.signingCertSha256.any { it.lowercase() in local }
        return if (match) AuthDecision.Allow
        else AuthDecision.Deny("peer signing certs do not match local signing key")
    }
}

/** Allows peers whose signing lineage intersects a fixed cert allowlist. */
class AllowlistAuthorizer(certSha256: Set<String>) : EmbedAuthorizer {
    private val allowed = certSha256.map { it.lowercase() }.toSet()

    override fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision {
        if (peer.signingCertSha256.isEmpty()) return AuthDecision.Deny("peer has no readable signing certs")
        val match = peer.signingCertSha256.any { it.lowercase() in allowed }
        return if (match) AuthDecision.Allow
        else AuthDecision.Deny("peer signing certs not in allowlist")
    }
}

/** Allow if any delegate allows; otherwise Deny with all reasons joined by "; ". */
fun anyOf(vararg authorizers: EmbedAuthorizer): EmbedAuthorizer = EmbedAuthorizer { peer, request ->
    val reasons = mutableListOf<String>()
    for (a in authorizers) {
        when (val d = a.authorize(peer, request)) {
            is AuthDecision.Allow -> return@EmbedAuthorizer AuthDecision.Allow
            is AuthDecision.Deny -> reasons += d.reason
        }
    }
    AuthDecision.Deny(if (reasons.isEmpty()) "no authorizers configured" else reasons.joinToString("; "))
}
