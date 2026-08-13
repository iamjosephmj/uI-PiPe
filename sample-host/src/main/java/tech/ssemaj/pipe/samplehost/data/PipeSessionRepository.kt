package tech.ssemaj.pipe.samplehost.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.ssemaj.pipe.host.PipeSession

/** Holds the current full-screen pane session so the ViewModel can close it on teardown. */
class PipeSessionRepository {
    private val _session = MutableStateFlow<PipeSession?>(null)
    val session: StateFlow<PipeSession?> = _session.asStateFlow()

    fun set(session: PipeSession) {
        _session.value = session
    }

    fun close() {
        _session.value?.close()
        _session.value = null
    }
}
