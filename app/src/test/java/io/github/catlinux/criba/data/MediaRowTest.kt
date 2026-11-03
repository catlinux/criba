package io.github.catlinux.criba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRowTest {
    private val base = "content://media/external/file"

    private fun row(
        displayName: String? = "foto.jpg",
        sizeBytes: Long = 200_000_000L,
        modifiedEpochSeconds: Long = 1_700_000_000L,
        relativePath: String? = "DCIM/Camera/",
    ) = MediaRow(
        id = 42L,
        displayName = displayName,
        sizeBytes = sizeBytes,
        modifiedEpochSeconds = modifiedEpochSeconds,
        relativePath = relativePath,
    )

    @Test
    fun `descarta tamanos negativos`() = assertNull(row(sizeBytes = -1).toScanFile(base))

    @Test
    fun `descarta nombres nulos`() = assertNull(row(displayName = null).toScanFile(base))

    @Test
    fun `descarta nombres vacios`() = assertNull(row(displayName = "").toScanFile(base))

    @Test
    fun `construye el id con la uri base`() {
        assertEquals("$base/42", row().toScanFile(base)?.id)
    }

    @Test
    fun `conserva tamano fecha y ruta relativa`() {
        val file = row().toScanFile(base)

        assertEquals("foto.jpg", file?.displayName)
        assertEquals(200_000_000L, file?.sizeBytes)
        assertEquals(1_700_000_000L, file?.modifiedEpochSeconds)
        assertEquals("DCIM/Camera/", file?.relativePath)
    }

    @Test
    fun `conserva la ruta relativa nula`() = assertNull(row(relativePath = null).toScanFile(base)?.relativePath)
}
