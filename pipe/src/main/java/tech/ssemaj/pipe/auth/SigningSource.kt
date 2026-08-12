package tech.ssemaj.pipe.auth

/** Abstraction over PackageManager signing lookups so gate logic is unit-testable. */
interface SigningSource {
    /** Packages owned by [uid]; empty if unknown. Never throws. */
    fun packagesForUid(uid: Int): List<String>
    /** SHA-256 (lowercase hex) of each cert in the package's signing lineage, oldest→newest; empty if unreadable. */
    fun certLineageSha256(packageName: String): List<String>
    /** Uid currently assigned to [packageName], or -1 if unresolvable. Never throws. */
    fun uidForPackage(packageName: String): Int = -1
}
