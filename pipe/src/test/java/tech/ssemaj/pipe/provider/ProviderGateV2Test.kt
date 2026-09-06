package tech.ssemaj.pipe.provider

import android.content.ComponentName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.PackageManagerSource
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.GateResult
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.transport.Protocol

private class FakeSource(
    private val pkgs: Map<Int, List<String>> = mapOf(10001 to listOf("com.host")),
    private val certs: Map<String, List<String>> = mapOf("com.host" to listOf("aa11")),
) : PackageManagerSource {
    override fun packagesForUid(uid: Int) = pkgs[uid] ?: emptyList()
    override fun certLineageSha256(packageName: String) = certs[packageName] ?: emptyList()
    override fun uidForPackage(packageName: String) = pkgs.entries.firstOrNull { packageName in it.value }?.key ?: -1
    override fun serviceResolvable(component: ComponentName) = true
}
private val allow = PipeAuthorizer { _, _ -> AuthDecision.Allow }
private val deny = PipeAuthorizer { _, _ -> AuthDecision.Deny("nope") }
private val req = PipeRequest("pane")

class ProviderGateV2Test {
    @Test fun admitsTrusted() = runTest {
        val r = ProviderGate(IdentityResolver(FakeSource()), allow).admit(10001, req, Protocol.VERSION)
        assertTrue(r is GateResult.Admitted); assertEquals(10001, (r as GateResult.Admitted).peer.uid)
    }
    @Test fun refusesOnDeny() = runTest {
        assertEquals(GateResult.Refused("nope"),
            ProviderGate(IdentityResolver(FakeSource()), deny).admit(10001, req, Protocol.VERSION))
    }
    @Test fun failsOnUnknownUid() = runTest {
        assertTrue(ProviderGate(IdentityResolver(FakeSource()), allow).admit(99999, req, Protocol.VERSION) is GateResult.Failed)
    }
    @Test fun identityCheckedBeforePolicy() = runTest {
        // unknown uid + deny-all ⇒ Failed (identity), NOT Refused (policy)
        val r = ProviderGate(IdentityResolver(FakeSource()), deny).admit(99999, req, Protocol.VERSION)
        assertTrue(r is GateResult.Failed)
    }
    @Test fun protocolCheckedFirst() = runTest {
        val r = ProviderGate(IdentityResolver(FakeSource(pkgs = emptyMap())), allow).admit(10001, req, Protocol.VERSION + 1)
        assertTrue(r is GateResult.Failed && (r as GateResult.Failed).message.contains("protocol"))
    }
}
