package tech.ssemaj.pipe.sampleprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository

@RunWith(AndroidJUnit4::class)
class KeystoreRepositoryTest {

    @Test fun signsChallengeAndCleansUpAlias() {
        val challenge = ByteArray(32) { (it * 3).toByte() }
        val issued = KeystoreRepository().signWithAttestedKey(challenge)

        val leaf = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(issued.certChainDer.first())) as X509Certificate
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(leaf.publicKey); update(challenge); verify(issued.signature)
        }
        assertTrue("signature must verify against leaf key", ok)
        assertTrue("chain must have at least leaf", issued.certChainDer.isNotEmpty())

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("alias must be deleted", ks.aliases().toList().any { it.startsWith("pipe-cert-") })
    }
}
