package tech.ssemaj.pipe.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.auth.AndroidSigningSource
import tech.ssemaj.pipe.core.Pipe
import tech.ssemaj.pipe.host.ProviderComponent

/** A provider resolved by intent action, with its component, label, and signing cert lineage. */
data class ProviderDescriptor(
    val component: ProviderComponent,
    val packageName: String,
    val label: CharSequence?,
    val certSha256: List<String>,
)

/** A service resolved by [android.content.pm.PackageManager], before cert enrichment. */
internal data class ResolvedService(
    val packageName: String,
    val serviceClass: String,
    val label: CharSequence?,
)

/** Pure mapping from resolved services to [ProviderDescriptor]s; no PackageManager/Context needed. */
internal fun buildDescriptors(
    raw: List<ResolvedService>,
    certOf: (String) -> List<String>,
): List<ProviderDescriptor> = raw.map { service ->
    ProviderDescriptor(
        component = ProviderComponent(service.packageName, service.serviceClass),
        packageName = service.packageName,
        label = service.label,
        certSha256 = certOf(service.packageName),
    )
}

/** Resolves installed Pipe providers by intent action, so hosts don't hardcode provider components. */
object PipeDiscovery {
    suspend fun query(context: Context, action: String = Pipe.ACTION_OPEN_PANE): List<ProviderDescriptor> =
        withContext(Dispatchers.IO) {
            val pm = context.applicationContext.packageManager
            val signingSource = AndroidSigningSource(context)
            val raw = pm.queryIntentServices(Intent(action), PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { resolveInfo ->
                    resolveInfo.serviceInfo?.let { serviceInfo ->
                        ResolvedService(
                            packageName = serviceInfo.packageName,
                            serviceClass = serviceInfo.name,
                            label = resolveInfo.loadLabel(pm),
                        )
                    }
                }
            buildDescriptors(raw) { signingSource.certLineageSha256(it) }
        }
}
