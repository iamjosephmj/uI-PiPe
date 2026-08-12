package tech.ssemaj.pipe.host

import kotlinx.coroutines.runBlocking
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.GateResult

/** Host-side pre-bind gate: verify the exact package we are about to bind. */
class HostGate(
    private val resolver: IdentityResolver,
    private val authorizer: PipeAuthorizer,
) {
    // TODO(task 4-8): thread a suspend admit() through PipeView.open instead of runBlocking.
    fun admit(component: ProviderComponent, request: PipeRequest): GateResult {
        val uid = resolver.uidForPackage(component.packageName)
        val peer = resolver.forPackage(component.packageName, uid)
            ?: return GateResult.Failed("provider signing certs unreadable (not installed?)")
        return when (val d = runBlocking { authorizer.authorize(peer, request) }) {
            is AuthDecision.Allow -> GateResult.Admitted(peer)
            is AuthDecision.Deny -> GateResult.Refused(d.reason)
        }
    }
}
