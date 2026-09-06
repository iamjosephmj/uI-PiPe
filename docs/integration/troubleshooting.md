# Troubleshooting

Something failed at runtime. Start from the error you saw, then scan the FAQ.

- [Error reference](#error-reference)
- [`NOT_VISIBLE` — provider invisible](#not_visible--provider-invisible)
- [FAQ / gotchas](#faq--gotchas)
- [Nothing renders — checklist](#nothing-renders--checklist)

## Error reference

Every failure is a sealed `PipeException` (delivered to `PipeFullScreen.open`'s `onError`, or as
`PipeState.Closed(cause)` for a live session). Exhaustive `when`s are compiler-checked.

| Exception | What it means | What to do |
|---|---|---|
| `PipeDeniedException` (`.reason`, `.source`) | A policy refused the open. `source` says which side: `HOST_POLICY` = your authorizer; `PROVIDER_POLICY` = their gate or their `PaneResult.Reject`. | `HOST_POLICY`: fix your authorizer/allowlist. `PROVIDER_POLICY`: the `.reason` string is theirs — surface or log it. No bind happened on either side. |
| `PipeProviderUnavailableException` `.kind = NOT_VISIBLE` | Your app can't see the provider package — missing `<queries>`, or the provider genuinely isn't installed (indistinguishable, by design of package visibility). | See [below](#not_visible--provider-invisible). |
| `PipeProviderUnavailableException` `.kind = NO_SERVICE` | Package visible, but the component you addressed isn't bindable: wrong service class string, service missing from the provider's manifest, or not exported. | Diff `ProviderComponent(packageName, serviceClass)` against the provider's manifest. Fully-qualified class name, no abbreviations. |
| `PipeProviderUnavailableException` `.kind = CERT_UNREADABLE` | Provider installed and reachable, but its signing certs can't be read — identity can't be verified, so nothing opens. | Rare/ambient (PackageManager failure). Retry; if persistent, capture `adb bugreport`. |
| `PipeProviderUnavailableException` `.kind = NOT_INSTALLED` | Informational: only hosts with their own install tracking (e.g. Play Install Library) can distinguish this from `NOT_VISIBLE`. | If you track installs, use your source of truth for the "install CTA" UX; otherwise treat like `NOT_VISIBLE`. |
| `PipeTimeoutException` | The whole handshake (bind + verification + provider's `onOpenPane`) exceeded the timeout — default 10 s. | If the provider legitimately does slow work before showing UI (keygen, network), raise `timeout = …` in `open()`. If not, capture a trace — someone's gate/`onOpenPane` is hanging. |
| `PipeVersionMismatchException` (`.hostVersion`, `.providerVersion`) | The two apps embed different library versions with incompatible wire protocols. | Align library versions across both apps; the open fails cleanly, never half-renders. |
| `PipeTransportException` | Binder-level failure: provider process died, service disconnected, malformed open. | For a live pane this is terminal (`Closed(cause)`) — re-open if your UX wants to. Pre-open, check that the provider isn't crashing (`adb logcat`) — their `onOpenPane` throwing surfaces here as `"provider failed to open pane: …"`. |

## `NOT_VISIBLE` — provider invisible

Android 11+ package visibility: your app cannot see packages it hasn't declared, and "invisible"
and "not installed" look identical from your process. (On Android 9/10 there is no visibility
filtering — this failure mode doesn't exist there.) The fix is one manifest declaration, *before*
`<application>`:

```xml
<!-- Tightest: one known partner -->
<queries>
    <package android:name="com.partner.app" />
</queries>

<!-- Or: every app that declares a Pipe provider (pairs with PipeDiscovery) -->
<queries>
    <intent>
        <action android:name="tech.ssemaj.pipe.action.OPEN_PANE" />
    </intent>
</queries>
```

Verify what your app can see:

```bash
adb shell dumpsys package your.app.id | grep -i -A8 queries   # your app's visibility declarations
adb shell pm list packages | grep partner                      # is it installed at all?
```

Notes:

- Same-app providers (own second process) never need `<queries>` — your own package is always
  visible to you.
- Instrumented tests: a test app binding a provider in *another* test app needs the `<queries>`
  declaration too — put it in the test app's manifest (`src/androidTest/AndroidManifest.xml` if it
  isn't merged automatically).

## FAQ / gotchas

**The provider's `onOpenPane` throws — what does the host see?**
`PipeTransportException("provider failed to open pane: …")`. Return `PaneResult.Reject` for
controlled refusals instead — that maps to `PipeDeniedException` with your reason attached.

**Robolectric unit tests fail with weird SDK errors.**
Pin the SDK: `@Config(sdk = [35])` (Robolectric's latest full-support level at this library's
test setup). The sample apps' test suites show the working configuration.

**Compose inside the pane crashes with `BadTokenException` / missing `ViewTreeLifecycleOwner`.**
The pane root *is* a lifecycle/saved-state/view-model owner, but if you insert intermediate views
that reset those, Compose can't resolve them. Attach `ComposeView` directly under the pane content
(or keep the root's owners intact). `ModalBottomSheet`/dialogs needing activity windows don't work
in panes — draw the sheet inside the pane ([provider guide §6](provider.md#6-use-compose-inside-the-pane)).

**R8 obfuscation broke typed messaging.**
`pipe-serialization` type-tags messages with `T::class.qualifiedName`; obfuscation renames classes
and the tags stop matching across apps. Keep contract names:
`-keepnames @kotlinx.serialization.Serializable class your.contract.**` — details in the
[messaging guide](messaging.md#r8--obfuscation).

**Timeouts on cold start / first open.**
First bind pays process start + classload. 10 s covers it in practice; if you ship heavy
initialization in the provider's `Application`, either trim it or raise `timeout`.

**Does it work on emulators? OEM X?**
The library uses public window/binder APIs only; verified end-to-end on API 30 (emulator) and API
36 (Pixel). OEM window-policy divergences (custom RAM/kill policies, aggressive background limits)
can affect any cross-process binding — the mitigation on the host side is `bindImportance =
IMPORTANT` for same-app panes, and testing on your target OEMs.

**`send()` returned `false`. Retry?**
No — `false` means the session/peer is gone; it's terminal. Open a new session if the UX calls
for it. (Channel semantics: ordered, de-duplicated, at-most-once — see the
[messaging guide](messaging.md#semantics--read-this-once).)

**The pane closed by itself.**
`session.state` → `PipeState.Closed(cause)`: `null` cause = clean close (either side, or BACK);
non-null = failure (`PEER_DIED` surfaces as `PipeTransportException`). The provider's mirror is
`PipeContent.onClosed(reason)`.

## Nothing renders — checklist

1. `onError` fired? Start from the [error reference](#error-reference).
2. `<queries>` declared for the provider package? ([`NOT_VISIBLE`](#not_visible--provider-invisible))
3. Service exported + intent-filter with `tech.ssemaj.pipe.action.OPEN_PANE` in the provider?
4. `ProviderComponent` exactly matches the provider's manifest (`packageName` + fully-qualified
   service class)?
5. Both sides on the same library version?
6. Pane transparent by default — maybe it *is* rendering, just blank: check that provider content
   actually draws (opaque test: `PaneSpec(translucent = false)`).
7. `adb logcat | grep -i pipe` — the library logs transport-level failures.

---

Back to the **[integration index](README.md)**.
