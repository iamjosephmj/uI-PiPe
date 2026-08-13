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

/** Requires :sample-provider installed. Proves a suspending host authorizer that yields off the
 *  fast path and then denies still refuses the pane: denial surfaces, nothing renders. */
@RunWith(AndroidJUnit4::class)
class SuspendAuthorizerDenialTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun suspendingAuthorizerDeny_noPaneRendered() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("targetAuthorizer", "suspend-deny")
        ActivityScenario.launch<MainActivity>(intent)
        assertTrue("host screen did not appear",
            device.wait(Until.hasObject(By.text("Request certification")), TIMEOUT))
        device.findObject(By.text("Request certification")).click()
        assertTrue("host did not report denial",
            device.wait(Until.hasObject(By.text("denied: async-policy")), TIMEOUT))
        assertFalse("pane must never render after async denial", device.hasObject(By.text("pane-ready")))
    }
}
