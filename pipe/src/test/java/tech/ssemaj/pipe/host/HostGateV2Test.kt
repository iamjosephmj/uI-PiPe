package tech.ssemaj.pipe.host

import android.content.ComponentName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PackageManagerSource
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.GateResult
import tech.ssemaj.pipe.core.PipeRequest

private class FakeSource(
    private val certs: Map<String, List<String>> = mapOf("com.provider" to listOf("aa11")),
    private val uids: Map<String, Int> = mapOf("com.provider" to 10055),
    private val serviceOk: Boolean = true,
) : PackageManagerSource {
    override fun packagesForUid(uid: Int) = emptyList<String>()
    override fun certLineageSha256(packageName: String) = certs[packageName] ?: emptyList()
    override fun uidForPackage(packageName: String) = uids[packageName] ?: -1
    override fun serviceResolvable(component: ComponentName) = serviceOk
}
private val comp = ProviderComponent("com.provider", "com.provider.PaneService")
private val req = PipeRequest("pane")

class HostGateV2Test {
    @Test fun admitsTrustedAndCarriesUid() = runTest {
        val r = HostGate(FakeSource(), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertTrue(r is GateResult.Admitted)
        assertEquals(10055, (r as GateResult.Admitted).peer.uid)
        assertEquals(listOf("com.provider"), r.peer.packages)
    }
    @Test fun refusesOnDeny() = runTest {
        assertEquals(GateResult.Refused("untrusted"),
            HostGate(FakeSource(), PipeAuthorizer { _, _ -> AuthDecision.Deny("untrusted") }).admit(comp, req))
    }
    @Test fun failsUnavailableWhenCertsUnreadable() = runTest {
        val r = HostGate(FakeSource(certs = emptyMap()), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertTrue(r is GateResult.Failed && (r as GateResult.Failed).message.startsWith("unavailable:"))
    }
    @Test fun failsNotVisibleWhenPackageUnresolvable() = runTest {
        // Service unresolvable AND package invisible (uid unresolvable): the missing-<queries> /
        // not-installed bucket.
        val r = HostGate(FakeSource(uids = emptyMap(), serviceOk = false), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertEquals("unavailable:NOT_VISIBLE", (r as GateResult.Failed).message)
    }
    @Test fun failsNoServiceWhenServiceMissingButPackageVisible() = runTest {
        // Service unresolvable but package resolves: wrong component / not bindable.
        val r = HostGate(FakeSource(serviceOk = false), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertEquals("unavailable:NO_SERVICE", (r as GateResult.Failed).message)
    }
    @Test fun availabilityCheckedBeforePolicy() = runTest {
        // Even an allow-all authorizer can't rescue an invisible provider.
        val r = HostGate(FakeSource(uids = emptyMap(), serviceOk = false), PipeAuthorizer { _, _ -> AuthDecision.Allow }).admit(comp, req)
        assertTrue(r is GateResult.Failed)
    }
}
