package io.github.catlinux.criba.data.apps

import android.app.role.RoleManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.AppSizes
import io.github.catlinux.criba.domain.AppSource
import io.github.catlinux.criba.domain.AppUsageInfo
import io.github.catlinux.criba.domain.UsageAccess
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PackageManagerAppSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val usageAccess: UsageAccess,
) : AppSource {
    override suspend fun listApps(): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val packages = launcherPackages(packageManager)
        val protectedPackages = protectedRolePackages(packageManager)
        val hasUsageAccess = usageAccess.isGranted()
        val usageByPackage = if (hasUsageAccess) lastUseByPackage() else emptyMap()
        val storageStats = if (hasUsageAccess) context.getSystemService(StorageStatsManager::class.java) else null

        packages.mapNotNull { packageName ->
            try {
                val info = packageInfo(packageManager, packageName)
                val applicationInfo = info.applicationInfo
                AppUsageInfo(
                    packageName = packageName,
                    label = applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName,
                    sizes = storageStats?.let { sizesFor(it, packageName) },
                    lastUsedEpochSeconds = usageByPackage[packageName],
                    firstInstallEpochSeconds = info.firstInstallTime / 1000,
                    isSystem = applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0,
                    isUpdatedSystem = applicationInfo?.flags?.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    isProtectedRole = packageName in protectedPackages,
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }

    @Suppress("DEPRECATION")
    private fun launcherPackages(packageManager: PackageManager): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                packageManager.queryIntentActivities(intent, 0)
            }
        return resolved.mapNotNull { it.activityInfo?.packageName }.distinct()
    }

    @Suppress("DEPRECATION")
    private fun protectedRolePackages(packageManager: PackageManager): Set<String> {
        val packages = mutableSetOf<String>()

        packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo
            ?.packageName
            ?.let(packages::add)

        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
            ?.takeIf { it.isNotEmpty() }
            ?.let(packages::add)

        packages += roleHolderPackages()
        return packages
    }

    /**
     * El stub de compileSdk no expone getRoleHolders, así que se consulta por reflexión; si el
     * método no existe o lanza, se ignora tal como pide el plan.
     */
    private fun roleHolderPackages(): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return emptySet()
        val method = try {
            RoleManager::class.java.getMethod("getRoleHolders", String::class.java)
        } catch (e: NoSuchMethodException) {
            return emptySet()
        }
        val holders = mutableSetOf<String>()
        for (role in listOf(RoleManager.ROLE_HOME, RoleManager.ROLE_DIALER, RoleManager.ROLE_SMS)) {
            try {
                if (roleManager.isRoleAvailable(role)) {
                    @Suppress("UNCHECKED_CAST")
                    val roleHolders = method.invoke(roleManager, role) as? List<String> ?: emptyList()
                    holders += roleHolders
                }
            } catch (e: Exception) {
                // Un rol sin titulares o sin permiso de consulta no aporta datos: se ignora.
            }
        }
        return holders
    }

    private fun sizesFor(storageStats: StorageStatsManager, packageName: String): AppSizes? =
        try {
            val stats = storageStats.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle(),
            )
            toAppSizes(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        } catch (e: Exception) {
            // SecurityException, NameNotFoundException o IOException: se muestra «sin datos».
            null
        }

    private fun lastUseByPackage(): Map<String, Long> {
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val start = now - DAYS_OF_HISTORY * MILLIS_PER_DAY
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now)
            ?: return emptyMap()
        return latestUsePerPackage(stats.map { UsageRecord(it.packageName, it.lastTimeUsed) })
    }

    private companion object {
        const val DAYS_OF_HISTORY = 365L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
