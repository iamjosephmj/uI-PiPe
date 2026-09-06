package tech.ssemaj.pipe.core

import android.os.Bundle
import android.os.Parcelable
import kotlin.ConsistentCopyVisibility
import kotlinx.parcelize.Parcelize

/**
 * One message on the two-way channel between host and provider.
 *
 * The [payload] is an opaque [Bundle] — put whatever both sides agree on in it, or use
 * [PipeCodec][tech.ssemaj.pipe.serialization.PipeCodec] (`:pipe-serialization`) to carry typed,
 * CBOR-encoded payloads instead.
 *
 * Delivery is ordered and de-duplicated per direction, at-most-once. The [seq] used for that is
 * stamped by the sending side of the library; apps never set or read it.
 */
@ConsistentCopyVisibility
@Parcelize
data class PipeMessage internal constructor(
    val payload: Bundle,
    // library-owned; apps never set this. Internal so binary API hides it.
    internal val seq: Long = UNSET_SEQ,
) : Parcelable {
    /** Creates a message with the given [payload]. */
    constructor(payload: Bundle) : this(payload, UNSET_SEQ)

    companion object {
        /** Sequence value a message carries before the library stamps it. */
        const val UNSET_SEQ = -1L
    }

    internal fun withSeq(newSeq: Long) = copy(seq = newSeq)
}
