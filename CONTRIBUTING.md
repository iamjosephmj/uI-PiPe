# Contributing to uI-PiPe

Thanks for your interest! uI-PiPe is a small, security-sensitive library, so contributions are held to a high bar for correctness and clarity. This guide covers how to build, test, and submit changes.

By contributing, you agree that your contributions are licensed under the project's [Apache-2.0 License](LICENSE).

## Ground rules

- **It's a security boundary.** The whole point is a verified cross-process boundary. Anything touching identity, gating, UID checks, or the transport is reviewed carefully — explain *why* a change is safe, not just what it does.
- **Public API is contracted.** `:pipe`'s public surface is tracked by the binary-compatibility-validator in `pipe/api/pipe.api`. CI fails if it drifts (see below).
- **Match the surrounding code.** Small, focused files; KDoc on public declarations explaining the non-obvious *why*; no unrelated refactoring in a feature PR.

## Prerequisites

- **JDK 17** (CI uses Temurin 17; 17+ works locally).
- **Android SDK** with the platform for `compileSdk` (currently 36). `minSdk` is **30**.
- A device or emulator on **API 28+** for the instrumented tests. The supported range is 28–36;
  the suite is verified on an API 28 emulator, an API 30 emulator, and a Pixel 6 Pro (API 36).

Everything runs through the Gradle wrapper (`./gradlew`) — no local Gradle needed.

## Build & test

```bash
# Fast feedback: binary-compat check + unit tests (what CI runs)
./gradlew :pipe:apiCheck \
          :pipe:testDebugUnitTest \
          :pipe-serialization:testDebugUnitTest \
          :sample-host:testDebugUnitTest \
          :sample-provider:testDebugUnitTest

# Build everything
./gradlew assembleDebug
```

### Instrumented / connected tests (need a device)

These are the real end-to-end proof — cross-process render, the certification round-trip, and the adversarial denial paths. Install all four apps first, then run:

```bash
./gradlew :sample-provider:installDebug :sample-host:installDebug \
          :evil-provider:installDebug :evil-host:installDebug
./gradlew :sample-host:connectedDebugAndroidTest \
          :evil-host:connectedDebugAndroidTest \
          :sample-provider:connectedDebugAndroidTest
```

Please run these on at least one device before submitting changes to the transport, the pane window, or the gates.

### Changing the public API

If you add, remove, or change anything public in `:pipe`, regenerate the API dump and commit it:

```bash
./gradlew :pipe:apiDump
git add pipe/api/pipe.api
```

CI runs `:pipe:apiCheck` and will fail if `pipe.api` doesn't match the code. Also add a
[CHANGELOG.md](CHANGELOG.md) entry under **Unreleased** — breaking changes must be listed even
pre-1.0.

### Docs

- Integrator-facing docs live in `docs/integration/` (guides).
- KDoc on public declarations is the API reference — explain the non-obvious *why*, not the
  signature. Generate and browse it with:

```bash
./gradlew :dokkaGeneratePublicationHtml   # → build/dokka/html/index.html
```

## Project layout

```
:pipe                 The library: host API, provider API, transport (AIDL), identity, channels.
:pipe-serialization   Optional typed (CBOR) messaging over the raw PipeMessage envelope.
:sample-contract      Shared @Serializable message contract for the samples.
:sample-host / :sample-provider   The demo: a verified hardware-attestation consent flow.
:sample-solo          One app, N processes: in-app isolation + three-pane tiling demo.
:evil-host / :evil-provider       Differently-signed adversarial apps that assert denial in both directions.
```

Deep dive: **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Tests we expect

- **Unit tests** for pure logic (identity mapping, sequencers, gates, use cases).
- **Instrumented tests** for anything that renders or crosses the process boundary.
- **Adversarial coverage.** Security properties are asserted by the `evil-*` apps (different signing key), not just claimed — if you touch the trust model, extend them.

Known gotchas you'll hit:
- **Robolectric** (4.16) tops out at **API 35** — pin Robolectric unit tests with `@Config(sdk = [35])` (compileSdk 36 has no SDK jar).
- **Compose in a pane:** framework components that spawn their own window (`Dialog`, `ModalBottomSheet`, `Popup`) throw `BadTokenException` inside the Service-owned pane — draw dialogs/sheets **inline** in the transparent full-screen canvas instead.

## Pull requests

1. Branch from `master`.
2. Keep the change focused; include tests.
3. Make sure **CI is green** (`apiCheck` + unit tests) and, for transport/pane/gate changes, that the connected suite passes on a device.
4. Describe the change and — for anything security-relevant — why it's safe.

## Reporting security issues

Please do **not** open a public issue for a vulnerability in the trust/verification model. Report it privately to the maintainer (see the GitHub profile) so it can be assessed and fixed before disclosure.
