package tech.ssemaj.pipe.core

/**
 * Base of every failure the library reports to app code. Sealed — the exact failure is always one
 * of the concrete types below, so a `when` over them is exhaustive. Never crosses the binder.
 */
sealed class PipeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Which side's policy refused an open. See [PipeDeniedException]. */
enum class DenialSource {
    /** The host's own [PipeAuthorizer][tech.ssemaj.pipe.auth.PipeAuthorizer] refused, before binding. */
    HOST_POLICY,

    /** The provider's gate or [onOpenPane][tech.ssemaj.pipe.provider.PipeProviderService.onOpenPane] refused. */
    PROVIDER_POLICY,
}

/**
 * The open was refused by policy, not by a failure: a gate looked at the verified peer and said no.
 *
 * [reason] is the denying side's human-readable reason (the deny reason of the
 * [PipeAuthorizer][tech.ssemaj.pipe.auth.PipeAuthorizer] that refused); [source] tells you which
 * side refused. A denied open produces no bind and no window on either side.
 */
class PipeDeniedException(val reason: String, val source: DenialSource) :
    PipeException("Denied by ${source.name}: $reason")

/**
 * The open did not complete within its timeout (default 10 s — see
 * [PipeFullScreen.open][tech.ssemaj.pipe.host.PipeFullScreen.open]). Covers the whole handshake:
 * bind, verification, and the provider's `onOpenPane`.
 */
class PipeTimeoutException(message: String = "Timed out opening pane") : PipeException(message)

/**
 * The provider cannot be opened at all — something about its presence or readability is wrong,
 * before any policy runs. [kind] distinguishes the cases an integrator needs to react to
 * differently:
 *
 * - [Unavailable.NOT_VISIBLE] — the provider's package cannot be seen by your app. On API 30+ this
 *   is almost always a missing `<queries>` declaration in your manifest; it also covers a provider
 *   that is genuinely not installed (the platform does not distinguish the two for your app).
 * - [Unavailable.NO_SERVICE] — the package is visible, but the service component you addressed is
 *   missing, not exported, or otherwise not bindable.
 * - [Unavailable.CERT_UNREADABLE] — the provider is installed and reachable but its signing
 *   certificates cannot be read, so its identity cannot be verified. Nothing opens.
 * - [Unavailable.NOT_INSTALLED] — informational: hosts that track installs themselves (e.g. via
 *   the Play Install Library) can report this precisely; from package visibility alone it is
 *   indistinguishable from [Unavailable.NOT_VISIBLE].
 */
class PipeProviderUnavailableException(val kind: Unavailable) : PipeException(kind.name) {
    /** Why the provider was unavailable. */
    enum class Unavailable { NOT_INSTALLED, NOT_VISIBLE, CERT_UNREADABLE, NO_SERVICE }
}

/**
 * The host and provider speak different protocol versions — e.g. one app embeds an older library.
 * The open never binds. [hostVersion]/[providerVersion] are the wire-protocol versions each side
 * reported; keeping both apps on the same library version fixes it.
 */
class PipeVersionMismatchException(val hostVersion: Int, val providerVersion: Int) :
    PipeException("Protocol mismatch: host=$hostVersion provider=$providerVersion")

/**
 * The binder channel itself failed (process death mid-handshake, bind failure, malformed
 * transaction). A transport failure always tears the open or session down; there is nothing to
 * retry on the same session.
 */
class PipeTransportException(message: String, cause: Throwable? = null) : PipeException(message, cause)
