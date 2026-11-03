package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoverableSummaryTest {
    private fun file(
        id: String,
        name: String,
        size: Long = 100,
        relativePath: String? = "Download/",
    ) = ScanFile(
        id = id,
        displayName = name,
        sizeBytes = size,
        modifiedEpochSeconds = 0,
        relativePath = relativePath,
    )

    @Test
    fun `un mapa vacio no tiene recuperable`() {
        val summary = RecoverableCalculator.summarize(emptyMap())

        assertEquals(0, summary.uniqueCount)
        assertEquals(0L, summary.uniqueBytes)
        assertEquals(emptySet<ReportCategory>(), summary.categories)
    }

    @Test
    fun `una sola categoria cuenta sus archivos`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(ReportCategory.LARGE_FILES to listOf(file("a", "a.bin", 300), file("b", "b.bin", 200))),
        )

        assertEquals(2, summary.uniqueCount)
        assertEquals(500L, summary.uniqueBytes)
        assertEquals(setOf(ReportCategory.LARGE_FILES), summary.categories)
    }

    @Test
    fun `dos categorias sin solapamiento suman`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.LARGE_FILES to listOf(file("a", "a.bin", 300)),
                ReportCategory.SCREENSHOTS to listOf(file("b", "b.png", 200, "Pictures/Screenshots/")),
            ),
        )

        assertEquals(2, summary.uniqueCount)
        assertEquals(500L, summary.uniqueBytes)
    }

    @Test
    fun `el mismo archivo con ids distintos cuenta una sola vez`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.LARGE_FILES to listOf(file("content://media/1", "video.mp4", 900, "DCIM/")),
                ReportCategory.SCREENSHOTS to listOf(file("/storage/emulated/0/DCIM/video.mp4", "video.mp4", 900, "DCIM/")),
            ),
        )

        assertEquals(1, summary.uniqueCount)
        assertEquals(900L, summary.uniqueBytes)
    }

    @Test
    fun `mismo nombre y tamano en distinta carpeta cuenta dos veces`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.LARGE_FILES to listOf(
                    file("a", "video.mp4", 900, "DCIM/"),
                    file("b", "video.mp4", 900, "Movies/"),
                ),
            ),
        )

        assertEquals(2, summary.uniqueCount)
        assertEquals(1800L, summary.uniqueBytes)
    }

    @Test
    fun `mismo nombre y carpeta con distinto tamano cuenta dos veces`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.LARGE_FILES to listOf(
                    file("a", "video.mp4", 900, "DCIM/"),
                    file("b", "video.mp4", 800, "DCIM/"),
                ),
            ),
        )

        assertEquals(2, summary.uniqueCount)
        assertEquals(1700L, summary.uniqueBytes)
    }

    @Test
    fun `una ruta relativa nula se trata como vacia`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.APKS to listOf(file("a", "app.apk", 100, null)),
                ReportCategory.SCREENSHOTS to listOf(file("b", "app.apk", 100, null)),
            ),
        )

        assertEquals(1, summary.uniqueCount)
        assertEquals(100L, summary.uniqueBytes)
    }

    @Test
    fun `las categorias reflejan las claves del mapa`() {
        val summary = RecoverableCalculator.summarize(
            mapOf(
                ReportCategory.OLD_DOWNLOADS to emptyList(),
                ReportCategory.APKS to listOf(file("a", "app.apk", 100)),
            ),
        )

        assertEquals(setOf(ReportCategory.OLD_DOWNLOADS, ReportCategory.APKS), summary.categories)
    }
}
