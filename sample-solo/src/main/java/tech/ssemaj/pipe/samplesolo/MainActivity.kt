package tech.ssemaj.pipe.samplesolo

import android.app.Application
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import tech.ssemaj.pipe.core.PipeRequest
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
        info.text = "Host process: ${Application.getProcessName()}\nhost pid ${Process.myPid()}"

        findViewById<Button>(R.id.open).setOnClickListener {
            val extras = Bundle().apply {
                putInt("hostPid", Process.myPid())
                putString("hostProc", Application.getProcessName())
            }
            PipeFullScreen.open(
                activity = this,
                provider = ProviderComponent(packageName, "tech.ssemaj.pipe.samplesolo.PaneService"),
                request = PipeRequest("solo.demo", extras),
                onError = { e -> info.append("\nerror: ${e::class.simpleName}") },
            )
        }
    }
}
