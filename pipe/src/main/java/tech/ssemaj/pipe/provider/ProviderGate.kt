package tech.ssemaj.pipe.provider

import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PackageManagerSource
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.GateResult
import tech.ssemaj.pipe.core.GateWire
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.transport.Protocol

/**
 * Provider-side gate: protocol check → kernel-backed identity → policy.
 *
 * The calling uid is captured on the binder thread *before* this runs, so the gate only ever sees
 * a kernel-derived [PeerIdentity][tech.ssemaj.pipe.auth.PeerIdentity] — never a self-reported one.
 */
internal class ProviderGate(
    private val resolver: IdentityResolver,
    private val authorizer: PipeAuthorizer,
) {
    suspend fun admit(callingUid: Int, request: PipeRequest, protocolVersion: Int): GateResult {
        if (protocolVersion != Protocol.VERSION) {
            return GateResult.Failed(GateWire.versionMismatch(protocolVersion, Protocol.VERSION))
        }
        val peer = resolver.forUid(callingUid)
            ?: return GateResult.Failed("caller identity unreadable")
        return when (val d = authorizer.authorize(peer, request)) {
            is AuthDecision.Allow -> GateResult.Admitted(peer)
            is AuthDecision.Deny -> GateResult.Refused(d.reason)
        }
    }
}
