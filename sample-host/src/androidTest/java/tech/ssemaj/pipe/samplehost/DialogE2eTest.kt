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

@RunWith(AndroidJUnit4::class)
class DialogE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun dialogPane_rendersMultiTapAndDismissCloses() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue(device.wait(Until.hasObject(By.text("Dialog")), TIMEOUT))
        device.findObject(By.text("Dialog")).click()
        assertTrue("dialog pane did not render",
            device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))
        // Gesture 1: Decline.
        assertTrue(device.wait(Until.hasObject(By.text("Decline")), TIMEOUT))
        device.findObject(By.text("Decline")).click()
        assertTrue("dialog pane did not react to first tap",
            device.wait(Until.hasObject(By.text("Certification declined")), 20_000L))
        // Gesture 2: Start over → consent returns (surface accepts a second gesture inside the dialog).
        assertTrue(device.wait(Until.hasObject(By.text("Start over")), TIMEOUT))
        device.findObject(By.text("Start over")).click()
        assertTrue("dialog pane froze after first gesture",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
        // Dismiss via back closes the dialog and the pane. The background embedded Card pane
        // (opened independently by MainActivity, never requested against) shows its own
        // permanent "pane-ready" text throughout the test, so checking for that text to go away
        // would never observe the dialog's own state. "Approve" is only ever shown by the
        // dialog's pane in this flow (the background pane is never requested against), so its
        // disappearance is what actually proves the dialog's pane is gone.
        device.pressBack()
        assertTrue("dialog did not close on back",
            device.wait(Until.gone(By.text("Approve")), TIMEOUT))
    }
}
