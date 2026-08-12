package tech.ssemaj.pipe.samplehost.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.samplehost.domain.CertificationResult
import tech.ssemaj.pipe.samplehost.presentation.FlowPhase
import tech.ssemaj.pipe.samplehost.presentation.PipeStatus
import tech.ssemaj.pipe.samplehost.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificationScreen(
    state: UiState,
    onPaneViewCreated: (PipeView) -> Unit,
    onRequest: () -> Unit,
    onReopen: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pipe Certification") },
                actions = {
                    StatusChip(state.pipeStatus)
                    TextButton(onClick = onReopen) { Text("Reopen") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                key(state.paneGeneration) {
                    AndroidView(
                        factory = { context -> PipeView(context).also(onPaneViewCreated) },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
            val inFlight = state.phase is FlowPhase.WaitingForProvider || state.phase is FlowPhase.Verifying
            val terminal = state.phase is FlowPhase.Done || state.phase is FlowPhase.Declined ||
                state.phase is FlowPhase.Timeout || state.phase is FlowPhase.PipeFailure
            // A pane accepts one interactive consent per session, so each certification runs in a
            // fresh session: after a result the primary action reopens before requesting again.
            Button(
                onClick = if (terminal) onReopen else onRequest,
                enabled = state.pipeStatus == PipeStatus.CONNECTED && !inFlight,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (terminal) "Certify again" else "Request certification") }
            PhaseCard(state.phase)
        }
    }
}

@Composable
private fun StatusChip(status: PipeStatus) {
    AssistChip(onClick = {}, label = {
        Text(
            when (status) {
                PipeStatus.CONNECTING -> "Connecting…"
                PipeStatus.CONNECTED -> "Connected"
                PipeStatus.CLOSED -> "Closed"
            }
        )
    })
}

@Composable
private fun PhaseCard(phase: FlowPhase) {
    when (phase) {
        FlowPhase.Idle -> {}
        FlowPhase.WaitingForProvider -> ProgressRow("Challenge sent — waiting for provider approval")
        FlowPhase.Verifying -> ProgressRow("Verifying certification")
        is FlowPhase.Done -> ResultCard(phase.result)
        is FlowPhase.Declined -> MessageCard("Provider declined: ${phase.reason}")
        FlowPhase.Timeout -> MessageCard("No response from provider")
        is FlowPhase.PipeFailure -> MessageCard(phase.text, isError = true)
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResultCard(result: CertificationResult) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !result.verified -> "Verification failed"
                    result.hardwareBacked -> "Hardware-verified"
                    else -> "Software-backed"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (result.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text("signature ${if (result.signatureOk) "ok" else "FAILED"} · " +
                "challenge ${result.challengeOk?.let { if (it) "ok" else "FAILED" } ?: "n/a"} · " +
                "chain ${if (result.chainOk) "ok" else "FAILED"}")
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide certificate chain" else "Show certificate chain")
            }
            if (expanded) {
                result.certificates.forEach { cert ->
                    Column {
                        Text(cert.subject, style = MaterialTheme.typography.bodyMedium)
                        Text("issuer: ${cert.issuer}", style = MaterialTheme.typography.bodySmall)
                        Text("sha256: ${cert.sha256.take(32)}…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
