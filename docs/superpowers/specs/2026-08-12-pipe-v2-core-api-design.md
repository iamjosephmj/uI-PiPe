# Pipe v2 — Phase 1: Coroutine-First Core API, Typed Messaging & Publishing

**Status:** design (approved)
**Date:** 2026-08-12
**Supersedes public API of:** [2026-08-12-pipe-design.md](2026-08-12-pipe-design.md) (v1, `implemented`)

## Summary

Pipe embeds a provider app's live, fully-interactive UI inside a host app over
`SurfaceControlViewHost` + AIDL, mutually gated by signing certificate. v1
proved the security model and the transport. v2 makes Pipe **easy to adopt**:
a coroutine/Flow-first public API, an opt-in typed-message layer, provider
discovery, multi-pane providers, suspendable authorization policy, and — the
prerequisite for anyone to use it at all — a published, binary-compatibility-
tracked artifact with the raw AIDL transport hidden behind `@RestrictTo`.

This is **Phase 1 of three**. Phase 2 adds the Compose layer (`PipePane`
composable + Compose provider panes + full-screen/window attach). Phase 3
rebuilds the sample apps as a showcase (workspace shell + rich editor pane).
This spec covers Phase 1 only; Phases 2–3 get their own spec → plan → build
cycles.

**This is a clean v2**: the public API breaks freely. Nothing consumes v1 yet
(it was never published), so there is no back-compat burden. The **security
core is preserved unchanged** — kernel-backed uid identity on the provider,
verified-ComponentName identity on the host, cert lineage, shared-uid union
semantics, the uid-gated callback binders, and the evil-app denial proof all
carry forward.

## Goals

1. **Coroutine-first host API** — `suspend fun open()` that throws a sealed
   `PipeException`, a `StateFlow<PipeState>` for connection state, a
   `Flow<PipeMessage>` for inbound messages, and lifecycle/scope auto-close.
2. **Kill the v1 footguns** — the friction audit's contract hazards: `peer`
   throwing before open, `send()` silently no-oping, missing-`<queries>`
   misreported as "not installed", dead error codes, no timeout, main-thread
   PackageManager I/O.
3. **Suspendable authorization** — `PipeAuthorizer.authorize()` becomes a
   `suspend fun`, enabling remote-policy and user-consent gates.
4. **Provider discovery** — resolve installed providers by intent action
   instead of hardcoding an internal service class name.
5. **Multi-pane providers** — one provider service serves multiple concurrent
   hosts, each with an isolated session; no more silent eviction.
6. **Typed messaging** — an opt-in `:pipe-serialization` module: `@Serializable`
   payloads over the existing Bundle envelope, shared via a contract module.
7. **Publishable & tracked** — `maven-publish` for `:pipe` and
   `:pipe-serialization`, sources + javadoc, binary-compatibility-validator
   (`api/*.api`), and `@RestrictTo`/`internal` on the AIDL transport + `OpenSpec`.
8. **Off-device testability** — inject the `Handler`/`CoroutineDispatcher` so
   gate and session logic is `runTest`-able without an emulator.

## Non-Goals (Phase 1)

- Jetpack Compose host composable (`PipePane`) or Compose-authored provider
  panes — **Phase 2**. (The `ViewTreeLifecycleOwner`/`SavedStateRegistryOwner`
  plumbing a `ComposeView` pane needs lands with Phase 2.)
- Full-screen / dedicated-window attach — **Phase 2**.
- Showcase sample apps — **Phase 3**. Phase 1 keeps the existing sample apps
  building against the new API only enough to run the e2e/security suites;
  visual polish is Phase 3.
- Request/response correlation (message ids, `suspend fun request(): Reply`).
  The typed layer ships fire-and-forget streams; correlation is a candidate
  follow-up, explicitly deferred.
- Timeout is **added** here (a goal), but a configurable per-call retry/backoff
  policy is out of scope.

## Global Constraints

- `minSdk = 35`, `compileSdk = 36`, `targetSdk = 36` (unchanged).
- Library namespace `tech.ssemaj.pipe`; group coordinates `tech.ssemaj.pipe`.
- Kotlin only. Coroutines via `kotlinx-coroutines-android`. `:pipe` core takes
  a dependency on `kotlinx-coroutines-core/-android` but **not** on
  `kotlinx-serialization` — that stays in `:pipe-serialization`.
- Public API is tracked by binary-compatibility-validator; every task that
  changes the surface updates the `.api` dump.
- The AIDL interfaces (`IEmbedProvider`, `IHostChannel`, `IGuestChannel`,
  `IOpenResultCallback`, `IEmbedSession`), `OpenSpec`, and `Protocol` become
  non-public (`@RestrictTo(LIBRARY_GROUP)` on generated-facing wrappers;
  `internal` where Kotlin allows). No third party can implement the transport
  and bypass `PipeProviderService`.
- All host-facing callbacks/flows emit on the main dispatcher; the injected
  dispatcher defaults to `Dispatchers.Main.immediate`.
- Security invariants are **unchanged and non-negotiable**: provider identity
  is `Binder.getCallingUid()` only; host identity is the resolved+bound
  `ComponentName` only; the callback binders remain uid-gated; empty/unreadable
  cert sets deny.

## Architecture

Four facets of `:pipe`, plus the sibling `:pipe-serialization`:

```
:pipe
  core/      PipeRequest, PipeMessage, PipeSize, PipeState, PipeException,
             DenialSource, CloseReason, Pipe (constants: ACTION_OPEN_PANE, …)
  auth/      PeerIdentity, AuthDecision, PipeAuthorizer (suspend), built-ins,
             IdentityResolver, AndroidSigningSource  [carried from v1, +suspend]
  transport/ AIDL + OpenSpec + Protocol             [carried from v1, now @RestrictTo]
  channel/   sequencers                             [carried from v1]
  discovery/ PipeDiscovery, ProviderDescriptor      [new]
  host/      PipeView (suspend open), PipeSession, HostGate  [rewritten API]
  provider/  PipeProviderService (multi-pane, suspend onOpenPane),
             PaneResult, PipeContent, HostHandle, ProviderGate  [rewritten API]
:pipe-serialization
             PipeCodec, send<T>/messagesOf<T> extensions        [new module]
```

### Host: connection state model

```kotlin
sealed interface PipeState {
    data object Connecting : PipeState
    data class Open(val peer: PeerIdentity) : PipeState
    data class Closed(val cause: PipeException?) : PipeState   // cause == null ⇒ clean close
}
```

`PipeState` is a `StateFlow` on the session. Denial is **not** a state — a
denied `open()` never returns a session; it throws. This removes the v1
"denied path has no session but a callback fires" asymmetry.

### Host: error model

```kotlin
sealed class PipeException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PipeDeniedException(val reason: String, val source: DenialSource) : PipeException(...)
class PipeTimeoutException(...) : PipeException(...)          // wires the dead v1 TIMEOUT code
class PipeProviderUnavailableException(val kind: Unavailable) : PipeException(...)
    // Unavailable: NOT_INSTALLED | NOT_VISIBLE (missing <queries>) | CERT_UNREADABLE | NO_SERVICE
class PipeVersionMismatchException(val host: Int, val provider: Int) : PipeException(...)  // wires dead VERSION_MISMATCH
class PipeTransportException(...) : PipeException(...)

enum class DenialSource { HOST_POLICY, PROVIDER_POLICY }   // "I refused them" vs "they refused me"
```

`NOT_VISIBLE` is the fix for the single most likely integration mistake: a
missing `<queries>` entry now yields a message that names `<queries>`, not a
misleading "not installed?".

### Host: the entry point

```kotlin
class PipeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    // test seam; defaults to Main.immediate + the real PackageManager source
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : FrameLayout(context, attrs) {

    /** Suspends until the pane is Open. Throws PipeException on denial/timeout/
     *  transport failure. Cert I/O runs on Dispatchers.IO internally. */
    suspend fun open(
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(context),
        timeout: Duration = 10.seconds,
    ): PipeSession

    /** Fire-and-forget lifecycle binding: opens, and closes the session at
     *  ON_DESTROY. Returns the launched Job. Errors surface via onError. */
    fun openIn(
        owner: LifecycleOwner, provider: ProviderComponent, request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(context),
        onError: (PipeException) -> Unit = {},
        onSession: (PipeSession) -> Unit = {},
    ): Job

    fun close()   // closes the current session, if any
}
```

`open()` is main-safe: it internally moves the PackageManager cert lookup to
`Dispatchers.IO` (fixing v1's main-thread PM I/O) and resumes on the main
dispatcher for the SCVH handoff. Calling `open()` twice concurrently on one
view is illegal and throws `IllegalStateException`; the caller closes first.

### Host: the session

```kotlin
class PipeSession internal constructor(...) {
    val peer: PeerIdentity                       // always valid; open() returned ⇒ Open reached
    val state: StateFlow<PipeState>
    val messages: Flow<PipeMessage>              // cold; callbackFlow; completes on Closed
    suspend fun send(message: PipeMessage): Boolean   // false if the channel is gone
    suspend fun resize(size: PipeSize)
    fun close()
}
```

`messages` is a cold `callbackFlow` that registers on collection and completes
when the session closes (clean or error). It must **not** hang on the denial
path — denial is delivered by `open()` throwing, before any session exists.

### Provider: multi-pane service

```kotlin
abstract class PipeProviderService : Service() {
    /** Default same-signing-key; override for allowlist/anyOf/remote policy. */
    open fun authorizer(): PipeAuthorizer = PipeAuthorizers.sameSigningKey(this)

    /** Called on the main thread AFTER the caller passes the (possibly
     *  suspending) gate. Return Content to open, or Reject to refuse this
     *  specific request (e.g. unknown action) without throwing. */
    abstract suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult
}

sealed interface PaneResult {
    data class Content(val content: PipeContent) : PaneResult
    data class Reject(val reason: String) : PaneResult   // ⇒ host sees PipeDeniedException(PROVIDER_POLICY)
}

interface PipeContent {           // View-based in Phase 1; Compose variant in Phase 2
    val view: View
    fun onMessage(message: PipeMessage) {}
    fun onResized(size: PipeSize) {}
    fun onClosed(reason: CloseReason) {}
}

interface HostHandle {
    val peer: PeerIdentity
    suspend fun send(message: PipeMessage): Boolean
}
```

**Multi-pane**: the service holds a `Map<IBinder, ActivePane>` keyed by the
host's callback binder, not a single `active` slot. Each host gets its own
`ActivePane` (own SCVH, own sequencers, own lifecycle). A pane closes only its
own resources; `onDestroy` closes all. This removes v1's silent-eviction bug.
The `CloseReason` set gains no new values — eviction no longer happens.

### Auth: suspendable policy

```kotlin
fun interface PipeAuthorizer {
    suspend fun authorize(peer: PeerIdentity, request: PipeRequest): AuthDecision
}
```

Built-ins (`sameSigningKey`, `allowlist`, `anyOf`) implement it with
non-suspending bodies. The **provider** gate suspends inside the service's
`CoroutineScope` (a `SupervisorJob` + main dispatcher owned by the service)
before building the pane; because `IEmbedProvider.open` is already `oneway`,
the binder thread returns immediately and the result is delivered later via the
callback — suspension does not block a binder thread. The **host** gate
suspends inside `open()` before `bindService`. `AuthDecision` is unchanged
(`Allow` / `Deny(reason)`).

Gate order is unchanged: provider = protocol → identity → policy; host =
identity → policy (pre-bind).

### Discovery

```kotlin
data class ProviderDescriptor(
    val component: ProviderComponent,
    val packageName: String,
    val label: CharSequence?,          // from PackageManager, for UI
    val certSha256: List<String>,      // lineage, so a host can pre-filter by trust
)

object PipeDiscovery {
    /** Providers exposing [action], resolved via queryIntentServices. Requires
     *  the host's manifest to allow visibility (documented). Runs off the main
     *  thread internally. */
    suspend fun query(context: Context, action: String = Pipe.ACTION_OPEN_PANE): List<ProviderDescriptor>
}
```

Discovery removes the hardcoded internal-class-name coupling. Identity is still
verified cryptographically at `open()`; discovery is a convenience, not a trust
boundary — a `ProviderDescriptor` is just a typed `ComponentName` + metadata.

### Typed messaging (`:pipe-serialization`)

```kotlin
// consumers share a small module of @Serializable payloads:
@Serializable data class EditorPatch(val range: IntRange, val text: String)

// the module provides:
suspend inline fun <reified T> PipeSession.send(payload: T): Boolean          // CBOR → Bundle → send
inline fun <reified T> PipeSession.messagesOf(): Flow<T>                       // filter+decode
suspend inline fun <reified T> HostHandle.send(payload: T): Boolean           // provider side
```

Wire format: CBOR-encoded `ByteArray` carried in the `PipeMessage` Bundle under
a reserved key, alongside the existing `schemaVersion`. Decoding a wrong type
yields no emission (filtered), never a crash. The raw `PipeMessage`/`Bundle`
API remains available for consumers who don't want the dependency.

## Footgun Fixes (from the v1 friction audit)

| v1 hazard | v2 resolution |
|---|---|
| `session.peer` throws before `onOpened` | `open()` suspends to Open; `peer` is always valid |
| `send()` silently no-ops | returns `Boolean`; provider `HostHandle.send` too |
| missing `<queries>` → "not installed?" | `PipeProviderUnavailableException(NOT_VISIBLE)` |
| dead `TIMEOUT`/`CERT_UNREADABLE`/`VERSION_MISMATCH` | wired to real exception types |
| no open timeout | `open(timeout = 10.seconds)`, throws `PipeTimeoutException` |
| denial via two paths, indistinguishable | `DenialSource.{HOST_POLICY, PROVIDER_POLICY}` |
| main-thread PackageManager I/O | cert lookup on `Dispatchers.IO` inside `open()` |
| `ACTION_OPEN_PANE` magic string ×3 | `Pipe.ACTION_OPEN_PANE` constant |
| public AIDL transport = gate bypass | `@RestrictTo`/`internal` |
| one-pane-per-service silent eviction | multi-pane service |
| unknown action must throw | `PaneResult.Reject` |
| `seq` app-settable, corrupts ordering | `seq` moves out of the public constructor |
| hardcoded `Handler`, not `runTest`-able | injected `CoroutineDispatcher` |
| README "gap-free delivery" vs gap-accepting code | doc corrected to "ordered, de-duplicated, gaps tolerated" |

## Testing Strategy

- **Unit (`runTest`, off-device)** — newly possible via dispatcher injection:
  gate ordering (incl. the identity-before-policy test the v1 review
  recommended: unknown uid + deny-all authorizer must yield the identity
  failure, not the policy refusal); `PipeState`/exception mapping; sequencers;
  discovery filtering; typed codec round-trip; suspend-authorizer allow/deny.
- **Instrumented e2e** — the v1 suite ported to the new API: render, touch,
  IME, both channel directions, teardown, plus **multi-pane** (two hosts, one
  provider, both live) and **close/reopen** (the single highest-value test the
  v1 review flagged as missing — pins sequencer reset, surface re-attach, input
  token re-acquisition).
- **Security e2e** — the evil-provider/evil-host suites carried forward
  unchanged in intent, ported to the new API, still proving both-direction
  denial and the uid-gated handoff. A suspend-authorizer denial case is added
  (a policy that suspends then denies still refuses cleanly).
- **Publishing** — `publishToMavenLocal` smoke + `apiCheck` (binary-compat
  validator) in the build.

## Migration Notes

Internal only (no external consumers). The v1 sample apps are updated to the
new API as part of Phase 1 so the suites run; their UI is intentionally left
minimal until Phase 3. The v1 spec is marked superseded-in-public-API.

## Open Questions (resolved)

1. Break v1 API? **Yes** — clean v2, nothing published.
2. Suspendable authorizers? **Yes.**
3. Discovery API? **Yes.**
4. Multi-pane? **Yes** — lift the one-pane limit.
5. Typed messaging placement? **Separate `:pipe-serialization` module.**
6. Request/response correlation? **Deferred** (fire-and-forget streams in v2).
