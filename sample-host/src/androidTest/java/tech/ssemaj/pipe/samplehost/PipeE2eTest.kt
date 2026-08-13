package tech.ssemaj.pipe.samplehost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L
private const val CRYPTO_TIMEOUT = 20_000L // key attestation generation can be slow

/**
 * Requires BOTH apps installed. Certification runs in the provider's full-screen sub-window (a real
 * window in the host's hierarchy), so UiAutomator drives the pane-side assertions.
 */
@RunWith(AndroidJUnit4::class)
class PipeE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Retry until the target appears, then click. A just-focused sub-window's accessibility tree
     *  can lag one poll behind a hasObject() match, so a single-shot findObject() may still miss. */
    private fun tap(text: String, timeout: Long = TIMEOUT) {
        val obj = device.wait(Until.findObject(By.text(text)), timeout)
        assertNotNull("\"$text\" never became tappable", obj)
        obj.click()
    }

    @Before fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("host screen did not appear",
            device.wait(Until.hasObject(By.text("Request certification")), TIMEOUT))
    }

    private fun startAndReachConsent() {
        tap("Request certification")
        // The provider's full-screen pane renders and (host auto-request) shows consent.
        assertTrue("consent never appeared in pane",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
    }

    @Test fun certificationRoundTrip_bothChannelsAndVerify() {
        startAndReachConsent()
        tap("Approve") // input into the provider's own window
        assertTrue("pane never confirmed issuance",
            device.wait(Until.hasObject(By.text("Certification issued")), CRYPTO_TIMEOUT))
        // Dismiss the pane; the host shows the attestation result it already verified.
        tap("Done")
        assertTrue("host never showed a verification badge",
            device.wait(Until.hasObject(By.textContains("-verified")), CRYPTO_TIMEOUT) ||
                device.hasObject(By.text("Software-backed")))
    }

    @Test fun declineFlowReachesHost() {
        startAndReachConsent()
        tap("Decline")
        assertTrue("pane did not confirm decline",
            device.wait(Until.hasObject(By.text("Certification declined")), TIMEOUT))
        tap("Done")
        assertTrue("host never showed decline",
            device.wait(Until.hasObject(By.textStartsWith("Provider declined:")), TIMEOUT))
    }

    @Test fun paneAcceptsMultipleGestures() {
        startAndReachConsent()
        // Gesture 1: Decline.
        tap("Decline")
        assertTrue("pane did not react to first gesture",
            device.wait(Until.hasObject(By.text("Certification declined")), CRYPTO_TIMEOUT))
        // Gesture 2: Start over → consent returns, proving the window keeps accepting input.
        tap("Start over")
        assertTrue("pane froze after first gesture",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
    }

    @Test fun closingHostTearsDownPane() {
        startAndReachConsent()
        device.pressHome()
        assertTrue("pane still visible after host left foreground",
            device.wait(Until.gone(By.text("Approve")), TIMEOUT))
    }
}
