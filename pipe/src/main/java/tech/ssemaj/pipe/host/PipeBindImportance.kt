package tech.ssemaj.pipe.host

import android.content.Context

/**
 * How much of the host's process priority the bound provider pane inherits.
 *
 * A pane renders in the provider's process — often a separate process, sometimes a separate app.
 * This controls the Android **bind importance**: how the platform ranks that process for scheduling
 * and low-memory-killer purposes while the pane is open. The host itself is always the top-app while
 * its pane is showing; this only changes where the *provider's* process sits relative to it.
 *
 * @see PipeFullScreen.open
 */
enum class PipeBindImportance {
    /**
     * Default. The provider process is raised only to **visible** importance (`oom_score_adj` ≈ 100):
     * alive and unthrottled while the pane is up, but ranked *below* the host — under memory pressure
     * the platform reclaims the pane's process before the host's, and it is the host that a provider
     * can never make itself harder to kill than. Use this unless a pane genuinely must be as
     * survivable as the host.
     */
    NORMAL,

    /**
     * The provider process inherits the host's own foreground/top importance via
     * [Context.BIND_IMPORTANT]: while the host is the top app the pane's process is ranked alongside
     * it (`oom_score_adj` ≈ 0), so the platform will not reclaim the pane before the host. This hands
     * the provider host-level survivability — a priority-inflation lever — so reserve it for panes you
     * fully trust to hold that priority (typically your own same-app processes).
     */
    IMPORTANT,
    ;

    /** The `bindService` flags this importance maps to. */
    internal fun toBindFlags(): Int {
        var flags = Context.BIND_AUTO_CREATE
        if (this == IMPORTANT) flags = flags or Context.BIND_IMPORTANT
        return flags
    }
}
