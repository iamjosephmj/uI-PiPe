# Security Policy

uI-PiPe is a **security boundary** — its purpose is to let one app render another app's UI across the process boundary *only* after a mutual, cryptographic identity check. Bugs in that boundary matter, so please report them responsibly.

## Reporting a vulnerability

**Do not open a public issue for a security vulnerability.**

Report it privately via GitHub's **[Report a vulnerability](https://github.com/iamjosephmj/uI-PiPe/security/advisories/new)** (Security → Advisories → *Report a vulnerability*), or by contacting the maintainer through the email on their [GitHub profile](https://github.com/iamjosephmj).

Please include:
- affected version / commit,
- a clear description and, ideally, a minimal reproduction (a small adversarial host/provider, like the `evil-*` sample apps),
- the impact you believe it has (e.g. a provider rendering without being verified, a host driving a session it wasn't admitted to, identity spoofing).

You'll get an acknowledgement as soon as reasonably possible. This is a solo-maintained alpha, so fixes are best-effort — but boundary-breaking reports are taken seriously and prioritized. Please give a reasonable window to fix before any public disclosure, and let me know if you'd like credit.

## In scope

The things that make the boundary trustworthy:
- **Identity & gating** — `Binder.getCallingUid()` / signing-cert derivation, `HostGate` / `ProviderGate`, `PipeAuthorizers`, the fail-closed authorization path.
- **Per-call UID re-checks** on the live session (`ActivePane`, host channel stubs).
- **The window handoff** — the host window token, the `TYPE_APPLICATION_PANEL` pane, teardown.
- **The transport** — anything that lets an unverified peer render, or lets a message/close be spoofed.

## Out of scope (by design)

These are documented trade-offs, not vulnerabilities (see [ARCHITECTURE.md §12](ARCHITECTURE.md)):
- The provider owns a **full-screen (transparent by default) window over the host** — a larger on-screen surface than an embedded pane. This is made safe by verifying the provider's signing identity before the window token is handed over; it is intended for a **closed app family / vetted partners**, not an open marketplace of arbitrary providers.
- A **legitimately verified** provider behaving badly within its own pane (the trust anchor is signing identity — same key or an allowlist — not runtime sandboxing of provider code).
- OEM- or ROM-specific window-policy differences (worth reporting as compatibility issues, but not treated as vulnerabilities in the core model).
- Devices below API 30, where the mechanism does not exist (open fails with a clean `PipeTransportException`).

## Supported versions

Alpha (`1.0.0-alpha0x`). Only the latest release/`master` is supported for security fixes.
