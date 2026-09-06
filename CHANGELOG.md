# Changelog

All notable changes to uI-PiPe are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/) — pre-1.0, so breaking changes are possible between alphas
(they will always be listed here).

## [Unreleased]

### Changed

- **`minSdk` lowered from 30 (Android 11) to 28 (Android 9/Pie).** The cross-process sub-window
  mechanism and the signing-auth stack run on public APIs available since API 28; the only API-30
  dependency (`WindowInsets.Type`) is branched in the pane root and the host's system-bar dim.
  Verified end-to-end on an API 28 emulator — the full instrumented certification suite (render,
  input into the pane, both-direction messaging, teardown, adversarial denials) plus the solo
  tiling demo — alongside the existing API 30 and API 36 verifications.

## [1.0.0-alpha03] — 2026-09-06

Focused on making the library ready for real integrators: the public API is now exactly the
integrator surface, failures are typed and actionable, the docs are integration-first, and the
samples got a full visual redesign.

### Changed (breaking, pre-1.0 API cleanup)

- **Public API is now exactly the integrator surface.** Implementation types that were public only
  for testing are now `internal` and removed from the ABI: `HostGate`, `ProviderGate`,
  `IdentityResolver`, `SigningSource`/`AndroidSigningSource` (renamed
  `PackageManagerSource`/`AndroidPackageManagerSource`), `OutboundSequencer`/`InboundSequencer`,
  `SameSigningKeyAuthorizer`/`AllowlistAuthorizer` — construct authorizers via `PipeAuthorizers`
  instead. `GateResult` moved from `tech.ssemaj.pipe.provider` to internal core vocabulary.
- **`anyOf` moved into `PipeAuthorizers`** (`PipeAuthorizers.anyOf(...)`) — one home for
  authorization construction. The old top-level function is gone.
- **`PipeFullScreen.open` exposes `timeout: Duration`** (default 10 s,
  `PipeFullScreen.DEFAULT_OPEN_TIMEOUT`) instead of a hardcoded value.
- Removed dead/vestigial API: `PipeError` (superseded by the `PipeException` hierarchy),
  `Pipe.PERMISSION_BIND_PANE` (never enforced), `PipeMessage.schemaVersion` (never read).

### Added

- **`PaneSpec.edgeToEdge`** — full-bleed panes: no automatic insets padding on the pane root, so a
  scrim covers the pane's whole window.
- **`PipeFullScreen.open(dimSystemBars = true)`** — dims the host's own status/navigation-bar
  strips while a pane is open. A pane is a sub-window of the host and the window manager constrains
  sub-windows to the parent's content frame, so a pane scrim can never cover the bar strips itself;
  the host (whose window *is* full-screen) dims them instead, matching the pane's 70%-black scrim.
- **Actionable provider-unavailability taxonomy.** The host gate now distinguishes
  `NOT_VISIBLE` (missing `<queries>` / not installed — previously a generic transport error) from
  `NO_SERVICE` (wrong component / not bindable) before binding, and a failed `bindService` maps to
  `NO_SERVICE` instead of `PipeTransportException`.
- Full KDoc across the public API (visible in the IDE and in the generated Dokka reference).
- Dokka API-reference generation (`./gradlew :dokkaGeneratePublicationHtml`).
- Integration documentation suite: `docs/integration/` — [host](docs/integration/host.md),
  [provider](docs/integration/provider.md), [messaging](docs/integration/messaging.md),
  [security](docs/integration/security.md), [troubleshooting](docs/integration/troubleshooting.md).
- Shared wire-error markers are centralized (`GateWire`, internal) — producers and the parser can
  no longer drift apart.

### Fixed

- Provider error strings no longer degrade to `"provider failed to open pane: null"` when
  `onOpenPane` throws a throwable without a message.
- The open intent is built from `Pipe.ACTION_OPEN_PANE` instead of a duplicate string literal.

### Removed

- The KYC codelab sample trio (`:sample-kyc-contract`, `:sample-kyc-host`, `:sample-kyc-verifier`),
  its codelab docs, and the hydra/RASP build plugin that existed only to guard it. The flagship
  `:sample-host` + `:sample-provider` pair (with the [integration guides](docs/integration/README.md))
  is the reference integration path.

## [1.0.0-alpha02] — 2026-08-14

First public alpha of the two-process pane architecture.

### Added

- `PipeFullScreen.open` host entry point with verified handshake, sessions, and typed failures.
- `PipeProviderService` provider base class with gating, `PaneSpec` window shaping, and
  per-host panes.
- Mutual signing-identity gates (`PipeAuthorizers`: `sameSigningKey`, `allowlist`, `anyOf`).
- Two-way sequenced message channel; `:pipe-serialization` typed CBOR messaging.
- `PipeDiscovery` provider discovery.
- Same-app N-process mode and multi-pane tiling (`:sample-solo`).
- Hardware key-attestation sample round-trip (`:sample-host` / `:sample-provider`) and
  adversarial test apps (`:evil-host`, `:evil-provider`).

[Unreleased]: https://github.com/iamjosephmj/uI-PiPe/compare/1.0.0-alpha03...HEAD
[1.0.0-alpha03]: https://github.com/iamjosephmj/uI-PiPe/compare/1.0.0-alpha02...1.0.0-alpha03
[1.0.0-alpha02]: https://github.com/iamjosephmj/uI-PiPe/releases/tag/1.0.0-alpha02
