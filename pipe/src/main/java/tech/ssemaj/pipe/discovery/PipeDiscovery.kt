package tech.ssemaj.pipe.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.auth.AndroidPackageManagerSource
import tech.ssemaj.pipe.core.Pipe
import tech.ssemaj.pipe.host.ProviderComponent

/**
 * A provider resolved by intent action: everything a host needs to render a picker and later open
 * the pane. [certSha256] is the provider's full signing lineage — pin it (e.g. via
 * [PipeAuthorizers.allowlist][tech.ssemaj.pipe.auth.PipeAuthorizers.allowlist]) before trusting it.
 */
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

/**
 * Finds installed Pipe providers by their `OPEN_PANE` intent action, so hosts don't have to
 * hardcode provider components.
 *
 * Requires the discovered packages to be *visible* to your app (API 30+): declare a `<queries>`
 * element with the provider's package — or with the
 * [action][Pipe.ACTION_OPEN_PANE] — in your manifest. Invisible providers are simply not returned.
 */
object PipeDiscovery {
    /**
     * All installed providers answering [action] (default: the Pipe open action), with labels and
     * signing-cert lineages. Runs on `Dispatchers.IO`; safe to call from any dispatcher.
     */
    suspend fun query(context: Context, action: String = Pipe.ACTION_OPEN_PANE): List<ProviderDescriptor> =
        withContext(Dispatchers.IO) {
            val pm = context.applicationContext.packageManager
            val signingSource = AndroidPackageManagerSource(context)
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
