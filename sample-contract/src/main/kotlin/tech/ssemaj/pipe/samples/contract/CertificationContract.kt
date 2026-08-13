package tech.ssemaj.pipe.samples.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pane action the host requests and the provider's manifest/service handles. */
const val ACTION_CERTIFICATION = "pipe.demo.certification"

/** Host → provider: certify possession of a device key by signing [nonce]. */
@Serializable
class CertificationRequest(val nonce: ByteArray, val hostDisplayName: String)

/** Provider → host. Always send/collect as the sealed supertype (see PipeCodec type matching). */
@Serializable
sealed interface CertificationResponse {
    @Serializable
    @SerialName("granted")
    class Granted(
        val signature: ByteArray,
        val certChainDer: List<ByteArray>,
        /** Provider's claim only — the host verifies independently from the chain. */
        val securityLevel: SecurityLevel,
    ) : CertificationResponse

    @Serializable
    @SerialName("declined")
    class Declined(val reason: String) : CertificationResponse
}

@Serializable
enum class SecurityLevel { HARDWARE, SOFTWARE }
