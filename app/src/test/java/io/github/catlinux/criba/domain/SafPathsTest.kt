package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafPathsTest {
    @Test
    fun `primary sin ruta es la raiz del almacenamiento`() =
        assertEquals("/storage/emulated/0", SafPaths.documentIdToPath("primary:"))

    @Test
    fun `primary con ruta`() =
        assertEquals("/storage/emulated/0/Documents/Fotos", SafPaths.documentIdToPath("primary:Documents/Fotos"))

    @Test
    fun `primary con ruta anidada`() =
        assertEquals("/storage/emulated/0/DCIM/Camera/2024", SafPaths.documentIdToPath("primary:DCIM/Camera/2024"))

    @Test
    fun `tarjeta sd con ruta`() =
        assertEquals("/storage/ABCD-1234/Musica", SafPaths.documentIdToPath("ABCD-1234:Musica"))

    @Test
    fun `tarjeta sd sin ruta`() =
        assertEquals("/storage/ABCD-1234", SafPaths.documentIdToPath("ABCD-1234:"))

    @Test
    fun `volumen home no se reconoce`() = assertNull(SafPaths.documentIdToPath("home:x"))

    @Test
    fun `volumen raw no se reconoce`() = assertNull(SafPaths.documentIdToPath("raw:/x"))

    @Test
    fun `sin dos puntos no se reconoce`() = assertNull(SafPaths.documentIdToPath("primary"))

    @Test
    fun `cadena vacia no se reconoce`() = assertNull(SafPaths.documentIdToPath(""))
}
