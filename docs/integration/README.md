# Integrating uI-PiPe

Everything you need to put a verified, cross-process pane into production. Start with the guide
for your side of the boundary; both guides assume nothing but a working Android project.

| Guide | Read it when… |
|---|---|
| **[Host guide](host.md)** | You want another app's (or your own second process's) screen rendered *inside* your activity, safely. |
| **[Provider guide](provider.md)** | You ship the screen other apps render inside their windows. |
| **[Messaging guide](messaging.md)** | The pane and the host need to talk — requests, results, typed contracts. |
| **[Security & trust](security.md)** | You're deciding *who* may connect to whom — authorizers, cert pinning, attestation. |
| **[Troubleshooting](troubleshooting.md)** | Something failed at runtime. Error reference, `<queries>`, R8, timeouts, FAQ. |

Also worth knowing about:

- **[API reference](../../#documentation)** — generate it locally with `./gradlew :dokkaGeneratePublicationHtml`.
- **[ARCHITECTURE.md](../../ARCHITECTURE.md)** — how it all works under the hood (wire protocol, gates, window mechanics).
- **`:sample-host` + `:sample-provider`** — the working reference implementation: a Compose host, a consent-dialog provider, and a full hardware-attestation round-trip.

## The 60-second mental model

- Two roles: a **host** (your activity) and a **provider** (a service in another app — or another
  process of your own app) that renders a real window over the host's screen.
- Every open is a **mutually-verified handshake**: each side proves its signing identity to the
  other before anything binds or renders. You control that policy with an *authorizer*.
- The pane is the provider's own full-screen window — you place nothing in your layout. The host
  gets back a **session**: state, messages, close.
- A denial, timeout, or crash produces **no bind and no window** — failures are typed exceptions,
  never half-rendered UI.

## Minimal host

```kotlin
PipeFullScreen.open(
    activity = this,
    provider = ProviderComponent("com.partner.app", "com.partner.app.PaneService"),
    request = PipeRequest("partner.screen"),
    onSession = { session -> /* live: session.messages, session.send(...), session.close() */ },
    onError = { e -> /* typed PipeException — see troubleshooting */ },
)
```

(Plus one manifest line — see the [host guide](host.md#2-declare-visibility).)

## Minimal provider

```kotlin
class PaneService : PipeProviderService() {
    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val label = TextView(this).apply { text = "pane-ready" }
        return PaneResult.Content(object : PipeContent {
            override val view = label
            override fun onMessage(m: PipeMessage) { label.text = m.payload.getString("text") }
        })
    }
}
```

(Plus a manifest service entry — see the [provider guide](provider.md#2-declare-the-service).)

## Requirements

- `minSdk 30` (Android 11) on both sides; no `@hide` APIs, reflection, or
  `SurfaceControlViewHost` — public APIs only, so it's Play-policy safe.
- Kotlin coroutines (the API is coroutine-first).
- Same library version on both sides of a pane (mismatched versions fail cleanly with
  `PipeVersionMismatchException`, never with a broken render).
