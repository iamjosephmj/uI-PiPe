package tech.ssemaj.pipe.auth

import android.content.ComponentName

/**
 * The PackageManager facts identity and availability are built from, behind a seam so gate logic is
 * unit-testable without a device.
 *
 * Every function is total: it returns a sentinel (`-1`, empty list, `false`) instead of throwing,
 * so callers can treat "PackageManager said no / threw" as "unknown" uniformly.
 */
internal interface PackageManagerSource {
    /** Packages owned by [uid]; empty if unknown. */
    fun packagesForUid(uid: Int): List<String>

    /** SHA-256 (lowercase hex) of each cert in the package's signing lineage, oldest→newest; empty if unreadable. */
    fun certLineageSha256(packageName: String): List<String>

    /** Uid currently assigned to [packageName], or -1 if unresolvable. */
    fun uidForPackage(packageName: String): Int

    /** Whether the service [component] exists and is visible to this app; `false` if not or unknown. */
    fun serviceResolvable(component: ComponentName): Boolean
}
