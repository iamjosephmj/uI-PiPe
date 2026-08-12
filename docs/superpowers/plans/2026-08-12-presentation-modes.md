# Pipe Presentation Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full-screen and dialog presentation categories to the pipe library alongside the embedded pane, sharing the same SurfaceControlViewHost embedding and coroutine session, and demo all three (plus a polished multi-pane) in the sample apps.

**Architecture:** A `PipePresentation` enum on `PipeRequest` selects the mode; the host opens via `PipeView` (embedded, unchanged), `PipeFullScreen` (full-bleed container), or `PipeDialog` (dialog + scrim). Full-screen/dialog panes are the whole interactive surface, so `PipeView` gives them direct persistent input (z-order on top) instead of embedded mode's per-gesture touch transfer — which makes multi-tap forms work.

**Tech Stack:** Kotlin 2.2.10, SurfaceControlViewHost, androidx.activity ComponentActivity, Compose Material 3 (sample-host), UiAutomator e2e on Pixel 6 Pro `19011FDEE0040L`.

**Spec:** `docs/superpowers/specs/2026-08-12-presentation-modes-design.md`

## Global Constraints

- minSdk 35, compileSdk/targetSdk 36, JVM target 17.
- `JAVA_HOME=~/.jdks/temurin-23.0.2` for all Gradle commands; device serial `19011FDEE0040L` (`adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard` before device tests).
- Test-anchor strings that MUST survive: `pane-ready`, `Approve`, `Decline`, `Certification issued`, `denied: ` (incl. `denied: async-policy`), `EVIL-PANE`.
- `PipeRequest`'s new field MUST have a default (`PipePresentation.EMBEDDED`) so every existing call site compiles unchanged.
- `:pipe` runs binary-compatibility-validator: after any public API change, run `./gradlew :pipe:apiDump` and commit the updated `pipe/api/pipe.api`. `./gradlew :pipe:apiCheck` must pass.
- Full-screen and dialog panes MUST accept repeated interactive gestures (multi-tap) — this is a device-verified gate, not optional. Embedded mode's touch behavior must not change (its suites stay green).
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## File Structure

```
pipe/src/main/java/tech/ssemaj/pipe/
  core/PipePresentation.kt              (NEW enum)
  core/PipeRequest.kt                   (add presentation field + internal forcePresentation)
  host/PipeView.kt                      (direct-input mode for non-embedded presentations)
  host/PipeFullScreen.kt                (NEW)
  host/PipeDialog.kt                    (NEW)
pipe/build.gradle.kts                   (api androidx.activity)
pipe/api/pipe.api                       (regenerated)
pipe/src/test/java/…/PipeRequestPresentationTest.kt (NEW)
sample-host/src/main/java/…/
  presentation/ui/CertificationScreen.kt (Full-screen / Dialog buttons)
  presentation/CertificationViewModel.kt (openFullScreen / openDialog hooks)
  MainActivity.kt                        (wire the two new modes)
  MultiPaneActivity.kt                   (Material UI polish)
sample-provider/src/main/java/…/DemoPaneService.kt (layout branch on request.presentation)
sample-host/src/androidTest/java/…/
  FullScreenE2eTest.kt                   (NEW)
  DialogE2eTest.kt                       (NEW)
gradle/libs.versions.toml               (androidx-activity entry)
```

---

### Task 1: Core — `PipePresentation` + `PipeRequest.presentation`

**Files:**
- Create: `pipe/src/main/java/tech/ssemaj/pipe/core/PipePresentation.kt`
- Modify: `pipe/src/main/java/tech/ssemaj/pipe/core/PipeRequest.kt`
- Test: `pipe/src/test/java/tech/ssemaj/pipe/core/PipeRequestPresentationTest.kt`
- Regenerate: `pipe/api/pipe.api`

**Interfaces:**
- Produces: `enum class PipePresentation { EMBEDDED, FULL_SCREEN, DIALOG }`; `PipeRequest(action, extras, presentation = EMBEDDED)`; `internal fun PipeRequest.forcePresentation(mode): PipeRequest`.

- [ ] **Step 1: Write the failing test** `pipe/src/test/java/tech/ssemaj/pipe/core/PipeRequestPresentationTest.kt`:

```kotlin
package tech.ssemaj.pipe.core

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipeRequestPresentationTest {

    @Test fun defaultPresentationIsEmbedded() {
        assertEquals(PipePresentation.EMBEDDED, PipeRequest("a").presentation)
    }

    @Test fun parcelizePreservesPresentation() {
        val original = PipeRequest("a", presentation = PipePresentation.DIALOG)
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val restored = PipeRequest.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        assertEquals(PipePresentation.DIALOG, restored.presentation)
    }

    @Test fun forcePresentationOverrides() {
        assertEquals(PipePresentation.FULL_SCREEN,
            PipeRequest("a").forcePresentation(PipePresentation.FULL_SCREEN).presentation)
    }

    @Test fun forcePresentationReturnsSameInstanceWhenUnchanged() {
        val req = PipeRequest("a", presentation = PipePresentation.DIALOG)
        assertSame(req, req.forcePresentation(PipePresentation.DIALOG))
    }
}
```

This is an Android (Parcel) test — put it under `src/test` and rely on the module's `unitTests { isReturnDefaultValues = true }`? No: Parcel needs Robolectric. `:pipe` already lists `robolectric` as a test dep? If not, add `testImplementation(libs.robolectric)` and `testImplementation(libs.androidx.test.ext)` to `pipe/build.gradle.kts`, and annotate with `@RunWith(org.robolectric.RobolectricTestRunner::class)` instead of `AndroidJUnit4`. Check `pipe/build.gradle.kts` first; use whichever runner the module already supports (the existing `:pipe` unit tests reveal the convention).

- [ ] **Step 2: Run to verify fail** — `JAVA_HOME=~/.jdks/temurin-23.0.2 ./gradlew :pipe:testDebugUnitTest --tests "*PipeRequestPresentationTest*"`; expect compile failure (unresolved `PipePresentation` / `forcePresentation`).

- [ ] **Step 3: Implement** `core/PipePresentation.kt`:

```kotlin
package tech.ssemaj.pipe.core

/** How the host presents a pane. Chosen by the host; readable by the provider via [PipeRequest]. */
enum class PipePresentation { EMBEDDED, FULL_SCREEN, DIALOG }
```

Modify `core/PipeRequest.kt`:

```kotlin
package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Launch payload the host sends to the provider; [action] selects the pane, [extras] parameterize
 *  it, [presentation] tells the provider how the host is showing the pane. */
@Parcelize
data class PipeRequest(
    val action: String,
    val extras: Bundle = Bundle(),
    val presentation: PipePresentation = PipePresentation.EMBEDDED,
) : Parcelable

/** Returns a copy pinned to [mode] (or the same instance when already in that mode). Library-internal:
 *  the host containers use it to guarantee the right mode reaches the provider regardless of caller. */
internal fun PipeRequest.forcePresentation(mode: PipePresentation): PipeRequest =
    if (presentation == mode) this else copy(presentation = mode)
```

- [ ] **Step 4: Run to verify pass** — same command; expect PASS (4 tests).

- [ ] **Step 5: Regenerate API + verify** — `./gradlew :pipe:apiDump` then `./gradlew :pipe:apiCheck :pipe:assembleDebug`; BUILD SUCCESSFUL. Confirm `pipe/api/pipe.api` now lists `PipePresentation` and the new `PipeRequest` constructor/`presentation` accessor.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: PipePresentation on PipeRequest (embedded/full-screen/dialog)"
```

---

### Task 2: Full-screen — `PipeView` direct input + `PipeFullScreen` + demo + device gate

**Files:**
- Modify: `pipe/src/main/java/tech/ssemaj/pipe/host/PipeView.kt`, `pipe/build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `pipe/src/main/java/tech/ssemaj/pipe/host/PipeFullScreen.kt`
- Modify: `sample-provider/.../DemoPaneService.kt` (layout branch), `sample-host/.../presentation/CertificationViewModel.kt`, `.../presentation/ui/CertificationScreen.kt`, `.../MainActivity.kt`
- Create: `sample-host/src/androidTest/java/tech/ssemaj/pipe/samplehost/FullScreenE2eTest.kt`
- Regenerate: `pipe/api/pipe.api`

**Interfaces:**
- Consumes: `PipeRequest.presentation`, `forcePresentation` (Task 1); `PipeView.open`/`openIn`, `PipeSession`.
- Produces: `object PipeFullScreen { fun open(activity, provider, request, authorizer, onSession, onError): Job }`.

- [ ] **Step 1: Catalog + dependency.** In `gradle/libs.versions.toml` add under `[versions]` `activity = "1.10.1"` and under `[libraries]` `androidx-activity = { module = "androidx.activity:activity", version.ref = "activity" }`. In `pipe/build.gradle.kts` add `api(libs.androidx.activity)` (public: `PipeFullScreen`/`PipeDialog` expose `ComponentActivity`).

- [ ] **Step 2: `PipeView` direct-input mode.** In `PipeView.kt`:
  - Add import `tech.ssemaj.pipe.core.PipePresentation`.
  - Add a field near `current`: `@Volatile private var directInput = false`.
  - In the `init` touch listener, guard the transfer so it only runs in embedded mode:

```kotlin
surfaceView.setOnTouchListener { _, event ->
    if (!directInput && event.actionMasked == MotionEvent.ACTION_DOWN) {
        val embedded = embeddedInputToken
        val hostToken = surfaceView.rootSurfaceControl?.inputTransferToken
        if (embedded != null && hostToken != null) {
            runCatching { windowManager?.transferTouchGesture(hostToken, embedded) }
                .onSuccess { transferred ->
                    if (transferred == false) {
                        Log.w(TAG, "transferTouchGesture returned false; pane may not receive touch")
                    }
                }
                .onFailure { t -> Log.w(TAG, "transferTouchGesture threw; pane may not receive touch", t) }
        }
    }
    false
}
```

  - At the very start of `open(...)` (inside `withContext(dispatcher) { ... }`, before `check(current == null)`), pin the input mode from the request. Non-embedded panes are the whole interactive surface, so render the embedded surface on top and take input directly (no per-gesture transfer):

```kotlin
if (request.presentation != PipePresentation.EMBEDDED) {
    directInput = true
    surfaceView.setZOrderOnTop(true)
}
```

  - In `close(...)` reset for reuse: set `directInput = false` and `surfaceView.setZOrderOnTop(false)` where `current` is cleared (both terminate/close paths that null `current`).

- [ ] **Step 3: Implement `PipeFullScreen.kt`:**

```kotlin
package tech.ssemaj.pipe.host

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Job
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeRequest

/** Opens a pane that fills [activity]'s content area. Back or session close removes the container. */
object PipeFullScreen {
    fun open(
        activity: ComponentActivity,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
        onSession: (PipeSession) -> Unit = {},
        onError: (PipeException) -> Unit = {},
    ): Job {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val pipeView = PipeView(activity)
        root.addView(pipeView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                pipeView.close()
                remove(root, pipeView, this)
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        return pipeView.openIn(
            owner = activity,
            provider = provider,
            request = request.forcePresentation(PipePresentation.FULL_SCREEN),
            authorizer = authorizer,
            onError = { e -> remove(root, pipeView, backCallback); onError(e) },
            onSession = { session -> onSession(session) },
        )
    }

    private fun remove(root: ViewGroup, view: PipeView, cb: OnBackPressedCallback) {
        cb.isEnabled = false
        cb.remove()
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
```

(`forcePresentation` is internal to `:pipe`, same module — accessible here.)

- [ ] **Step 4: Provider — presentation-aware padding + a "Start over" affordance (multi-gesture).** In `DemoPaneService.onOpenPane`:
  - Presentation padding: `val pad = if (request.presentation == tech.ssemaj.pipe.core.PipePresentation.DIALOG) 24 else 48` and use it in the root `setPadding`.
  - Capture the last request so the pane can re-enter consent without a host round-trip: add `var lastRequest: CertificationRequest? = null`; in `onMessage`, set `lastRequest = req` before `presenter.onRequest(req)`.
  - Add a `MaterialButton` `startOver` (text `Start over`), initially `View.GONE`, placed after `resultText` in the root. Its click: `lastRequest?.let(presenter::onRequest)`.
  - In the presenter-state collector, show `startOver` in the `Issued`/`Declined` states and hide it in `Idle`/`Consent`:

```kotlin
startOver.visibility =
    if (state is PanePresenter.State.Issued || state is PanePresenter.State.Declined) View.VISIBLE
    else View.GONE
```

  This makes any pane repeatable: Approve/Decline is gesture 1, "Start over" is gesture 2 — both provider-side, proving the surface accepts repeated touch. Keep every existing anchor string.

- [ ] **Step 5: Sample-host wiring (auto-request so consent shows immediately).** The full-screen/dialog panes fill the window, so the host sends the certification request itself on open (no host-side Request button is visible). In `CertificationViewModel`, store the `ProviderComponent` (pass it into the VM from `MainActivity`, or add a `setProvider`), and add:

```kotlin
fun openFullScreen(activity: androidx.activity.ComponentActivity) {
    tech.ssemaj.pipe.host.PipeFullScreen.open(
        activity = activity,
        provider = provider, // the stored ProviderComponent
        request = tech.ssemaj.pipe.core.PipeRequest(
            tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION,
            presentation = tech.ssemaj.pipe.core.PipePresentation.FULL_SCREEN,
        ),
        onSession = { session ->
            // Drive the flow: send the request so the pane shows consent immediately.
            viewModelScope.launch { container.requestCertification(session, "Pipe Sample Host") }
        },
    )
}
```

(`container.requestCertification` returns an Outcome; for the container demos we don't need to render the host badge — the pane shows issued/declined and "Start over". Ignoring the return value is fine, or verify and log.) Add a `Full-screen` `OutlinedButton` to `CertificationScreen` near the Request button calling an `onOpenFullScreen` lambda; wire it in `MainActivity` to `viewModel.openFullScreen(this)`. Keep the embedded flow intact.

- [ ] **Step 6: `FullScreenE2eTest.kt`** — proves render + **multi-tap** + teardown:

```kotlin
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
```

The two-gesture assertion is the non-negotiable gate: Decline (gesture 1 → "Certification declined") then Start over (gesture 2 → consent/"Approve" returns). If the second tap does not register, the direct-input model is wrong — fix it (Step 7), do not weaken the test.

- [ ] **Step 7: Build, regenerate API, device gate** — `./gradlew :pipe:apiDump && ./gradlew :pipe:apiCheck :sample-host:assembleDebug :sample-provider:assembleDebug`. Then wake device, `./gradlew :sample-host:installDebug :sample-provider:installDebug`, and run `./gradlew :sample-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tech.ssemaj.pipe.samplehost.FullScreenE2eTest`. Must PASS. If multi-tap fails (pane freezes after first gesture), the `setZOrderOnTop(true)` direct-input model needs adjustment: try `surfaceView.setZOrderMediaOverlay(true)` instead, or keep `transferTouchGesture` but re-arm it (log whether the host receives the 2nd ACTION_DOWN, as in the certification-demo investigation). Do NOT weaken the test; fix the input model until two taps register. Report what worked.

- [ ] **Step 8: Regression** — run the existing embedded suites to confirm no touch regression: `./gradlew :sample-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tech.ssemaj.pipe.samplehost.PipeE2eTest`. Must PASS.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: full-screen pipes (PipeFullScreen) with direct-input multi-gesture panes"
```

---

### Task 3: Dialog — `PipeDialog` + demo + device gate

**Files:**
- Create: `pipe/src/main/java/tech/ssemaj/pipe/host/PipeDialog.kt`
- Modify: `sample-host/.../presentation/CertificationViewModel.kt`, `.../presentation/ui/CertificationScreen.kt`, `.../MainActivity.kt`
- Create: `sample-host/src/androidTest/java/tech/ssemaj/pipe/samplehost/DialogE2eTest.kt`
- Regenerate: `pipe/api/pipe.api`

**Interfaces:**
- Consumes: Task 1 + Task 2 (`PipeView` direct input, `forcePresentation`).
- Produces: `class PipeDialog { companion object { fun show(activity, provider, request, authorizer, onSession, onError, onDismiss): PipeDialog }; fun dismiss() }`.

- [ ] **Step 1: Implement `PipeDialog.kt`:**

```kotlin
package tech.ssemaj.pipe.host

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipePresentation
import tech.ssemaj.pipe.core.PipeRequest

/** Shows a pane inside a dimmed dialog. Dismiss (scrim/back) closes the session cleanly. */
class PipeDialog private constructor(private val dialog: Dialog) {

    fun dismiss() = dialog.dismiss()

    companion object {
        fun show(
            activity: ComponentActivity,
            provider: ProviderComponent,
            request: PipeRequest,
            authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
            onSession: (PipeSession) -> Unit = {},
            onError: (PipeException) -> Unit = {},
            onDismiss: () -> Unit = {},
        ): PipeDialog {
            val pipeView = PipeView(activity)
            val container = FrameLayout(activity).apply {
                val m = (16 * resources.displayMetrics.density).toInt()
                setPadding(m, m, m, m)
                addView(pipeView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
            val dialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(container)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val dm = activity.resources.displayMetrics
                window?.setLayout((dm.widthPixels * 0.92f).toInt(), (dm.heightPixels * 0.62f).toInt())
            }
            val handle = PipeDialog(dialog)
            var openSession: PipeSession? = null
            dialog.setOnDismissListener {
                openSession?.close()
                onDismiss()
            }
            dialog.show()
            pipeView.openIn(
                owner = activity,
                provider = provider,
                request = request.forcePresentation(PipePresentation.DIALOG),
                authorizer = authorizer,
                onError = { e -> dialog.dismiss(); onError(e) },
                onSession = { session -> openSession = session; onSession(session) },
            )
            return handle
        }
    }
}
```

(A dialog dimmed scrim comes from the default dialog window dim; the transparent background + inner padding gives a card feel. The `PipeView` renders its surface on top within the dialog window — square-cornered surface inside padded container is acceptable for v1 per the spec.)

- [ ] **Step 2: Sample-host wiring.** Add `openDialog(activity)` to `CertificationViewModel` mirroring `openFullScreen` — call `PipeDialog.show(...)` with `presentation = DIALOG` and the same `onSession` auto-request (`viewModelScope.launch { container.requestCertification(session, "Pipe Sample Host") }`). Add a `Dialog` `OutlinedButton` next to `Full-screen` in `CertificationScreen`; wire in `MainActivity` to `viewModel.openDialog(this)`.

- [ ] **Step 3: `DialogE2eTest.kt`** — render + multi-tap + dismiss-closes:

```kotlin
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
        // Dismiss via back closes the dialog and the pane.
        device.pressBack()
        assertTrue("dialog did not close on back",
            device.wait(Until.gone(By.text("pane-ready")), TIMEOUT))
    }
}
```

- [ ] **Step 4: Build, regenerate API, device gate** — `./gradlew :pipe:apiDump && ./gradlew :pipe:apiCheck :sample-host:assembleDebug`, install, run `DialogE2eTest` on the device. Must PASS (render + multi-tap + dismiss). Same input-model fallback note as Task 2 Step 7 if multi-tap fails inside the dialog window.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: dialog pipes (PipeDialog) with dimmed scrim and multi-gesture panes"
```

---

### Task 4: Multi-pane UI polish + full verification sweep

**Files:**
- Modify: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/MultiPaneActivity.kt`, `sample-host/src/main/res/layout/activity_multi_pane.xml`
- Modify: `docs/superpowers/specs/2026-08-12-presentation-modes-design.md` (status)

- [ ] **Step 1: Material multi-pane layout.** Replace the bare `TextView` + `PipeView` stack in `activity_multi_pane.xml` with two Material `CardView`s (use `com.google.android.material.card.MaterialCardView` — add `implementation(libs.material.components)` to `sample-host/build.gradle.kts` if absent), each holding a labeled header row (`TextView` "Pane A" / "Pane B" + a status `TextView`) and the `PipeView`. Give the activity a Material theme (`Theme.Material3.DayNight.NoActionBar`) via `android:theme` on the `<activity>` or the app theme. Keep the view ids (`status_a`, `pane_a`, `status_b`, `pane_b`) so `MultiPaneActivity.kt` and `MultiPaneE2eTest` are unchanged. Example card:

```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent" android:layout_height="0dp"
    android:layout_weight="1" android:layout_margin="8dp"
    app:cardCornerRadius="16dp" app:cardElevation="2dp">
    <LinearLayout android:orientation="vertical"
        android:layout_width="match_parent" android:layout_height="match_parent"
        android:padding="12dp">
        <TextView android:text="Pane A" android:textStyle="bold"
            android:layout_width="wrap_content" android:layout_height="wrap_content" />
        <TextView android:id="@+id/status_a" android:text="A: opening"
            android:layout_width="wrap_content" android:layout_height="wrap_content" />
        <tech.ssemaj.pipe.host.PipeView android:id="@+id/pane_a"
            android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

(Repeat for Pane B with `status_b`/`pane_b`.) Wrap the two cards in a vertical `LinearLayout` root. Note: the multi-pane panes stay embedded mode — do NOT change their presentation; they keep the existing single-gesture-per-session behavior, which the test already accounts for.

- [ ] **Step 2: Assemble** — `./gradlew :sample-host:assembleDebug`; BUILD SUCCESSFUL.

- [ ] **Step 3: Whole-repo build + BCV** — `./gradlew build -x lint -x connectedDebugAndroidTest`; BUILD SUCCESSFUL (unit tests + `:pipe:apiCheck` green).

- [ ] **Step 4: Full connected suite (clean install)** — wake device; uninstall the four app ids; `./gradlew :sample-provider:installDebug :evil-provider:installDebug :evil-host:installDebug :sample-host:installDebug`; then `./gradlew :sample-host:connectedDebugAndroidTest :evil-host:connectedDebugAndroidTest :sample-provider:connectedDebugAndroidTest`. All green (sample-host now includes FullScreen + Dialog + MultiPane + Reopen + PipeE2e + Security + SuspendDenial). Paste results.

- [ ] **Step 5: Spec status** — set the presentation-modes spec `**Status:** implemented`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: Material multi-pane UI; verification sweep for presentation modes"
```
