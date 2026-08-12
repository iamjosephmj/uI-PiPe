package tech.ssemaj.pipe.samplehost.domain

import java.security.SecureRandom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.serialization.messagesOf
import tech.ssemaj.pipe.serialization.send
import kotlin.time.Duration.Companion.milliseconds

class RequestCertificationUseCase(
    private val nonceSource: () -> ByteArray = { ByteArray(32).also { SecureRandom().nextBytes(it) } },
) {
    sealed interface Outcome {
        data class NeedsVerification(val nonce: ByteArray, val granted: CertificationResponse.Granted) : Outcome
        data class Declined(val reason: String) : Outcome
        data object Timeout : Outcome
    }

    suspend operator fun invoke(session: PipeSession, hostDisplayName: String): Outcome {
        val nonce = nonceSource()
        session.send(CertificationRequest(nonce, hostDisplayName))
        val response = withTimeoutOrNull(RESPONSE_TIMEOUT_MS.milliseconds) {
            session.messagesOf<CertificationResponse>().first()
        } ?: return Outcome.Timeout
        return when (response) {
            is CertificationResponse.Granted -> Outcome.NeedsVerification(nonce, response)
            is CertificationResponse.Declined -> Outcome.Declined(response.reason)
        }
    }

    companion object { const val RESPONSE_TIMEOUT_MS = 15_000L }
}
