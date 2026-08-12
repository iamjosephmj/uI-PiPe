package tech.ssemaj.pipe.samplehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samplehost.di.AppContainer
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase

class CertificationViewModel(
    private val container: AppContainer = AppContainer(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var stateWatcher: Job? = null

    /** Called from the AndroidView factory each time a pane view is (re)created. */
    fun onPaneViewReady(view: PipeView, provider: ProviderComponent, authorizer: PipeAuthorizer) {
        _uiState.update { it.copy(pipeStatus = PipeStatus.CONNECTING, phase = FlowPhase.Idle) }
        viewModelScope.launch {
            try {
                val session = container.sessionRepository.open(view, provider, authorizer)
                _uiState.update { it.copy(pipeStatus = PipeStatus.CONNECTED) }
                stateWatcher?.cancel()
                stateWatcher = launch {
                    session.state.collect { s ->
                        if (s is PipeState.Closed) {
                            _uiState.update { it.copy(pipeStatus = PipeStatus.CLOSED) }
                            if (s.cause != null) {
                                _uiState.update { it.copy(phase = FlowPhase.PipeFailure("Provider disconnected")) }
                            }
                        }
                    }
                }
            } catch (e: PipeDeniedException) {
                _uiState.update {
                    it.copy(pipeStatus = PipeStatus.CLOSED, phase = FlowPhase.PipeFailure("denied: ${e.reason}"))
                }
            } catch (e: PipeException) {
                _uiState.update {
                    it.copy(pipeStatus = PipeStatus.CLOSED, phase = FlowPhase.PipeFailure("error: ${e::class.simpleName}"))
                }
            }
        }
    }

    fun requestCertification(hostDisplayName: String) {
        val session = container.sessionRepository.session.value ?: return
        _uiState.update { it.copy(phase = FlowPhase.WaitingForProvider) }
        viewModelScope.launch {
            when (val outcome = container.requestCertification(session, hostDisplayName)) {
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
    }

    fun reopen() {
        stateWatcher?.cancel()
        container.sessionRepository.close()
        _uiState.update {
            it.copy(pipeStatus = PipeStatus.CONNECTING, phase = FlowPhase.Idle, paneGeneration = it.paneGeneration + 1)
        }
    }

    override fun onCleared() {
        container.sessionRepository.close()
    }
}
