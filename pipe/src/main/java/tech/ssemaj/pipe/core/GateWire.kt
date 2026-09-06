package tech.ssemaj.pipe.core

/**
 * The error markers gates pass as strings across the `onError(String)` wire callback.
 *
 * These strings are part of the wire contract between the two sides of the library — this object is
 * their single source of truth, used by both producers (the gates) and the consumer
 * ([toPipeException][tech.ssemaj.pipe.host.toPipeException]) so the two can never drift apart.
 * The wording is stable protocol surface: never change an existing marker, only add new ones.
 */
internal object GateWire {
    private const val PREFIX_UNAVAILABLE = "unavailable:"
    private const val PREFIX_VERSION_MISMATCH = "protocol version mismatch"

    /** Marker for a provider that cannot be opened at all, e.g. `unavailable:NO_SERVICE`. */
    fun unavailable(kind: PipeProviderUnavailableException.Unavailable): String =
        PREFIX_UNAVAILABLE + kind.name

    /** True if [message] is an [unavailable] marker, and which kind it names. */
    fun parseUnavailable(message: String): PipeProviderUnavailableException.Unavailable? {
        if (!message.startsWith(PREFIX_UNAVAILABLE)) return null
        val name = message.removePrefix(PREFIX_UNAVAILABLE)
        return PipeProviderUnavailableException.Unavailable.entries.firstOrNull { it.name == name }
    }

    /** Marker for a host/provider protocol-version mismatch. */
    fun versionMismatch(hostVersion: Int, providerVersion: Int): String =
        "$PREFIX_VERSION_MISMATCH: host=$hostVersion provider=$providerVersion"

    /** Extracts the host/provider versions from a [versionMismatch] marker, or null. */
    fun parseVersionMismatch(message: String): Pair<Int, Int>? {
        if (!message.startsWith(PREFIX_VERSION_MISMATCH)) return null
        val host = Regex("host=(\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val provider = Regex("provider=(\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return host to provider
    }
}
