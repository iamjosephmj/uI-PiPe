package tech.ssemaj.pipe.kychost

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.kyc.KycContract
import tech.ssemaj.pipe.samples.kyc.KycRequest
import tech.ssemaj.pipe.samples.kyc.KycLevel
import tech.ssemaj.pipe.samples.kyc.KycResult
import tech.ssemaj.pipe.serialization.messagesOf
import tech.ssemaj.pipe.serialization.send

class MainActivity : AppCompatActivity() {

    private companion object {
        const val VERIFIER_PKG = "tech.ssemaj.pipe.kycverifier"
        const val VERIFIER_SVC = "tech.ssemaj.pipe.kycverifier.KycVerifierService"
        // Filled in Task 4 with VerifyID's real signing-cert SHA-256 (lowercase hex, no colons).
        const val VERIFIER_CERT_SHA256 = "21027f81c7dacf5c09246d1eb6e61a4ea797ef5a8e198e74721be8739cd3e706"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val status = findViewById<TextView>(R.id.status)

        findViewById<Button>(R.id.verify).setOnClickListener {
            status.text = "Opening VerifyID…"
            val reference = "MB-" + System.currentTimeMillis()
            val extras = Bundle().apply {
                putString("reference", reference)
                putString("bankName", "Meridian Bank")
                putString("level", KycLevel.ENHANCED.name)
            }
            PipeFullScreen.open(
                activity = this,
                provider = ProviderComponent(VERIFIER_PKG, VERIFIER_SVC),
                request = PipeRequest(KycContract.ACTION_KYC, extras),
                authorizer = PipeAuthorizers.allowlist(VERIFIER_CERT_SHA256),
                onSession = { session ->
                    lifecycleScope.launch {
                        session.send(KycRequest(reference, KycLevel.ENHANCED))
                        val result = session.messagesOf<KycResult>().firstOrNull()
                        if (result != null) {
                            status.text = "Verification ${result.status} (ref ${result.reference})"
                        }
                        // else: session closed before a result (e.g. RASP killed the verifier);
                        // the state-close collector below shows the failure message.
                    }
                    // Graceful teardown: a non-null Closed cause (e.g. RASP killed the verifier) is
                    // an unexpected failure, not a normal close.
                    lifecycleScope.launch {
                        val closed = session.state.first { it is PipeState.Closed } as PipeState.Closed
                        if (closed.cause != null) {
                            status.text = "Verification unavailable: runtime integrity check failed"
                        }
                    }
                },
                onError = { e ->
                    status.text = when (e) {
                        is PipeDeniedException -> "Verifier rejected: ${e.reason}"
                        else -> "Verification unavailable (${e::class.simpleName})"
                    }
                },
            )
        }
    }
}
