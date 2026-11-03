package io.github.catlinux.criba.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedPathsTest {
    private fun assertProtected(path: String) =
        assertTrue("debería estar protegida: $path", ProtectedPaths.isProtected(path))

    private fun assertNotProtected(path: String) =
        assertFalse("no debería estar protegida: $path", ProtectedPaths.isProtected(path))

    @Test
    fun `una ruta vacia esta protegida`() = assertProtected("")

    @Test
    fun `una ruta relativa esta protegida`() = assertProtected("Download/a.pdf")

    @Test
    fun `una uri content esta protegida`() = assertProtected("content://media/external/file/42")

    @Test
    fun `un punto punto que escapa de la raiz esta protegido`() =
        assertProtected("/storage/emulated/0/../../etc/passwd")

    @Test
    fun `las rutas del sistema estan protegidas`() = assertProtected("/system/app")

    @Test
    fun `los datos de la app estan protegidos`() = assertProtected("/data/data/x")

    @Test
    fun `android data de otra app esta protegido`() =
        assertProtected("/storage/emulated/0/Android/data/com.x/files")

    @Test
    fun `android obb esta protegido`() =
        assertProtected("/storage/emulated/0/Android/obb")

    @Test
    fun `android data en mayusculas esta protegido`() =
        assertProtected("/storage/emulated/0/ANDROID/DATA/com.x")

    @Test
    fun `sdcard android data esta protegido`() =
        assertProtected("/sdcard/Android/data")

    @Test
    fun `el volumen externo por id esta protegido en android data`() =
        assertProtected("/storage/ABCD-1234/Android/data")

    @Test
    fun `un punto punto se resuelve antes de decidir`() =
        assertProtected("/storage/emulated/0/Download/../Android/data/x")

    @Test
    fun `un documento en descargas no esta protegido`() =
        assertNotProtected("/storage/emulated/0/Download/a.pdf")

    @Test
    fun `un apk en una subcarpeta de descargas no esta protegido`() =
        assertNotProtected("/storage/emulated/0/Download/sub/x.apk")

    @Test
    fun `la carpeta de descargas no esta protegida`() =
        assertNotProtected("/storage/emulated/0/Download")

    @Test
    fun `una foto en pictures no esta protegida`() =
        assertNotProtected("/storage/emulated/0/Pictures/foto.jpg")

    @Test
    fun `android media no esta protegido`() =
        assertNotProtected("/storage/emulated/0/Android/media/x")

    @Test
    fun `un archivo de base de datos esta protegido`() =
        assertProtected("/storage/emulated/0/Download/x.db")

    @Test
    fun `una extension sqlite en mayusculas esta protegida`() =
        assertProtected("/storage/emulated/0/Download/a.SQLITE")

    @Test
    fun `un almacen de claves jks esta protegido`() =
        assertProtected("/storage/emulated/0/Download/a.jks")

    @Test
    fun `una clave pem en mayusculas esta protegida`() =
        assertProtected("/storage/emulated/0/Download/id.PEM")

    @Test
    fun `una carpeta databases esta protegida`() =
        assertProtected("/storage/emulated/0/Download/databases")

    @Test
    fun `la carpeta ssh esta protegida`() =
        assertProtected("/storage/emulated/0/.ssh/id_rsa")

    @Test
    fun `la carpeta gnupg esta protegida`() =
        assertProtected("/storage/emulated/0/Download/.gnupg")

    @Test
    fun `la raiz storage emulated esta protegida`() = assertProtected("/storage/emulated")
}
