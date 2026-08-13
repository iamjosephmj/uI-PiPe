package tech.ssemaj.pipe.internal

/**
 * Runs a fire-and-forget binder call, swallowing any failure.
 *
 * A `oneway` call to a peer that has died throws `DeadObjectException` (a `RemoteException`). During
 * teardown that is expected and harmless — the peer is exactly the thing we're tearing down — so
 * every non-result-bearing remote call is wrapped in this to make the intent ("best-effort; a dead
 * peer is fine") explicit rather than scattering bare `runCatching {}` at each call site.
 */
internal inline fun ignoringRemote(block: () -> Unit) {
    runCatching(block)
}
