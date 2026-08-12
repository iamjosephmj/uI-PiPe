package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Launch payload the host sends to the provider; [action] selects the pane, [extras] parameterize
 *  it, [presentation] tells the provider how the host is showing the pane. */
@Parcelize
data class PipeRequest(
    val action: String,
    val extras: Bundle = Bundle(),
    val presentation: PipePresentation = PipePresentation.EMBEDDED,
) : Parcelable

/** Returns a copy pinned to [mode] (or the same instance when already in that mode). Library-internal:
 *  the host containers use it to guarantee the right mode reaches the provider regardless of caller. */
internal fun PipeRequest.forcePresentation(mode: PipePresentation): PipeRequest =
    if (presentation == mode) this else copy(presentation = mode)
