package tech.ssemaj.pipe.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.core.PipeRequest

private const val CERT_A = "aa11" // abbreviated hashes are fine: authorizers treat them as opaque strings
private const val CERT_B = "bb22"
private const val CERT_ROTATED_OLD = "cc33"

class AuthorizersTest {
    private fun peer(vararg certs: String) = PeerIdentity(10001, listOf("com.example.peer"), certs.toList())
    private val request = PipeRequest("test")

    @Test fun sameSigningKeyAllowsMatchingCert() {
        val auth = SameSigningKeyAuthorizer(setOf(CERT_A))
        assertEquals(AuthDecision.Allow, auth.authorize(peer(CERT_A), request))
    }

    @Test fun sameSigningKeyDeniesDifferentCert() {
        val auth = SameSigningKeyAuthorizer(setOf(CERT_A))
        assertTrue(auth.authorize(peer(CERT_B), request) is AuthDecision.Deny)
    }

    @Test fun sameSigningKeyAllowsViaRotationLineage() {
        // Local app still on old key; peer rotated but lineage includes the old cert.
        val auth = SameSigningKeyAuthorizer(setOf(CERT_ROTATED_OLD))
        assertEquals(AuthDecision.Allow, auth.authorize(peer(CERT_ROTATED_OLD, CERT_A), request))
    }

    @Test fun sameSigningKeyDeniesEmptyPeerCerts() {
        val auth = SameSigningKeyAuthorizer(setOf(CERT_A))
        assertTrue(auth.authorize(peer(), request) is AuthDecision.Deny)
    }

    @Test fun sameSigningKeyIsCaseInsensitive() {
        val auth = SameSigningKeyAuthorizer(setOf("AA11"))
        assertEquals(AuthDecision.Allow, auth.authorize(peer("aa11"), request))
    }

    @Test fun allowlistAllowsListedCert() {
        val auth = AllowlistAuthorizer(setOf(CERT_B))
        assertEquals(AuthDecision.Allow, auth.authorize(peer(CERT_B), request))
    }

    @Test fun allowlistDeniesUnlistedCert() {
        val auth = AllowlistAuthorizer(setOf(CERT_B))
        assertTrue(auth.authorize(peer(CERT_A), request) is AuthDecision.Deny)
    }

    @Test fun anyOfAllowsWhenAnyAllows() {
        val auth = anyOf(AllowlistAuthorizer(setOf(CERT_B)), SameSigningKeyAuthorizer(setOf(CERT_A)))
        assertEquals(AuthDecision.Allow, auth.authorize(peer(CERT_A), request))
    }

    @Test fun anyOfDeniesWithJoinedReasonsWhenAllDeny() {
        val auth = anyOf(
            EmbedAuthorizer { _, _ -> AuthDecision.Deny("first") },
            EmbedAuthorizer { _, _ -> AuthDecision.Deny("second") },
        )
        val decision = auth.authorize(peer(CERT_A), request)
        assertEquals(AuthDecision.Deny("first; second"), decision)
    }

    @Test fun anyOfWithNoAuthorizersDenies() {
        assertTrue(anyOf().authorize(peer(CERT_A), request) is AuthDecision.Deny)
    }
}
