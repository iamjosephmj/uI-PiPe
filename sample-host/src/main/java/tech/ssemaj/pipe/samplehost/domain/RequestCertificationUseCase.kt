package tech.ssemaj.pipe.samplehost.domain

import java.security.SecureRandom
import kotlinx.coroutines.flow.firstOrNull
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
        // firstOrNull (not first): if the session closes before any response — e.g. the user
        // dismisses a dialog/full-screen pane by tapping the scrim or pressing back — the messages
        // flow completes empty. first() would throw NoSuchElementException synchronously on the main
        // thread during teardown and crash the app; firstOrNull yields null → a clean Timeout.
        val response = withTimeoutOrNull(RESPONSE_TIMEOUT_MS.milliseconds) {
            session.messagesOf<CertificationResponse>().firstOrNull()
        } ?: return Outcome.Timeout
        return when (response) {
            is CertificationResponse.Granted -> Outcome.NeedsVerification(nonce, response)
            is CertificationResponse.Declined -> Outcome.Declined(response.reason)
        }
    }

    // The wait spans a human reading the consent prompt in the pane and tapping Approve, so it
    // must be generous — 15s regularly expired before a person (or a second pane) could respond.
    companion object { const val RESPONSE_TIMEOUT_MS = 60_000L }
}
