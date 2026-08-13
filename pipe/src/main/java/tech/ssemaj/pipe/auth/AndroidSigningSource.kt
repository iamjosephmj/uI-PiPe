package tech.ssemaj.pipe.auth

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

private val API33 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/** Real [SigningSource] backed by [PackageManager]. */
class AndroidSigningSource(context: Context) : SigningSource {
    private val pm = context.applicationContext.packageManager

    override fun packagesForUid(uid: Int): List<String> =
        try { pm.getPackagesForUid(uid)?.toList().orEmpty() } catch (_: Exception) { emptyList() }

    override fun uidForPackage(packageName: String): Int = try {
        if (API33) pm.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageUid(packageName, 0)
    } catch (_: Exception) { -1 }

    override fun certLineageSha256(packageName: String): List<String> = try {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val info: PackageInfo =
            if (API33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, flags)
        val signingInfo = info.signingInfo ?: return emptyList()
        val signatures =
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory // includes rotation lineage
        signatures.orEmpty().map { it.toByteArray().sha256Hex() }
    } catch (_: Exception) {
        emptyList()
    }
}
