package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Launch payload the host sends to the provider. [action] selects the pane the provider builds;
 * [extras] parameterize it.
 *
 * Pipe presents every pane as a single full-screen sub-window drawn over the host activity, so
 * there is no presentation mode to choose — the provider always renders full-screen.
 */
@Parcelize
data class PipeRequest(
    val action: String,
    val extras: Bundle = Bundle(),
) : Parcelable
