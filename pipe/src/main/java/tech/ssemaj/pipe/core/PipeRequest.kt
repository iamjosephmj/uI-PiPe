package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Launch payload the host sends to the provider; [action] selects the pane, [extras] parameterize it. */
@Parcelize
data class PipeRequest(val action: String, val extras: Bundle = Bundle()) : Parcelable
