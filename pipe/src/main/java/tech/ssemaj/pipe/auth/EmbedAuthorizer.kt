package tech.ssemaj.pipe.auth

import tech.ssemaj.pipe.core.PipeRequest

/** Policy hook: the library authenticates (builds a verified [PeerIdentity]); this decides. */
fun interface EmbedAuthorizer {
    fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision
}
