package tech.ssemaj.pipe.host

import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PackageManagerSource
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.GateResult
import tech.ssemaj.pipe.core.GateWire
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeRequest

/**
 * Host-side pre-bind gate: is the provider we are about to hand our window token to *there*, and
 * is it *trusted*?
 *
 * Checks run in the order an integrator needs to reason about: availability first (package visible?
 * service present?), then identity (readable signing certs), then policy (the [authorizer]).
 * Runs before `bindService` — a refusal produces no bind and no window.
 */
internal class HostGate(
    private val source: PackageManagerSource,
    private val authorizer: PipeAuthorizer,
) {
    private val resolver = IdentityResolver(source)

    suspend fun admit(component: ProviderComponent, request: PipeRequest): GateResult {
        if (!source.serviceResolvable(component.toComponentName())) {
            // Package invisible → almost always a missing <queries> declaration (or genuinely not
            // installed — indistinguishable from here). Package visible but service not → wrong
            // component or not bindable.
            val kind =
                if (source.uidForPackage(component.packageName) == -1) {
                    PipeProviderUnavailableException.Unavailable.NOT_VISIBLE
                } else {
                    PipeProviderUnavailableException.Unavailable.NO_SERVICE
                }
            return GateResult.Failed(GateWire.unavailable(kind))
        }
        val peer = resolver.forPackage(component.packageName)
            ?: return GateResult.Failed(GateWire.unavailable(PipeProviderUnavailableException.Unavailable.CERT_UNREADABLE))
        return when (val d = authorizer.authorize(peer, request)) {
            is AuthDecision.Allow -> GateResult.Admitted(peer)
            is AuthDecision.Deny -> GateResult.Refused(d.reason)
        }
    }
}
