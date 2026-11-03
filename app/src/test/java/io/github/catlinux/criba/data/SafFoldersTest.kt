package io.github.catlinux.criba.data

import io.github.catlinux.criba.domain.FolderSummary
import io.github.catlinux.criba.domain.ProtectedPaths
import io.github.catlinux.criba.domain.SafPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafFoldersTest {
    private class FakeChildren(
        private val tree: Map<String, List<DocumentNode>>,
        private val unreadable: Set<String>,
    ) : DocumentChildren {
        override fun children(documentId: String): List<DocumentNode>? =
            if (documentId in unreadable) null else tree[documentId].orEmpty()
    }

    private fun dir(id: String, name: String) = DocumentNode(id, name, isDirectory = true, sizeBytes = 0, lastModifiedMillis = 0)

    private fun file(id: String, name: String, size: Long, modifiedMillis: Long = 0) =
        DocumentNode(id, name, isDirectory = false, sizeBytes = size, lastModifiedMillis = modifiedMillis)

    private fun summarize(
        tree: Map<String, List<DocumentNode>>,
        isProtected: (String) -> Boolean = { false },
        idToPath: (String) -> String? = SafPaths::documentIdToPath,
        maxDepth: Int = 20,
        unreadable: Set<String> = emptySet(),
    ): List<FolderSummary> = summarizeTopLevelFolders(
        rootDocumentId = "root",
        children = FakeChildren(tree, unreadable),
        isProtected = isProtected,
        idToPath = idToPath,
        maxDepth = maxDepth,
    )

    @Test
    fun `resume una carpeta vacia`() {
        val result = summarize(mapOf("root" to listOf(dir("primary:vacia", "vacia")), "primary:vacia" to emptyList()))

        assertEquals(1, result.size)
        assertEquals(0, result.single().fileCount)
        assertEquals(0L, result.single().totalBytes)
        assertNull(result.single().newestModifiedEpochSeconds)
        assertEquals("/storage/emulated/0/vacia", result.single().path)
        assertEquals("vacia", result.single().name)
    }

    @Test
    fun `cuenta archivos anidados y usa la fecha mas reciente`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:fotos", "fotos")),
            "primary:fotos" to listOf(
                file("primary:fotos/a.jpg", "a.jpg", 100, 5_000),
                dir("primary:fotos/sub", "sub"),
            ),
            "primary:fotos/sub" to listOf(
                file("primary:fotos/sub/b.jpg", "b.jpg", 200, 9_000),
                file("primary:fotos/sub/c.jpg", "c.jpg", 0, 3_000),
            ),
        )

        val foto = summarize(tree).single()

        assertEquals(3, foto.fileCount)
        assertEquals(300L, foto.totalBytes)
        assertEquals(9L, foto.newestModifiedEpochSeconds)
    }

    @Test
    fun `una carpeta con solo subcarpetas vacias cuenta cero archivos`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:madre", "madre")),
            "primary:madre" to listOf(dir("primary:madre/hija", "hija")),
            "primary:madre/hija" to emptyList(),
        )

        assertEquals(0, summarize(tree).single().fileCount)
    }

    @Test
    fun `omite una carpeta protegida y todo lo que cuelga`() {
        val tree = mapOf(
            "root" to listOf(
                dir("primary:privada", "privada"),
                dir("primary:abierta", "abierta"),
            ),
            "primary:privada" to listOf(file("primary:privada/secreto.txt", "secreto.txt", 100)),
            "primary:abierta" to listOf(file("primary:abierta/libre.txt", "libre.txt", 50)),
        )

        val result = summarize(tree, isProtected = { it.endsWith("/privada") })

        assertEquals(listOf("abierta"), result.map { it.name })
        assertEquals(1, result.single().fileCount)
    }

    @Test
    fun `excluye una carpeta que contiene un archivo protegido`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:mezcla", "mezcla"), dir("primary:limpia", "limpia")),
            "primary:mezcla" to listOf(
                file("primary:mezcla/datos.db", "datos.db", 500, 10_000),
                file("primary:mezcla/foto.jpg", "foto.jpg", 100, 20_000),
            ),
            "primary:limpia" to listOf(file("primary:limpia/foto.jpg", "foto.jpg", 100, 20_000)),
        )

        val result = summarize(tree, isProtected = { it.endsWith(".db") })

        assertEquals(listOf("limpia"), result.map { it.name })
    }

    @Test
    fun `una carpeta que solo contiene algo protegido no figura como vacia`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:solo", "solo")),
            "primary:solo" to listOf(file("primary:solo/claves.jks", "claves.jks", 5)),
        )

        assertEquals(emptyList<FolderSummary>(), summarize(tree, isProtected = { it.endsWith(".jks") }))
    }

    @Test
    fun `una carpeta ilegible no figura como vacia`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:ilegible", "ilegible"), dir("primary:anidada", "anidada")),
            "primary:anidada" to listOf(dir("primary:anidada/dentro", "dentro")),
        )

        val result = summarize(tree, unreadable = setOf("primary:ilegible", "primary:anidada/dentro"))

        assertEquals(emptyList<FolderSummary>(), result)
    }

    @Test
    fun `omite lo que no se puede traducir a ruta`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:oculta", "oculta"), dir("primary:visible", "visible")),
            "primary:oculta" to listOf(file("primary:oculta/x.txt", "x.txt", 10)),
            "primary:visible" to listOf(file("primary:visible/y.txt", "y.txt", 20)),
        )
        val customToPath: (String) -> String? = { id ->
            if (id.startsWith("primary:oculta")) null else SafPaths.documentIdToPath(id)
        }

        val result = summarize(tree, idToPath = customToPath)

        assertEquals(listOf("visible"), result.map { it.name })
        assertEquals(1, result.single().fileCount)
    }

    @Test
    fun `ignora los archivos sueltos de la raiz`() {
        val tree = mapOf(
            "root" to listOf(
                file("primary:documento.txt", "documento.txt", 100),
                dir("primary:carpeta", "carpeta"),
            ),
            "primary:carpeta" to listOf(file("primary:carpeta/x.txt", "x.txt", 10)),
        )

        val result = summarize(tree)

        assertEquals(listOf("carpeta"), result.map { it.name })
        assertEquals(1, result.single().fileCount)
    }

    @Test
    fun `los tamanos negativos cuentan cero`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:rara", "rara")),
            "primary:rara" to listOf(file("primary:rara/x.bin", "x.bin", -5)),
        )

        val result = summarize(tree).single()

        assertEquals(1, result.fileCount)
        assertEquals(0L, result.totalBytes)
    }

    @Test
    fun `ignora una fecha de modificacion cero`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:sinfecha", "sinfecha")),
            "primary:sinfecha" to listOf(file("primary:sinfecha/x.txt", "x.txt", 10, modifiedMillis = 0)),
        )

        assertNull(summarize(tree).single().newestModifiedEpochSeconds)
    }

    @Test
    fun `excluye una carpeta mas profunda que la profundidad maxima`() {
        val tree = mapOf(
            "root" to listOf(dir("primary:profunda", "profunda")),
            "primary:profunda" to listOf(
                file("primary:profunda/directo.txt", "directo.txt", 10),
                dir("primary:profunda/sub", "sub"),
            ),
            "primary:profunda/sub" to listOf(file("primary:profunda/sub/dentro.txt", "dentro.txt", 20)),
        )

        assertEquals(emptyList<FolderSummary>(), summarize(tree, maxDepth = 1))
        val completa = summarize(tree, maxDepth = 2).single()
        assertEquals(2, completa.fileCount)
        assertEquals(30L, completa.totalBytes)
    }

    @Test
    fun `con las rutas reales omite android data y conserva lo demas`() {
        val tree = mapOf(
            "root" to listOf(
                dir("primary:Android/data/com.x", "com.x"),
                dir("primary:Documents/viejo", "viejo"),
            ),
            "primary:Android/data/com.x" to listOf(file("primary:Android/data/com.x/x.bin", "x.bin", 100)),
            "primary:Documents/viejo" to listOf(file("primary:Documents/viejo/y.pdf", "y.pdf", 40)),
        )

        val result = summarize(tree, isProtected = ProtectedPaths::isProtected)

        assertEquals(listOf("viejo"), result.map { it.name })
        assertEquals("/storage/emulated/0/Documents/viejo", result.single().path)
        assertEquals(1, result.single().fileCount)
        assertEquals(40L, result.single().totalBytes)
    }
}
