package tech.ssemaj.pipe.auth

/** What a [PipeAuthorizer] decided about a verified peer. Sealed — exactly allow, or deny with a reason. */
sealed interface AuthDecision {
    /** The peer may open; the handshake proceeds. */
    data object Allow : AuthDecision

    /** The peer may not open; [reason] is surfaced to the other side inside the [PipeDeniedException][tech.ssemaj.pipe.core.PipeDeniedException]. */
    data class Deny(val reason: String) : AuthDecision
}
