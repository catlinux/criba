package io.github.catlinux.criba.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ByteFormatTest {
    private val en = Locale.ENGLISH

    @Test
    fun `bytes sin escalar`() = assertEquals("999 B", ByteFormat.format(999, en))

    @Test
    fun `usa unidades decimales`() {
        assertEquals("1.0 kB", ByteFormat.format(1_000, en))
        assertEquals("1.5 MB", ByteFormat.format(1_500_000, en))
        assertEquals("64.0 GB", ByteFormat.format(64_000_000_000, en))
    }

    @Test
    fun `no pasa de TB`() = assertEquals("2000.0 TB", ByteFormat.format(2_000_000_000_000_000, en))

    @Test
    fun `rechaza negativos`() {
        assertThrows(IllegalArgumentException::class.java) { ByteFormat.format(-1, en) }
    }
}
