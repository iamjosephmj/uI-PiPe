package tech.ssemaj.pipe.evilhost

import androidx.test.core.app.ActivityScenario
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

/** Requires :sample-provider installed. The evil host's own gate would also deny; to prove the
 *  PROVIDER-side gate, the evil host passes an allow-all authorizer (its own policy is its business —
 *  the provider must still refuse it). In EvilMainActivity use:
 *  `authorizer = EmbedAuthorizer { _, _ -> AuthDecision.Allow }`. */
@RunWith(AndroidJUnit4::class)
class EvilHostDeniedTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun providerDeniesEvilHost_noSurfaceBuilt() {
        ActivityScenario.launch(EvilMainActivity::class.java)
        assertTrue("evil host was not denied by provider",
            device.wait(Until.hasObject(By.textStartsWith("denied:")), TIMEOUT))
        assertFalse("real pane must never render inside evil host", device.hasObject(By.text("pane-ready")))
    }
}
