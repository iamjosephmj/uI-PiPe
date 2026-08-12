# Pipe

**Pipe** is an Android library that lets one app render a **live, fully-interactive UI inside another app's window** — cooperatively and with a cryptographic gate on both ends. A *host* app embeds a `PipeView` in its layout; a *provider* app renders a `View` into it, over the platform's `SurfaceControlViewHost` transport, with a two-way typed message channel between them. Both apps opt in, each verifies the other's signing identity, and the host controls placement, size, lifetime, and revocation — it deliberately does **not** rely on any window-token/overlay side channel, only the platform's sanctioned cross-process UI APIs.

Status: **v1, implemented.** Single artifact, `:pipe` (namespace `tech.ssemaj.pipe`). Targets a **closed app family / vetted partners**, not an open marketplace.

## Requirements

- **`minSdk = 35`** (Android 15) for both host and provider apps.

  Clean cross-process input **and IME** delivery for a fully-interactive embedded pane requires `android.window.InputTransferToken`, which only exists from API 35. `PipeView` uses it both to hand its own input token to the provider (`OpenSpec.inputTransferToken`, built from `surfaceView.rootSurfaceControl.inputTransferToken`) and to transfer live touch gestures into the embedded pane on every `ACTION_DOWN` (`WindowManager.transferTouchGesture`). There is no lower-SDK degraded-input fallback in v1.
- `compileSdk = 36`, Kotlin, `kotlinx-parcelize` (the wire types are `@Parcelize` classes).
- The provider service must be `android:exported="true"` with the `tech.ssemaj.pipe.action.OPEN_PANE` intent-filter action (see below); no other exported surface is required.

## Host quickstart

Add a `PipeView` to your layout:

```xml
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/host_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />

    <tech.ssemaj.pipe.host.PipeView
        android:id="@+id/pipe_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

Open a pane and wire callbacks:

```kotlin
import androidx.core.os.bundleOf
import tech.ssemaj.pipe.auth.EmbedAuthorizers
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeError
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeHostCallbacks
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent

class MainActivity : AppCompatActivity() {
    private var session: PipeSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.host_status)
        val pipeView = findViewById<PipeView>(R.id.pipe_view)

        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        session = pipeView.open(
            provider = provider,
            request = PipeRequest("demo.editor"),
            authorizer = EmbedAuthorizers.sameSigningKey(this),
            callbacks = object : PipeHostCallbacks {
                override fun onOpened(session: PipeSession) { status.text = "opened" }
                override fun onMessage(message: PipeMessage) { status.text = "msg: ${message.payload.getString("text")}" }
                override fun onDenied(reason: String) { status.text = "denied: $reason" }
                override fun onError(error: PipeError) { status.text = "error: ${error.code}" }
                override fun onClosed(reason: CloseReason) { status.text = "closed: $reason" }
            },
        )
    }

    fun sendHello() {
        session?.send(PipeMessage(bundleOf("text" to "hello-from-host")))
    }
}
```

`PipeView.open()` returns a `PipeSession` immediately (`send`, `resize`, `close`, `peer`); the real gate/bind/open handshake runs asynchronously and reports back through `callbacks` on the main thread. Calling `open()` again on the same `PipeView` closes any existing pane first (one pane per view). Detaching the `PipeView` from the window also closes the pane.

Declare the provider(s) you intend to bind to in your `<queries>` block (required on API 30+ package-visibility rules):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <package android:name="tech.ssemaj.pipe.sampleprovider" />
    </queries>
    ...
</manifest>
```

## Provider quickstart

Subclass `PipeProviderService` and build a `View` per open request:

```kotlin
import androidx.core.os.bundleOf
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService

class DemoPaneService : PipeProviderService() {

    override fun onOpenPane(request: PipeRequest, host: HostHandle): PipeContent {
        val editText = EditText(this).apply { hint = "type here" }
        val status = TextView(this).apply { text = "pane-ready" }
        val button = Button(this).apply {
            text = "Ping Host"
            setOnClickListener {
                host.send(PipeMessage(bundleOf("type" to "ping", "text" to editText.text.toString())))
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(editText); addView(button)
        }
        return object : PipeContent {
            override val view: View = root
            override fun onMessage(message: PipeMessage) {
                status.text = message.payload.getString("text") ?: "(no text)"
            }
        }
    }
}
```

`onOpenPane` runs on the main thread, only after the calling host has already passed the provider's gate (`ProviderGate` inside `PipeProviderService.onBind().open()`). Override `authorizer()` to change who may embed (default `EmbedAuthorizers.sameSigningKey(this)`). `PipeContent.onResized`/`onClosed` are optional (default no-ops); `PipeContent.view` is rendered by the library into a `SurfaceControlViewHost` sized to the host's requested `widthPx`/`heightPx`.

Manifest declaration — the service **must** be exported with the `OPEN_PANE` action so hosts can bind it:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="My Pane Provider">
        <service
            android:name=".DemoPaneService"
            android:exported="true">
            <intent-filter>
                <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
            </intent-filter>
        </service>
        <!-- optional same-key defense-in-depth, see Security notes below -->
        <!-- android:permission="tech.ssemaj.pipe.permission.BIND_PANE" -->
    </application>
</manifest>
```

Any app that wants to bind this service must in turn declare it in a `<queries>` block (see Host quickstart above) — that's a platform package-visibility requirement, independent of Pipe's own gate.

## Trust & authorization

Every open is a **mutual** gate; identity is always cryptographic and kernel-backed, never a self-reported package name.

```
 Host                                              Provider
  │  1. resolve ComponentName, read its signing     │
  │     certs from PackageManager                   │
  │  2. hostAuthorizer.authorize(providerPeer, req)  │
  │       Deny ──────────────────────► onDenied, no bind
  │       Allow                                      │
  │  3. bind() the *exact* ComponentName just        │
  │     verified (no TOCTOU)                         │
  │────────────── bindService ──────────────────────►│
  │  4. open(spec, hostChannel, callback)             │
  │───────────────── open() ─────────────────────────►│  5. Binder.getCallingUid() (kernel-enforced,
  │                                                    │     unspoofable) → packages → signing certs
  │                                                    │  6. providerAuthorizer.authorize(hostPeer, req)
  │                                                    │       Deny ──► onDenied(reason), no SCVH built
  │                                                    │       Allow
  │                                                    │  7. onOpenPane(...) → SurfaceControlViewHost
  │◄──────── SurfacePackage + session + channel ──────│
  │  8. attach SurfacePackage, wire touch transfer     │
```

Both sides supply their own `EmbedAuthorizer`; policies may be asymmetric (e.g. host trusts a broad partner allowlist, provider only trusts its own signing key).

```kotlin
fun interface EmbedAuthorizer {
    fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision
}
sealed interface AuthDecision {
    data object Allow : AuthDecision
    data class Deny(val reason: String) : AuthDecision
}
data class PeerIdentity(val uid: Int, val packages: List<String>, val signingCertSha256: List<String>)
```

Built-in authorizers (`tech.ssemaj.pipe.auth`):

```kotlin
// Trust anyone signed with (any cert in the rotation lineage of) *this* app's own key.
EmbedAuthorizers.sameSigningKey(context)   // -> EmbedAuthorizer

// Trust a fixed set of partner certs.
EmbedAuthorizers.allowlist("aa11...sha256...", "bb22...sha256...")   // -> EmbedAuthorizer

// Combine: allow if any delegate allows; Deny reasons are joined with "; ".
val authorizer = anyOf(EmbedAuthorizers.sameSigningKey(context), EmbedAuthorizers.allowlist(partnerCertSha256))
```

To compute a partner's cert SHA-256 for an allowlist, run either:

```bash
keytool -printcert -jarfile app.apk
# or, for the app's signing key rather than the built APK:
apksigner verify --print-certs app.apk
```

Both print a `SHA-256` fingerprint per signer; lowercase it (colons optional either way — `AllowlistAuthorizer`/`SameSigningKeyAuthorizer` lowercase before comparing) and pass it to `EmbedAuthorizers.allowlist(...)`.

A custom authorizer is just another implementation — nothing special is required:

```kotlin
class RiskScoringAuthorizer(private val trustedUids: Set<Int>) : EmbedAuthorizer {
    override fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision =
        if (peer.uid in trustedUids) AuthDecision.Allow
        else AuthDecision.Deny("uid ${peer.uid} not in trusted set")
}
```

`PeerIdentity` is built **only** by the library — from a binder uid (provider side, via `Binder.getCallingUid()`) or a resolved `ComponentName` (host side, via `PackageManager`) — never from anything the peer sends. An `EmbedAuthorizer` never touches `PackageManager` or a self-reported name; it only sees the verified `PeerIdentity` the gate hands it.

## Channel

Once a pane is open, both sides get a two-way message pipe. `PipeMessage` is the wire envelope:

```kotlin
data class PipeMessage(
    val payload: Bundle,
    val schemaVersion: Int = 1,
    val seq: Long = UNSET_SEQ,   // UNSET_SEQ = -1L
) : Parcelable
```

- `payload` is a plain `Bundle` — apps own their own schema inside it (see `bundleOf("text" to ..., "type" to ...)` in the quickstarts above).
- `schemaVersion` is app-owned; the library does not interpret it.
- `seq` is **library-owned**: leave it `UNSET_SEQ` when constructing a message to send. `PipeSession.send()` / `HostHandle.send()` stamp it via an internal `OutboundSequencer` before it crosses the binder; the receiving side's `InboundSequencer` uses it to guarantee ordered, gap-free delivery and drops/logs out-of-order or duplicate messages rather than delivering them to `onMessage`.
- Delivery is ordered per-direction (host→provider and provider→host are independent streams), but not synchronous — `send()` returns immediately.

## Security notes

- **Identity is never a claimed package name.** The provider trusts only `Binder.getCallingUid()` (kernel-enforced, unspoofable) mapped through `PackageManager.getPackagesForUid()`/signing certs; the host trusts only the `ComponentName` it explicitly resolved and verified *before* binding — verified is bound, so there's no TOCTOU window between "checked" and "used".
- **Shared UID callers:** if a calling uid maps to more than one package (`android:sharedUserId`), `IdentityResolver.forUid` builds `PeerIdentity.signingCertSha256` as the **union** of every package's cert lineage under that uid, and the built-in authorizers (`sameSigningKey`, `allowlist`) admit if **any** cert in that union matches — same as the single-package case. The gate is fail-closed on *readability*, not on per-package trust: if any package under the uid can't be attributed a signing lineage, `forUid` returns `null` and the whole peer is refused. A stricter "every package under this uid must individually be trusted" policy is not the built-in behavior — it can be implemented as a custom `EmbedAuthorizer` that inspects `PeerIdentity.packages`/`signingCertSha256` itself.
- **The exported provider service checks first.** `PipeProviderService.onBind().open()` runs the cert-check gate as the very first thing inside the AIDL entry point; an unauthorized caller gets `onDenied`/`onError` and nothing else — no `SurfaceControlViewHost` is ever constructed for a denied caller.
- **Denial is terminal.** For a caller/provider that's denied (or errored) before a pane ever opened, only `onDenied`/`onError` fires — there is no trailing `onClosed`, since nothing was ever opened to close.
- **Optional defense-in-depth:** a provider that only ever expects same-signing-key hosts can additionally set `android:permission="tech.ssemaj.pipe.permission.BIND_PANE"` on its `<service>` and require/declare that signature-level permission — this is a cheap OS-level pre-filter *in addition to* the cert-hash gate, not a replacement for it (allowlist/partner-cert setups still rely on the gate, since the permission model can't express "any of these N certs").
- **`security/evil.keystore` is a test fixture, not a release key.** It's a throwaway debug-signing key committed to this repo solely so `:evil-host`/`:evil-provider` can be built and installed with a signing identity that is guaranteed to differ from `:sample-host`/`:sample-provider`, to exercise the deny paths end-to-end. Never reuse it, and never ship an app signed with it.

## Testing

Unit tests (authorizer logic, gate logic, message sequencing, core types) live under `pipe/src/test`:

```bash
./gradlew :pipe:testDebugUnitTest
```

End-to-end and security instrumented tests run on-device (`sample-host` + `sample-provider` for the happy path and mutual-gate assertions; `evil-host` + `evil-provider`, signed with `security/evil.keystore`, for the deny-in-both-directions security suite). Install all four sample/evil APKs, then run the connected suites:

```bash
./gradlew :sample-provider:installDebug :sample-host:installDebug :evil-provider:installDebug :evil-host:installDebug
./gradlew :sample-host:connectedDebugAndroidTest :evil-host:connectedDebugAndroidTest
```

`sample-host`'s suite covers pane render, touch + IME interactivity, channel round-trips both ways, and clean teardown, plus a security test proving the host denies an evil provider (no surface, no bind). `evil-host`'s suite proves the provider denies an evil host (with an allow-all host-side authorizer, so only the provider's gate is under test) even though the evil host's own authorization policy would have let it through.

A full build (all six modules, including the evil ones) is:

```bash
./gradlew build
```
