# uI-PiPe KYC Codelab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Google-Codelabs-style GitHub Pages tutorial plus its two runnable sample apps: a mock bank host that embeds a separately-signed, RASP-guarded mock KYC verifier via uI-PiPe, pinned by cert allowlist, exchanging typed results over pipe-serialization.

**Architecture:** Three new Gradle modules in the existing `pipe` repo — a pure-JVM `:sample-kyc-contract` (shared `@Serializable` wire types), a `:sample-kyc-verifier` provider app (own signing key; `PipeProviderService` rendering a mock KYC wizard; hydra RASP applied conditionally via a `-Prasp` gradle property), and a `:sample-kyc-host` bank app (`PipeFullScreen.open` with a pinned-cert `allowlist` authorizer). The codelab lives as claat markdown at `docs/codelab/kyc.md`, generated to static HTML under `docs/codelab/` and served by GitHub Pages from `/docs`.

**Tech Stack:** Kotlin 2.2.10, AGP 8.13.0, uI-PiPe (`:pipe` + `:pipe-serialization`), kotlinx-serialization-cbor, plain Android Views (`ViewFlipper`), hydra RASP Gradle plugin `tech.thessemaj.hydra:2.3.0` (Maven Central), Google `claat`.

## Global Constraints

- Android modules: `compileSdk = 36`, `minSdk = 30`, `targetSdk = 36`, Java 17 / `jvmTarget = "17"`; plugins via `libs.plugins.*` catalog aliases only (no hardcoded plugin versions in module files).
- Pure-JVM contract module: `libs.plugins.kotlin.jvm` + `libs.plugins.kotlin.serialization`, Java toolchain 17, sources under `src/main/kotlin`.
- Provider service must be `exported="true"` with an `<intent-filter>` for action `tech.ssemaj.pipe.action.OPEN_PANE`; host binds by explicit `ProviderComponent(packageName, serviceClass)` and needs a `<queries><package .../></queries>` entry (Android 11+ visibility).
- All new non-`:pipe` modules must be added to `apiValidation.ignoredProjects` in the root `build.gradle.kts` (only `:pipe`/`:pipe-serialization` carry API dumps).
- **Trust boundary:** host uses `PipeAuthorizers.allowlist(<verifier cert sha256>)`; verifier overrides `authorizer()` to admit the host (host→verifier pinning only). Cert format is **lowercase hex SHA-256 of the signing cert, no colons**, matching `PeerIdentity.signingCertSha256`.
- **RASP scoping:** hydra applies to `:sample-kyc-verifier` **only when the `rasp` gradle property is present** (`-Prasp`); a plain build stays emulator-runnable. hydra is lethal with no observe mode.
- Naming/copy: host label **"Meridian Bank"**, verifier label **"VerifyID"**, both fictional; all KYC data is mocked/placeholder — no real PII, camera, branding, or domains.
- No AI-attribution trailers on commits (repo convention).
- Verified end-to-end on emulator-5554 (API 30) and Pixel 6 Pro (serial `19011FDEE0040L`, API 36). `JAVA_HOME=/home/joseph/.jdks/temurin-23.0.2` for Gradle; `adb` at `$HOME/Android/Sdk/platform-tools`.

---

## Reference: exact uI-PiPe API used by this plan

```kotlin
// host
object PipeFullScreen { fun open(activity: ComponentActivity, provider: ProviderComponent,
  request: PipeRequest, authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
  bindImportance: PipeBindImportance = PipeBindImportance.NORMAL,
  onSession: (PipeSession) -> Unit = {}, onError: (PipeException) -> Unit = {}): Job }
data class ProviderComponent(val packageName: String, val serviceClass: String)
object PipeAuthorizers { fun sameSigningKey(context: Context): PipeAuthorizer
  fun allowlist(vararg certSha256: String): PipeAuthorizer; fun anyOf(vararg a: PipeAuthorizer): PipeAuthorizer }
fun interface PipeAuthorizer { suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision }
sealed interface AuthDecision { data object Allow; data class Deny(val reason: String) } // objects: AuthDecision.Allow
data class PeerIdentity(val uid: Int, val packages: List<String>, val signingCertSha256: List<String>)
interface PipeSession { val peer: PeerIdentity; val state: StateFlow<PipeState>
  val messages: Flow<PipeMessage>; suspend fun send(message: PipeMessage): Boolean; fun close() }
sealed interface PipeState { data object Connecting; data class Open(val peer: PeerIdentity)
  data class Closed(val cause: PipeException?) }
// provider
abstract class PipeProviderService : Service() {
  open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)
  abstract suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult }
sealed interface PaneResult { data class Content(val content: PipeContent, val spec: PaneSpec = PaneSpec())
  data class Reject(val reason: String) }
interface PipeContent { val view: View; fun onMessage(message: PipeMessage) {}; fun onClosed(reason: CloseReason) {} }
interface HostHandle { val peer: PeerIdentity; suspend fun send(message: PipeMessage): Boolean; fun close() }
// core
@Parcelize data class PipeRequest(val action: String, val extras: Bundle = Bundle())
enum class CloseReason { HOST_CLOSED, PROVIDER_CLOSED, PEER_DIED }
sealed class PipeException; class PipeDeniedException(val reason: String, val source: DenialSource)
class PipeTransportException(message: String, cause: Throwable? = null); class PipeTimeoutException
class PipeProviderUnavailableException(val kind: Unavailable) // NOT_INSTALLED/NOT_VISIBLE/CERT_UNREADABLE/NO_SERVICE
enum class DenialSource { HOST_POLICY, PROVIDER_POLICY }
// pipe-serialization (com.github...:pipe-serialization)
object PipeCodec { inline fun <reified T> encode(payload: T): PipeMessage
  inline fun <reified T> decodeOrNull(message: PipeMessage): T? }
suspend inline fun <reified T> PipeSession.send(payload: T): Boolean
inline fun <reified T> PipeSession.messagesOf(): Flow<T>
suspend inline fun <reified T> HostHandle.send(payload: T): Boolean
```

**Failure paths for the RASP finale (host side):** hydra terminates the verifier process at startup, so the kill most likely arrives **before** the pane opens — surfacing via `onError(PipeTransportException)` (bind connected then died) or `onError(PipeProviderUnavailableException)`. If it were killed after open, it would arrive via `session.state == PipeState.Closed(cause != null)`. The host must handle **both** and render the same graceful "verification unavailable" state. Which path fires is confirmed on-device in Task 6.

---

## Task 1: `:sample-kyc-contract` — shared wire types

**Files:**
- Create: `sample-kyc-contract/build.gradle.kts`
- Create: `sample-kyc-contract/src/main/kotlin/tech/ssemaj/pipe/samples/kyc/KycContract.kt`
- Test: `sample-kyc-contract/src/test/kotlin/tech/ssemaj/pipe/samples/kyc/KycContractRoundTripTest.kt`
- Modify: `settings.gradle.kts` (add `include(":sample-kyc-contract")`)
- Modify: `build.gradle.kts` (add `"sample-kyc-contract"` to `apiValidation.ignoredProjects`)

**Interfaces:**
- Produces: `KycContract.ACTION_KYC = "pipe.demo.kyc"`; `@Serializable data class KycRequest(val reference: String, val level: KycLevel)`; `@Serializable data class KycResult(val reference: String, val status: KycStatus, val issuedAtEpochMs: Long)`; `@Serializable enum class KycLevel { BASIC, ENHANCED }`; `@Serializable enum class KycStatus { APPROVED, DECLINED, ERROR }`. Consumed by Tasks 2 (verifier) and 3 (host).

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, add after `include(":sample-contract")`:
```kotlin
include(":sample-kyc-contract")
```
In root `build.gradle.kts`, add **only this module** to the `ignoredProjects` line (bcv 0.16.3 rejects `ignoredProjects` entries for projects that don't exist yet, so `sample-kyc-host`/`sample-kyc-verifier` are added in their own creating tasks):
```kotlin
apiValidation {
    ignoredProjects.addAll(listOf("sample-host", "sample-provider", "evil-host", "evil-provider", "sample-contract", "sample-kyc-contract"))
}
```

- [ ] **Step 2: Write the module build file**

Create `sample-kyc-contract/build.gradle.kts` (mirrors `:sample-contract`):
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.junit)
}
```

- [ ] **Step 3: Write the failing round-trip test**

Create `sample-kyc-contract/src/test/kotlin/tech/ssemaj/pipe/samples/kyc/KycContractRoundTripTest.kt`:
```kotlin
package tech.ssemaj.pipe.samples.kyc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class KycContractRoundTripTest {
    @Test fun request_roundtrips() {
        val req = KycRequest(reference = "ref-123", level = KycLevel.ENHANCED)
        val back = Cbor.decodeFromByteArray<KycRequest>(Cbor.encodeToByteArray(req))
        assertEquals(req, back)
    }

    @Test fun result_roundtrips() {
        val res = KycResult(reference = "ref-123", status = KycStatus.APPROVED, issuedAtEpochMs = 1_723_680_000_000L)
        val back = Cbor.decodeFromByteArray<KycResult>(Cbor.encodeToByteArray(res))
        assertEquals(res, back)
    }
}
```

- [ ] **Step 4: Run the test, verify it fails to compile**

Run: `./gradlew :sample-kyc-contract:test`
Expected: FAIL — `KycRequest`/`KycResult`/`KycLevel`/`KycStatus` unresolved.

- [ ] **Step 5: Write the contract types**

Create `sample-kyc-contract/src/main/kotlin/tech/ssemaj/pipe/samples/kyc/KycContract.kt`:
```kotlin
package tech.ssemaj.pipe.samples.kyc

import kotlinx.serialization.Serializable

/** Shared wire contract for the KYC codelab. Pure-JVM so both the host and verifier apps can depend on it. */
object KycContract {
    /** Request action the bank host opens the verifier with. */
    const val ACTION_KYC = "pipe.demo.kyc"
}

/** Depth of the identity check the bank is asking for (mock). */
@Serializable
enum class KycLevel { BASIC, ENHANCED }

/** Outcome the verifier reports back to the bank (mock). */
@Serializable
enum class KycStatus { APPROVED, DECLINED, ERROR }

/** Bank → verifier: start a verification for an opaque [reference] at the requested [level]. */
@Serializable
data class KycRequest(val reference: String, val level: KycLevel)

/** Verifier → bank: the result for [reference]. [issuedAtEpochMs] is a wall-clock stamp set by the verifier. */
@Serializable
data class KycResult(val reference: String, val status: KycStatus, val issuedAtEpochMs: Long)
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `./gradlew :sample-kyc-contract:test`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts sample-kyc-contract/
git commit -m "feat: add :sample-kyc-contract shared wire types for the KYC codelab"
```

---

## Task 2: `:sample-kyc-verifier` — mock KYC provider app (no RASP yet)

**Files:**
- Create: `sample-kyc-verifier/build.gradle.kts`
- Create: `sample-kyc-verifier/src/main/AndroidManifest.xml`
- Create: `sample-kyc-verifier/src/main/java/tech/ssemaj/pipe/kycverifier/KycVerifierService.kt`
- Create: `sample-kyc-verifier/src/main/java/tech/ssemaj/pipe/kycverifier/KycPaneView.kt`
- Create: `security/kyc-verifier.keystore` (generated in a step below)
- Modify: `settings.gradle.kts` (add `include(":sample-kyc-verifier")`)
- Modify: `build.gradle.kts` (add `"sample-kyc-verifier"` to `apiValidation.ignoredProjects`)

**Interfaces:**
- Consumes: `KycContract.ACTION_KYC`, `KycRequest`, `KycResult`, `KycLevel`, `KycStatus` (Task 1); `PipeProviderService`, `PaneResult.Content`, `PipeContent`, `HostHandle`, `PipeCodec`, `HostHandle.send<T>` (uI-PiPe).
- Produces: an installable APK exposing service `tech.ssemaj.pipe.kycverifier.KycVerifierService` (action `tech.ssemaj.pipe.action.OPEN_PANE`), applicationId `tech.ssemaj.pipe.kycverifier`, signed with `security/kyc-verifier.keystore`.

- [ ] **Step 1: Register the module**

In `settings.gradle.kts` add:
```kotlin
include(":sample-kyc-verifier")
```
In root `build.gradle.kts`, add `"sample-kyc-verifier"` to the `apiValidation.ignoredProjects` list (app modules must be ignored or bcv demands an API dump):
```kotlin
    ignoredProjects.addAll(listOf("sample-host", "sample-provider", "evil-host", "evil-provider", "sample-contract", "sample-kyc-contract", "sample-kyc-verifier"))
```

- [ ] **Step 2: Generate the verifier's own signing key**

Run from repo root (creates a key distinct from the debug key the host uses):
```bash
keytool -genkeypair -v -keystore security/kyc-verifier.keystore \
  -alias verifier -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass verifierpass -keypass verifierpass \
  -dname "CN=VerifyID, O=VerifyID Inc, C=US"
```
Expected: `security/kyc-verifier.keystore` created.

- [ ] **Step 3: Write the module build file**

Create `sample-kyc-verifier/build.gradle.kts` (RASP wiring is added in Task 5; this is the plain build):
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "tech.ssemaj.pipe.kycverifier"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.kycverifier"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("verifier") {
            storeFile = rootProject.file("security/kyc-verifier.keystore")
            storePassword = "verifierpass"
            keyAlias = "verifier"
            keyPassword = "verifierpass"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("verifier") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":pipe"))
    implementation(project(":pipe-serialization"))
    implementation(project(":sample-kyc-contract"))
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
}
```

- [ ] **Step 4: Write the manifest**

Create `sample-kyc-verifier/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="VerifyID">
        <service
            android:name=".KycVerifierService"
            android:exported="true">
            <intent-filter>
                <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 5: Write the mock KYC pane view**

Create `sample-kyc-verifier/src/main/java/tech/ssemaj/pipe/kycverifier/KycPaneView.kt`. A `ViewFlipper` wizard (Consent → mock ID capture → mock selfie → Verified), driven programmatically; on the final screen it calls back with the chosen [KycStatus].
```kotlin
package tech.ssemaj.pipe.kycverifier

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.samples.kyc.KycStatus

/**
 * The verifier's UI, rendered inside the host's window as a uI-PiPe pane. A small mock wizard —
 * no real camera or PII. [onDecision] is invoked once with the user's outcome; [onClose] dismisses.
 */
class KycPaneView(
    ctx: Context,
    private val bankName: String,
    private val onDecision: (KycStatus) -> Unit,
    private val onClose: () -> Unit,
) : PipeContent {

    private val flipper = ViewFlipper(ctx)

    override val view: View get() = flipper

    init {
        flipper.setBackgroundColor(Color.parseColor("#0D141D"))
        flipper.addView(screen(ctx, "Verify your identity",
            "VerifyID needs to confirm your identity for $bankName.", "Continue") { flipper.showNext() })
        flipper.addView(screen(ctx, "Scan your ID",
            "[ mock document frame ]\nNo real camera — this is a demo.", "Capture") { flipper.showNext() })
        flipper.addView(screen(ctx, "Liveness selfie",
            "[ mock selfie frame ]\nNo real camera — this is a demo.", "Capture") { flipper.showNext() })
        flipper.addView(screen(ctx, "Verified ✓",
            "Identity confirmed for $bankName.", "Done") { onDecision(KycStatus.APPROVED) })
    }

    private fun screen(ctx: Context, title: String, body: String, cta: String, onCta: () -> Unit): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(72, 72, 72, 72)
            addView(TextView(ctx).apply { text = title; textSize = 24f; setTextColor(Color.WHITE) })
            addView(TextView(ctx).apply {
                text = "\n$body\n"; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8B98A5"))
            })
            addView(Button(ctx).apply { text = cta; setOnClickListener { onCta() } })
            addView(Button(ctx).apply {
                text = "Cancel"; setOnClickListener { onDecision(KycStatus.DECLINED) }
            })
        }

    override fun onMessage(message: PipeMessage) { /* request handled in the service before mount */ }
    override fun onClosed(reason: CloseReason) { onClose() }
}
```

- [ ] **Step 6: Write the provider service**

Create `sample-kyc-verifier/src/main/java/tech/ssemaj/pipe/kycverifier/KycVerifierService.kt`. It reads the `KycRequest` (from the first typed message or a default), builds the pane, and on decision sends a typed `KycResult` back and closes. It overrides `authorizer()` to admit the differently-signed host (host→verifier pinning only — see Global Constraints).
```kotlin
package tech.ssemaj.pipe.kycverifier

import android.os.SystemClock
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeProviderService
import tech.ssemaj.pipe.samples.kyc.KycResult
import tech.ssemaj.pipe.samples.kyc.KycStatus
import tech.ssemaj.pipe.serialization.send

/**
 * VerifyID's KYC provider. Renders a mock verification wizard in the bank's window and returns a
 * typed [KycResult]. In the `guarded` build (Task 5) hydra RASP self-terminates this process on a
 * compromised runtime, before any of this runs.
 */
class KycVerifierService : PipeProviderService() {

    // Host→verifier pinning is the codelab's lesson: the BANK pins US. We accept the bank as-is.
    // In production you would pin the bank's cert here too (symmetric); see the codelab callout.
    override fun authorizer(): PipeAuthorizer = PipeAuthorizer { _, _ -> AuthDecision.Allow }

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        // The bank's opaque correlation id, echoed back in the result.
        val reference = request.extras.getString("reference") ?: "unknown"
        val bankName = request.extras.getString("bankName") ?: "the bank"

        val content = KycPaneView(
            ctx = this,
            bankName = bankName,
            onDecision = { status ->
                paneScope.launch {
                    host.send(KycResult(reference, status, System.currentTimeMillis()))
                    host.close()
                }
            },
            onClose = {},
        )
        return PaneResult.Content(content)
    }
}
```
Note: `SystemClock` import is unused above; remove it — use `System.currentTimeMillis()` only.

- [ ] **Step 7: Assemble the verifier**

Run: `./gradlew :sample-kyc-verifier:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `sample-kyc-verifier/build/outputs/apk/debug/sample-kyc-verifier-debug.apk`.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts sample-kyc-verifier/ security/kyc-verifier.keystore
git commit -m "feat: add :sample-kyc-verifier mock KYC provider app (VerifyID)"
```

---

## Task 3: `:sample-kyc-host` — Meridian Bank host app

**Files:**
- Create: `sample-kyc-host/build.gradle.kts`
- Create: `sample-kyc-host/src/main/AndroidManifest.xml`
- Create: `sample-kyc-host/src/main/res/layout/activity_main.xml`
- Create: `sample-kyc-host/src/main/java/tech/ssemaj/pipe/kychost/MainActivity.kt`
- Modify: `settings.gradle.kts` (add `include(":sample-kyc-host")`)
- Modify: `build.gradle.kts` (add `"sample-kyc-host"` to `apiValidation.ignoredProjects`)

**Interfaces:**
- Consumes: `KycContract.ACTION_KYC`, `KycRequest`, `KycResult`, `KycLevel`, `KycStatus` (Task 1); `PipeFullScreen.open`, `ProviderComponent`, `PipeAuthorizers.allowlist`, `PipeSession`, `PipeState`, `PipeException`, `PipeDeniedException`, `PipeCodec`/`messagesOf`/`send<T>` (uI-PiPe). Binds the verifier service from Task 2.
- Produces: an installable bank app with applicationId `tech.ssemaj.pipe.kychost`, signed with the default debug key (deliberately different from the verifier).

- [ ] **Step 1: Register the module**

In `settings.gradle.kts` add:
```kotlin
include(":sample-kyc-host")
```
In root `build.gradle.kts`, add `"sample-kyc-host"` to the `apiValidation.ignoredProjects` list:
```kotlin
    ignoredProjects.addAll(listOf("sample-host", "sample-provider", "evil-host", "evil-provider", "sample-contract", "sample-kyc-contract", "sample-kyc-verifier", "sample-kyc-host"))
```

- [ ] **Step 2: Write the module build file**

Create `sample-kyc-host/build.gradle.kts` (default debug signing — no `signingConfigs` block, so it differs from the verifier's key):
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "tech.ssemaj.pipe.kychost"
    compileSdk = 36
    defaultConfig {
        applicationId = "tech.ssemaj.pipe.kychost"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":pipe"))
    implementation(project(":pipe-serialization"))
    implementation(project(":sample-kyc-contract"))
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
}
```

- [ ] **Step 3: Write the manifest (with package visibility)**

Create `sample-kyc-host/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <package android:name="tech.ssemaj.pipe.kycverifier" />
    </queries>
    <application android:label="Meridian Bank" android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Write the layout**

Create `sample-kyc-host/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:gravity="center" android:padding="24dp">

    <TextView android:id="@+id/title" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="Meridian Bank" android:textSize="28sp" />
    <TextView android:id="@+id/status" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="12dp" android:text="Account setup: identity check required" />
    <Button android:id="@+id/verify" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="24dp" android:text="Start verification" />
</LinearLayout>
```

- [ ] **Step 5: Write the host Activity (pinned allowlist + typed result + graceful failure)**

Create `sample-kyc-host/src/main/java/tech/ssemaj/pipe/kychost/MainActivity.kt`. The cert digest constant is filled in Task 4 (a clearly-marked placeholder value here so it compiles; Task 4 replaces it and verifies).
```kotlin
package tech.ssemaj.pipe.kychost

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.kyc.KycContract
import tech.ssemaj.pipe.samples.kyc.KycRequest
import tech.ssemaj.pipe.samples.kyc.KycLevel
import tech.ssemaj.pipe.samples.kyc.KycResult
import tech.ssemaj.pipe.serialization.messagesOf
import tech.ssemaj.pipe.serialization.send

class MainActivity : AppCompatActivity() {

    private companion object {
        const val VERIFIER_PKG = "tech.ssemaj.pipe.kycverifier"
        const val VERIFIER_SVC = "tech.ssemaj.pipe.kycverifier.KycVerifierService"
        // Filled in Task 4 with VerifyID's real signing-cert SHA-256 (lowercase hex, no colons).
        const val VERIFIER_CERT_SHA256 = "REPLACE_IN_TASK_4"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)

        findViewById<Button>(R.id.verify).setOnClickListener {
            status.text = "Opening VerifyID…"
            val reference = "MB-" + System.currentTimeMillis()
            val extras = Bundle().apply {
                putString("reference", reference)
                putString("bankName", "Meridian Bank")
                putString("level", KycLevel.ENHANCED.name)
            }
            PipeFullScreen.open(
                activity = this,
                provider = ProviderComponent(VERIFIER_PKG, VERIFIER_SVC),
                request = PipeRequest(KycContract.ACTION_KYC, extras),
                authorizer = PipeAuthorizers.allowlist(VERIFIER_CERT_SHA256),
                onSession = { session ->
                    lifecycleScope.launch {
                        session.send(KycRequest(reference, KycLevel.ENHANCED))
                        // firstOrNull (not first): if the verifier dies before sending a result
                        // (e.g. RASP self-terminates), the flow closes empty — return null and let
                        // the state-close collector below render the graceful failure message.
                        val result = session.messagesOf<KycResult>().firstOrNull()
                        if (result != null) {
                            status.text = "Verification ${result.status} (ref ${result.reference})"
                        }
                    }
                    // Graceful teardown: a non-null Closed cause (e.g. RASP killed the verifier) is
                    // an unexpected failure, not a normal close.
                    lifecycleScope.launch {
                        val closed = session.state.first { it is PipeState.Closed } as PipeState.Closed
                        if (closed.cause != null) {
                            status.text = "Verification unavailable: runtime integrity check failed"
                        }
                    }
                },
                onError = { e ->
                    status.text = when (e) {
                        is PipeDeniedException -> "Verifier rejected: ${e.reason}"
                        else -> "Verification unavailable (${e::class.simpleName})"
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 6: Assemble the host**

Run: `./gradlew :sample-kyc-host:assembleDebug`
Expected: BUILD SUCCESSFUL. (End-to-end run is Task 4, after the real cert digest is pinned.)

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts sample-kyc-host/
git commit -m "feat: add :sample-kyc-host Meridian Bank host app (allowlist-pinned)"
```

---

## Task 4: Pin the verifier cert & verify end-to-end (dev, emulator)

**Files:**
- Modify: `sample-kyc-host/src/main/java/tech/ssemaj/pipe/kychost/MainActivity.kt` (replace `VERIFIER_CERT_SHA256`)

**Interfaces:**
- Consumes: the built APKs from Tasks 2–3; the verifier keystore from Task 2.
- Produces: a working pinned host↔verifier embed; the confirmed lowercase-hex cert digest.

- [ ] **Step 1: Extract VerifyID's signing-cert SHA-256**

Run:
```bash
keytool -list -v -keystore security/kyc-verifier.keystore -alias verifier -storepass verifierpass \
  | grep -i "SHA256:" | head -1
```
Copy the fingerprint, then normalise to the uI-PiPe format (lowercase hex, no colons):
```bash
keytool -list -v -keystore security/kyc-verifier.keystore -alias verifier -storepass verifierpass \
  | grep -i "SHA256:" | head -1 | sed 's/.*SHA256: //; s/://g' | tr 'A-Z' 'a-z'
```
Expected: a 64-char lowercase hex string. Record it.

- [ ] **Step 2: Pin it in the host**

In `MainActivity.kt`, replace:
```kotlin
        const val VERIFIER_CERT_SHA256 = "REPLACE_IN_TASK_4"
```
with the 64-char lowercase hex string from Step 1, e.g.:
```kotlin
        const val VERIFIER_CERT_SHA256 = "3a7bd3e2360a3d29eea436fcfb7e44c735d117c42d1c1835420b6b9942dd4f1b"
```

- [ ] **Step 3: Install both apps on the emulator**

Run (starts an API 30 emulator if needed; uses emulator-5554):
```bash
export JAVA_HOME=/home/joseph/.jdks/temurin-23.0.2
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
./gradlew :sample-kyc-verifier:installDebug :sample-kyc-host:installDebug
```
Expected: both `Success`.

- [ ] **Step 4: Run the happy path**

```bash
adb -s emulator-5554 shell am start -n tech.ssemaj.pipe.kychost/.MainActivity
```
Tap **Start verification** → VerifyID pane appears over Meridian Bank → step through Continue/Capture/Capture/Done.
Expected: the bank's status reads `Verification APPROVED (ref MB-…)`.

- [ ] **Step 5: Prove the pin is load-bearing (negative test)**

Temporarily change one hex char of `VERIFIER_CERT_SHA256`, reinstall the host (`./gradlew :sample-kyc-host:installDebug`), relaunch, tap Start verification.
Expected: status reads `Verifier rejected: peer signing certs not in allowlist` (a `PipeDeniedException`, `HOST_POLICY`). Then **restore the correct digest** and reinstall.

- [ ] **Step 6: Commit**

```bash
git add sample-kyc-host/src/main/java/tech/ssemaj/pipe/kychost/MainActivity.kt
git commit -m "feat: pin VerifyID signing cert in the Meridian Bank host; verified end-to-end on emulator"
```

---

## Task 5: hydra RASP on the `guarded` build (`-Prasp`)

**Files:**
- Modify: `gradle/libs.versions.toml` (add hydra plugin)
- Modify: `build.gradle.kts` (root — declare hydra `apply false`)
- Modify: `sample-kyc-verifier/build.gradle.kts` (conditional apply + applicationId suffix when `-Prasp`)

**Interfaces:**
- Consumes: the verifier module from Task 2.
- Produces: `assembleDebug -Prasp` yields a hydra-hardened APK (applicationId `tech.ssemaj.pipe.kycverifier.guarded`); a plain `assembleDebug` stays emulator-runnable.

- [ ] **Step 1: Add hydra to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
hydra = "2.3.0"
```
Under `[plugins]` add:
```toml
hydra = { id = "tech.thessemaj.hydra", version.ref = "hydra" }
```

- [ ] **Step 2: Declare hydra (apply false) at the root**

In root `build.gradle.kts` `plugins { }` block add:
```kotlin
    alias(libs.plugins.hydra) apply false
```

- [ ] **Step 3: Conditionally apply hydra in the verifier**

At the TOP of `sample-kyc-verifier/build.gradle.kts`, after the `plugins { }` block, add:
```kotlin
// RASP is opt-in: `-Prasp` applies the hydra plugin (lethal, physical-device only). A plain build
// stays emulator-runnable. Gradle plugins are module-global, so this gates the whole module build.
val raspEnabled = providers.gradleProperty("rasp").isPresent
if (raspEnabled) apply(plugin = libs.plugins.hydra.get().pluginId)
```
Then make the applicationId distinct when guarded, so both can coexist — change `defaultConfig`:
```kotlin
    defaultConfig {
        applicationId = if (raspEnabled) "tech.ssemaj.pipe.kycverifier.guarded" else "tech.ssemaj.pipe.kycverifier"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
```
Note: when guarded, the host must bind the guarded applicationId. For the codelab that swap is a documented step; for local verification below we install the guarded APK under its own id and point a one-off host build at it, OR simply observe the pre-open failure. Keep the primary happy-path host pinned to the plain id.

- [ ] **Step 4: Build the plain (dev) APK — still emulator-safe**

Run: `./gradlew :sample-kyc-verifier:assembleDebug`
Expected: BUILD SUCCESSFUL; no hydra tasks in the log.

- [ ] **Step 5: Build the guarded APK**

Run: `./gradlew :sample-kyc-verifier:assembleDebug -Prasp`
Expected: BUILD SUCCESSFUL; hydra tasks appear in the log; APK applicationId `tech.ssemaj.pipe.kycverifier.guarded`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts sample-kyc-verifier/build.gradle.kts
git commit -m "feat: apply hydra RASP to the verifier under -Prasp (guarded build)"
```

---

## Task 6: On-device RASP finale — confirm the kill & graceful host recovery

**Files:**
- Modify (only if Step 3 shows a gap): `sample-kyc-host/.../MainActivity.kt` failure copy.

**Interfaces:**
- Consumes: guarded APK (Task 5), host (Task 4).
- Produces: documented, verified behavior of which failure path fires and that the host degrades gracefully.

- [ ] **Step 1: Install the guarded verifier on the emulator (a "compromised" runtime for hydra)**

```bash
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
./gradlew :sample-kyc-verifier:assembleDebug -Prasp
adb -s emulator-5554 install -r sample-kyc-verifier/build/outputs/apk/debug/sample-kyc-verifier-debug.apk
```
The emulator trips hydra's emulator detection → the guarded process self-terminates on launch.

- [ ] **Step 2: Point a one-off host at the guarded id and observe**

Temporarily set `VERIFIER_PKG = "tech.ssemaj.pipe.kycverifier.guarded"` and (leave `VERIFIER_SVC` as the same class name), reinstall host, launch, tap Start verification while watching logcat:
```bash
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n tech.ssemaj.pipe.kychost/.MainActivity
adb -s emulator-5554 logcat | grep -iE "hydra|kyc|pipe" &
```
Expected: the verifier process dies; the bank's status shows one of the graceful messages ("Verification unavailable …" or "runtime integrity check failed"), **not** a crash.

- [ ] **Step 3: Record which path fired; tighten copy if needed**

Note whether the failure arrived via `onError` (pre-open — most likely) or `PipeState.Closed(cause)` (post-open). Both already render a graceful message in Task 3's `MainActivity`. If the wording is unclear for the path that actually fires, adjust the corresponding branch's string. Revert the temporary `VERIFIER_PKG` change back to the plain id.

- [ ] **Step 4: (Optional) confirm on the Pixel with a clean runtime**

On the physical Pixel 6 Pro (`19011FDEE0040L`, not rooted, not an emulator) the guarded verifier should pass hydra and run the happy path. Install guarded there and confirm APPROVED — proving RASP blocks only compromised runtimes. If the Pixel is unavailable, note it and rely on Step 2's negative result.

- [ ] **Step 5: Commit (if any copy changed)**

```bash
git add sample-kyc-host/
git commit -m "test: verify RASP kill + graceful host recovery on device"
```

---

## Task 7: CI + CONTRIBUTING wiring

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `CONTRIBUTING.md`

**Interfaces:**
- Consumes: all new modules.
- Produces: CI builds the contract tests + both apps' plain builds; contributor docs updated.

- [ ] **Step 1: Add the modules to CI**

In `.github/workflows/ci.yml`, extend the gradle task list (the `run: >` block) to append:
```
          :sample-kyc-contract:test
          :sample-kyc-host:assembleDebug
          :sample-kyc-verifier:assembleDebug
```
(Do **not** add `-Prasp` — the guarded build is device-only; hydra self-terminates in CI's headless env and the plugin may require device state.)

- [ ] **Step 2: Update CONTRIBUTING project layout**

In `CONTRIBUTING.md` under `## Project layout`, add three lines to the module inventory:
```
:sample-kyc-contract  Shared @Serializable KYC wire types for the codelab.
:sample-kyc-host      "Meridian Bank" — codelab host app.
:sample-kyc-verifier  "VerifyID" — codelab KYC provider (RASP-guarded via -Prasp).
```

- [ ] **Step 3: Add a codelab-regeneration note**

In `CONTRIBUTING.md` under `## Build & test`, add a subsection:
```markdown
### Regenerating the KYC codelab

The codelab source is `docs/codelab/kyc.md`. Regenerate the static site with
[claat](https://github.com/googlecodelabs/tools):

    claat export -o docs/codelab docs/codelab/kyc.md

Commit the regenerated `docs/codelab/<id>/` output alongside the source.
```

- [ ] **Step 4: Verify CI tasks locally**

Run: `./gradlew :sample-kyc-contract:test :sample-kyc-host:assembleDebug :sample-kyc-verifier:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml CONTRIBUTING.md
git commit -m "ci: build KYC codelab modules; document codelab regeneration"
```

---

## Task 8: Author & generate the codelab

**Files:**
- Create: `docs/codelab/kyc.md` (claat source)
- Create: `docs/codelab/<generated-id>/` (claat output — committed)
- Modify: `README.md` (link to the codelab)

**Interfaces:**
- Consumes: the final source of Tasks 1–5 (the codelab displays those exact files).
- Produces: a browsable codelab served from `docs/codelab/`.

- [ ] **Step 1: Obtain claat**

Prefer a prebuilt binary (no Go needed) from the claat releases, or `go install github.com/googlecodelabs/tools/claat@latest` if Go is present. Verify:
```bash
claat version
```

- [ ] **Step 2: Write the claat source header + step scaffold**

Create `docs/codelab/kyc.md` beginning with claat metadata, then the eight steps. Each `##` is a step; fill each with the goal, the exact code/commands from the referenced task, and a "verify it" checkpoint:
```markdown
author: Joseph MJ
summary: Embed a RASP-guarded KYC verifier in a bank app with uI-PiPe
id: uipipe-kyc-codelab
categories: android,security
environments: Web
status: Published
feedback link: https://github.com/iamjosephmj/uI-PiPe/issues

# Embed a RASP-guarded KYC verifier with uI-PiPe

## Introduction
Duration: 3:00

What you'll build: Meridian Bank (host) embeds VerifyID (a separately-signed, RASP-guarded
KYC verifier) as a live pane, verified by signing-cert identity. Two security layers:
uI-PiPe's identity gate and hydra's runtime self-protection.
(Include the finished-result screenshot/gif here.)

## Project setup
Duration: 4:00
(Add the :pipe / :pipe-serialization deps and the three empty modules — settings.gradle.kts
and the ignoredProjects edit from Task 1 Step 1.)

## The shared contract
Duration: 3:00
(Embed the full KycContract.kt from Task 1 Step 5 and the build file from Task 1 Step 2.)

## Build the KYC verifier
Duration: 6:00
(Embed KycVerifierService.kt + KycPaneView.kt + the manifest + build file from Task 2.
Call out authorizer() being overridden and why.)

## Build the bank host
Duration: 5:00
(Embed MainActivity.kt + layout + manifest from Task 3. Explain PipeFullScreen.open and the
graceful failure handling.)

## Pin the trust boundary
Duration: 4:00
(The keytool commands from Task 2 Step 2 and Task 4 Step 1; wiring allowlist(); the negative
test from Task 4 Step 5. One paragraph callout: the reverse — verifier pinning the host — is
symmetric; here we teach host→verifier.)

## Run it end-to-end
Duration: 3:00
(Install/run commands from Task 4 Steps 3-4; expected APPROVED result.)

## Add RASP with hydra
Duration: 4:00
(The libs.versions.toml + root + module edits from Task 5; explain zero-code hardening and
lethal-by-default; the -Prasp build. Then Task 6: watch the guarded verifier die on an
emulator and the bank recover gracefully. Wrap-up + links.)
```

- [ ] **Step 3: Generate the site**

Run:
```bash
claat export -o docs/codelab docs/codelab/kyc.md
```
Expected: `docs/codelab/uipipe-kyc-codelab/` created with `index.html`.

- [ ] **Step 4: Spot-check in a browser**

Open `docs/codelab/uipipe-kyc-codelab/index.html`. Verify: left step nav, per-step durations, code blocks render, no broken steps.

- [ ] **Step 5: Link from the README and note Pages source**

In `README.md`, add near the top a line linking the codelab (relative path so it works on GitHub Pages from `/docs`):
```markdown
**Codelab:** [Embed a RASP-guarded KYC verifier](docs/codelab/uipipe-kyc-codelab/) — step-by-step tutorial.
```
Confirm GitHub Pages is (or will be) configured to serve from `/docs` on `master` (repo Settings → Pages; note it in the PR description if it needs enabling).

- [ ] **Step 6: Commit**

```bash
git add docs/codelab/ README.md
git commit -m "docs: add the uI-PiPe KYC codelab (claat) and link it from the README"
```

---

## Self-review notes

- **Spec coverage:** §3 decisions → Tasks 1 (contract), 2 (verifier + own key), 3 (host + allowlist), 4 (pin + e2e), 5 (hydra via `-Prasp` — mechanism deviation from "flavors", see below), 6 (RASP finale), 7 (CI/CONTRIBUTING), 8 (claat codelab, Pages from `/docs`). §7 data contract → Task 1 + wired in 2/3. §8 trust mechanics → Task 4. §10 naming → Global Constraints + manifests. §11 verification → Tasks 4/6 + CI in 7.
- **Deviation flagged:** the spec's "dev/guarded product flavors" is implemented as a `-Prasp` gradle-property toggle because Gradle plugins apply per-module, not per-flavor. Same intent (one guarded build, one emulator-safe build); distinct applicationId suffix lets both coexist. Confirm this substitution is acceptable at execution kickoff.
- **Added beyond spec:** verifier `authorizer()` override (Task 2 Step 6) — mandatory, since the default `sameSigningKey` would reject the differently-signed host.
- **Type consistency:** `KycRequest`/`KycResult`/`KycLevel`/`KycStatus`/`KycContract.ACTION_KYC` identical across Tasks 1–3; `VERIFIER_CERT_SHA256` placeholder in Task 3 is replaced in Task 4; extras keys `reference`/`bankName`/`level` written by host (Task 3 Step 5) and read by verifier (Task 2 Step 6) match.
- **Known non-TDD tasks:** the app/UI and codelab tasks verify by assemble + on-device run (matching the repo's existing sample convention), not unit tests; only `:sample-kyc-contract` carries unit tests.
