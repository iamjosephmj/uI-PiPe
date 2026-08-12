# Pipe Presentation Modes — full-screen and dialog pipes

**Status:** implemented
**Date:** 2026-08-12
**Depends on:** pipe v2 phase 1 + certification demo. Extends the host-side surface; the provider API is unchanged.

## Goal

Add two presentation categories alongside the current embedded view-based pane: **full-screen** pipes (the pane fills a host-owned full-screen container) and **dialog** pipes (the pane sits in a host `Dialog` with a dimmed scrim). Same `SurfaceControlViewHost` embedding, same verified identity and coroutine session, same provider `PaneResult.Content` — only the host container and surface size differ. Each mode gets a polished sample-host demo.

## Non-goals

- No bottom-sheet mode, no custom enter/exit animations, no provider-driven size negotiation (the host sets the size).
- No change to the provider service contract: `onOpenPane` still returns `PaneResult.Content`.
- Not fixing embedded mode's one-gesture-per-session limitation ([[pipeview-single-gesture-per-session]]); the container modes avoid it by construction (see Touch model).

## Decisions (from brainstorming)

1. **Mechanism:** reuse SCVH embedding wrapped in host-owned containers. One security model, maximum reuse.
2. **Control:** the host chooses the mode at open time; the provider can *read* it (via `request.presentation`) to adapt layout but does not control it.
3. **API shape:** dedicated host types alongside `PipeView` — `PipeFullScreen` and `PipeDialog` — sharing the `PipeView.open()` / `PipeSession` core.

## Core change

New enum in `:pipe` core:

```kotlin
package tech.ssemaj.pipe.core

enum class PipePresentation { EMBEDDED, FULL_SCREEN, DIALOG }
```

`PipeRequest` gains an optional field (default keeps every existing caller source-compatible):

```kotlin
@Parcelize
data class PipeRequest(
    val action: String,
    val extras: Bundle = Bundle(),
    val presentation: PipePresentation = PipePresentation.EMBEDDED,
) : Parcelable
```

`presentation` already reaches the provider: `OpenSpec` carries the `PipeRequest`, and `PipeProviderService.onOpenPane(spec.request, ...)` hands it through. No `OpenSpec` change needed. The provider may branch on `request.presentation`; the demo provider does, for nicer layouts.

## Host API

### Full-screen — `PipeFullScreen`

```kotlin
object PipeFullScreen {
    /**
     * Adds a full-bleed PipeView to [activity]'s content root, opens [provider] in FULL_SCREEN mode,
     * and returns the live session. Closing the session (or [PipeSession.close]) removes the container.
     * Ties teardown to [activity]'s ON_DESTROY.
     */
    fun open(
        activity: ComponentActivity,
        provider: ProviderComponent,
        request: PipeRequest,
        authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
        onSession: (PipeSession) -> Unit = {},
        onError: (PipeException) -> Unit = {},
    ): Job
}
```

Internals: create a `PipeView`, add it at `MATCH_PARENT` to `android.R.id.content`, call `openIn(activity, provider, request.forcePresentation(FULL_SCREEN), authorizer, ...)`. On close/error, remove the view. Back press: the container intercepts it (a lightweight `OnBackPressedCallback`) to close the session and remove itself, so the pane behaves like a screen.

### Dialog — `PipeDialog`

```kotlin
class PipeDialog private constructor(...) {
    companion object {
        /**
         * Shows a dialog hosting a PipeView (DIALOG mode), dimmed scrim, sized to a fraction of the
         * screen. Dismiss (scrim/back) closes the session cleanly. Denial/timeout auto-dismiss and
         * report via [onError].
         */
        fun show(
            activity: ComponentActivity,
            provider: ProviderComponent,
            request: PipeRequest,
            authorizer: PipeAuthorizer = PipeAuthorizers.sameSigningKey(activity),
            onSession: (PipeSession) -> Unit = {},
            onError: (PipeException) -> Unit = {},
            onDismiss: () -> Unit = {},
        ): PipeDialog
    }
    fun dismiss()
}
```

Internals: a plain `android.app.Dialog` (no appcompat dependency in the core library) whose content is a rounded card containing a `PipeView` (width ≈ 90% screen, height ≈ 60%, adjustable). `open()` runs on the activity scope; a successful session calls `onSession`; failure dismisses and calls `onError`. `setOnDismissListener` closes the session (`HOST_CLOSED`) and calls `onDismiss`.

`forcePresentation(mode)` is a small internal `PipeRequest` copy helper so callers can pass any `request` and the container guarantees the right mode reaches the provider.

## Touch model (hard requirement)

Full-screen and dialog panes are the entire interactive surface — nothing of the host overlaps them — so they **must** support repeated interactive gestures (multi-button forms). Embedded mode's one-shot `transferTouchGesture`-per-`ACTION_DOWN` breaks after the first gesture; the container modes avoid it by letting the embedded window receive touch **directly and persistently** rather than via per-gesture transfer.

`PipeView` gains an internal input mode selected at open time by `request.presentation`:
- `EMBEDDED` → current behavior (host content may overlay; per-gesture `transferTouchGesture`).
- `FULL_SCREEN` / `DIALOG` → the embedded pane surface is the top input target (`SurfaceView.setZOrderOnTop(true)` or equivalent), the host `SurfaceView` does not intercept, so every gesture reaches the provider's views directly.

**Device-verified gate:** an instrumented multi-tap test on the Pixel 6 Pro (serial `19011FDEE0040L`) must show a full-screen and a dialog pane each accepting **two consecutive** button taps in one session. If `setZOrderOnTop(true)` conflicts with the dialog scrim / rounded corners, resolve it in implementation (e.g. z-order media overlay, or a transparent host window behind the surface) — the requirement (repeated gestures work) does not move.

## Error handling

Same `PipeException` surface (denied / timeout / transport / provider-died). Each container translates a failed `open()` into teardown + callback:
- Full-screen: remove the container view, call `onError`.
- Dialog: auto-dismiss, call `onError`.
- User dismiss (scrim/back) or activity destroy: close the session cleanly (`HOST_CLOSED`); no error.

## Sample apps (proper UI for all three modes)

`sample-host` MainActivity gains a mode chooser so the demo shows all three side by side. Reusing the certification pane and its single-gesture-safe reopen model where relevant:

- **Embedded** (existing): the card-framed pane in the main screen.
- **Full-screen**: a "Full-screen" button → `PipeFullScreen.open(...)`; the certification pane fills the screen with a top app bar ("Certification") and a close affordance. Provider reads `FULL_SCREEN` → roomier padding.
- **Dialog**: a "Dialog" button → `PipeDialog.show(...)`; the pane appears as a centered Material card with scrim. Provider reads `DIALOG` → compact layout.

`MultiPaneActivity` keeps its two embedded panes but gets a real Material look (labeled cards, status chips per pane) instead of the bare `TextView`s, so the multi-pane demo is presentable too.

The provider `DemoPaneService` branches layout on `request.presentation` (padding/typography) while keeping the same consent → attest → issue flow and the test-anchor strings (`pane-ready`, `Approve`, `Decline`, `Certification issued`).

## Testing

- **JVM unit**: `PipeRequest` default `presentation == EMBEDDED`; parcelize round-trip preserves it; `forcePresentation` overrides it.
- **Instrumented (Pixel 6 Pro)**:
  - `FullScreenE2eTest`: launch host, open full-screen, pane renders (`pane-ready`), **two** consecutive interactions work (request → Approve → issued, then a second request/consent tap), back/close tears down.
  - `DialogE2eTest`: open dialog, pane renders, multi-tap works, scrim/back dismiss closes the session (pane gone).
  - Existing suites (embedded PipeE2eTest, security, multi-pane, reopen) stay green.
- Gate: full connected suite green on `19011FDEE0040L`.

## Files

```
pipe/src/main/java/tech/ssemaj/pipe/
  core/PipePresentation.kt              (NEW)
  core/PipeRequest.kt                   (add presentation field)
  host/PipeView.kt                      (input mode by presentation)
  host/PipeFullScreen.kt                (NEW)
  host/PipeDialog.kt                    (NEW)
pipe/src/test/java/…/PipeRequestTest.kt (NEW)
sample-host/src/main/java/…/
  MainActivity.kt                       (mode chooser: Full-screen / Dialog buttons)
  MultiPaneActivity.kt                  (Material UI polish)
  presentation/ui/CertificationScreen.kt (mode buttons)
sample-provider/src/main/java/…/DemoPaneService.kt (layout by presentation)
sample-host/src/androidTest/java/…/
  FullScreenE2eTest.kt                  (NEW)
  DialogE2eTest.kt                      (NEW)
```

## API/BCV

`:pipe` runs binary-compatibility-validator. Adding an enum constructor param with a default to `PipeRequest` changes the public ABI (new synthetic constructor + field); `apiDump` must be regenerated and the new public types (`PipePresentation`, `PipeFullScreen`, `PipeDialog`) added to `pipe/api/pipe.api`.

`PipeFullScreen`/`PipeDialog` take `androidx.activity.ComponentActivity` (for `lifecycleScope` + `onBackPressedDispatcher`). This adds `androidx.activity:activity` as a public (`api`) dependency of `:pipe`; it is small and already transitively present in host apps. The plan adds the catalog entry and the `api(...)` line.
