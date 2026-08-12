package tech.ssemaj.pipe.auth

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/** Real [SigningSource] backed by [PackageManager]. */
class AndroidSigningSource(context: Context) : SigningSource {
    private val pm = context.applicationContext.packageManager

    override fun packagesForUid(uid: Int): List<String> =
        try { pm.getPackagesForUid(uid)?.toList().orEmpty() } catch (_: Exception) { emptyList() }

    override fun uidForPackage(packageName: String): Int =
        try { pm.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0)) } catch (_: Exception) { -1 }

    override fun certLineageSha256(packageName: String): List<String> = try {
        val info = pm.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
        val signingInfo = info.signingInfo ?: return emptyList()
        val signatures =
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory // includes rotation lineage
        signatures.orEmpty().map { it.toByteArray().sha256Hex() }
    } catch (_: Exception) {
        emptyList()
    }
}
