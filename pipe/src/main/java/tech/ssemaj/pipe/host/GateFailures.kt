package tech.ssemaj.pipe.host

import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeProviderUnavailableException
import tech.ssemaj.pipe.core.PipeTransportException
import tech.ssemaj.pipe.core.PipeVersionMismatchException

/**
 * Maps a gate-failure message to the typed [PipeException] callers see.
 *
 * Both a host-side [GateResult.Failed][tech.ssemaj.pipe.provider.GateResult.Failed] and a
 * provider-side `onError` string funnel through here, so the wording of these markers is part of
 * the wire contract between the two sides.
 */
internal fun String.toPipeException(): PipeException = when {
    this == "unavailable:CERT_UNREADABLE" ->
        PipeProviderUnavailableException(PipeProviderUnavailableException.Unavailable.CERT_UNREADABLE)
    this == "unavailable:NOT_VISIBLE" ->
        PipeProviderUnavailableException(PipeProviderUnavailableException.Unavailable.NOT_VISIBLE)
    startsWith("protocol") -> {
        val host = Regex("host=(\\d+)").find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val provider = Regex("provider=(\\d+)").find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        PipeVersionMismatchException(host, provider)
    }
    else -> PipeTransportException(this)
}
