package tech.ssemaj.pipe.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.EmbedAuthorizer
import tech.ssemaj.pipe.auth.IdentityResolver
import tech.ssemaj.pipe.auth.SigningSource
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.transport.Protocol

private class FakeSource(
    private val packages: Map<Int, List<String>> = mapOf(10001 to listOf("com.host")),
    private val certs: Map<String, List<String>> = mapOf("com.host" to listOf("aa11")),
) : SigningSource {
    override fun packagesForUid(uid: Int) = packages[uid] ?: emptyList()
    override fun certLineageSha256(packageName: String) = certs[packageName] ?: emptyList()
}

private val allowAll = EmbedAuthorizer { _, _ -> AuthDecision.Allow }
private val denyAll = EmbedAuthorizer { _, _ -> AuthDecision.Deny("nope") }
private val request = PipeRequest("pane")

class ProviderGateTest {
    @Test fun admitsTrustedCallerAtCurrentProtocol() {
        val gate = ProviderGate(IdentityResolver(FakeSource()), allowAll)
        val result = gate.admit(10001, request, Protocol.VERSION)
        assertTrue(result is GateResult.Admitted)
        assertEquals(10001, (result as GateResult.Admitted).peer.uid)
    }

    @Test fun refusesWhenAuthorizerDenies() {
        val gate = ProviderGate(IdentityResolver(FakeSource()), denyAll)
        assertEquals(GateResult.Refused("nope"), gate.admit(10001, request, Protocol.VERSION))
    }

    @Test fun failsOnUnknownUid() {
        val gate = ProviderGate(IdentityResolver(FakeSource()), allowAll)
        assertTrue(gate.admit(99999, request, Protocol.VERSION) is GateResult.Failed)
    }

    @Test fun failsOnProtocolMismatchBeforeTouchingIdentity() {
        val gate = ProviderGate(IdentityResolver(FakeSource(packages = emptyMap())), allowAll)
        val result = gate.admit(10001, request, Protocol.VERSION + 1)
        assertTrue(result is GateResult.Failed)
        assertTrue((result as GateResult.Failed).message.contains("protocol"))
    }
}
