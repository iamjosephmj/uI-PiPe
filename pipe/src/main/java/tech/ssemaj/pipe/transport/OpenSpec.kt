package tech.ssemaj.pipe.transport

import android.os.IBinder
import android.os.Parcelable
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize
import tech.ssemaj.pipe.core.PipeRequest

/**
 * Everything the provider needs to build the embedded hierarchy.
 *
 * The host token is carried in one of two pre-35-safe forms so this class can load on API 30–34
 * (where `android.window.InputTransferToken` does not exist):
 *  - [hostToken]: the host's public window token (`View.getWindowToken()`) on API 30–34, which
 *    satisfies `SurfaceControlViewHost`'s host-token requirement so the pane renders. Touch is
 *    then delivered by forwarding (`IEmbedSession.dispatchInput`) — public APIs only, no `@hide`.
 *  - [inputToken]: the API-35 `android.window.InputTransferToken`, held as a generic [Parcelable]
 *    (never referenced by its concrete type here) so pre-35 devices never resolve the class.
 * Exactly one is non-null, selected by the host's API level.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Parcelize
data class OpenSpec(
    val hostToken: IBinder?,
    val inputToken: Parcelable?,
    val displayId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val request: PipeRequest,
    val protocolVersion: Int,
) : Parcelable
