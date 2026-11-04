package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    private val source = "/storage/emulated/0/DCIM/foto.jpg"

    @Test
    fun `rechaza un destino dentro de la papelera`() {
        assertFalse(BackupDestinationPolicy.accepts("/storage/emulated/0/.criba-trash/copia", listOf(source)))
        assertFalse(BackupDestinationPolicy.accepts("/storage/emulated/0/.criba-trash", listOf(source)))
    }

    @Test
    fun `rechaza el destino igual a la carpeta del origen`() {
        assertFalse(BackupDestinationPolicy.accepts("/storage/emulated/0/DCIM", listOf(source)))
    }

    @Test
    fun `rechaza un destino ancestro del origen`() {
        assertFalse(BackupDestinationPolicy.accepts("/storage/emulated/0", listOf(source)))
        assertFalse(BackupDestinationPolicy.accepts("/storage/emulated/0/DCIM", listOf(source)))
    }

    @Test
    fun `acepta una ruta ajena`() {
        assertTrue(BackupDestinationPolicy.accepts("/storage/emulated/0/Criba-copia", listOf(source)))
    }

    @Test
    fun `acepta un destino sin ruta de sistema`() {
        assertTrue(BackupDestinationPolicy.accepts(null, listOf(source)))
    }

    @Test
    fun `detecta el almacenamiento interno`() {
        assertTrue(BackupDestinationPolicy.isInternalStorage("/storage/emulated/0/Criba-copia", "/storage/emulated/0"))
        assertFalse(BackupDestinationPolicy.isInternalStorage("/sdcard/Criba-copia", "/storage/emulated/0"))
        assertFalse(BackupDestinationPolicy.isInternalStorage(null, "/storage/emulated/0"))
    }

    @Test
    fun `un nombre sin colision se conserva`() {
        assertEquals("foto.jpg", uniqueBackupName("foto.jpg", emptySet()))
    }

    @Test
    fun `un nombre con colision inserta el sufijo antes de la extension`() {
        assertEquals("foto (1).jpg", uniqueBackupName("foto.jpg", setOf("foto.jpg")))
        assertEquals("foto (2).jpg", uniqueBackupName("foto.jpg", setOf("foto.jpg", "foto (1).jpg")))
    }

    @Test
    fun `un nombre sin extension inserta el sufijo al final`() {
        assertEquals("notas (1)", uniqueBackupName("notas", setOf("notas")))
    }

    @Test
    fun `compara sin distinguir mayusculas`() {
        assertEquals("foto (1).jpg", uniqueBackupName("foto.jpg", setOf("FOTO.JPG")))
    }

    @Test
    fun `un punto inicial no se trata como extension`() {
        assertEquals(".oculto (1)", uniqueBackupName(".oculto", setOf(".oculto")))
    }
}
