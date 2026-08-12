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
    // TEMPORARY (removed in Task 4/5 when this gate becomes suspend): bridges the
    // suspend authorizer to this non-suspend call site. WARNING: runBlocking here runs
    // on the caller thread (the main thread, via PipeView.open). An authorizer that hops
    // to Dispatchers.Main / posts to a Handler and awaits it will DEADLOCK until this
    // shim is removed. Until Task 4/5 land, only non-dispatching authorizers (cert
    // checks, allowlist) are safe.
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
