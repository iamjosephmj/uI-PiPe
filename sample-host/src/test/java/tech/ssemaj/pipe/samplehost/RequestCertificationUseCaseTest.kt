package tech.ssemaj.pipe.samplehost

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase
import tech.ssemaj.pipe.serialization.PipeCodec

private class FakeSession : PipeSession {
    val sent = mutableListOf<PipeMessage>()
    val incoming = Channel<PipeMessage>(Channel.UNLIMITED)
    override val peer = PeerIdentity(uid = 10_001, packages = listOf("fake"), signingCertSha256 = emptyList())
    override val state: StateFlow<PipeState> = MutableStateFlow<PipeState>(PipeState.Open(peer))
    override val messages: Flow<PipeMessage> = incoming.receiveAsFlow()
    override suspend fun send(message: PipeMessage): Boolean { sent.add(message); return true }
    override fun close() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.16 tops out at API 35; compileSdk 36 has no SDK jar
class RequestCertificationUseCaseTest {

    private val useCase = RequestCertificationUseCase(nonceSource = { ByteArray(32) { 7 } })

    @Test fun sendsRequestAndReturnsGrantedForVerification() = runTest {
        val session = FakeSession()
        launch {
            // Echo the provider: wait for the request, reply Granted.
            while (session.sent.isEmpty()) kotlinx.coroutines.yield()
            val request = PipeCodec.decodeOrNull<CertificationRequest>(session.sent.first())!!
            assertEquals("Test Host", request.hostDisplayName)
            val reply: CertificationResponse = CertificationResponse.Granted(
                signature = byteArrayOf(9), certChainDer = listOf(byteArrayOf(1)),
                securityLevel = SecurityLevel.HARDWARE,
            )
            session.incoming.send(PipeCodec.encode(reply))
        }
        val outcome = useCase(session, hostDisplayName = "Test Host")
        val needs = outcome as RequestCertificationUseCase.Outcome.NeedsVerification
        assertArrayEquals(ByteArray(32) { 7 }, needs.nonce)
        assertArrayEquals(byteArrayOf(9), needs.granted.signature)
    }

    @Test fun declinedPassesReasonThrough() = runTest {
        val session = FakeSession()
        launch {
            while (session.sent.isEmpty()) kotlinx.coroutines.yield()
            val reply: CertificationResponse = CertificationResponse.Declined("nope")
            session.incoming.send(PipeCodec.encode(reply))
        }
        val outcome = useCase(session, "Test Host")
        assertEquals("nope", (outcome as RequestCertificationUseCase.Outcome.Declined).reason)
    }

    @Test fun timesOutWhenProviderSilent() = runTest {
        val outcome = useCase(FakeSession(), "Test Host") // virtual time: timeout fires instantly under runTest
        assertTrue(outcome is RequestCertificationUseCase.Outcome.Timeout)
    }
}
