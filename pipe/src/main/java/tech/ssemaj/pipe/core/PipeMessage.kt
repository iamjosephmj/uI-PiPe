package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One message on the two-way channel. [seq] is stamped by the sending side of the
 * library; apps leave it as [UNSET_SEQ].
 */
@Parcelize
data class PipeMessage(
    val payload: Bundle,
    val schemaVersion: Int = 1,
    val seq: Long = UNSET_SEQ,
) : Parcelable {
    companion object { const val UNSET_SEQ = -1L }
}
