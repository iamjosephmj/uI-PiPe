# Provider guide

You are integrating uI-PiPe **as a provider**: your service renders a real, full-screen window
inside a verified host's activity — in your own process, with your own UI toolkit of choice.

Sections 1–4 are the required path; the rest is shaping the pane and hardening your side.

- [1. Add the dependency](#1-add-the-dependency)
- [2. Declare the service](#2-declare-the-service)
- [3. Implement the pane](#3-implement-the-pane)
- [4. Decide who may open it](#4-decide-who-may-open-it)
- [5. Shape the window](#5-shape-the-window)
- [6. Use Compose inside the pane](#6-use-compose-inside-the-pane)
- [7. Talk to the host](#7-talk-to-the-host)
- [8. Know your lifecycle](#8-know-your-lifecycle)
- [9. Reject cleanly](#9-reject-cleanly)
- [10. Test the provider](#10-test-the-provider)

## 1. Add the dependency

Same as the [host](host.md#1-add-the-dependency): JitPack repository, then

```kotlin
implementation("com.github.iamjosephmj.uI-PiPe:pipe:1.0.0-alpha04")
implementation("com.github.iamjosephmj.uI-PiPe:pipe-serialization:1.0.0-alpha04") // optional: typed messages
```

## 2. Declare the service

Your pane is an exported `Service` answering the Pipe action:

```xml
<service
    android:name=".PaneService"
    android:exported="true">
    <intent-filter>
        <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
    </intent-filter>
</service>
```

`android:exported="true"` is required for a host in *another app* to bind. For a provider only
your own app binds (own second process), keep `exported="false"` and add `android:process=":pane"`
— see [host guide §9](host.md#9-own-app-second-process).

## 3. Implement the pane

Subclass `PipeProviderService` and answer `onOpenPane`:

```kotlin
class PaneService : PipeProviderService() {

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val name = request.extras.getString("userDisplayName") ?: "there"
        val label = TextView(this).apply { text = "Hello, $name" }

        return PaneResult.Content(object : PipeContent {
            override val view = label
            override fun onMessage(m: PipeMessage) {
                label.text = m.payload.getString("text")   // host → you
            }
            override fun onClosed(reason: CloseReason) {
                // Pane came down: which side closed, or the host died (PEER_DIED).
            }
        })
    }
}
```

The contract in one breath:

- `onOpenPane` runs on the main thread inside the service's `paneScope`. Build your UI and return
  `PaneResult.Content` — or `PaneResult.Reject` (see [section 9](#9-reject-cleanly)).
- The pane is **yours**: a real full-screen window the library adds over the host, parented to the
  host's window token. Nothing of the host's layout touches you; your crashes cannot take the host
  down and vice versa.
- Default appearance: **full-screen and transparent**, so the host shows through — a dialog, a
  bottom sheet, a scrim are all things *you draw and animate inside* the pane
  ([section 6](#6-use-compose-inside-the-pane)).
- BACK pressed inside the pane dismisses it (`CloseReason.PROVIDER_CLOSED`). `host.close()` is
  your programmatic equivalent.
- `host.peer` is the calling app's verified identity — display it if your UX benefits from
  showing *who* is asking.

## 4. Decide who may open it

The mirror of the host's [authorizer decision](host.md#5-choose-an-authorizer) — same API, your
side of the handshake. Override `authorizer()`:

```kotlin
override fun authorizer(): PipeAuthorizer =
    PipeAuthorizers.allowlist("aa11…", "bb22…")   // only known hosts may open my pane
```

The default is `sameSigningKey` (only your own app family). Choose deliberately:

- **You serve one partner app** — `allowlist(theirCert)`. This is the symmetric, recommended
  setup: the host pins your cert, you pin theirs.
- **You serve a vetted set** — `allowlist(...)` with the full set, or `PipeAuthorizers.anyOf(...)`
  to combine rules.
- **You serve whoever finds you via discovery** — return `Allow` in `authorizer()` and treat
  `host.peer` as the input to *per-request* decisions inside `onOpenPane` (log it, rate-limit it,
  price it). Understand what you're giving up: any installed app can bind and render. See
  [security & trust](security.md#the-asymmetric-trap) for the trade-off.

`authorize` is `suspend` on both sides — you may consult a backend before admitting a host.

## 5. Shape the window

`PaneSpec` controls what the pane's *window* is (everything inside it is yours):

```kotlin
PaneResult.Content(content)                                // default: full-screen transparent
PaneResult.Content(content, PaneSpec(translucent = false)) // opaque full-screen
PaneResult.Content(content, PaneSpec(focusable = false))   // pass-through display (no IME/input)
```

| Property | Default | Meaning |
|---|---|---|
| `translucent` | `true` | Host content shows through. `false` = opaque. |
| `focusable` | `true` | Pane is an IME/key target. `false` also implies non-touch-modal. |
| `touchModal` | `true` | `false` (`FLAG_NOT_TOUCH_MODAL`) consumes touches only within bounds and passes the rest through — the basis for tiling. |
| `gravity`, `widthPx`, `heightPx` | top-start, `MATCH_PARENT`, `MATCH_PARENT | Window geometry for **tiling / multi-pane**: several region-sized, non-touch-modal panes tile independently. |

Tiling is genuine window geometry, not a dialog API — `:sample-solo` runs host + three
independently-interactive pane processes in one window. It is aimed at **trusted, signing-verified
app families** (a transparent, partial pane still captures input over its bounds).

## 6. Use Compose inside the pane

The pane's root view is a lifecycle / saved-state / view-model owner, so `ComposeView` works out
of the box:

```kotlin
override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
    val compose = ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { ConsentDialog(request.extras.getString("nonce")!!) }
    }
    return PaneResult.Content(object : PipeContent {
        override val view = compose
    })
}
```

Two constraints to know:

- **Compose must be attached to a window with a `LifecycleOwner`** — the pane root provides one,
  but if you wrap your content in intermediate views, keep the root's owners reachable (don't
  create a fresh `ViewTreeLifecycleOwner` mid-hierarchy). If you see `BadTokenException` or
  "ViewTreeLifecycleOwner not found" style crashes, this is why.
- **`ModalBottomSheet` and window-tied dialogs don't work** — they need an activity window/dialog
  token the service-owned pane doesn't have. Draw the dialog *inside* the pane instead: your own
  scrim + centered card, or a sheet you animate yourself. That is exactly how `:sample-provider`
  builds its consent dialog (see `ConsentDialog.kt`).

Plain Views, `ViewFlipper`s, SurfaceViews — all fine; the pane doesn't care what you draw.

## 7. Talk to the host

`HostHandle` is your channel:

```kotlin
host.send(PipeMessage(bundleOf("status" to "done")))   // suspend; false ⇒ host is gone (terminal)
host.close()                                          // dismiss your own pane
host.peer                                             // verified identity of the calling app
```

Incoming messages arrive on `PipeContent.onMessage` (main thread). For typed messages
(`@Serializable` + CBOR), see the [messaging guide](messaging.md) — it works identically on both
sides.

Coroutines: use `paneScope` for provider-initiated work (sends after user interaction, timers).
Failures in it are logged, not thrown to a crash handler — keep your own `CoroutineExceptionHandler`
if you need reporting.

## 8. Know your lifecycle

- **One `ActivePane` per host binder.** Several hosts can hold panes simultaneously; they are
  independent, and one host's teardown never touches another's pane.
- **Same host re-opens** → the previous pane for that host is closed and replaced (no leaked
  windows).
- **Host process dies** → `linkToDeath` tears down exactly that pane (`CloseReason.PEER_DIED`).
- **Your service is destroyed** → all panes close, `paneScope` cancels. The service is created on
  bind and destroyed when the last host unbinds — standard bound-service rules.
- **The pane window's insets/BACK** are handled by the library's pane root — you get content
  insets padding for free.

## 9. Reject cleanly

Returning `PaneResult.Reject` refuses *this* request after the host is otherwise verified — wrong
action, missing extras, business rules:

```kotlin
override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult =
    if (request.action != ACTION_WE_SUPPORT) PaneResult.Reject("unsupported action: ${request.action}")
    else buildPane(request, host)
```

The host receives `PipeDeniedException(reason, source = PROVIDER_POLICY)`. Use policy
([section 4](#4-decide-who-may-open-it)) for *who*; use `Reject` for *what* — and prefer `Reject`
over throwing from `onOpenPane` (a throw maps to a transport error on the host side and loses your
reason).

## 10. Test the provider

Keep `onOpenPane` a thin adapter over testable pieces:

- **Presenter/state machines are pure Kotlin** — unit-test them directly. `:sample-provider`
  extracts `PanePresenter` (a plain state machine) and tests all consent flows without a window.
- **Repositories** (keystore, network) — test behind fakes; `:sample-provider`'s
  `KeystoreRepositoryTest` runs on-device to verify real AndroidKeyStore behavior and key cleanup.
- **End-to-end** — pair with a host app in an instrumented test; the `evil-host` app demonstrates
  the negative direction (a wrongly-signed host is denied, nothing renders).

---

Next: **[Messaging guide](messaging.md)** — or back to the **[integration index](README.md)**.
