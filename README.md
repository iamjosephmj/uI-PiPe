# Pipe

**Pipe** lets one Android app render a **live, fully interactive UI inside another app's window** — across a process boundary, with a cryptographic identity check on both ends. A *host* app places a pane in its layout; a *provider* app renders a `View` into it, in its **own process**, over the platform's `SurfaceControlViewHost` transport, with a two-way typed message channel between them.

Both apps opt in, each verifies the other's signing identity before anything crosses, and the host controls placement, size, lifetime, and revocation. Pipe uses only the platform's sanctioned cross-process UI APIs — no window-token or draw-over side channel.

**Two UI threads, two render threads, two heaps and two GCs — a whole second runtime working for you, isolated from yours.** The pane renders on the *provider's* main thread and heap, so host jank never stalls the pane and a provider crash can't take down the host — while a signing-identity gate keeps the two apps in distinct trust domains. (Isolation, not extra CPU: the two runtimes still share the device's cores.)

Status: **implemented — first release.** Coroutine-first API, three presentation modes (embedded / full-screen / dialog), opt-in typed messaging. Targets a **closed app family / vetted partners**, not an open marketplace.

> **How it works:** [`ARCHITECTURE.md`](ARCHITECTURE.md) covers the process/threading model, the wire protocol, the trust model, and the presentation modes in depth. This README is the task-level guide.

## Contents

- [Requirements](#requirements)
- [Install](#install)
- [Host quickstart](#host-quickstart)
- [Provider quickstart](#provider-quickstart)
- [Presentation modes](#presentation-modes)
- [Trust & authorization](#trust--authorization)
- [Typed messaging](#typed-messaging)
- [Channel semantics](#channel-semantics)
- [Discovery](#discovery)
- [Security notes](#security-notes)
- [Testing](#testing)
- [Known limitations](#known-limitations)

## Requirements

- **`minSdk = 35`** (Android 15) for both host and provider apps — an implementation choice, not a hard platform limit.

  Cross-process embedding (`SurfaceControlViewHost`) and embedded **touch** input have existed since API 30 (Android 11). Pipe sets its floor at 35 because it builds on the *public* input-transfer APIs added there — `android.window.InputTransferToken` and `WindowManager.transferTouchGesture()` — which also make cross-process **IME** clean and reliable (the real weak spot before 35, when the equivalent wiring relied on hidden APIs). A lower floor toward 30, with a pre-35 input path, is feasible but not currently implemented.
- `compileSdk = 36`, Kotlin, coroutines. The wire types are `@Parcelize` classes (`kotlin-parcelize`).
- The provider service must be `android:exported="true"` with the `tech.ssemaj.pipe.action.OPEN_PANE` intent-filter action.

## Install

```kotlin
dependencies {
    implementation("tech.ssemaj.pipe:pipe:1.0.0-alpha01")
    // Optional: typed (@Serializable) messages over the pipe.
    implementation("tech.ssemaj.pipe:pipe-serialization:1.0.0-alpha01")
}
```

## Host quickstart

Put a `PipeView` in your layout:

```xml
<tech.ssemaj.pipe.host.PipeView
    android:id="@+id/pipe_view"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1" />
```

Open a pane from a coroutine. `PipeView.open()` is `suspend`: it runs the gate + bind + handshake and returns a live `PipeSession`, or throws a `PipeException` (denied / timeout / transport). The session exposes `state` and `messages` as flows:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pipeView = findViewById<PipeView>(R.id.pipe_view)

        val provider = ProviderComponent(
            packageName = "com.example.provider",
            serviceClass = "com.example.provider.DemoPaneService",
        )

        lifecycleScope.launch {
            try {
                val session = pipeView.open(
                    provider = provider,
                    request = PipeRequest("demo.editor"),
                    authorizer = PipeAuthorizers.sameSigningKey(this@MainActivity), // default
                )
                // Collect provider → host messages.
                launch { session.messages.collect { onMessage(it) } }
                // Observe lifecycle.
                launch { session.state.collect { if (it is PipeState.Closed) finishFlow() } }
                // Host → provider.
                session.send(PipeMessage(bundleOf("text" to "hello-from-host")))
            } catch (e: PipeDeniedException) {
                // e.reason — the provider (or your own authorizer) refused.
            } catch (e: PipeException) {
                // timeout / transport / provider-died
            }
        }
    }
}
```

One pane per view: calling `open()` again while a session is live throws — `close()` first. Detaching the `PipeView` closes the pane. For a fire-and-forget variant that ties teardown to a `LifecycleOwner`, use `pipeView.openIn(owner, provider, request, onSession = { … }, onError = { … })`.

Declare the providers you bind in your `<queries>` block (API 30+ package-visibility):

```xml
<queries>
    <package android:name="com.example.provider" />
</queries>
```

## Provider quickstart

Subclass `PipeProviderService` and build a `View` per request. `onOpenPane` is `suspend` and runs on the provider's main thread (`paneScope`), only *after* the calling host passed the provider's gate. Return `PaneResult.Content(...)` or `PaneResult.Reject(reason)`:

```kotlin
class DemoPaneService : PipeProviderService() {

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val status = TextView(this).apply { text = "pane-ready" }
        val button = Button(this).apply {
            text = "Ping host"
            setOnClickListener {
                paneScope.launch {
                    host.send(PipeMessage(bundleOf("text" to "pong")))
                }
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(button)
        }
        return PaneResult.Content(object : PipeContent {
            override val view: View = root
            override fun onMessage(message: PipeMessage) {
                status.text = message.payload.getString("text") ?: "(no text)"
            }
        })
    }
}
```

Override `authorizer()` to change who may embed (default `PipeAuthorizers.sameSigningKey(this)`). `PipeContent.onResized` / `onClosed` are optional. Manifest:

```xml
<service
    android:name=".DemoPaneService"
    android:exported="true">
    <intent-filter>
        <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
    </intent-filter>
    <!-- optional same-key OS pre-filter, see Security notes: -->
    <!-- android:permission="tech.ssemaj.pipe.permission.BIND_PANE" -->
</service>
```

## Presentation modes

The provider's `PaneResult.Content` is identical across modes; the **host** picks how the pane is shown, and the mode travels in `PipeRequest.presentation` so the provider *may* adapt its layout.

```kotlin
// Embedded (default): the PipeView in your layout, as above.

// Full-screen: a full-bleed, inset-padded pane over your activity.
PipeFullScreen.open(
    activity = this,
    provider = provider,
    request = PipeRequest("demo.editor", presentation = PipePresentation.FULL_SCREEN),
    onSession = { session -> /* … */ },
)

// Dialog: a dimmed scrim + centered card; dismiss (scrim/back) closes the session.
PipeDialog.show(
    activity = this,
    provider = provider,
    request = PipeRequest("demo.editor", presentation = PipePresentation.DIALOG),
    onSession = { session -> /* … */ },
)
```

Full-screen and dialog panes take input directly and support **repeated** gestures. Embedded panes have a one-interaction-per-session limitation — see [Known limitations](#known-limitations).

## Trust & authorization

Every open is a **mutual** gate, and identity is always cryptographic and kernel-backed — never a self-reported package name. The provider derives the host's identity from `Binder.getCallingUid()`; the host derives the provider's from the exact `ComponentName` it is about to bind. A `PipeAuthorizer` only ever sees the verified `PeerIdentity` the library built:

```kotlin
fun interface PipeAuthorizer {
    suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision  // Allow | Deny(reason)
}
data class PeerIdentity(val uid: Int, val packages: List<String>, val signingCertSha256: List<String>)
```

`authorize` is `suspend`, so a policy may consult a backend, an attestation check, or a consent prompt before deciding. Built-ins (`PipeAuthorizers`):

```kotlin
PipeAuthorizers.sameSigningKey(context)              // only peers signed with this app's own key (default)
PipeAuthorizers.allowlist("aa11…sha256…", "bb22…")   // specific partner signing certs
anyOf(PipeAuthorizers.sameSigningKey(context), PipeAuthorizers.allowlist(partnerCert))  // compose
```

Compute a partner's signing-cert SHA-256 for an allowlist:

```bash
keytool -printcert -jarfile app.apk          # cert in the APK
apksigner verify --print-certs app.apk       # the app's signing key
```

Lowercase the printed `SHA-256` (colons optional; the built-ins normalize before comparing). A custom policy is just another `PipeAuthorizer`:

```kotlin
val authorizer = PipeAuthorizer { peer, request ->
    if (peer.uid in trustedUids) AuthDecision.Allow
    else AuthDecision.Deny("uid ${peer.uid} not trusted")
}
```

## Typed messaging

`:pipe-serialization` layers `@Serializable` messages over the raw channel with CBOR, no transport change. Put the contract in a module both apps share:

```kotlin
@Serializable sealed interface DemoMessage {
    @Serializable data class Ping(val text: String) : DemoMessage
    @Serializable data class Pong(val text: String) : DemoMessage
}
```

Send and collect by type:

```kotlin
// Host                                    // Provider
session.send<DemoMessage>(Ping("hi"))      host.send<DemoMessage>(Pong("hi back"))
session.messagesOf<DemoMessage>()          // Flow<DemoMessage>
    .collect { … }
```

Send sealed hierarchies as the **supertype** (`send<DemoMessage>(…)`), not the concrete subtype — the codec matches on the exact qualified name.

## Channel semantics

Once a pane is open, both directions carry `PipeMessage`s:

```kotlin
PipeMessage(payload: Bundle, schemaVersion: Int = 1)   // seq is library-owned, not part of the public ctor
```

- `payload` is a plain `Bundle`; your app owns the schema inside it (or use typed messaging above).
- Host→provider and provider→host are **independent** streams, each stamped with a monotonic sequence internally.
- Delivery is **ordered** and **de-duplicated** (a regressed/repeated message is dropped), and **gap-tolerant** — the sender may skip sequence numbers and the receiver accepts forward jumps. It is at-most-once and in order, **not** gap-free.
- `send()` is `suspend` and returns a `Boolean` (false if the peer is already gone); it does not block on delivery.

## Discovery

To find installed providers for a pane action instead of hard-coding a `ProviderComponent`:

```kotlin
val providers: List<ProviderDescriptor> =
    PipeDiscovery.query(context, action = Pipe.ACTION_OPEN_PANE)
// each: component, packageName, label, certSha256 — apply your own authorizer to choose.
```

## Security notes

- **Identity is never a claimed package name.** The provider trusts only `Binder.getCallingUid()` (kernel-enforced) mapped through `PackageManager`; the host trusts only the `ComponentName` it resolved and verified *before* binding — verified-is-bound, so there's no TOCTOU window.
- **Both gates run before anything expensive.** The host authorizes the provider before it binds; the provider runs its gate as the first thing inside the AIDL entry point — a denied caller gets `onDenied`/`onError` and **no** `SurfaceControlViewHost` is ever built. Denial is terminal (no trailing close).
- **Live sessions stay UID-gated.** After open, every inbound binder call (`send`/`resize`/`close`/`onClosed`) is re-checked against the admitted UID on both sides, so a leaked binder handle can't drive the session from another UID.
- **Shared-UID callers:** if a UID maps to multiple packages (`android:sharedUserId`), `PeerIdentity.signingCertSha256` is the **union** of every package's cert lineage, and the built-ins admit if **any** cert matches. The gate is fail-closed on *readability*: if any package under the UID can't be attributed a signing lineage, the whole peer is refused. A stricter per-package policy can be a custom `PipeAuthorizer`.
- **Optional OS pre-filter:** a same-key-only provider can also set `android:permission="tech.ssemaj.pipe.permission.BIND_PANE"` (signature-level) on its `<service>` — a cheap pre-filter *in addition to* the cert gate, not a replacement (allowlist setups still rely on the gate).
- **`security/evil.keystore` is a test fixture, not a release key.** It's a throwaway debug key committed solely so `:evil-host`/`:evil-provider` can be signed with an identity guaranteed to differ from the sample apps, to exercise the deny paths. Never reuse or ship it.

## Testing

Unit tests (authorizer/gate logic, sequencing, core types, cert-chain verification):

```bash
./gradlew :pipe:testDebugUnitTest
```

End-to-end and security tests run on-device. Install the sample + evil apps, then run the connected suites:

```bash
./gradlew :sample-provider:installDebug :sample-host:installDebug \
          :evil-provider:installDebug :evil-host:installDebug
./gradlew :sample-host:connectedDebugAndroidTest :evil-host:connectedDebugAndroidTest
```

`sample-host` covers pane render, cross-process touch, both-direction channels, teardown, the three presentation modes, and reopen/multi-pane — plus a security test proving the host denies an evil provider (no surface, no bind). `evil-host` proves the provider denies an evil host even when the host's own policy would allow it.

Full build of all modules:

```bash
./gradlew build
```

## Known limitations

- **`minSdk = 35`** — a deliberately high floor and an implementation choice: Pipe builds on the public `InputTransferToken` / `transferTouchGesture` APIs (API 35). `SurfaceControlViewHost` embedding and touch go back to API 30; a lower floor means a pre-35 input path (hidden-API wiring, weaker IME on 30–34) and isn't currently implemented.
- **Embedded single-gesture-per-session** — an embedded pane reliably receives only the *first* interactive gesture of a session; treat an embedded session as single-interaction and reopen for the next (the sample host demonstrates this). Full-screen and dialog modes are unaffected.
- **Coarse-grained by design** — every message is a binder transaction; Pipe suits pane-render + occasional messages, not high-frequency small-message loops.
- **Alpha** — a coherent, adversarially-tested alpha, not yet a hardened release.
