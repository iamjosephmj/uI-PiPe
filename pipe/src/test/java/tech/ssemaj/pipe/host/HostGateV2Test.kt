package tech.ssemaj.pipe.host

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.SigningSource
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.GateResult

private class FakeSource(
    private val certs: Map<String, List<String>> = mapOf("com.provider" to listOf("aa11")),
    private val uids: Map<String, Int> = mapOf("com.provider" to 10055),
) : SigningSource {
    override fun packagesForUid(uid: Int) = emptyList<String>()
    override fun certLineageSha256(packageName: String) = certs[packageName] ?: emptyList()
    override fun uidForPackage(packageName: String) = uids[packageName] ?: -1
}
private val comp = ProviderComponent("com.provider", "com.provider.PaneService")
private val req = PipeRequest("pane")

class HostGateV2Test {
    @Test fun admitsTrustedAndCarriesUid() = runTest {
        val r = HostGate(IdentityResolver(FakeSource()), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertTrue(r is GateResult.Admitted)
        assertEquals(10055, (r as GateResult.Admitted).peer.uid)
        assertEquals(listOf("com.provider"), r.peer.packages)
    }
    @Test fun refusesOnDeny() = runTest {
        assertEquals(GateResult.Refused("untrusted"),
            HostGate(IdentityResolver(FakeSource()), PipeAuthorizer { _, _ -> AuthDecision.Deny("untrusted") }).admit(comp, req))
    }
    @Test fun failsUnavailableWhenCertsUnreadable() = runTest {
        val r = HostGate(IdentityResolver(FakeSource(certs = emptyMap())), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertTrue(r is GateResult.Failed && (r as GateResult.Failed).message.startsWith("unavailable:"))
    }
}
