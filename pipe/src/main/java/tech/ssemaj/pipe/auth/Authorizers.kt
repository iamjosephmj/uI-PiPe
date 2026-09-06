package tech.ssemaj.pipe.auth

import tech.ssemaj.pipe.core.PipeRequest

/** Allows peers whose signing lineage intersects the local app's signing certs. */
internal class SameSigningKeyAuthorizer(localCertsSha256: Set<String>) : PipeAuthorizer {
    private val local = localCertsSha256.map { it.lowercase() }.toSet()
    override suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision {
        if (peer.signingCertSha256.isEmpty()) return AuthDecision.Deny("peer has no readable signing certs")
        return if (peer.signingCertSha256.any { it.lowercase() in local }) AuthDecision.Allow
        else AuthDecision.Deny("peer signing certs do not match local signing key")
    }
}

/** Allows peers whose signing lineage intersects a fixed cert allowlist. */
internal class AllowlistAuthorizer(certSha256: Set<String>) : PipeAuthorizer {
    private val allowed = certSha256.map { it.lowercase() }.toSet()
    override suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision {
        if (peer.signingCertSha256.isEmpty()) return AuthDecision.Deny("peer has no readable signing certs")
        return if (peer.signingCertSha256.any { it.lowercase() in allowed }) AuthDecision.Allow
        else AuthDecision.Deny("peer signing certs not in allowlist")
    }
}
