package tech.ssemaj.pipe.auth

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakePackageManagerSource(
    private val uidToPackages: Map<Int, List<String>>,
    private val packageToCerts: Map<String, List<String>>,
) : PackageManagerSource {
    override fun packagesForUid(uid: Int) = uidToPackages[uid] ?: emptyList()
    override fun certLineageSha256(packageName: String) = packageToCerts[packageName] ?: emptyList()
    override fun uidForPackage(packageName: String) = -1
    override fun serviceResolvable(component: ComponentName) = true
}

class IdentityResolverTest {
    @Test fun forUidBuildsIdentityWithUnionOfCerts() {
        val resolver = IdentityResolver(FakePackageManagerSource(
            uidToPackages = mapOf(10001 to listOf("com.a", "com.b")),
            packageToCerts = mapOf("com.a" to listOf("aa11"), "com.b" to listOf("aa11", "bb22")),
        ))
        val peer = resolver.forUid(10001)!!
        assertEquals(10001, peer.uid)
        assertEquals(listOf("com.a", "com.b"), peer.packages)
        assertEquals(listOf("aa11", "bb22"), peer.signingCertSha256)
    }

    @Test fun forUidReturnsNullWhenNoPackages() {
        val resolver = IdentityResolver(FakePackageManagerSource(emptyMap(), emptyMap()))
        assertNull(resolver.forUid(10001))
    }

    @Test fun forUidReturnsNullWhenAnyPackageHasNoReadableCerts() {
        // Shared-uid safety: if we cannot attribute certs to every package, trust nothing.
        val resolver = IdentityResolver(FakePackageManagerSource(
            uidToPackages = mapOf(10001 to listOf("com.a", "com.b")),
            packageToCerts = mapOf("com.a" to listOf("aa11")),
        ))
        assertNull(resolver.forUid(10001))
    }

    @Test fun forPackageBuildsIdentity() {
        val resolver = IdentityResolver(FakePackageManagerSource(
            uidToPackages = emptyMap(),
            packageToCerts = mapOf("com.p" to listOf("cc33")),
        ))
        val peer = resolver.forPackage("com.p", uid = 10123)!!
        assertEquals(PeerIdentity(10123, listOf("com.p"), listOf("cc33")), peer)
    }

    @Test fun forPackageReturnsNullWhenCertsUnreadable() {
        val resolver = IdentityResolver(FakePackageManagerSource(emptyMap(), emptyMap()))
        assertNull(resolver.forPackage("com.p"))
    }
}
