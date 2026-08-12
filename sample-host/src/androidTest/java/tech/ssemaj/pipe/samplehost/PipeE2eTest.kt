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

/**
 * Requires BOTH apps installed:
 *   ./gradlew :sample-host:installDebug :sample-provider:installDebug
 * The embedded pane is a real window of the provider process, so UiAutomator
 * (which sees all windows) is used instead of Espresso for pane-side assertions.
 */
@RunWith(AndroidJUnit4::class)
class PipeE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
        // Pane rendered ⇒ open handshake + SurfacePackage attach worked.
        assertTrue("pane did not render", device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))
    }

    @Test fun paneRendersAndHostReportsOpened() {
        assertNotNull(device.findObject(By.text("opened")))
    }

    @Test fun touchInPaneReachesProvider_andChannelProviderToHost() {
        device.findObject(By.text("Ping Host")).click() // touch crosses into provider window
        assertTrue("host never got provider message",
            device.wait(Until.hasObject(By.textStartsWith("msg:")), TIMEOUT))
    }

    @Test fun imeTextEntryWorksInsidePane() {
        val edit = device.findObject(By.clazz("android.widget.EditText"))
        edit.click()
        edit.text = "typed-in-pane"
        device.findObject(By.text("Ping Host")).click()
        assertTrue("typed text did not round-trip to host",
            device.wait(Until.hasObject(By.text("msg: typed-in-pane")), TIMEOUT))
    }

    @Test fun channelHostToProvider() {
        device.findObject(By.text("Send To Pane")).click()
        assertTrue("pane never showed host message",
            device.wait(Until.hasObject(By.text("hello-from-host")), TIMEOUT))
    }

    @Test fun closingHostActivityTearsDownPane() {
        device.pressHome()
        // Pane window must be gone once the host UI is gone.
        assertTrue("pane still visible after host gone",
            device.wait(Until.gone(By.text("pane-ready")), TIMEOUT))
    }
}
