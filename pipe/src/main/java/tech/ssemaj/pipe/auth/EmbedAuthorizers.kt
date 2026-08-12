package tech.ssemaj.pipe.auth

import android.content.Context

/** Convenience factories for the built-in authorizers. */
object EmbedAuthorizers {
    /** Allows peers signed with (any cert in the lineage of) this app's own signing key. */
    fun sameSigningKey(context: Context): EmbedAuthorizer {
        val local = AndroidSigningSource(context).certLineageSha256(context.packageName).toSet()
        return SameSigningKeyAuthorizer(local)
    }

    fun allowlist(vararg certSha256: String): EmbedAuthorizer = AllowlistAuthorizer(certSha256.toSet())
}
