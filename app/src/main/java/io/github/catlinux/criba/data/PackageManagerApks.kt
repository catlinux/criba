package io.github.catlinux.criba.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.ApkInfo
import io.github.catlinux.criba.domain.ApkInspector
import io.github.catlinux.criba.domain.InstalledAppsSource
import io.github.catlinux.criba.domain.ScanFile
import javax.inject.Inject

class PackageManagerApkInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApkInspector {
    @Suppress("DEPRECATION")
    override fun inspect(file: ScanFile): ApkInfo? {
        val info = try {
            context.packageManager.getPackageArchiveInfo(file.id, 0)
        } catch (e: Exception) {
            return null
        } ?: return null
        val packageName = info.packageName ?: return null
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return ApkInfo(packageName = packageName, versionCode = versionCode)
    }
}

/**
 * Sin QUERY_ALL_PACKAGES, Android 11+ oculta muchas apps instaladas, así que «ya instalada» puede no
 * detectarse; el error va en la dirección conservadora: nunca marca como instalada una app que no lo está.
 */
class PackageManagerInstalledApps @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstalledAppsSource {
    @Suppress("DEPRECATION")
    override fun installedVersionCode(packageName: String): Long? =
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageInfo(packageName, 0)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
}
