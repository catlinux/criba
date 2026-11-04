package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionPolicyTest {
    private fun candidate(
        path: String,
        sizeBytes: Long = 100,
        name: String = path.substringAfterLast('/'),
    ) = DeletionCandidate(
        path = path,
        displayName = name,
        sizeBytes = sizeBytes,
        modifiedEpochSeconds = 0,
    )

    private fun verdict(path: String) = DeletionPolicy.evaluate(candidate(path))

    private fun assertProtected(path: String, reason: ProtectedReason) {
        val protected = verdict(path) as DeletionVerdict.Protected
        assertEquals(reason, protected.reason)
    }

    @Test
    fun `un zip en descargas se puede borrar`() =
        assertEquals(DeletionVerdict.Allowed, verdict("/storage/emulated/0/Download/archivo.zip"))

    @Test
    fun `un apk en descargas se puede borrar`() =
        assertEquals(DeletionVerdict.Allowed, verdict("/storage/emulated/0/Download/app.apk"))

    @Test
    fun `una imagen en minusculas exige copia verificada`() =
        assertEquals(
            DeletionVerdict.RequiresVerifiedBackup,
            verdict("/storage/emulated/0/Pictures/foto.jpg"),
        )

    @Test
    fun `un video en minusculas exige copia verificada`() =
        assertEquals(
            DeletionVerdict.RequiresVerifiedBackup,
            verdict("/storage/emulated/0/Movies/clip.mp4"),
        )

    @Test
    fun `un audio en minusculas exige copia verificada`() =
        assertEquals(
            DeletionVerdict.RequiresVerifiedBackup,
            verdict("/storage/emulated/0/Music/cancion.flac"),
        )

    @Test
    fun `las extensiones multimedia en mayusculas exigen copia verificada`() {
        assertEquals(DeletionVerdict.RequiresVerifiedBackup, verdict("/storage/emulated/0/Download/foto.JPG"))
        assertEquals(DeletionVerdict.RequiresVerifiedBackup, verdict("/storage/emulated/0/Download/clip.Mp4"))
        assertEquals(DeletionVerdict.RequiresVerifiedBackup, verdict("/storage/emulated/0/Download/cancion.FLAC"))
    }

    @Test
    fun `un archivo sin extension se puede borrar`() =
        assertEquals(DeletionVerdict.Allowed, verdict("/storage/emulated/0/Download/sin_extension"))

    @Test
    fun `un archivo que termina en punto se puede borrar`() =
        assertEquals(DeletionVerdict.Allowed, verdict("/storage/emulated/0/Download/raro."))

    @Test
    fun `un archivo oculto llamado punto jpg se decide por su extension`() =
        assertEquals(DeletionVerdict.RequiresVerifiedBackup, verdict("/storage/emulated/0/Download/.jpg"))

    @Test
    fun `una ruta relativa esta protegida por no ser absoluta`() =
        assertProtected("Download/archivo.zip", ProtectedReason.NOT_ABSOLUTE)

    @Test
    fun `una ruta que escapa con punto punto esta protegida`() =
        assertProtected("/storage/emulated/0/../../etc/archivo.zip", ProtectedReason.PROTECTED_PATH)

    @Test
    fun `android data de otra app esta protegido`() =
        assertProtected("/storage/emulated/0/Android/data/otra.app/x.zip", ProtectedReason.PROTECTED_PATH)

    @Test
    fun `un archivo dentro de la papelera propia esta protegido`() =
        assertProtected(
            "/storage/emulated/0/Android/data/io.github.catlinux.criba/files/trash/abc/x.zip",
            ProtectedReason.INSIDE_TRASH,
        )

    @Test
    fun `la propia carpeta de la papelera esta protegida`() =
        assertProtected(
            "/storage/emulated/0/Android/data/io.github.catlinux.criba/files/trash",
            ProtectedReason.INSIDE_TRASH,
        )

    @Test
    fun `una base de datos esta protegida`() =
        assertProtected("/storage/emulated/0/Download/datos.db", ProtectedReason.PROTECTED_PATH)

    @Test
    fun `un almacen de claves esta protegido`() =
        assertProtected("/storage/emulated/0/Download/clave.jks", ProtectedReason.PROTECTED_PATH)

    @Test
    fun `una clave pem esta protegida`() =
        assertProtected("/storage/emulated/0/Download/clave.pem", ProtectedReason.PROTECTED_PATH)

    @Test
    fun `una foto dentro de android data sigue protegida`() {
        val protected = verdict("/storage/emulated/0/Android/data/otra.app/files/foto.jpg")

        assertTrue(protected is DeletionVerdict.Protected)
        assertEquals(ProtectedReason.PROTECTED_PATH, (protected as DeletionVerdict.Protected).reason)
    }

    @Test
    fun `rechaza un tamano negativo`() {
        assertThrows(IllegalArgumentException::class.java) {
            candidate("/storage/emulated/0/Download/x.zip", sizeBytes = -1)
        }
    }
}
