package tech.ssemaj.pipe.sampleprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter

class PanePresenterTest {

    @Test fun startsIdle() {
        assertEquals(PanePresenter.State.Idle, PanePresenter().state.value)
    }

    @Test fun requestMovesToConsentWithFingerprint() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32) { 0x1F }, "Pipe Sample Host"))
        val s = p.state.value as PanePresenter.State.Consent
        assertEquals("Pipe Sample Host", s.hostName)
        assertEquals("1f1f1f1f1f1f1f1f", s.nonceFingerprint) // first 8 bytes, lowercase hex
        assertEquals(32, s.nonce.size)
    }

    @Test fun approveThenIssuedState() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onIssued(SecurityLevel.HARDWARE)
        assertTrue(p.state.value is PanePresenter.State.Issued)
    }

    @Test fun declineCarriesReason() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onDeclined("user said no")
        assertEquals("user said no", (p.state.value as PanePresenter.State.Declined).reason)
    }

    @Test fun secondRequestReentersConsent() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onDeclined("no")
        p.onRequest(CertificationRequest(ByteArray(32) { 2 }, "h2"))
        assertEquals("h2", (p.state.value as PanePresenter.State.Consent).hostName)
    }
}
