package tech.ssemaj.pipe.samplehost.di

import tech.ssemaj.pipe.samplehost.data.CertificationVerifier
import tech.ssemaj.pipe.samplehost.data.PipeSessionRepository
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase
import tech.ssemaj.pipe.samplehost.domain.VerifyCertificationUseCase

/** Hand-rolled DI: one instance per ViewModel. */
class AppContainer {
    val sessionRepository = PipeSessionRepository()
    val requestCertification = RequestCertificationUseCase()
    val verifyCertification = VerifyCertificationUseCase(CertificationVerifier())
}
