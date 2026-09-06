# Host guide

You are integrating uI-PiPe **as a host**: your activity hands its window to a verified provider,
which renders a real full-screen pane over it — from another process, possibly another app.

Time to a first pane: ~10 minutes. Sections 1–4 are the required path; the rest is tuning.

- [1. Add the dependency](#1-add-the-dependency)
- [2. Declare visibility](#2-declare-visibility)
- [3. Open a pane](#3-open-a-pane)
- [4. Handle failures](#4-handle-failures)
- [5. Choose an authorizer](#5-choose-an-authorizer)
- [6. Discover providers](#6-discover-providers)
- [7. Tune bind importance](#7-tune-bind-importance)
- [8. Tune the open timeout](#8-tune-the-open-timeout)
- [9. Own app, second process](#9-own-app-second-process)
- [10. Test the host](#10-test-the-host)

## 1. Add the dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts (your app module)
implementation("com.github.iamjosephmj.uI-PiPe:pipe:1.0.0-alpha04")
implementation("com.github.iamjosephmj.uI-PiPe:pipe-serialization:1.0.0-alpha04") // optional: typed messages
```

## 2. Declare visibility

On Android 11+ your app cannot see — and therefore cannot bind — packages it hasn't declared.
This is the single most common integration failure (`NOT_VISIBLE`; see
[troubleshooting](troubleshooting.md#not_visible--provider-invisible)). Declare the provider
**by package** (tightest, recommended):

> On Android 9/10 (API 28–29) package visibility filtering doesn't exist — every installed package
> is visible — so the declaration is inert there (the platform simply ignores it). Keep it
> unconditionally; it costs nothing and future-proofs the install base.

```xml
<manifest …>
    <queries>
        <package android:name="com.partner.app" />
    </queries>
    …
</manifest>
```

or **by intent action** (for open partner ecosystems — pairs well with [discovery](#6-discover-providers)):

```xml
<queries>
    <intent>
        <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
    </intent>
</queries>
```

Skip this step only for a provider inside your own app (same package — always visible; see
[section 9](#9-own-app-second-process)).

## 3. Open a pane

From a `ComponentActivity`:

```kotlin
PipeFullScreen.open(
    activity = this,
    provider = ProviderComponent(
        packageName = "com.partner.app",
        serviceClass = "com.partner.app.PaneService",   // the provider's PipeProviderService subclass
    ),
    request = PipeRequest(
        action = "partner.screen",                       // which pane the provider builds
        extras = bundleOf("amount" to 42),               // optional parameters
    ),
    onSession = { session ->
        // The pane is live and verified. session.peer is the provider's verified identity.
        lifecycleScope.launch { session.messages.collect { msg -> /* provider → host */ } }
        session.send(PipeMessage(bundleOf("text" to "hello")))   // host → provider
    },
    onError = { e -> handle(e) },   // section 4
)
```

Nothing goes in your layout — the pane is a window the provider adds over your activity, parented
to your window token. `open()` returns the `Job` of the open itself (complete once the session is
delivered or the open failed); the session is how you drive the pane afterwards.

### What closes the pane

Exactly one of these, whichever happens first — teardown is idempotent and nothing leaks:

- `session.close()` — you end it (the normal "done" path).
- The provider closes it (`HostHandle.close()`, or BACK pressed inside the pane).
- Your activity hits `ON_DESTROY`.
- Back-press while focus is still on the host (the fallback; BACK inside the pane is handled by
  the pane's own window).
- The provider process dies — you get `PipeState.Closed(cause = PipeTransportException)`.

Observe the session's lifetime via `session.state: StateFlow<PipeState>`:
`Connecting → Open(peer) → Closed(cause)`. A `Closed(null)` is a clean close; a non-null cause is
a failure (e.g. the peer died).

## 4. Handle failures

`onError` receives a **sealed** `PipeException` — a `when` over it is exhaustive, so the compiler
keeps you honest when new failure types appear:

```kotlin
onError = { e ->
    when (e) {
        is PipeDeniedException ->
            // Policy said no. e.source: which side refused; e.reason: the denying authorizer's reason.
            show("Partner declined: ${e.reason}")
        is PipeProviderUnavailableException ->
            // e.kind: NOT_VISIBLE (almost always a missing <queries> — see troubleshooting),
            // NO_SERVICE (wrong component / not exported), or CERT_UNREADABLE.
            when (e.kind) {
                PipeProviderUnavailableException.Unavailable.NOT_VISIBLE -> promptToInstall()
                PipeProviderUnavailableException.Unavailable.NO_SERVICE -> reportBug()
                else -> showGenericError()
            }
        is PipeTimeoutException -> retryOffer()          // see section 8
        is PipeVersionMismatchException -> askPartnerToUpdate() // host/provider library skew
        is PipeTransportException -> showGenericError()  // binder-level failure; session is dead
    }
}
```

Full table with causes and fixes: [troubleshooting § Error reference](troubleshooting.md#error-reference).

Two guarantees worth internalizing:

- **A failed open is invisible.** Denial, timeout, unavailability — all produce no bind and no
  window. You never clean up a half-rendered pane.
- **A failed *session* is terminal.** If a live pane's provider process dies, the session moves to
  `Closed(cause)`; there is no reconnect. Open again if your UX calls for it.

## 5. Choose an authorizer

The `authorizer` parameter is your trust policy: the library verifies the provider's signing
identity (kernel/PackageManager-derived, never self-reported) and hands it to your policy before
anything binds.

```kotlin
PipeAuthorizers.sameSigningKey(this)          // default — only your own signing key (your other
                                             // apps / your own second process)
PipeAuthorizers.allowlist("aa11…", "bb22…")   // specific partner signing certs (SHA-256)
PipeAuthorizers.anyOf(sameSigningKey(ctx), allowlist(cert))  // either is enough
PipeAuthorizer { peer, request ->            // custom — suspend, so it can hit a backend
    if (backend.isPartner(peer.signingCertSha256)) AuthDecision.Allow
    else AuthDecision.Deny("not a current partner")
}
```

Getting a partner's cert hash:

```bash
apksigner verify --print-certs partner.apk
# Signer #1 certificate SHA-256 digest: aa11…
```

If the partner rotates signing keys, list the whole lineage in the allowlist. Deeper discussion
(including the provider-side mirror of this decision): [security & trust](security.md).

## 6. Discover providers

Don't want to hardcode `ProviderComponent`s? Ask what's installed:

```kotlin
lifecycleScope.launch {
    val providers = PipeDiscovery.query(context)   // Dispatchers.IO inside; safe from anywhere
    // [ProviderDescriptor(component, packageName, label, certSha256)]
    //   → render a picker from `label`, pin with `certSha256`, open `component`.
}
```

Discovery respects [package visibility](#2-declare-visibility): declare the `<intent>`-form
`<queries>` element to see all Pipe providers, or per-package entries for a closed set. Invisible
providers are simply not returned.

## 7. Tune bind importance

The pane renders in the provider's process. `bindImportance` decides where that process ranks
while the pane is up:

- `NORMAL` (default) — the provider process is *visible*-important: alive and unthrottled, but
  ranked below you. Under memory pressure the platform reclaims it first. Use this unless you
  have a reason not to.
- `IMPORTANT` — the provider inherits your foreground priority (`BIND_IMPORTANT`). For panes that
  must be as survivable as the host — typically **your own same-app processes** (this is what
  `:sample-solo`'s multi-pane demo uses to keep three pane processes alive at once). This hands a
  provider a priority-inflation lever; don't give it to third parties lightly.

## 8. Tune the open timeout

`open()` bounds the entire handshake — bind, verification, *and* the provider's `onOpenPane` — and
fails with `PipeTimeoutException` when it expires (default: 10 s, `PipeFullScreen.DEFAULT_OPEN_TIMEOUT`).

```kotlin
PipeFullScreen.open(this, provider, request, timeout = 20.seconds, …)
```

Raise it if the provider does slow work before its UI is ready (key generation, network); lower
it for time-critical UX. A timeout tears the attempt down completely — no bind, no window.

### Dimming the system bars for scrim-style panes

A pane is a sub-window of your window, and the window manager constrains sub-windows to your
window's *content* frame — a provider's scrim cannot cover the status/navigation-bar strips, even
with `PaneSpec(edgeToEdge = true)`. Those strips show *your* pixels, so the host dims them:

```kotlin
PipeFullScreen.open(this, provider, request, dimSystemBars = true, …)
```

This adds animated 70%-black strips over your decor while the pane is open (touch-through; removed
on close). Use it for dialog/sheet-style panes; leave it off for region/tiling panes.

## 9. Own app, second process

Host and provider can be one app. Put the service in its own process and point the host at it:

```xml
<service
    android:name=".PaneService"
    android:process=":pane"
    android:exported="false" />
```

```kotlin
PipeFullScreen.open(
    this,
    ProviderComponent(packageName, "$packageName.PaneService"),
    PipeRequest("demo.editor"),
)
```

No `<queries>` needed (your own package is always visible); `sameSigningKey` passes trivially. You
get real process isolation for heavy or risky UI — own heap, own GC, own UI thread, own crash
domain — rendered seamlessly inside your activity. `:sample-solo` shows this, plus a three-pane
tiling variant where each pane is a separately-interactive band.

## 10. Test the host

Two patterns that work well (both used by the sample apps):

**Unit-test your open logic against a fake session.** `PipeSession` is a small interface — fake
it:

```kotlin
class FakeSession(override val peer: PeerIdentity) : PipeSession {
    override val state = MutableStateFlow<PipeState>(PipeState.Open(peer))
    private val channel = Channel<PipeMessage>(Channel.UNLIMITED)
    override val messages = channel.receiveAsFlow()
    val sent = mutableListOf<PipeMessage>()
    override suspend fun send(message: PipeMessage) = sent.add(message)
    override fun close() { state.value = PipeState.Closed(null) }
}
```

Drive your use-cases/ViewModels with it (see `sample-host`'s `RequestCertificationUseCaseTest`).

**Instrumented-test the real thing.** Install host + provider (+ the `evil-*` apps for negative
tests) on a device or emulator and assert through the public API — `sample-host`'s
`PipeE2eTest` covers the round-trip, decline, and teardown; its `SecurityE2eTest` proves a
wrongly-signed provider is denied *without binding* (asserted via `pidof`).

```bash
./gradlew :sample-host:connectedDebugAndroidTest
```

---

Next: **[Messaging guide](messaging.md)** — or back to the **[integration index](README.md)**.
