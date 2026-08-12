package tech.ssemaj.pipe.samplehost.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION

/** Owns the live session; callers pass the PipeView at open time (never retained here). */
class PipeSessionRepository {
    private val _session = MutableStateFlow<PipeSession?>(null)
    val session: StateFlow<PipeSession?> = _session.asStateFlow()

    suspend fun open(view: PipeView, provider: ProviderComponent, authorizer: PipeAuthorizer): PipeSession {
        val s = view.open(provider, PipeRequest(ACTION_CERTIFICATION), authorizer)
        _session.value = s
        return s
    }

    fun close() {
        _session.value?.close()
        _session.value = null
    }
}
