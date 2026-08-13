package tech.ssemaj.pipe.transport

import android.os.IBinder
import android.os.Parcelable
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize
import tech.ssemaj.pipe.core.PipeRequest

/**
 * Everything the provider needs to add its full-screen pane over the host.
 *
 * [hostToken] is the host activity's window token (`View.getWindowToken()` of the decor view).
 * The provider adds a `TYPE_APPLICATION_PANEL` sub-window parented to that token, so the pane is a
 * real window in the host's window hierarchy — a first-class focus/IME/input target on every API
 * from 30 up, with no `@hide` APIs and no per-gesture touch forwarding.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Parcelize
data class OpenSpec(
    val hostToken: IBinder,
    val request: PipeRequest,
    val protocolVersion: Int,
) : Parcelable
