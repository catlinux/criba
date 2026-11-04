package io.github.catlinux.criba.data.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSourceDeviceTest {
    @Test
    fun listAppsHonraElAccesoDeUso() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val usageAccess = AppOpsUsageAccess(context)
        val source = PackageManagerAppSource(context, usageAccess)

        val apps = source.listApps()

        if (!usageAccess.isGranted()) {
            assertTrue(
                "sin acceso de uso no debe haber tamaños ni últimos usos",
                apps.all { it.sizes == null && it.lastUsedEpochSeconds == null },
            )
        } else {
            assertTrue("debe haber al menos una app con icono de lanzador", apps.isNotEmpty())
            assertTrue("debe haber al menos una app con tamaño conocido", apps.any { it.sizes != null })
            apps.mapNotNull { it.sizes }.forEach { sizes ->
                assertTrue(sizes.appBytes >= 0)
                assertTrue(sizes.dataBytes >= 0)
                assertTrue(sizes.cacheBytes >= 0)
            }
        }
    }
}
