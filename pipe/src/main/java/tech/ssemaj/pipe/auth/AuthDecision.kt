package tech.ssemaj.pipe.auth

sealed interface AuthDecision {
    data object Allow : AuthDecision
    data class Deny(val reason: String) : AuthDecision
}
