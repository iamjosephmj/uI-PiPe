package tech.ssemaj.pipe.transport

import android.os.Parcelable
import android.window.InputTransferToken
import androidx.annotation.RestrictTo
import kotlinx.parcelize.Parcelize
import tech.ssemaj.pipe.core.PipeRequest

/** Everything the provider needs to build the embedded hierarchy. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Parcelize
data class OpenSpec(
    val inputTransferToken: InputTransferToken,
    val displayId: Int,
    val widthPx: Int,
    val heightPx: Int,
    val request: PipeRequest,
    val protocolVersion: Int,
) : Parcelable
