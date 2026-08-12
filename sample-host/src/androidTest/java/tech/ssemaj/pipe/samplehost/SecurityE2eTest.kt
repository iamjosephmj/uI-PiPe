package tech.ssemaj.pipe.samplehost

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L

/** Requires :evil-provider installed. */
@RunWith(AndroidJUnit4::class)
class SecurityE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun hostDeniesEvilProvider_noSurfaceNoBind() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("targetPackage", "tech.ssemaj.pipe.evilprovider")
            .putExtra("targetService", "tech.ssemaj.pipe.evilprovider.EvilPaneService")
        ActivityScenario.launch<MainActivity>(intent)
        assertTrue("host did not report denial",
            device.wait(Until.hasObject(By.textStartsWith("denied:")), TIMEOUT))
        assertFalse("evil pane must never render", device.hasObject(By.text("EVIL-PANE")))
        // No bind happened: the evil provider process must not be running.
        val ps = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pidof tech.ssemaj.pipe.evilprovider")
        ps.use {
            assertTrue("evil provider process is running — bind happened",
                java.io.FileInputStream(it.fileDescriptor).readBytes().decodeToString().isBlank())
        }
    }
}
