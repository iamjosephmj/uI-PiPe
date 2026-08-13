package tech.ssemaj.pipe.host

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeState

class PipeSessionContractTest {
    private val fake = object : PipeSession {
        override val peer = PeerIdentity(10001, listOf("com.p"), listOf("aa11"))
        override val state = MutableStateFlow<PipeState>(PipeState.Open(peer))
        override val messages = emptyFlow<PipeMessage>()
        override suspend fun send(message: PipeMessage) = true
        override fun close() {}
    }
    @Test fun exposesPeerAndState() = runTest {
        assertEquals(10001, fake.peer.uid)
        assertEquals(PipeState.Open(fake.peer), fake.state.value)
        assertEquals(true, fake.send(PipeMessage(android.os.Bundle())))
    }
}
