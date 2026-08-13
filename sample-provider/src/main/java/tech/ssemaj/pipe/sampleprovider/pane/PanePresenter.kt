package tech.ssemaj.pipe.sampleprovider.pane

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.SecurityLevel

/** Pure per-pane state machine; the service wires it to views and the issue use case. */
class PanePresenter {
    sealed interface State {
        data object Idle : State
        data class Consent(val hostName: String, val nonceFingerprint: String, val nonce: ByteArray) : State
        data class Issued(val level: SecurityLevel) : State
        data class Declined(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onRequest(request: CertificationRequest) {
        val fingerprint = request.nonce.take(8).joinToString("") { "%02x".format(it) }
        _state.value = State.Consent(request.hostDisplayName, fingerprint, request.nonce)
    }

    fun onIssued(level: SecurityLevel) {
        _state.value = State.Issued(level)
    }

    fun onDeclined(reason: String) {
        _state.value = State.Declined(reason)
    }
}
