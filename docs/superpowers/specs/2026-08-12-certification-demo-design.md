# Certification Demo — sample-host / sample-provider redesign

**Status:** approved (design)
**Date:** 2026-08-12
**Depends on:** pipe v2 phase 1 (Tasks 1–12 complete). Supersedes plan Task 13's UI assumptions; Task 13's test coverage (multi-pane, close/reopen) is folded into this spec. Task 14 (README) follows this work and documents the new demo.

## Goal

Turn the bare-bones sample apps into a polished, architecturally exemplary demo: the host requests a **device certification** from the provider over the pipe. The provider renders a consent UI inside the host's window, performs real Android Keystore hardware key attestation, and returns a signed, attested response the host cryptographically verifies. The demo showcases every pipe v2 capability: coroutine sessions, bidirectional typed messaging (`:pipe-serialization`), cross-process UI + touch, denial handling, and teardown.

## Non-goals

- No DI framework (Hilt), no persistence layer, no multi-provider discovery UI.
- No hard requirement of hardware attestation: unknown attestation roots degrade the result badge, never hard-fail (keeps emulators/dev devices and e2e green).
- The evil modules keep their minimal UI; only their assertions' target strings must survive.

## Certification flow

Both demo apps remain debug-signed with the same key; the host opens the pane with `PipeAuthorizers.sameSigningKey`.

1. **Open** — Host embeds the provider pane (`PipeView` in Compose via `AndroidView`). Pane opens in Idle state showing the literal caption `pane-ready` (test anchor).
2. **Challenge** — User taps **Request certification**. Host generates a 32-byte `SecureRandom` nonce and sends `CertificationRequest(nonce, hostDisplayName)` over the pipe via the CBOR codec from `:pipe-serialization`.
3. **Consent** — Pane switches to consent: host display name, "requests a device certification", nonce fingerprint (first 8 hex bytes), **Approve** / **Decline** buttons.
4. **Attest + sign** (Approve) — Provider generates a fresh EC P-256 `AndroidKeyStore` key with `setAttestationChallenge(nonce)` (binding the host's nonce into the attestation extension), signs the nonce (`SHA256withECDSA`), replies `CertificationResponse.Granted(signature, certChainDer)`. Decline replies `CertificationResponse.Declined(reason)`. Key aliases are per-request and deleted after signing.
5. **Verify** — Host runs four independent checks:
   a. signature over nonce verifies against the leaf public key;
   b. attestation extension (OID `1.3.6.1.4.1.11129.2.1.17`) carries exactly our nonce as `attestationChallenge`;
   c. chain signatures verify leaf→root;
   d. root certificate matches a known Google hardware-attestation root (public keys embedded as PEM constants in `CertificationVerifier`) → **Hardware-verified**, otherwise **Software-backed**.
   Each check failure is reported individually in the UI state.
6. **Result** — Host renders the result card (badge, security level, truncated nonce hex, expandable per-cert chain details: subject CN + SHA-256 fingerprint).

**Timeout:** host waits max 15 s for the provider's response → "No response" state with retry.

**Attestation fallback:** if key generation with attestation throws (unsupported device), the provider retries without `setAttestationChallenge`, marks the response `securityLevel = SOFTWARE`, and the host badge degrades; check (b)/(d) are reported as not-applicable rather than failed.

## Modules

### New `:sample-contract` (Android library)

`@Serializable` message types shared by both apps — demonstrates the contract-module pattern for typed pipe messaging:

```kotlin
@Serializable class CertificationRequest(val nonce: ByteArray, val hostDisplayName: String)

@Serializable sealed interface CertificationResponse {
    @Serializable class Granted(
        val signature: ByteArray,
        val certChainDer: List<ByteArray>,
        val securityLevel: SecurityLevel, // provider's claim; host verifies independently
    ) : CertificationResponse
    @Serializable class Declined(val reason: String) : CertificationResponse
}

@Serializable enum class SecurityLevel { HARDWARE, SOFTWARE }
```

Depends on `kotlinx-serialization` only (plus `:pipe-serialization` usage stays in the apps).

### sample-host (Compose + Material 3)

```
presentation/
  CertificationViewModel   // single UiState; phases Idle → WaitingForProvider → Verifying → Result/Failed
  ui/                      // Compose screens, theme (dynamic color, edge-to-edge)
domain/
  RequestCertificationUseCase  // nonce gen + send + await typed response (15s timeout)
  VerifyCertificationUseCase   // checks a–d; pure JVM, unit-testable
data/
  PipeSessionRepository    // wraps PipeView.open/session/state/messages as flows
  CertificationVerifier    // X.509 parsing, attestation-extension ASN.1, chain + root validation
di/AppContainer            // manual wiring
```

UI: top bar "Pipe Certification" + pipe-state chip (`Connecting…/Connected/Closed` from `session.state`); provider pane in a rounded elevated card; **Request certification** button; step progress (Challenge sent → Provider approved → Verifying → done); result card; error card preserving the literal prefix `denied: ` for security tests; **Reopen** app-bar action (close + open — drives the reopen e2e). A plain `MultiPaneActivity` hosts two `PipeView`s on the same provider for the multi-pane e2e.

### sample-provider (Views pane, Material 3 themed)

```
pane/PanePresenter         // consent state machine: Idle → Consent(request) → Issued/Declined; plain class (no androidx ViewModel in the service), unit-testable
domain/IssueCertificationUseCase
data/KeystoreRepository    // generateAttestedKey(alias, challenge), sign, deleteAlias; attestation fallback
```

Pane states: Idle (`pane-ready` caption), Consent (host name + nonce fingerprint + Approve/Decline), Issued ("Certification issued"), Declined.

## Error handling

Typed UI states, no toasts: pipe denial (`PipeDeniedException` → `denied: <reason>` card), provider death (`PipeState.Closed` cause → "Provider disconnected"), decline (neutral card), per-check verification failure, provider Keystore failure (fallback path above), response timeout (retry).

## Test-anchor strings (must survive)

| String | Used by |
|---|---|
| `pane-ready` | e2e + `EvilHostDeniedTest` + `SuspendAuthorizerDenialTest` |
| `denied: ` prefix (incl. `denied: async-policy`) | `SecurityE2eTest`, `EvilHostDeniedTest`, `SuspendAuthorizerDenialTest` |
| `EVIL-PANE` | `SecurityE2eTest` |

## Testing

- **JVM unit**: `VerifyCertificationUseCase` on fixture chains (happy / wrong challenge / broken chain / unknown root); `PanePresenter` transitions; contract CBOR round-trip.
- **Instrumented (ported)**: `PipeE2eTest` becomes the certification flow — pane renders, consent tap crosses the pipe (touch), both message directions (request over host→provider, response provider→host), teardown on activity close. The old IME assertion is dropped in favor of the typed-message assertions.
- **Instrumented (new, folds in old-plan Task 13)**: `MultiPaneE2eTest` (two panes, one provider, independent round-trips), `ReopenE2eTest` (Reopen action, then a full certification round-trip proving sequencer reset + surface re-attach + token re-acquisition).
- Gate: full connected suite green on Pixel 6 Pro (19011FDEE0040L).

## Dependencies added

- Compose BOM + Material 3 + activity-compose + lifecycle-viewmodel-compose (sample-host)
- kotlinx-serialization plugin/runtime (`:sample-contract`, both apps), `:pipe-serialization` (both apps)
- Material Components (sample-provider views theme)
