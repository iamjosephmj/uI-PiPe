package tech.ssemaj.pipe.kycverifier

import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeProviderService
import tech.ssemaj.pipe.samples.kyc.KycResult
import tech.ssemaj.pipe.samples.kyc.KycStatus
import tech.ssemaj.pipe.serialization.send

/**
 * VerifyID's KYC provider. Renders a mock verification wizard in the bank's window and returns a
 * typed [KycResult]. In the `guarded` build (Task 5) hydra RASP self-terminates this process on a
 * compromised runtime, before any of this runs.
 */
class KycVerifierService : PipeProviderService() {

    // Host→verifier pinning is the codelab's lesson: the BANK pins US. We accept the bank as-is.
    // In production you would pin the bank's cert here too (symmetric); see the codelab callout.
    override fun authorizer(): PipeAuthorizer = PipeAuthorizer { _, _ -> AuthDecision.Allow }

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        // The bank's opaque correlation id, echoed back in the result.
        val reference = request.extras.getString("reference") ?: "unknown"
        val bankName = request.extras.getString("bankName") ?: "the bank"

        val content = KycPaneView(
            ctx = this,
            bankName = bankName,
            onDecision = { status ->
                paneScope.launch {
                    host.send(KycResult(reference, status, System.currentTimeMillis()))
                    host.close()
                }
            },
            onClose = {},
        )
        return PaneResult.Content(content)
    }
}
