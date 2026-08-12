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

/** Close + reopen on one PipeView: sequencer reset, surface re-attach, token re-acquisition. */
@RunWith(AndroidJUnit4::class)
class ReopenE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun reopenThenFullRoundTrip() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue(device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))

        device.findObject(By.text("Reopen")).click()
        assertTrue("pane did not come back after reopen",
            device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))

        device.findObject(By.text("Request certification")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
        device.findObject(By.text("Approve")).click()
        assertTrue("round-trip after reopen failed",
            device.wait(Until.hasObject(By.textContains("-verified")), CRYPTO_TIMEOUT) ||
                device.hasObject(By.text("Software-backed")))
    }
}
