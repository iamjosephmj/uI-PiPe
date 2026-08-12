# Pipe — gated app-to-app embeddable UI (design)

**Date:** 2026-08-12
**Status:** design, pending review
**Project:** `~/AndroidStudioProjects/pipe`

## Summary

**Pipe** is an Android library that lets one app render a **live, fully-interactive UI inside another app's window** — cooperatively and with a cryptographic gate on both ends. A *host* app embeds a `PipeView` in its layout; a *provider* app renders a `View`/`ComposeView` (e.g., a WebView, a checkout panel, a mini-app) into it, over the supported `SurfaceControlViewHost` transport, with a two-way typed message channel between them.

It is the legitimate, consented realization of "UI inside another app": both apps opt in, each verifies the other's signing identity, and the host controls placement, size, lifetime, and revocation. It deliberately does **not** rely on any window-token/overlay side channel — it uses the platform's sanctioned cross-process UI APIs.

## Goals

- Embed a provider app's interactive UI inside a host app's view, in-process-isolated (provider UI runs in the provider's process).
- Full interactivity: touch, scroll, **and** text input / IME.
- Mutual, cryptographic trust: each side verifies the other's signing certificate before extending trust.
- Pluggable authorization: the library authenticates; a swappable adapter authorizes.
- Two-way typed message channel between host and embedded UI.
- Host-controlled lifecycle: size, close, revoke at any time.

## Non-goals (v1)

- Open marketplace / arbitrary untrusted app-to-app embedding (this v1 targets a **closed app family / vetted partners**).
- End-user consent prompts (trust is developer-established via signing keys / allowlists; a consent adapter is possible later).
- Lower-SDK "degraded input" mode (v1 pins a modern minSdk for the interactive guarantee; see Constraints).
- Splitting into multiple artifacts (single artifact in v1; can split later if bloat matters).

## Key decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Primary scenario | Closed app family / vetted partners |
| Interactivity | Fully interactive (touch + scroll + text/IME) |
| Trust model | Mutual cert verification; **same signing key** default + **partner cert allowlist** |
| Authorization | **Pluggable adapter** (`EmbedAuthorizer`); library owns authentication, adapter owns authorization |
| Comms | Full **bidirectional** typed message channel |
| Transport | **`SurfaceControlViewHost` + bound AIDL service** (Approach A) |

## Architecture & components

One artifact, **`:pipe`** (namespace `tech.ssemaj.pipe`), with three facets:

- **Host facet** (`pipe.host`) — `PipeView` (a `FrameLayout` wrapping a `SurfaceView`). Resolves + verifies the provider, binds it, hands over its input/host token + launch request + a channel callback, attaches the returned `SurfacePackage`.
- **Provider facet** (`pipe.provider`) — abstract `PipeProviderService`. Verifies the caller (binder uid → cert), builds the `SurfaceControlViewHost`, returns the `SurfacePackage`, wires the channel. The app implements `onOpenPane(request, host) → PipeContent`.
- **Shared core** (`pipe.core`, `pipe.auth`, `pipe.transport`, `pipe.channel`) — the AIDL contracts, `EmbedAuthorizer`/`PeerIdentity`/`AuthDecision` + built-in authorizers, SCVH plumbing, and the reliable message pipe. The only part both apps must agree on.

Two sample apps ship in-repo — **`:sample-host`**, **`:sample-provider`** (provider renders an interactive WebView pane) — and double as e2e + security tests.

**Mental model:** `startActivityForResult`, but the "activity" renders *inside your view* instead of taking over the screen — with a cryptographic gate on both ends and a live two-way channel.

## The gate (security core)

Mutual verification; identity is always cryptographic + kernel-backed, never a self-reported package name.

**Direction 1 — Host verifies Provider (before binding).** Host resolves the provider as an explicit `ComponentName`, queries `PackageManager` for its signing certs, runs the host's authorizer. Only on Allow does it bind — and binds *that exact ComponentName* (verified == bound, no TOCTOU). Stops the host handing its input token + screen real estate to a hostile provider.

**Direction 2 — Provider verifies Host (inside the `open()` call).** Provider reads `Binder.getCallingUid()` (kernel-enforced, unspoofable), maps to package(s) via `getPackagesForUid()`, extracts signing cert SHA-256s, runs the provider's authorizer. Only on Allow does it build the `SurfaceControlViewHost`. Never reads a package name from intent/extras. Stops a hostile host embedding/scraping a sensitive provider panel.

**Hardening baked in:**
- Identity from binder uid only (provider side) / verified `ComponentName` (host side).
- Shared-uid callers: require **all** packages trusted, else reject (safer default).
- Exported provider service performs the uid cert-check as its first action; untrusted binders get nothing.
- Optional `signature`-level permission on the provider service as cheap defense-in-depth for the same-key case (cert check remains the real gate for allowlist partners).
- Signer rotation handled via `hasSigningCertificate()` lineage for same-key mode.

## Authorization adapter

The library extracts a **verified** `PeerIdentity` and hands it to a swappable adapter that makes the policy call. The adapter never touches `PackageManager` or trusts a self-reported name.

```kotlin
fun interface EmbedAuthorizer {
    fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision
}
sealed interface AuthDecision { object Allow : AuthDecision; data class Deny(val reason: String) : AuthDecision }
data class PeerIdentity(val uid: Int, val packages: List<String>, val signingCertSha256: List<String>)

// built-ins (ordinary adapters)
object SameSigningKey : EmbedAuthorizer
class Allowlist(certSha256: Set<String>) : EmbedAuthorizer
fun anyOf(vararg authorizers: EmbedAuthorizer): EmbedAuthorizer
```
Custom engines (server-driven allowlist, user-consent prompt, per-request scoping, risk scoring) are just other `EmbedAuthorizer` implementations. Both host and provider each supply their own; policies may be asymmetric.

## Handshake & data flow (host drives)

1. `pipeView.open(providerComponent, request, hostAuthorizer, callbacks)`.
2. **Host authorizes provider** (pre-bind): extract provider cert hashes → `PeerIdentity` → `hostAuthorizer`. Deny → `onDenied`, no bind.
3. Bind the exact `ComponentName`; obtain `IEmbedProvider`.
4. Host `open(...)` passes: its **host/input token**, display id, initial size, the launch `request`, and an `IHostChannel` (provider→host).
5. **Provider authorizes host** (inside `open()`): `Binder.getCallingUid()` → `PeerIdentity` → provider authorizer. Deny → `OpenResult.denied`, no surface built.
6. Provider calls app's `onOpenPane(request, hostHandle) → PipeContent(view)`; wraps it in a `SurfaceControlViewHost` bound to the host token; returns the `SurfacePackage` + `IEmbedSession` + `IGuestChannel` (host→provider).
7. Host attaches the `SurfacePackage` into its `SurfaceView`, wires input transfer → **live interactive UI inside the host**.

**After open:** two-way over the channel binders; ordered delivery; each `PipeMessage` carries `{payload: Bundle, schemaVersion: Int, seq: Long}`. Library provides the reliable pipe; app layers its typed schema.

**Lifecycle & revocation:** host `session.close()` or detaching the (lifecycle-aware) `PipeView` → provider tears down SCVH, releases content, closes channel. Binder-death watchers both ways (provider death → `onClosed`; host death → provider cleanup). Resize via `session.resize(w,h)`.

## Public API

### Host
```kotlin
class PipeView(context: Context, attrs: AttributeSet?) : FrameLayout
fun PipeView.open(
    provider: ProviderComponent,                  // package + service class
    request: PipeRequest,                         // Parcelable/Bundle launch payload
    authorizer: EmbedAuthorizer = SameSigningKey,
    callbacks: PipeHostCallbacks,
): PipeSession

interface PipeHostCallbacks {
    fun onOpened(session: PipeSession) {}
    fun onMessage(message: PipeMessage) {}        // provider -> host
    fun onResized(w: Int, h: Int) {}
    fun onDenied(reason: String) {}
    fun onError(error: PipeError) {}
    fun onClosed(reason: CloseReason) {}
}
interface PipeSession { fun send(message: PipeMessage); fun resize(w: Int, h: Int); fun close(); val peer: PeerIdentity }
```

### Provider
```kotlin
abstract class PipeProviderService : Service() {
    abstract fun authorizer(): EmbedAuthorizer          // default SameSigningKey
    abstract fun onOpenPane(request: PipeRequest, host: HostHandle): PipeContent
}
interface PipeContent {
    val view: View                                       // or ComposeView
    fun onMessage(message: PipeMessage) {}               // host -> provider
    fun onResized(w: Int, h: Int) {}
    fun onClosed(reason: CloseReason) {}
}
interface HostHandle { fun send(message: PipeMessage); val peer: PeerIdentity }
```

### Provider manifest
```xml
<service android:name=".MyPaneService" android:exported="true">
    <intent-filter><action android:name="tech.ssemaj.pipe.action.OPEN_PANE"/></intent-filter>
</service>
<!-- optional same-key defense-in-depth: android:permission="tech.ssemaj.pipe.permission.BIND_PANE" -->
```

## Error handling

- Denied (either direction) → `onDenied(reason)`; provider builds nothing.
- Provider not installed / component not found / cert unreadable → `onError`.
- AIDL/contract version incompatible (negotiated at `open()`) → `onError(versionMismatch)`.
- Surface/transport failure or binder death → `onClosed`.
- Bind/open timeouts → `onError(timeout)`.

## Platform constraints

- Transport: `SurfaceControlViewHost` (API 30+) + `SurfacePackage`.
- Clean cross-process input **and IME** for a fully-interactive pane needs `InputTransferToken` → **API 35 (Android 15)**.
- **Decision:** v1 `minSdk = 35` to guarantee the interactive contract. Acceptable for a closed family (controlled fleet). Lower-SDK degraded-input mode is future work. *(Open for confirmation.)*

## Testing strategy

- **Unit:** authorizer logic (`SameSigningKey`, `Allowlist`, `anyOf`) against fabricated `PeerIdentity`; message envelope ordering/version; cert-hash extraction helpers.
- **Instrumented / e2e:** `sample-host` + `sample-provider` verify the pane renders, touch + IME work, channel round-trips both ways, resize, and clean teardown.
- **Security:** an "evil" host and provider signed with a *different* key must be denied in *both* directions (no surface, no bind); shared-uid rejection; TOCTOU (verified==bound) check.

## Open questions

1. Confirm `minSdk = 35` for v1 (vs a lower floor with degraded input).
2. Single artifact vs `pipe-core`/`pipe-host`/`pipe-provider` split (v1 = single).
3. `PipeMessage` payload type: `Bundle` (simple) vs `ByteArray` + app-owned codec (leaner, versionable). Leaning `Bundle` for v1.
