package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OldApksScannerTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun file(name: String, size: Long, ageDays: Long = 100) =
        ScanFile(
            id = name,
            displayName = name,
            sizeBytes = size,
            modifiedEpochSeconds = now - ageDays * day,
            relativePath = null,
        )

    private fun afterScan(
        vararg files: ScanFile,
        info: Map<String, ApkInfo> = emptyMap(),
        installed: Map<String, Long> = emptyMap(),
        minAgeDays: Int = 30,
    ): ApkScanResult = runSuspend {
        OldApksScanner(
            source = FileSource { files.toList() },
            inspector = ApkInspector { info[it.displayName] },
            installed = InstalledAppsSource { installed[it] },
            nowEpochSeconds = { now },
            minAgeDays = minAgeDays,
        ).scan()
    }

    @Test
    fun `marca ya instalado si la version instalada es igual`() {
        val result = afterScan(
            file("app.apk", 100),
            info = mapOf("app.apk" to ApkInfo("com.x", 5)),
            installed = mapOf("com.x" to 5L),
        )

        assertEquals(ApkReason.ALREADY_INSTALLED, result.candidates.single().reason)
    }

    @Test
    fun `marca ya instalado si la version instalada es mayor`() {
        val result = afterScan(
            file("app.apk", 100),
            info = mapOf("app.apk" to ApkInfo("com.x", 5)),
            installed = mapOf("com.x" to 6L),
        )

        assertEquals(ApkReason.ALREADY_INSTALLED, result.candidates.single().reason)
    }

    @Test
    fun `marca viejo si la version instalada es menor`() {
        val result = afterScan(
            file("app.apk", 100),
            info = mapOf("app.apk" to ApkInfo("com.x", 6)),
            installed = mapOf("com.x" to 5L),
        )

        assertEquals(ApkReason.OLD, result.candidates.single().reason)
    }

    @Test
    fun `marca viejo si la app no esta instalada`() {
        val result = afterScan(
            file("app.apk", 100),
            info = mapOf("app.apk" to ApkInfo("com.x", 6)),
        )

        assertEquals(ApkReason.OLD, result.candidates.single().reason)
    }

    @Test
    fun `excluye un apk reciente que no esta instalado`() {
        val result = afterScan(
            file("app.apk", 100, ageDays = 30),
            info = mapOf("app.apk" to ApkInfo("com.x", 6)),
            minAgeDays = 31,
        )

        assertEquals(emptyList<ApkCandidate>(), result.candidates)
    }

    @Test
    fun `incluye un apk viejo si el inspector devuelve null`() {
        val result = afterScan(file("desconocido.apk", 100))

        assertEquals(ApkReason.OLD, result.candidates.single().reason)
    }

    @Test
    fun `excluye un apk reciente si el inspector devuelve null`() {
        val result = afterScan(file("desconocido.apk", 100, ageDays = 1))

        assertEquals(emptyList<ApkCandidate>(), result.candidates)
    }

    @Test
    fun `incluye el apk que iguala la antiguedad minima`() {
        val result = afterScan(file("justo.apk", 100, ageDays = 30))

        assertEquals(ApkReason.OLD, result.candidates.single().reason)
    }

    @Test
    fun `ignora los archivos que no son apk`() {
        val result = afterScan(
            file("foto.jpg", 100),
            file("documento.pdf", 100),
            file("app.apk", 100),
        )

        assertEquals(listOf("app.apk"), result.candidates.map { it.file.displayName })
    }

    @Test
    fun `reconoce la extension apk en mayusculas`() {
        val result = afterScan(file("APP.APK", 100))

        assertEquals(ApkReason.OLD, result.candidates.single().reason)
    }

    @Test
    fun `ordena de mayor a menor tamano y desempata por nombre`() {
        val result = afterScan(
            file("mediano.apk", 300),
            file("zeta.apk", 500),
            file("alfa.apk", 500),
        )

        assertEquals(
            listOf("alfa.apk", "zeta.apk", "mediano.apk"),
            result.candidates.map { it.file.displayName },
        )
    }

    @Test
    fun `totalBytes suma los candidatos`() {
        val result = afterScan(
            file("uno.apk", 200),
            file("dos.apk", 300),
        )

        assertEquals(500L, result.totalBytes)
    }

    @Test
    fun `rechaza una antiguedad no positiva`() {
        assertThrows(IllegalArgumentException::class.java) {
            OldApksScanner(
                source = FileSource { emptyList() },
                inspector = ApkInspector { null },
                installed = InstalledAppsSource { null },
                minAgeDays = 0,
            )
        }
    }
}
