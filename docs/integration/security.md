# Security & trust

The trust model an integrator needs to reason about — what's verified, by whom, and the decisions
that remain yours. Deep internals (wire format, UID re-checks, teardown races) live in
[ARCHITECTURE.md §7 & §12](../../ARCHITECTURE.md).

- [The handshake](#the-handshake)
- [What "verified peer" means](#what-verified-peer-means)
- [Choosing host policy](#choosing-host-policy)
- [Choosing provider policy](#choosing-provider-policy)
- [The asymmetric trap](#the-asymmetric-trap)
- [Key attestation on top](#key-attestation-on-top)
- [Honest limits](#honest-limits)

## The handshake

Every open is a **mutual, cryptographically-gated handshake**. Neither side trusts the other's
self-description:

```
Host                                          Provider
  │ resolve provider identity                   │
  │ (PackageManager signing certs)              │
  │ run MY authorizer ── deny ⇒ stop, no bind   │
  │ bind ─────────────────────────────────────► │ read calling UID from binder
  │                                             │ resolve UID → identity (kernel truth)
  │                                             │ run MY authorizer ── deny ⇒ no window
  │ ◄──────────────── pane window renders ───── │
  │ every callback re-checks the caller's UID   │
```

Key properties:

- **Identity is kernel/PackageManager-derived on both ends** — the provider reads the caller's
  UID from the binder transaction itself; the host resolves the exact package it's about to bind.
  Neither side's identity can be spoofed by intent extras or self-reported names.
- **Fail-closed everywhere.** A denial — either side, at any point — produces no bind and no
  window. There is no half-open state to clean up.
- **Callbacks are UID-gated after the handshake too** — a message that arrives on a channel is
  checked against the UID that opened it.
- **Shared-UID apps are handled conservatively**: if a UID maps to multiple packages and any of
  them has unreadable certs, the identity resolution fails closed.

## What "verified peer" means

`PeerIdentity(uid, packages, signingCertSha256)` — the other side's kernel UID, its package(s),
and the **full SHA-256 lineage of its signing certificates** (all certs, oldest→newest — so key
rotation is visible to your policy, not a surprise).

Your authorizer sees exactly this and nothing self-reported.

## Choosing host policy

You're handing a provider your window token — the right to draw full-screen over your UI and
receive input. That's the asset your policy protects.

| Policy | Use when |
|---|---|
| `PipeAuthorizers.sameSigningKey(context)` *(default)* | Your own app family / your own second process. You'd be granting your own key. |
| `PipeAuthorizers.allowlist(certA, certB)` | Closed set of vetted partners. **Pin the cert, not the package name** — package names are trivially squattable; signing certs are not. |
| `PipeAuthorizers.anyOf(a, b)` | Composite rules, e.g. "our key OR the partner's". |
| Custom `PipeAuthorizer` | Anything dynamic: consent prompts, backend revocation lists, per-request pricing. It's `suspend` — call what you need. |

Getting a cert hash: `apksigner verify --print-certs app.apk`. If a partner rotates keys, ask for
the whole lineage and allowlist it.

## Choosing provider policy

Your asset is your screen *inside someone else's app* — brand, and whatever data your pane shows.

| Policy | Use when |
|---|---|
| `sameSigningKey` *(default)* | Your pane only opens for your own app family. |
| `allowlist(theirCert)` | You serve one/some known host(s) — the symmetric, recommended setup. |
| Allow-all + per-request checks | Open ecosystems: gate on `host.peer` *inside* `onOpenPane` (rate-limit, feature-flag, price). Know what you give up — see below. |

## The asymmetric trap

A provider of a render-only pane may reasonably ship an allow-all authorizer — **the host pins
the provider's cert, the provider doesn't pin the host's.** When is that sound?

- The *provider's* asset (its UI) is only ever rendered *inside the host's window* — a random
  malicious app that binds gets… the provider's UI shown *to the malicious app's own user*.
  Low value to an attacker, so pinning the host is often unnecessary *for rendering alone*.
- The moment the provider **sends something valuable back** — keys, PII, signed attestations,
  API proxies — that reasoning collapses: any bound app becomes a data source to exfiltrate from.
  Then the provider must pin (`allowlist`) or gate per-request on `host.peer`.

Rule of thumb: **the side with the more valuable asset pins harder.** Render-only panes can be
lax; data-carrying panes must not be.

## Key attestation on top

When "the verified app" isn't enough and you need "this specific device key, hardware-backed":
run an attestation round-trip *over the channel* —

1. Host generates a nonce, sends it as part of `PipeRequest.extras` (or a message).
2. Provider generates an AndroidKeyStore key, signs the nonce, returns the cert chain.
3. Host verifies the chain to a Google hardware root **and** the nonce — binding the response to
   this session (anti-replay) and to hardware (anti-emulation).

`:sample-host` + `:sample-provider` implement exactly this (including the DER parsing and root
pinning). The library verifies *who*; attestation verifies *what hardware they hold*.

## Honest limits

- The trust anchor is **signing identity** — a closed app family / vetted partners, not an open
  marketplace. There is no runtime-permission model for panes.
- A transparent pane leaves host content visible while capturing input over its bounds — the
  threat this creates is *provider→host*, and it's exactly why the host gate exists. Tiling
  multiplies the surfaces; restrict it to signing-verified families.
- The provider process, once bound, holds a real window over the host until it closes cooperatively
  or the host unbinds (BACK/lifecycle/`session.close()` all work host-side regardless).
- OEM-specific window/LMK policies are outside the library's control (see
  [troubleshooting](troubleshooting.md#faq--gotchas)); behavior is verified on stock Android
  API 30–36.
- `PERMISSION`-based binding was cut from the protocol in favor of the two-gate model; don't add
  custom signature-permissions to the service expecting the library to enforce them — put that
  logic in your authorizers.

---

Back to the **[integration index](README.md)**.
