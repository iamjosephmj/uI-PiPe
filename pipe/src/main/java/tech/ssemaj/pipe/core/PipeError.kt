package tech.ssemaj.pipe.core

/** Local-side failure report; never crosses the binder. */
data class PipeError(val code: Code, val message: String) {
    enum class Code { PROVIDER_NOT_FOUND, CERT_UNREADABLE, VERSION_MISMATCH, TIMEOUT, TRANSPORT_FAILURE }
}
