package tech.ssemaj.pipe.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.core.PipeRequest

private const val CERT_A = "aa11"
private const val CERT_B = "bb22"

class PipeAuthorizerTest {
    private fun peer(vararg certs: String) = PeerIdentity(10001, listOf("com.peer"), certs.toList())
    private val request = PipeRequest("test")

    @Test fun sameKeyAllowsMatch() = runTest {
        assertEquals(AuthDecision.Allow, SameSigningKeyAuthorizer(setOf(CERT_A)).authorize(peer(CERT_A), request))
    }
    @Test fun sameKeyDeniesMismatch() = runTest {
        assertTrue(SameSigningKeyAuthorizer(setOf(CERT_A)).authorize(peer(CERT_B), request) is AuthDecision.Deny)
    }
    @Test fun sameKeyDeniesEmptyCerts() = runTest {
        assertTrue(SameSigningKeyAuthorizer(setOf(CERT_A)).authorize(peer(), request) is AuthDecision.Deny)
    }
    @Test fun sameKeyCaseInsensitive() = runTest {
        assertEquals(AuthDecision.Allow, SameSigningKeyAuthorizer(setOf("AA11")).authorize(peer("aa11"), request))
    }
    @Test fun allowlistAllows() = runTest {
        assertEquals(AuthDecision.Allow, AllowlistAuthorizer(setOf(CERT_B)).authorize(peer(CERT_B), request))
    }
    @Test fun anyOfAllowsWhenAnyAllows() = runTest {
        val a = anyOf(AllowlistAuthorizer(setOf(CERT_B)), SameSigningKeyAuthorizer(setOf(CERT_A)))
        assertEquals(AuthDecision.Allow, a.authorize(peer(CERT_A), request))
    }
    @Test fun anyOfJoinsDenyReasons() = runTest {
        val a = anyOf(PipeAuthorizer { _, _ -> AuthDecision.Deny("first") },
                      PipeAuthorizer { _, _ -> AuthDecision.Deny("second") })
        assertEquals(AuthDecision.Deny("first; second"), a.authorize(peer(CERT_A), request))
    }
    @Test fun anyOfEmptyDenies() = runTest {
        assertTrue(anyOf().authorize(peer(CERT_A), request) is AuthDecision.Deny)
    }
    @Test fun suspendingPolicyCanDeny() = runTest {
        val a = PipeAuthorizer { _, _ -> kotlinx.coroutines.yield(); AuthDecision.Deny("async") }
        assertEquals(AuthDecision.Deny("async"), a.authorize(peer(CERT_A), request))
    }
}
