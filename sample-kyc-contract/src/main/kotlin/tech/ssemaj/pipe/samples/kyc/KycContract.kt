package tech.ssemaj.pipe.samples.kyc

import kotlinx.serialization.Serializable

/** Shared wire contract for the KYC codelab. Pure-JVM so both the host and verifier apps can depend on it. */
object KycContract {
    /** Request action the bank host opens the verifier with. */
    const val ACTION_KYC = "pipe.demo.kyc"
}

/** Depth of the identity check the bank is asking for (mock). */
@Serializable
enum class KycLevel { BASIC, ENHANCED }

/** Outcome the verifier reports back to the bank (mock). */
@Serializable
enum class KycStatus { APPROVED, DECLINED, ERROR }

/** Bank → verifier: start a verification for an opaque [reference] at the requested [level]. */
@Serializable
data class KycRequest(val reference: String, val level: KycLevel)

/** Verifier → bank: the result for [reference]. [issuedAtEpochMs] is a wall-clock stamp set by the verifier. */
@Serializable
data class KycResult(val reference: String, val status: KycStatus, val issuedAtEpochMs: Long)
