package io.github.catlinux.criba.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageMappingTest {
    @Test
    fun `el ultimo uso por paquete es el maximo en segundos`() {
        val result = latestUsePerPackage(
            listOf(
                UsageRecord("com.a", 10_000),
                UsageRecord("com.a", 30_000),
                UsageRecord("com.b", 5_000),
            ),
        )

        assertEquals(30L, result["com.a"])
        assertEquals(5L, result["com.b"])
    }

    @Test
    fun `ignora registros con ultimo uso cero o negativo`() {
        val result = latestUsePerPackage(
            listOf(
                UsageRecord("com.a", 0),
                UsageRecord("com.b", -5),
                UsageRecord("com.c", 2_000),
            ),
        )

        assertEquals(setOf("com.c"), result.keys)
    }

    @Test
    fun `un paquete que solo tiene cero no aparece`() {
        val result = latestUsePerPackage(listOf(UsageRecord("com.a", 0), UsageRecord("com.a", 0)))

        assertFalse(result.containsKey("com.a"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `convierte milisegundos a segundos`() {
        val result = latestUsePerPackage(listOf(UsageRecord("com.a", 1_999)))

        assertEquals(1L, result["com.a"])
    }

    @Test
    fun `resta la cache de data para no contarla dos veces`() {
        val sizes = toAppSizes(appBytes = 100, dataBytes = 100, cacheBytes = 30)

        assertEquals(70L, sizes.dataBytes)
        assertEquals(200L, sizes.totalBytes)
    }

    @Test
    fun `una cache mayor que data deja data en cero`() {
        val sizes = toAppSizes(appBytes = 10, dataBytes = 20, cacheBytes = 50)

        assertEquals(0L, sizes.dataBytes)
        assertEquals(60L, sizes.totalBytes)
    }

    @Test
    fun `las entradas negativas se recortan a cero`() {
        val sizes = toAppSizes(appBytes = -10, dataBytes = -20, cacheBytes = -30)

        assertEquals(0L, sizes.appBytes)
        assertEquals(0L, sizes.dataBytes)
        assertEquals(0L, sizes.cacheBytes)
        assertEquals(0L, sizes.totalBytes)
    }

    @Test
    fun `el total suma app data sin cache y cache`() {
        val sizes = toAppSizes(appBytes = 500, dataBytes = 1_000, cacheBytes = 200)

        assertEquals(800L, sizes.dataBytes)
        assertEquals(1_500L, sizes.totalBytes)
    }
}
