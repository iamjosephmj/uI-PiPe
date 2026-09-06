package tech.ssemaj.pipe.host

import tech.ssemaj.pipe.core.GateWire
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeTransportException
import tech.ssemaj.pipe.core.PipeVersionMismatchException

/**
 * Maps a gate-failure wire message to the typed [PipeException] callers see.
 *
 * Both a host-side [GateResult.Failed][tech.ssemaj.pipe.core.GateResult.Failed] and a provider-side
 * `onError` string funnel through here; the markers themselves are produced and parsed only via
 * [GateWire], so the two sides cannot drift.
 */
internal fun String.toPipeException(): PipeException {
    GateWire.parseUnavailable(this)?.let { return PipeProviderUnavailableException(it) }
    GateWire.parseVersionMismatch(this)?.let { (host, provider) -> return PipeVersionMismatchException(host, provider) }
    return PipeTransportException(this)
}
