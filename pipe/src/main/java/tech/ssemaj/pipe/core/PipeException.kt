package tech.ssemaj.pipe.core

/** Base of all Pipe failures delivered to callers. Never crosses the binder. */
sealed class PipeException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class DenialSource { HOST_POLICY, PROVIDER_POLICY }

class PipeDeniedException(val reason: String, val source: DenialSource) :
    PipeException("Denied by ${source.name}: $reason")

class PipeTimeoutException(message: String = "Timed out opening pane") : PipeException(message)

class PipeProviderUnavailableException(val kind: Unavailable) : PipeException(kind.name) {
    enum class Unavailable { NOT_INSTALLED, NOT_VISIBLE, CERT_UNREADABLE, NO_SERVICE }
}

class PipeVersionMismatchException(val hostVersion: Int, val providerVersion: Int) :
    PipeException("Protocol mismatch: host=$hostVersion provider=$providerVersion")

class PipeTransportException(message: String, cause: Throwable? = null) : PipeException(message, cause)
