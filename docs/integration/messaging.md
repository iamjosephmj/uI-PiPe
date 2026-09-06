# Messaging guide

How the host and the provider talk: raw `PipeMessage`s, or typed `@Serializable` payloads over
`:pipe-serialization`. Both directions, one channel per pane.

- [Semantics — read this once](#semantics--read-this-once)
- [Raw messages](#raw-messages)
- [Typed messaging](#typed-messaging)
- [The shared contract module](#the-shared-contract-module)
- [Evolving a contract](#evolving-a-contract)
- [R8 / obfuscation](#r8--obfuscation)
- [Request–reply patterns](#requestreply-patterns)

## Semantics — read this once

Every message is one `oneway` binder transaction. What that buys you and what it costs:

- **Ordered** per direction, and **de-duplicated** (the library stamps and checks sequence
  numbers; regressed/duplicate deliveries are dropped).
- **At-most-once, gap-tolerant** — not gap-free. A send returning `false` is *terminal*
  (peer/session gone), never retryable on the same session. Gaps in `seq` can occur and are
  accepted by design.
- **Coarse-grained by intent.** Great for a pane plus dozens of messages (requests, results,
  progress, telemetry ticks). Not a high-frequency frame channel — that's not what a pane needs.

## Raw messages

`PipeMessage` wraps an opaque `Bundle` — both sides agree on keys:

```kotlin
// Host
session.send(PipeMessage(bundleOf("command" to "refresh")))

lifecycleScope.launch {
    session.messages.collect { m -> println(m.payload.getString("status")) }
}
```

```kotlin
// Provider — inside your PipeContent
override fun onMessage(m: PipeMessage) { render(m.payload.getString("command")) }
// …and to send back:
host.send(PipeMessage(bundleOf("status" to "ok")))
```

Bundles are flexible but stringly-typed — which is why the next section exists.

## Typed messaging

Add `:pipe-serialization` on both sides and messages become `@Serializable` types, CBOR-encoded
inside the same envelope:

```kotlin
// Host
session.send<CertificationResponse>(Granted(proof = byteArrayOf(...)))      // sealed supertype
session.messagesOf<CertificationRequest>().collect { req -> ... }           // Flow<CertificationRequest>

// Provider
host.send<CertificationRequest>(Request(nonce = nonce, hostName = "Meridian"))
```

(These exact extensions: `PipeSession.send<T>`, `PipeSession.messagesOf<T>`, `HostHandle.send<T>`.)

The codec is type-tagged: `messagesOf<T>()` silently skips messages that aren't `T` — so a
`Flow<T>` sees exactly one logical message type, and sealed hierarchies work as the "wire union".

## The shared contract module

Don't copy-paste DTOs between apps — make the wire contract its own tiny module both apps depend
on (this is `:sample-contract` in the repo):

```kotlin
// contract/src/main/kotlin/…/CertificationContract.kt — pure Kotlin, no Android
object CertificationContract {
    const val ACTION = "demo.certification"
}

@Serializable
data class CertificationRequest(val nonce: String, val hostDisplayName: String)

@Serializable
sealed class CertificationResponse {
    @Serializable @SerialName("granted") data class Granted(val certificateChain: List<ByteArray>) : CertificationResponse()
    @Serializable @SerialName("declined") data class Declined(val reason: String) : CertificationResponse()
}
```

Rules of thumb that keep contracts boring (in the best way):

- One sealed hierarchy per conversation; `@SerialName` on every case so CBOR tags are stable
  regardless of Kotlin name changes.
- The `action` string in `PipeRequest` selects the pane; the message types carry the conversation.
- Keep the contract module dependency-free — both sides and their tests link it trivially.

## Evolving a contract

- **Additive changes are safe**: new fields *with defaults*, new sealed cases. Old peers skip what
  they can't decode *by type* — but same-type shape changes are not tolerated by CBOR defaults
  alone, so treat non-additive changes (rename, retype, remove) as a **new message type** or a new
  `action`.
- Version the *contract module* alongside the library if you can — the simplest policy is "both
  sides upgrade together" (the library's `PipeVersionMismatchException` already enforces
  same-library-version on every open, and fails cleanly rather than half-working).

## R8 / obfuscation

`pipe-serialization` tags each message with `T::class.qualifiedName`. R8 renaming your contract
classes changes that tag, and cross-app messages stop matching — silently. Keep the names stable:

```proguard
# consumer-rules.pro of the CONTRACT module (or the app's proguard-rules.pro on both sides)
-keepnames @kotlinx.serialization.Serializable class your.contract.**
```

(Keeping names only — shrinking and the generated serializers are unaffected; kotlinx-serialization
ships its own rules for the serializer machinery.)

## Request–reply patterns

Two patterns cover most pane conversations:

**Await one reply (the consent/certification shape):**

```kotlin
val reply: CertificationResponse? = withTimeoutOrNull(60.seconds) {
    session.messagesOf<CertificationResponse>().firstOrNull()
}
```

`firstOrNull()` (not `first()`) is deliberate: if the pane closes without replying — user backed
out, provider crashed — the flow simply completes and you get `null`, which your UI already needs
to handle. `first()` would throw `NoSuchElementException` on that path. Race it with your own UX
timeout (`withTimeoutOrNull`), not with the library's open timeout (that one only bounds the
handshake).

**Continuous updates (the progress/session shape):** collect `messagesOf<T>()` for the pane's
lifetime and reduce it into your state holder; treat `session.state`
`→ Closed` as the end of the conversation.

---

Back to the **[integration index](README.md)**.
