package tech.ssemaj.pipe.samplehost

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L

/**
 * Two panes on one provider service render as two independent sessions: each opens, receives its
 * own certification request, and shows its own consent with a distinct challenge nonce. That proves
 * the provider keeps per-pane state (separate presenter + HostHandle) and the host→provider request
 * channel is delivered independently per session.
 *
 * The approve→attest→verify round-trip itself is covered per session by [PipeE2eTest]; it is not
 * re-driven here because UiAutomator cannot inject touch into two simultaneous
 * SurfaceControlViewHost windows (the transferTouchGesture handoff only fires for injected events
 * with a single embedded window). Two-pane approval was verified manually on device: tapping each
 * pane's Approve yields "A: verified" and "B: verified" independently.
 */
@RunWith(AndroidJUnit4::class)
class MultiPaneE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Polls until [count] objects match [selector]; UiAutomator has no multi-object SearchCondition. */
    private fun waitForCount(selector: BySelector, count: Int, timeout: Long): Boolean {
        val deadline = android.os.SystemClock.uptimeMillis() + timeout
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (device.findObjects(selector).size == count) return true
            Thread.sleep(250)
        }
        return device.findObjects(selector).size == count
    }

    @Test fun twoPanesOpenIndependentlyWithDistinctChallenges() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MultiPaneActivity::class.java)
        ActivityScenario.launch<MultiPaneActivity>(intent)

        // Both panes reach their own consent screen (requests auto-sent on open).
        assertTrue("two consent panes did not appear", waitForCount(By.text("Approve"), 2, TIMEOUT))

        // Distinct challenge nonces prove two independent sessions, each with its own request
        // delivered and its own pane built — not one pane echoed twice.
        val challenges = device.findObjects(By.textStartsWith("challenge ")).map { it.text }
        assertEquals("expected two challenge prompts", 2, challenges.size)
        assertTrue("the two panes must carry distinct challenge nonces", challenges[0] != challenges[1])
    }
}
