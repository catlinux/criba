package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScreenshotsScannerTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun file(
        name: String,
        relativePath: String?,
        size: Long = 100,
        ageSeconds: Long = 40 * 86_400L,
    ) = ScanFile(
        id = name,
        displayName = name,
        sizeBytes = size,
        modifiedEpochSeconds = now - ageSeconds,
        relativePath = relativePath,
    )

    private fun scan(vararg files: ScanFile, minAgeDays: Int = 30): ScreenshotsResult = runSuspend {
        ScreenshotsScanner(
            source = FileSource { files.toList() },
            nowEpochSeconds = { now },
            minAgeDays = minAgeDays,
        ).scan()
    }

    private fun bucket(result: ScreenshotsResult, bucket: AgeBucket) =
        result.groups.first { it.bucket == bucket }

    @Test
    fun `detecta la carpeta Screenshots de pictures`() {
        val result = scan(file("a.png", "Pictures/Screenshots/"))

        assertEquals(listOf("a.png"), bucket(result, AgeBucket.DAYS_30_TO_90).files.map { it.displayName })
    }

    @Test
    fun `detecta la carpeta Screenshots de dcim`() {
        val result = scan(file("a.png", "DCIM/Screenshots/"))

        assertEquals(1, result.totalCount)
    }

    @Test
    fun `detecta el segmento screenshots en minusculas`() {
        val result = scan(file("a.png", "screenshots/"))

        assertEquals(1, result.totalCount)
    }

    @Test
    fun `no trata MisScreenshots como captura`() {
        val result = scan(file("a.png", "Pictures/MisScreenshots/"))

        assertEquals(0, result.totalCount)
    }

    @Test
    fun `no trata Screenshots2 como captura`() {
        val result = scan(file("a.png", "Pictures/Screenshots2/"))

        assertEquals(0, result.totalCount)
    }

    @Test
    fun `no trata una ruta nula como captura`() {
        val result = scan(file("a.png", null))

        assertEquals(0, result.totalCount)
    }

    @Test
    fun `no trata pictures como captura`() {
        val result = scan(file("a.png", "Pictures/"))

        assertEquals(0, result.totalCount)
    }

    @Test
    fun `el borde de 30 dias entra en el primer cubo`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 30 * day))

        assertEquals(1, bucket(result, AgeBucket.DAYS_30_TO_90).files.size)
    }

    @Test
    fun `un segundo antes de 30 dias se excluye`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 30 * day - 1))

        assertEquals(0, result.totalCount)
    }

    @Test
    fun `el borde de 90 dias entra en el segundo cubo`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 90 * day))

        assertEquals(1, bucket(result, AgeBucket.DAYS_90_TO_365).files.size)
        assertEquals(0, bucket(result, AgeBucket.DAYS_30_TO_90).files.size)
    }

    @Test
    fun `un segundo antes de 90 dias sigue en el primer cubo`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 90 * day - 1))

        assertEquals(1, bucket(result, AgeBucket.DAYS_30_TO_90).files.size)
    }

    @Test
    fun `el borde de 365 dias entra en el tercer cubo`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 365 * day))

        assertEquals(1, bucket(result, AgeBucket.OVER_365).files.size)
        assertEquals(0, bucket(result, AgeBucket.DAYS_90_TO_365).files.size)
    }

    @Test
    fun `un segundo antes de 365 dias sigue en el segundo cubo`() {
        val result = scan(file("a.png", "Pictures/Screenshots/", ageSeconds = 365 * day - 1))

        assertEquals(1, bucket(result, AgeBucket.DAYS_90_TO_365).files.size)
    }

    @Test
    fun `siempre devuelve los tres grupos en orden`() {
        val result = scan()

        assertEquals(AgeBucket.entries, result.groups.map { it.bucket })
        assertEquals(3, result.groups.size)
    }

    @Test
    fun `ordena por tamano y desempata por nombre dentro de cada cubo`() {
        val result = scan(
            file("mediano.png", "Pictures/Screenshots/", size = 300),
            file("zeta.png", "Pictures/Screenshots/", size = 500),
            file("alfa.png", "Pictures/Screenshots/", size = 500),
        )

        assertEquals(
            listOf("alfa.png", "zeta.png", "mediano.png"),
            bucket(result, AgeBucket.DAYS_30_TO_90).files.map { it.displayName },
        )
    }

    @Test
    fun `calcula el total de bytes y el numero de capturas`() {
        val result = scan(
            file("uno.png", "Pictures/Screenshots/", size = 200, ageSeconds = 40 * day),
            file("dos.png", "DCIM/Screenshots/", size = 300, ageSeconds = 100 * day),
            file("tres.png", "Pictures/Screenshots/", size = 500, ageSeconds = 400 * day),
        )

        assertEquals(1000L, result.totalBytes)
        assertEquals(3, result.totalCount)
        assertEquals(200L, bucket(result, AgeBucket.DAYS_30_TO_90).totalBytes)
        assertEquals(300L, bucket(result, AgeBucket.DAYS_90_TO_365).totalBytes)
        assertEquals(500L, bucket(result, AgeBucket.OVER_365).totalBytes)
    }

    @Test
    fun `rechaza una antiguedad no positiva`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScreenshotsScanner(
                source = FileSource { emptyList() },
                nowEpochSeconds = { now },
                minAgeDays = 0,
            )
        }
    }
}
