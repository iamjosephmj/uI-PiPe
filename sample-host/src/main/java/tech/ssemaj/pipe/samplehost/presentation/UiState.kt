package tech.ssemaj.pipe.samplehost.presentation

import tech.ssemaj.pipe.samplehost.domain.CertificationResult

enum class PipeStatus { CONNECTING, CONNECTED, CLOSED }

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

data class UiState(
    val pipeStatus: PipeStatus = PipeStatus.CONNECTING,
    val phase: FlowPhase = FlowPhase.Idle,
    /** Incremented on Reopen; keys the AndroidView so a fresh PipeView is created. */
    val paneGeneration: Int = 0,
)
