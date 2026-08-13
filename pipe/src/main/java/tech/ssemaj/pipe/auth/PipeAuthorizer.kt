package tech.ssemaj.pipe.auth

import tech.ssemaj.pipe.core.PipeRequest

/** Policy hook. The library authenticates (builds a verified [PeerIdentity]); this decides.
 *  Suspendable so policy can consult a backend or prompt for consent. */
fun interface PipeAuthorizer {
    suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision
}
