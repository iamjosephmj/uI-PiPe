package tech.ssemaj.pipe.provider

import kotlinx.coroutines.runBlocking
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.transport.Protocol

sealed interface GateResult {
    data class Admitted(val peer: PeerIdentity) : GateResult
    data class Refused(val reason: String) : GateResult
    data class Failed(val message: String) : GateResult
}

/** Provider-side gate: protocol check → kernel-backed identity → policy. */
class ProviderGate(
    private val resolver: IdentityResolver,
    private val authorizer: PipeAuthorizer,
) {
    // TODO(task 4-8): thread a suspend admit() through the AIDL boundary instead of runBlocking.
    fun admit(callingUid: Int, request: PipeRequest, protocolVersion: Int): GateResult {
        if (protocolVersion != Protocol.VERSION) {
            return GateResult.Failed("protocol version mismatch: host=$protocolVersion provider=${Protocol.VERSION}")
        }
        val peer = resolver.forUid(callingUid)
            ?: return GateResult.Failed("caller identity unreadable")
        return when (val d = runBlocking { authorizer.authorize(peer, request) }) {
            is AuthDecision.Allow -> GateResult.Admitted(peer)
            is AuthDecision.Deny -> GateResult.Refused(d.reason)
        }
    }
}
