package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppsPresentationTest {
    private val now = 10_000_000L
    private val day = 86_400L

    @Test
    fun `sin dato devuelve null`() {
        assertNull(lastUseDaysAgo(now, null))
    }

    @Test
    fun `el mismo instante son cero dias`() {
        assertEquals(0L, lastUseDaysAgo(now, now))
    }

    @Test
    fun `calcula dias enteros`() {
        assertEquals(89L, lastUseDaysAgo(now, now - 89 * day))
        assertEquals(90L, lastUseDaysAgo(now, now - 90 * day))
        assertEquals(365L, lastUseDaysAgo(now, now - 365 * day))
    }

    @Test
    fun `un uso en el futuro nunca es negativo`() {
        assertEquals(0L, lastUseDaysAgo(now, now + 5 * day))
    }
}
