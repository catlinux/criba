package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnusedAppsScannerTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun app(
        packageName: String,
        label: String = packageName,
        sizes: AppSizes? = AppSizes(100, 200, 50),
        lastUsedAgeSeconds: Long? = 100 * day,
        installAgeSeconds: Long = 500 * day,
        isSystem: Boolean = false,
        isUpdatedSystem: Boolean = false,
        isProtectedRole: Boolean = false,
    ) = AppUsageInfo(
        packageName = packageName,
        label = label,
        sizes = sizes,
        lastUsedEpochSeconds = lastUsedAgeSeconds?.let { now - it },
        firstInstallEpochSeconds = now - installAgeSeconds,
        isSystem = isSystem,
        isUpdatedSystem = isUpdatedSystem,
        isProtectedRole = isProtectedRole,
    )

    private fun scan(vararg apps: AppUsageInfo, minUnusedDays: Int = 90): AppScanResult = runSuspend {
        UnusedAppsScanner(
            source = AppSource { apps.toList() },
            ownPackageName = "io.github.catlinux.criba",
            nowEpochSeconds = { now },
            minUnusedDays = minUnusedDays,
        ).scan()
    }

    @Test
    fun `incluye el uso anterior justo al umbral`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = 90 * day))

        assertEquals(AppReason.UNUSED_SINCE, result.candidates.single().reason)
    }

    @Test
    fun `excluye el uso un segundo antes del umbral`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = 90 * day - 1))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `sin datos de uso excluye una instalacion reciente`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = null, installAgeSeconds = 20 * day))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `sin datos de uso incluye instalacion antigua con motivo explicito`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = null, installAgeSeconds = 90 * day))

        assertEquals(AppReason.NO_USE_RECORDED, result.candidates.single().reason)
    }

    @Test
    fun `sin datos de uso excluye instalacion un segundo antes del umbral`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = null, installAgeSeconds = 90 * day - 1))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `uso reciente excluye aunque se instalara hace mucho`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = 10 * day, installAgeSeconds = 500 * day))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `uso en el futuro se considera reciente`() = runSuspend {
        val result = scan(app("com.ejemplo", lastUsedAgeSeconds = -1, installAgeSeconds = 500 * day))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `excluye la propia Criba`() = runSuspend {
        val result = UnusedAppsScanner(
            source = AppSource { listOf(app("io.github.catlinux.criba")) },
            ownPackageName = "io.github.catlinux.criba",
            nowEpochSeconds = { now },
        ).scan()

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `excluye apps del sistema no actualizadas`() = runSuspend {
        val result = scan(app("com.sistema", isSystem = true))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `incluye apps del sistema actualizadas si cumplen el umbral`() = runSuspend {
        val result = scan(app("com.actualizada", isSystem = true, isUpdatedSystem = true))

        assertEquals("com.actualizada", result.candidates.single().app.packageName)
    }

    @Test
    fun `excluye las apps de rol protegido`() = runSuspend {
        val result = scan(app("com.teclado", isProtectedRole = true))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
    }

    @Test
    fun `ordena por tamano descendente y desempata por etiqueta ignorando mayusculas`() = runSuspend {
        val result = scan(
            app("com.menor", label = "Menor", sizes = AppSizes(100, 0, 0)),
            app("com.zeta", label = "zeta", sizes = AppSizes(500, 0, 0)),
            app("com.alfa", label = "Alfa", sizes = AppSizes(500, 0, 0)),
        )

        assertEquals(listOf("com.alfa", "com.zeta", "com.menor"), result.candidates.map { it.app.packageName })
    }

    @Test
    fun `desempata por packageName cuando las etiquetas no distinguen mayusculas`() = runSuspend {
        val result = scan(
            app("com.z", label = "Aplicación", sizes = AppSizes(1, 0, 0)),
            app("com.a", label = "aplicación", sizes = AppSizes(1, 0, 0)),
        )

        assertEquals(listOf("com.a", "com.z"), result.candidates.map { it.app.packageName })
    }

    @Test
    fun `ordena las apps sin tamano al final por etiqueta`() = runSuspend {
        val result = scan(
            app("com.z", label = "Zeta", sizes = null),
            app("com.grande", label = "Grande", sizes = AppSizes(10_000, 0, 0)),
            app("com.alfa", label = "Alfa", sizes = null),
        )

        assertEquals(listOf("com.grande", "com.alfa", "com.z"), result.candidates.map { it.app.packageName })
    }

    @Test
    fun `total conocido omite tamanos desconocidos y los cuenta`() = runSuspend {
        val result = scan(
            app("com.uno", sizes = AppSizes(100, 200, 300)),
            app("com.dos", sizes = AppSizes(400, 500, 600)),
            app("com.sin-dato", sizes = null),
        )

        assertEquals(2100L, result.totalKnownBytes)
        assertEquals(1, result.unknownSizeCount)
    }

    @Test
    fun `devuelve resultado vacio sin candidatas`() = runSuspend {
        val result = scan(app("com.reciente", lastUsedAgeSeconds = 1 * day))

        assertEquals(emptyList<AppCandidate>(), result.candidates)
        assertEquals(0L, result.totalKnownBytes)
        assertEquals(0, result.unknownSizeCount)
    }

    @Test
    fun `rechaza umbral no positivo`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnusedAppsScanner(AppSource { emptyList() }, "io.github.catlinux.criba", minUnusedDays = 0)
        }
    }

    @Test
    fun `rechaza nombre de paquete propio vacio`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnusedAppsScanner(AppSource { emptyList() }, "")
        }
    }

    @Test
    fun `rechaza tamaños negativos de app data y cache`() {
        assertThrows(IllegalArgumentException::class.java) { AppSizes(-1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { AppSizes(0, -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { AppSizes(0, 0, -1) }
    }

    @Test
    fun `totalBytes suma app data y cache`() {
        assertEquals(600L, AppSizes(appBytes = 100, dataBytes = 200, cacheBytes = 300).totalBytes)
    }
}
