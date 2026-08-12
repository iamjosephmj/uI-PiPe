package tech.ssemaj.pipe.host

import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.EmbedAuthorizer
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.GateResult

/** Host-side pre-bind gate: verify the exact package we are about to bind. */
class HostGate(
    private val resolver: IdentityResolver,
    private val authorizer: EmbedAuthorizer,
) {
    fun admit(component: ProviderComponent, request: PipeRequest): GateResult {
        val peer = resolver.forPackage(component.packageName)
            ?: return GateResult.Failed("provider signing certs unreadable (not installed?)")
        return when (val d = authorizer.authorize(peer, request)) {
            is AuthDecision.Allow -> GateResult.Admitted(peer)
            is AuthDecision.Deny -> GateResult.Refused(d.reason)
        }
    }
}
