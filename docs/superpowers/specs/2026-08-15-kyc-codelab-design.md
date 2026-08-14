# uI-PiPe KYC Codelab — Design Spec

**Status:** approved design, ready for implementation planning
**Date:** 2026-08-15
**Author:** Joseph MJ

## 1. Goal

Ship a **Google Codelabs–style tutorial** (hosted on GitHub Pages) that teaches a
developer to build a realistic, security-hardened cross-app UI embedding with
`uI-PiPe`: a mock **bank host app** that embeds a **separately-signed, RASP-guarded
mock KYC verifier app** as a live pane, verifies its identity before handing over the
window token, and reacts gracefully when the verifier's runtime self-protection
(hydra RASP) terminates it.

The codelab doubles as the library's flagship end-to-end example, exercising the full
stack: `PipeFullScreen.open`, a cross-app `allowlist` authorizer, `PipeProviderService`,
typed messaging via `pipe-serialization`, and third-party RASP integration.

## 2. Why this scenario

KYC (Know Your Customer) identity verification is the canonical use case for the
library: a host needs to embed a flow that (a) comes from a **different trust domain**,
(b) handles **sensitive identity data**, and (c) must **prove its identity** before the
host trusts it. Two security layers are taught as the spine:

- **uI-PiPe identity gate** — the bank only embeds a verifier whose signing identity it
  has pinned (cert allowlist).
- **hydra RASP** — the verifier refuses to run in a compromised runtime (root, Frida
  hook, emulator, tamper) and self-terminates. The bank observes this as `PEER_DIED`
  and recovers.

The interlock between the two — hydra killing the verifier process, uI-PiPe surfacing
`PipeState.Closed(PEER_DIED)` — is the codelab's climax and the single most valuable
teaching moment.

## 3. Key decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| Topology | **Two separate apps** (host + verifier), different signing keys | Genuine two-trust-domain story; scopes hydra to the verifier APK only, keeping the host emulator-runnable |
| Codelab format | **Google Codelabs (`claat`)** from a markdown source | Authentic stepped codelab UX; single maintainable markdown source |
| RASP runnability | **Two verifier build flavors:** `dev` (no hydra) + `guarded` (hydra) | Everyone can complete the walkthrough on an emulator via `dev`; RASP stays real and is demonstrated via `guarded` on a physical device. hydra has no observe/non-lethal mode, so a variant split is the only way to keep the happy path runnable |
| Trust boundary | Host uses **`PipeAuthorizers.allowlist(verifierCertSha256)`** | Authentic cross-app identity pinning; the correct lesson for a security audience (not `sameSigningKey`, which cannot admit a differently-signed app) |
| Data exchange | Typed messages via **`pipe-serialization`** (CBOR) | Exercises the serialization module; realistic request/result contract |
| Pages source | **GitHub Pages from `/docs`**, codelab at `docs/codelab/` | Sits alongside existing `docs/media/`; no second publish flow; does not disturb README image links |

### Explicitly out of scope
- `PipeBindImportance` (unrelated to the KYC story; may appear only as an optional callout).
- Real camera / ML / biometric capture — **all KYC steps are mocked** with placeholders. No real PII is captured or transmitted.

## 4. Module structure

All new modules live in the existing `uI-PiPe` repo:

```
sample-kyc-host/                 # "Meridian Bank" — host app (single variant, emulator-OK)
sample-kyc-verifier/             # "VerifyID" — KYC provider app
    flavors:
      dev      → no hydra plugin (runs anywhere, incl. emulator)
      guarded  → applies id("tech.thessemaj.hydra") 2.3.0 (physical device only)
docs/codelab/                    # generated claat site (GitHub Pages)
    <source>.md                  # claat markdown source (single source of truth)
```

Both app modules: `minSdk 30` (matches library; hydra requires ≥28, satisfied),
`exported="true"` on the verifier's provider service (cross-app bind), Kotlin, AGP/Gradle
matching the repo's existing convention.

## 5. The host app — `sample-kyc-host` ("Meridian Bank")

- Single `ComponentActivity` with a "Start verification" button.
- On tap: `PipeFullScreen.open(activity, provider = ProviderComponent(VERIFIER_PKG, VERIFIER_SVC),
  request = PipeRequest(...), authorizer = PipeAuthorizers.allowlist(VERIFIER_CERT_SHA256),
  onSession = ..., onError = ...)`.
- Sends a typed `KycRequest` to the verifier once the session is live.
- Renders the returned `KycResult` (approved/declined/error) in its own UI.
- **Finale handling:** observes `session.state`; on `PipeState.Closed(cause)` where cause is a
  `PEER_DIED`/transport error, shows a clear "verification service unavailable (runtime
  integrity check failed)" state rather than crashing — the graceful-recovery lesson.

## 6. The verifier app — `sample-kyc-verifier` ("VerifyID")

- A `PipeProviderService` subclass; `onOpenPane(request, host)` returns a `PaneResult.Content`
  hosting a small **mock** KYC wizard (2–3 screens):
  1. **Consent** — "VerifyID needs to confirm your identity for Meridian Bank" + Continue.
  2. **Capture ID (mock)** — a placeholder document frame + "Capture" (no real camera).
  3. **Liveness selfie (mock)** — placeholder + "Capture".
  4. **Result** — a brief "Verified ✓" screen, then returns `KycResult` to the host and closes.
- Reads the host's `KycRequest` from `PipeRequest.extras` (or first typed message).
- Provider-side gate: may additionally verify the host via its own authorizer (optional; the
  primary lesson is host→verifier pinning).
- **`guarded` flavor only:** the hydra Gradle plugin is applied to this module's `guarded`
  flavor. Zero code change — native RASP runs at process startup. On a rooted/hooked/emulated
  device a CRITICAL finding terminates the verifier process.

## 7. Data contract — `pipe-serialization`

Typed messages serialized with the existing CBOR module:

```kotlin
@Serializable data class KycRequest(
    val reference: String,        // host's opaque correlation id
    val level: KycLevel,          // BASIC / ENHANCED (mock)
)

@Serializable data class KycResult(
    val reference: String,        // echoes the request
    val status: KycStatus,        // APPROVED / DECLINED / ERROR
    val issuedAtEpochMs: Long,    // wall-clock stamp; sample-app code may use System.currentTimeMillis()
)

@Serializable enum class KycLevel { BASIC, ENHANCED }
@Serializable enum class KycStatus { APPROVED, DECLINED, ERROR }
```

These types live in a tiny shared source set or are duplicated in both apps (decision for the
plan; a shared `:sample-kyc-contract` module is the clean option and will be evaluated during
planning). The codelab shows registering/using them through `pipe-serialization`.

## 8. Trust boundary mechanics (codelab step 5)

- Verifier is signed with its **own keystore** (the codelab provides exact `keytool` commands
  to generate it, and `apksigner`/`keytool -printcert` to extract the SHA-256 cert digest).
- Host pins that digest: `PipeAuthorizers.allowlist(VERIFIER_CERT_SHA256)`.
- The codelab explicitly contrasts this with the default `sameSigningKey` and explains *why*
  a cross-app embed needs an explicit allowlist — this is the security lesson, not a footnote.

## 9. Codelab content (claat, ~8 steps, ~30 min)

1. **Introduction** — the KYC scenario, the two-layer security model, what you'll build, a
   screenshot/gif of the finished result.
2. **Project setup** — add `uI-PiPe` + `pipe-serialization` deps; scaffold the two app modules.
3. **Build the verifier provider** — `PipeProviderService`, `onOpenPane`, the mock KYC pane UI.
4. **Build the bank host** — `PipeFullScreen.open`, session handling, rendering the result.
5. **Pin the trust boundary** — generate the verifier keystore, extract the cert digest, wire
   `allowlist`; contrast with `sameSigningKey`.
6. **Typed results** — `pipe-serialization` `KycRequest`/`KycResult`; run end-to-end on an
   emulator using the `dev` flavor and watch a verified embed succeed.
7. **Add RASP** — apply the hydra Gradle plugin to the `guarded` flavor (zero code); explain
   what it injects and that it's lethal-by-default.
8. **Trip it & recover** — install `guarded` on a real device (or show it on an emulator where
   RASP self-terminates on launch); watch the verifier die and the host handle `PEER_DIED`
   gracefully. Wrap-up + links.

Each step: goal, exact code/commands (no placeholders), and a "verify it" checkpoint.

## 10. App naming & copy

- Host label: **Meridian Bank** (fictional; not imitating a real institution).
- Verifier label: **VerifyID** (fictional).
- All identity data is fake/placeholder. No screen implies capture of a real user's PII.
- No real bank/verifier branding, logos, or domains.

## 11. Verification plan

- Both apps assemble (`dev` and `guarded` flavors) under the repo's JDK/Gradle.
- `dev` end-to-end verified on emulator-5554 (API 30) **and** Pixel 6 Pro (API 36): the bank
  opens the verifier pane, the mock flow runs, a `KycResult` returns and renders.
- `guarded` verified on the Pixel: RASP trips (emulator/root), verifier process terminates,
  host surfaces `PipeState.Closed` with the transport/peer-died cause and shows the graceful
  state.
- Cross-app authorizer verified: with the wrong/absent cert pin the host is denied
  (`PipeDeniedException`, `HOST_POLICY`); with the correct pin it is admitted.
- Codelab generated with `claat export`, spot-checked in a browser (nav, steps, code blocks
  render), and served from `docs/codelab/`.
- CI: add `:sample-kyc-host:assembleDebug` and `:sample-kyc-verifier:assembleDevDebug` to the
  existing workflow (the `guarded` flavor is device-only and excluded from CI assembly, or
  assembled without installing — decision for the plan).

## 12. Risks & mitigations

| Risk | Mitigation |
|---|---|
| hydra kills the happy-path flow on emulators | `dev`/`guarded` flavor split; walkthrough uses `dev` |
| Codelab drifts into "how to use hydra" instead of uI-PiPe | hydra confined to steps 7–8; uI-PiPe is the spine of steps 1–6 |
| Cert-pinning friction (keystore + digest) blocks readers | Codelab gives exact copy-paste `keytool`/`apksigner` commands and the expected output |
| `claat` toolchain in the repo | Only the generated static output is served; source markdown is the maintained artifact; document the one `claat export` command in CONTRIBUTING |
| GitHub Pages `/docs` conflicts with README media links | Codelab isolated under `docs/codelab/`; README links to `docs/media/` are untouched |
| hydra `guarded` flavor unbuildable in CI (Maven Central plugin resolution) | Assemble `guarded` locally/on device; keep CI to `dev` + host |

## 13. Open items for the implementation plan

- Shared contract types: dedicated `:sample-kyc-contract` module vs duplicated `@Serializable`
  classes in each app. (Lean: small shared module.)
- Exact CI matrix for the `guarded` flavor (assemble-only vs excluded).
- Whether the verifier also pins the host (bidirectional) or only host→verifier (primary lesson).
- Claat source location and the publish command wording for CONTRIBUTING.
