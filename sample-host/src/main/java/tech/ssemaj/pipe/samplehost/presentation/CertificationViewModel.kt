package tech.ssemaj.pipe.samplehost.presentation

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION
import tech.ssemaj.pipe.samplehost.di.AppContainer
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase

class CertificationViewModel(
    private val container: AppContainer = AppContainer(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var provider: ProviderComponent? = null
    private var authorizer: PipeAuthorizer? = null

    /** Called once from MainActivity with the provider target and the host's own gate policy. */
    fun configure(provider: ProviderComponent, authorizer: PipeAuthorizer) {
        this.provider = provider
        this.authorizer = authorizer
    }

    /** Launches the provider's full-screen certification pane over [activity] and drives the flow. */
    fun startCertification(activity: ComponentActivity) {
        val provider = provider ?: return
        val authorizer = authorizer ?: PipeAuthorizers.sameSigningKey(activity)
        _uiState.update { it.copy(phase = FlowPhase.WaitingForProvider) }
        PipeFullScreen.open(
            activity = activity,
            provider = provider,
            request = PipeRequest(ACTION_CERTIFICATION),
            authorizer = authorizer,
            // The provider's pane is a full-bleed scrim dialog — dim the host's bar strips to match.
            dimSystemBars = true,
            onSession = { session ->
                container.sessionRepository.set(session)
                viewModelScope.launch { runFlow(session) }
            },
            onError = { e -> _uiState.update { it.copy(phase = FlowPhase.PipeFailure(errorText(e))) } },
        )
    }

    private suspend fun runFlow(session: PipeSession) {
        when (val outcome = container.requestCertification(session, HOST_NAME)) {
            is RequestCertificationUseCase.Outcome.NeedsVerification -> {
                _uiState.update { it.copy(phase = FlowPhase.Verifying) }
                val result = container.verifyCertification(outcome.nonce, outcome.granted)
                _uiState.update { it.copy(phase = FlowPhase.Done(result)) }
            }
            is RequestCertificationUseCase.Outcome.Declined ->
                _uiState.update { it.copy(phase = FlowPhase.Declined(outcome.reason)) }
            RequestCertificationUseCase.Outcome.Timeout ->
                _uiState.update { it.copy(phase = FlowPhase.Timeout) }
        }
    }

    private fun errorText(e: PipeException): String = when (e) {
        is PipeDeniedException -> "denied: ${e.reason}"
        else -> "error: ${e::class.simpleName}"
    }

    override fun onCleared() {
        container.sessionRepository.close()
    }

    private companion object { const val HOST_NAME = "Pipe Sample Host" }
}
