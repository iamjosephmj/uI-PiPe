package tech.ssemaj.pipe.host

import android.content.ComponentName
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.CloseReason
import tech.ssemaj.pipe.core.PipeError
import tech.ssemaj.pipe.core.PipeMessage

data class ProviderComponent(val packageName: String, val serviceClass: String) {
    fun toComponentName(): ComponentName = ComponentName(packageName, serviceClass)
}

/** All callbacks arrive on the main thread. */
interface PipeHostCallbacks {
    fun onOpened(session: PipeSession) {}
    fun onMessage(message: PipeMessage) {}
    fun onResized(widthPx: Int, heightPx: Int) {}
    fun onDenied(reason: String) {}
    fun onError(error: PipeError) {}
    fun onClosed(reason: CloseReason) {}
}

interface PipeSession {
    fun send(message: PipeMessage)
    fun resize(widthPx: Int, heightPx: Int)
    fun close()
    val peer: PeerIdentity
}
