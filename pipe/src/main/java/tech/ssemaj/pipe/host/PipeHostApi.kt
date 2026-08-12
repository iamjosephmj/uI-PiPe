package tech.ssemaj.pipe.host

import android.content.ComponentName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeSize
import tech.ssemaj.pipe.core.PipeState

data class ProviderComponent(val packageName: String, val serviceClass: String) {
    fun toComponentName(): ComponentName = ComponentName(packageName, serviceClass)
}

/** A live embedded pane. Obtained from [PipeView.open]; valid until [close] or a Closed state. */
interface PipeSession {
    val peer: PeerIdentity
    val state: StateFlow<PipeState>
    val messages: Flow<PipeMessage>
    suspend fun send(message: PipeMessage): Boolean
    suspend fun resize(size: PipeSize)
    fun close()
}
