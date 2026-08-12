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
 * Requires BOTH apps installed. The embedded pane is a real window of the provider
 * process, so UiAutomator is used for pane-side assertions.
 */
@RunWith(AndroidJUnit4::class)
class PipeE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("pane did not render", device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))
    }

    @Test fun paneRendersAndHostReportsConnected() {
        assertNotNull(device.wait(Until.findObject(By.text("Connected")), TIMEOUT))
    }

    @Test fun certificationRoundTrip_touchAndBothChannels() {
        device.findObject(By.text("Request certification")).click()
        // Host→provider typed message arrived: consent UI appears in the pane.
        assertTrue("consent never appeared in pane",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
        device.findObject(By.text("Approve")).click() // touch crosses into the provider window
        // Provider→host: granted response arrives, host verifies.
        assertTrue("host never showed a verification badge",
            device.wait(Until.hasObject(By.textContains("-verified")), CRYPTO_TIMEOUT) ||
                device.hasObject(By.text("Software-backed")))
        assertTrue("pane never confirmed issuance",
            device.wait(Until.hasObject(By.text("Certification issued")), TIMEOUT))
    }

    @Test fun declineFlowReachesHost() {
        device.findObject(By.text("Request certification")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Decline")), TIMEOUT))
        device.findObject(By.text("Decline")).click()
        assertTrue("host never showed decline",
            device.wait(Until.hasObject(By.textStartsWith("Provider declined:")), TIMEOUT))
    }

    @Test fun closingHostActivityTearsDownPane() {
        device.pressHome()
        assertTrue("pane still visible after host gone",
            device.wait(Until.gone(By.text("pane-ready")), TIMEOUT))
    }
}
