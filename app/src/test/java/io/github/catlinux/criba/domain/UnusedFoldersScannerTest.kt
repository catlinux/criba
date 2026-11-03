package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnusedFoldersScannerTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun folder(
        name: String,
        fileCount: Int = 1,
        totalBytes: Long = 100,
        newestAgeSeconds: Long? = 400 * day,
    ) = FolderSummary(
        path = "/storage/emulated/0/$name",
        name = name,
        fileCount = fileCount,
        totalBytes = totalBytes,
        newestModifiedEpochSeconds = newestAgeSeconds?.let { now - it },
    )

    private fun scan(vararg folders: FolderSummary, minAgeDays: Int = 365): FolderScanResult = runSuspend {
        UnusedFoldersScanner(
            source = FolderSource { folders.toList() },
            nowEpochSeconds = { now },
            minAgeDays = minAgeDays,
        ).scan()
    }

    @Test
    fun `marca vacia la carpeta sin archivos`() {
        val result = scan(folder("vacia", fileCount = 0, totalBytes = 0, newestAgeSeconds = null))

        assertEquals(FolderReason.EMPTY, result.candidates.single().reason)
    }

    @Test
    fun `marca inactiva en el borde exacto de la antiguedad`() {
        val result = scan(folder("vieja", newestAgeSeconds = 365 * day))

        assertEquals(FolderReason.INACTIVE, result.candidates.single().reason)
    }

    @Test
    fun `excluye una carpeta un segundo mas nueva que el limite`() {
        val result = scan(folder("casi", newestAgeSeconds = 365 * day - 1))

        assertEquals(emptyList<FolderCandidate>(), result.candidates)
    }

    @Test
    fun `excluye una carpeta reciente`() {
        val result = scan(folder("reciente", newestAgeSeconds = 10 * day))

        assertEquals(emptyList<FolderCandidate>(), result.candidates)
    }

    @Test
    fun `excluye una carpeta con archivos y fecha desconocida`() {
        val result = scan(folder("sin fecha", newestAgeSeconds = null))

        assertEquals(emptyList<FolderCandidate>(), result.candidates)
    }

    @Test
    fun `ordena por tamano y desempata por nombre`() {
        val result = scan(
            folder("mediana", totalBytes = 300),
            folder("zeta", totalBytes = 500),
            folder("alfa", totalBytes = 500),
        )

        assertEquals(listOf("alfa", "zeta", "mediana"), result.candidates.map { it.folder.name })
    }

    @Test
    fun `calcula totales y recuentos por motivo`() {
        val result = scan(
            folder("vacia", fileCount = 0, totalBytes = 0, newestAgeSeconds = null),
            folder("vieja", totalBytes = 200),
            folder("reciente", totalBytes = 50, newestAgeSeconds = 10 * day),
        )

        assertEquals(200L, result.totalBytes)
        assertEquals(1, result.emptyCount)
        assertEquals(1, result.inactiveCount)
    }

    @Test
    fun `devuelve un resultado vacio sin carpetas`() {
        val result = scan()

        assertEquals(emptyList<FolderCandidate>(), result.candidates)
        assertEquals(0L, result.totalBytes)
        assertEquals(0, result.emptyCount)
        assertEquals(0, result.inactiveCount)
    }

    @Test
    fun `rechaza una antiguedad no positiva`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnusedFoldersScanner(
                source = FolderSource { emptyList() },
                nowEpochSeconds = { now },
                minAgeDays = 0,
            )
        }
    }

    @Test
    fun `rechaza un numero de archivos negativo`() {
        assertThrows(IllegalArgumentException::class.java) {
            FolderSummary(path = "/x", name = "x", fileCount = -1, totalBytes = 0, newestModifiedEpochSeconds = null)
        }
    }

    @Test
    fun `rechaza un total de bytes negativo`() {
        assertThrows(IllegalArgumentException::class.java) {
            FolderSummary(path = "/x", name = "x", fileCount = 0, totalBytes = -1, newestModifiedEpochSeconds = null)
        }
    }
}
