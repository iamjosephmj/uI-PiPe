package tech.ssemaj.pipe.samplehost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L
private const val CRYPTO_TIMEOUT = 20_000L

@RunWith(AndroidJUnit4::class)
class FullScreenE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun fullScreenPane_multiTapRoundTrip() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue(device.wait(Until.hasObject(By.text("Full-screen")), TIMEOUT))
        device.findObject(By.text("Full-screen")).click()
        // Pane renders and (host auto-request) shows consent.
        assertTrue("full-screen pane did not render",
            device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))
        assertTrue("consent did not appear", device.wait(Until.hasObject(By.text("Decline")), TIMEOUT))
        // Gesture 1: Decline → the pane reacts.
        device.findObject(By.text("Decline")).click()
        assertTrue("pane did not react to first tap",
            device.wait(Until.hasObject(By.text("Certification declined")), CRYPTO_TIMEOUT))
        // Gesture 2: Start over → consent returns. Proves the surface accepts a SECOND gesture.
        assertTrue("start-over control absent", device.wait(Until.hasObject(By.text("Start over")), TIMEOUT))
        device.findObject(By.text("Start over")).click()
        assertTrue("pane did not react to second tap (surface froze after first gesture)",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
    }
}
