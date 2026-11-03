package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OldDownloadsScannerTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun file(name: String, size: Long, ageDays: Long) =
        ScanFile(
            id = name,
            displayName = name,
            sizeBytes = size,
            modifiedEpochSeconds = now - ageDays * day,
            relativePath = null,
        )

    private fun source(vararg files: ScanFile) = FileSource { files.toList() }

    private fun scanner(vararg files: ScanFile, minAgeDays: Int = 90) =
        OldDownloadsScanner(
            source = source(*files),
            nowEpochSeconds = { now },
            minAgeDays = minAgeDays,
        )

    @Test
    fun `incluye el archivo que iguala la antiguedad minima`() = runSuspend {
        val result = scanner(file("justo.pdf", 100, ageDays = 90)).scan()

        assertEquals(listOf("justo.pdf"), result.files.map { it.displayName })
    }

    @Test
    fun `excluye un archivo un segundo mas nuevo que el limite`() = runSuspend {
        val justo = ScanFile(
            id = "nuevo.pdf",
            displayName = "nuevo.pdf",
            sizeBytes = 100,
            modifiedEpochSeconds = now - 90 * day + 1,
            relativePath = null,
        )

        assertEquals(emptyList<ScanFile>(), scanner(justo).scan().files)
    }

    @Test
    fun `excluye los apk de descargas`() = runSuspend {
        val result = scanner(
            file("viejo.pdf", 100, ageDays = 100),
            file("viejo.apk", 100, ageDays = 100),
        ).scan()

        assertEquals(listOf("viejo.pdf"), result.files.map { it.displayName })
    }

    @Test
    fun `excluye la extension apk en mayusculas`() = runSuspend {
        val result = scanner(file("viejo.APK", 100, ageDays = 100)).scan()

        assertEquals(emptyList<ScanFile>(), result.files)
    }

    @Test
    fun `ordena de mayor a menor tamano y desempata por nombre`() = runSuspend {
        val result = scanner(
            file("mediano.pdf", 300, ageDays = 100),
            file("zeta.pdf", 500, ageDays = 100),
            file("alfa.pdf", 500, ageDays = 100),
            file("pequeno.pdf", 100, ageDays = 100),
        ).scan()

        assertEquals(
            listOf("alfa.pdf", "zeta.pdf", "mediano.pdf", "pequeno.pdf"),
            result.files.map { it.displayName },
        )
    }

    @Test
    fun `devuelve un resultado vacio sin archivos validos`() = runSuspend {
        val result = scanner(file("reciente.pdf", 100, ageDays = 1)).scan()

        assertEquals(emptyList<ScanFile>(), result.files)
        assertEquals(0L, result.totalBytes)
    }

    @Test
    fun `totalBytes suma los archivos incluidos`() = runSuspend {
        val result = scanner(
            file("uno.pdf", 200, ageDays = 100),
            file("dos.pdf", 300, ageDays = 100),
            file("reciente.pdf", 50, ageDays = 1),
        ).scan()

        assertEquals(500L, result.totalBytes)
    }

    @Test
    fun `rechaza una antiguedad no positiva`() {
        assertThrows(IllegalArgumentException::class.java) { scanner(minAgeDays = 0) }
    }
}
