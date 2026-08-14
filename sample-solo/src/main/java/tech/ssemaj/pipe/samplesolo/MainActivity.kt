package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeBindImportance
import tech.ssemaj.pipe.host.PipeFullScreen
import tech.ssemaj.pipe.host.ProviderComponent

/**
 * Host side of the "same app, two processes" demo. This Activity runs in the app's **main** process
 * and opens a pane against [PaneService], which the manifest pins to a separate `:pane` process.
 *
 * Nothing about the library changes for this: the provider is just this app's own component, and the
 * default `sameSigningKey` gate is trivially satisfied (same app, same signing key). The pane renders
 * from a different process with its own heap, main thread, and crash domain — seamlessly, in this
 * Activity's window.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val info = findViewById<TextView>(R.id.host_info)
        info.text = "Host process: ${Application.getProcessName()}\n" +
            "host pid ${Process.myPid()} · UI thread “${Thread.currentThread().name}” tid ${Process.myTid()}"

        findViewById<Button>(R.id.open).setOnClickListener {
            val extras = Bundle().apply {
                putInt("hostPid", Process.myPid())
                putString("hostProc", Application.getProcessName())
                putInt("hostTid", Process.myTid())
            }
            PipeFullScreen.open(
                activity = this,
                provider = ProviderComponent(packageName, "tech.ssemaj.pipe.samplesolo.PaneService"),
                request = PipeRequest("solo.demo", extras),
                onError = { e -> info.append("\nerror: ${e::class.simpleName}") },
            )
        }

        // Experiment: open three panes at once, each from its own process → host + 3 = 4 processes,
        // three provider windows composed in one host window.
        findViewById<Button>(R.id.open_multi).setOnClickListener {
            listOf("PaneServiceA", "PaneServiceB", "PaneServiceC").forEach { svc ->
                PipeFullScreen.open(
                    activity = this,
                    provider = ProviderComponent(packageName, "tech.ssemaj.pipe.samplesolo.$svc"),
                    request = PipeRequest("multi"),
                    // Same app, fully trusted panes: let each pane process inherit the host's top
                    // priority so all four processes sit at oom_score_adj 0 while the panes are up.
                    bindImportance = PipeBindImportance.IMPORTANT,
                    onError = { e -> info.append("\n$svc: ${e::class.simpleName}") },
                )
            }
        }
    }
}
