package tech.ssemaj.pipe.samplehost.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samplehost.data.CertificationVerifier

class VerifyCertificationUseCase(private val verifier: CertificationVerifier) {
    suspend operator fun invoke(nonce: ByteArray, granted: CertificationResponse.Granted): CertificationResult =
        withContext(Dispatchers.Default) {
            verifier.verify(nonce, granted.signature, granted.certChainDer)
        }
}
