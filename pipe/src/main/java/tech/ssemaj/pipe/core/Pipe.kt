package tech.ssemaj.pipe.core

/**
 * Protocol constants shared by hosts and providers.
 *
 * [ACTION_OPEN_PANE] is the single intent action the whole protocol keys on: hosts bind with it
 * (explicit component + action), providers declare an intent filter for it in their manifest, and
 * [PipeDiscovery][tech.ssemaj.pipe.discovery.PipeDiscovery] finds installed providers by it.
 */
public object Pipe {
    /** The intent action of a provider's pane service. Stable protocol constant — never changes. */
    public const val ACTION_OPEN_PANE: String = "tech.ssemaj.pipe.action.OPEN_PANE"
}
