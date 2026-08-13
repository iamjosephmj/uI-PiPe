package tech.ssemaj.pipe.samplehost.presentation

import tech.ssemaj.pipe.samplehost.domain.CertificationResult

sealed interface FlowPhase {
    data object Idle : FlowPhase
    data object WaitingForProvider : FlowPhase
    data object Verifying : FlowPhase
    data class Done(val result: CertificationResult) : FlowPhase
    data class Declined(val reason: String) : FlowPhase
    data object Timeout : FlowPhase
    /** Pipe-level failure; [text] is user-visible and keeps the `denied: ` prefix for tests. */
    data class PipeFailure(val text: String) : FlowPhase
}

data class UiState(val phase: FlowPhase = FlowPhase.Idle) {
    /** A certification is mid-flight: the full-screen pane is up or the host is verifying. */
    val inProgress: Boolean get() = phase is FlowPhase.WaitingForProvider || phase is FlowPhase.Verifying
}
