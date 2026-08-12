package tech.ssemaj.pipe.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.EmbedAuthorizer
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.SigningSource
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.GateResult

private class FakeSource(
    private val certs: Map<String, List<String>> = mapOf("com.provider" to listOf("aa11")),
    private val uids: Map<String, Int> = mapOf("com.provider" to 10123),
) : SigningSource {
    override fun packagesForUid(uid: Int) = emptyList<String>()
    override fun certLineageSha256(packageName: String) = certs[packageName] ?: emptyList()
    override fun uidForPackage(packageName: String) = uids[packageName] ?: -1
}

private val component = ProviderComponent("com.provider", "com.provider.PaneService")
private val request = PipeRequest("pane")

class HostGateTest {
    @Test fun admitsProviderWithTrustedCert() {
        val gate = HostGate(IdentityResolver(FakeSource()), EmbedAuthorizer { _, _ -> AuthDecision.Allow })
        val result = gate.admit(component, request)
        assertTrue(result is GateResult.Admitted)
        assertEquals(listOf("com.provider"), (result as GateResult.Admitted).peer.packages)
    }

    @Test fun refusesWhenAuthorizerDenies() {
        val gate = HostGate(IdentityResolver(FakeSource()), EmbedAuthorizer { _, _ -> AuthDecision.Deny("untrusted") })
        assertEquals(GateResult.Refused("untrusted"), gate.admit(component, request))
    }

    @Test fun failsWhenProviderCertsUnreadable() {
        val gate = HostGate(IdentityResolver(FakeSource(certs = emptyMap())), EmbedAuthorizer { _, _ -> AuthDecision.Allow })
        assertTrue(gate.admit(component, request) is GateResult.Failed)
    }

    @Test fun admittedPeerCarriesResolvedUid() {
        val gate = HostGate(IdentityResolver(FakeSource()), EmbedAuthorizer { _, _ -> AuthDecision.Allow })
        val result = gate.admit(component, request)
        assertTrue(result is GateResult.Admitted)
        assertEquals(10123, (result as GateResult.Admitted).peer.uid)
    }

    @Test fun admittedPeerUidIsMinusOneWhenUnresolvable() {
        val gate = HostGate(IdentityResolver(FakeSource(uids = emptyMap())), EmbedAuthorizer { _, _ -> AuthDecision.Allow })
        val result = gate.admit(component, request)
        assertTrue(result is GateResult.Admitted)
        assertEquals(-1, (result as GateResult.Admitted).peer.uid)
    }
}
