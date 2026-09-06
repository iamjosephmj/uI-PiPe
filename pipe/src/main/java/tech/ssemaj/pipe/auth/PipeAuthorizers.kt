package tech.ssemaj.pipe.auth

import android.content.Context

/**
 * The built-in [PipeAuthorizer]s and the combinators over them — the single entry point for
 * constructing authorization policy.
 *
 * ```kotlin
 * PipeAuthorizers.sameSigningKey(context)                     // only your own signing key (default)
 * PipeAuthorizers.allowlist("aa11…", "bb22…")                 // specific partner signing certs
 * PipeAuthorizers.anyOf(sameSigningKey(ctx), allowlist(cert)) // either is enough
 * ```
 */
object PipeAuthorizers {
    /**
     * Allows peers whose signing lineage intersects this app's own signing certs — i.e. your other
     * apps, or your own app in another process. This is the default on both sides.
     */
    fun sameSigningKey(context: Context): PipeAuthorizer {
        val local = AndroidPackageManagerSource(context).certLineageSha256(context.packageName).toSet()
        return SameSigningKeyAuthorizer(local)
    }

    /**
     * Allows peers whose signing lineage intersects [certSha256] — a fixed set of partner signing
     * cert SHA-256 hashes (lowercase or uppercase hex). Get a partner's hash with
     * `apksigner verify --print-certs app.apk`. Rotating partners should list the whole lineage.
     */
    fun allowlist(vararg certSha256: String): PipeAuthorizer = AllowlistAuthorizer(certSha256.toSet())

    /**
     * Allows a peer that **any** of [authorizers] allows; denies with all their deny reasons
     * joined by `"; "` when none do. Evaluates in order and short-circuits on the first allow.
     */
    fun anyOf(vararg authorizers: PipeAuthorizer): PipeAuthorizer = PipeAuthorizer { peer, request ->
        val reasons = mutableListOf<String>()
        for (a in authorizers) when (val d = a.authorize(peer, request)) {
            is AuthDecision.Allow -> return@PipeAuthorizer AuthDecision.Allow
            is AuthDecision.Deny -> reasons += d.reason
        }
        AuthDecision.Deny(if (reasons.isEmpty()) "no authorizers configured" else reasons.joinToString("; "))
    }
}
