package tech.ssemaj.pipe.host

import android.content.ComponentName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeState

/**
 * Addresses one provider: its app package plus the fully-qualified class name of its
 * [PipeProviderService][tech.ssemaj.pipe.provider.PipeProviderService].
 *
 * ```kotlin
 * ProviderComponent("com.partner.app", "com.partner.app.PaneService")
 * ```
 *
 * For a provider inside your own app (another process), use `packageName` for both:
 * `ProviderComponent(packageName, "$packageName.PaneService")`.
 */
data class ProviderComponent(val packageName: String, val serviceClass: String) {
    /** The `ComponentName` this addresses; also the identity used for manifest `<queries>` entries. */
    fun toComponentName(): ComponentName = ComponentName(packageName, serviceClass)
}

/**
 * A live full-screen pane. Obtained from [PipeFullScreen.open]; valid until [close] or a
 * [PipeState.Closed] state. A session is a thin control surface — the pane itself is a window the
 * provider owns and renders in its own process.
 */
interface PipeSession {
    /** The provider's verified identity, captured before the pane was allowed to open. */
    val peer: PeerIdentity

    /** Connection state; ends in [PipeState.Closed] exactly once, whatever closes the pane. */
    val state: StateFlow<PipeState>

    /** Every message the provider sends on the channel, in delivery order. Hot; replay starts at collection. */
    val messages: Flow<PipeMessage>

    /**
     * Sends [message] to the provider. Returns `true` if the binder call was dispatched, `false`
     * if the session is closed or the peer is dead — a `false` is terminal for the session, not a
     * retryable send.
     */
    suspend fun send(message: PipeMessage): Boolean

    /** Closes the pane from the host side: the provider's window is torn down and the session ends. */
    fun close()
}
