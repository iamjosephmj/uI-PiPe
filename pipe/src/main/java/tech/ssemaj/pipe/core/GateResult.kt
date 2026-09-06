package tech.ssemaj.pipe.core

import tech.ssemaj.pipe.auth.PeerIdentity

/**
 * Outcome of a gate (either side's): admitted with the verified peer, refused by policy, or failed.
 *
 * Internal vocabulary shared by the host gate, the provider gate, and the coordinator that maps
 * failures to the typed [PipeException] callers see — hence it lives in the shared `core` package,
 * not in either side's.
 */
internal sealed interface GateResult {
    /** The peer is verified and authorized; [peer] is the kernel/PackageManager-derived identity. */
    data class Admitted(val peer: PeerIdentity) : GateResult

    /** Policy said no; [reason] is the denying authorizer's reason. */
    data class Refused(val reason: String) : GateResult

    /** The open cannot proceed (availability, identity, or protocol failure); [message] is a [GateWire] marker. */
    data class Failed(val message: String) : GateResult
}
