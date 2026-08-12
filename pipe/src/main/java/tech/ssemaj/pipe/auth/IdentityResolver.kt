package tech.ssemaj.pipe.auth

/** Builds verified [PeerIdentity] values from kernel/PackageManager facts. */
class IdentityResolver(private val source: SigningSource) {

    /** Identity for a binder caller uid, or null if it cannot be fully attributed. */
    fun forUid(uid: Int): PeerIdentity? {
        val packages = source.packagesForUid(uid)
        if (packages.isEmpty()) return null
        val certs = LinkedHashSet<String>()
        for (pkg in packages) {
            val lineage = source.certLineageSha256(pkg)
            if (lineage.isEmpty()) return null // can't attribute every package → trust nothing
            certs += lineage
        }
        return PeerIdentity(uid, packages, certs.toList())
    }

    /** Identity for a to-be-bound package, or null if its certs are unreadable. */
    fun forPackage(packageName: String, uid: Int = -1): PeerIdentity? {
        val lineage = source.certLineageSha256(packageName)
        if (lineage.isEmpty()) return null
        val resolvedUid = if (uid == -1) source.uidForPackage(packageName) else uid
        return PeerIdentity(resolvedUid, listOf(packageName), lineage)
    }

    /** Uid currently assigned to [packageName], or -1 if unresolvable. Never throws. */
    fun uidForPackage(packageName: String): Int = source.uidForPackage(packageName)
}
