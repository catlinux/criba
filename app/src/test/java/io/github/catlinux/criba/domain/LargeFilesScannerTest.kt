package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LargeFilesScannerTest {
    private fun file(name: String, size: Long) =
        ScanFile(
            id = name,
            displayName = name,
            sizeBytes = size,
            modifiedEpochSeconds = 0,
            relativePath = null,
        )

    private fun source(vararg files: ScanFile) = FileSource { files.toList() }

    @Test
    fun `incluye el archivo que iguala el umbral`() = runSuspend {
        val scanner = LargeFilesScanner(source(file("justo.bin", 100)), minSizeBytes = 100)

        assertEquals(listOf("justo.bin"), scanner.scan().files.map { it.displayName })
    }

    @Test
    fun `descarta archivos por debajo del umbral`() = runSuspend {
        val scanner = LargeFilesScanner(source(file("pequeno.bin", 99)), minSizeBytes = 100)

        assertEquals(emptyList<ScanFile>(), scanner.scan().files)
    }

    @Test
    fun `ordena de mayor a menor tamano`() = runSuspend {
        val scanner = LargeFilesScanner(
            source(file("mediano.bin", 300), file("grande.bin", 500), file("pequeno.bin", 100)),
            minSizeBytes = 100,
        )

        assertEquals(
            listOf("grande.bin", "mediano.bin", "pequeno.bin"),
            scanner.scan().files.map { it.displayName },
        )
    }

    @Test
    fun `desempata por nombre a igual tamano`() = runSuspend {
        val scanner = LargeFilesScanner(
            source(file("zeta.bin", 300), file("alfa.bin", 300)),
            minSizeBytes = 100,
        )

        assertEquals(listOf("alfa.bin", "zeta.bin"), scanner.scan().files.map { it.displayName })
    }

    @Test
    fun `devuelve resultado vacio si no hay archivos grandes`() = runSuspend {
        val scanner = LargeFilesScanner(source(), minSizeBytes = 100)
        val result = scanner.scan()

        assertEquals(emptyList<ScanFile>(), result.files)
        assertEquals(0L, result.totalBytes)
    }

    @Test
    fun `totalBytes suma los archivos filtrados`() = runSuspend {
        val scanner = LargeFilesScanner(
            source(file("uno.bin", 200), file("dos.bin", 300), file("tres.bin", 50)),
            minSizeBytes = 100,
        )

        assertEquals(500L, scanner.scan().totalBytes)
    }

    @Test
    fun `rechaza un tamano negativo en ScanFile`() {
        assertThrows(IllegalArgumentException::class.java) { file("negativo.bin", -1) }
    }

    @Test
    fun `rechaza un umbral no positivo`() {
        assertThrows(IllegalArgumentException::class.java) {
            LargeFilesScanner(source(), minSizeBytes = 0)
        }
    }

    @Test
    fun `el umbral por defecto son 100 MB decimales`() = runSuspend {
        val scanner = LargeFilesScanner(
            source(file("justo.bin", 100_000_000), file("pequeno.bin", 99_999_999)),
        )

        assertEquals(100_000_000L, LargeFilesScanner.DEFAULT_MIN_SIZE_BYTES)
        assertEquals(listOf("justo.bin"), scanner.scan().files.map { it.displayName })
    }
}
