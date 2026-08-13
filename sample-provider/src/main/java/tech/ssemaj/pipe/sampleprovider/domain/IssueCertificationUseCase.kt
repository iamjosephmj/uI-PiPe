package tech.ssemaj.pipe.sampleprovider.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository

class IssueCertificationUseCase(private val keystore: KeystoreRepository) {
    suspend operator fun invoke(nonce: ByteArray): CertificationResponse.Granted =
        withContext(Dispatchers.IO) {
            val issued = keystore.signWithAttestedKey(nonce)
            CertificationResponse.Granted(
                signature = issued.signature,
                certChainDer = issued.certChainDer,
                securityLevel = if (issued.attested) SecurityLevel.HARDWARE else SecurityLevel.SOFTWARE,
            )
        }
}
