package io.github.catlinux.criba.data.apps

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.UsageAccess
import javax.inject.Inject

class AppOpsUsageAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UsageAccess {
    override fun isGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
