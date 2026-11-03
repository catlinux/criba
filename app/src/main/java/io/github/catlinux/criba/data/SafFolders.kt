package io.github.catlinux.criba.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.FolderSource
import io.github.catlinux.criba.domain.FolderSummary
import io.github.catlinux.criba.domain.ProtectedPaths
import io.github.catlinux.criba.domain.SafPaths
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DocumentNode(
    val documentId: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
)

fun interface DocumentChildren {
    /** Devuelve null si el contenido no se pudo leer: una carpeta ilegible nunca debe pasar por vacía. */
    fun children(documentId: String): List<DocumentNode>?
}

private data class FolderStats(
    val fileCount: Int,
    val totalBytes: Long,
    val newestModifiedEpochSeconds: Long?,
    val complete: Boolean,
)

/**
 * Resume las subcarpetas directas de la raíz elegida. Una carpeta solo se devuelve si se ha podido
 * recorrer entera: si contiene algo protegido, no traducible a ruta, ilegible o más profundo que
 * [maxDepth], se excluye, para que nunca figure como vacía o sin uso con contenido que no se ha visto.
 */
fun summarizeTopLevelFolders(
    rootDocumentId: String,
    children: DocumentChildren,
    isProtected: (String) -> Boolean = ProtectedPaths::isProtected,
    idToPath: (String) -> String? = SafPaths::documentIdToPath,
    maxDepth: Int = 20,
): List<FolderSummary> =
    children.children(rootDocumentId).orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { folder ->
            val path = idToPath(folder.documentId)
            if (path == null || isProtected(path)) return@mapNotNull null
            val stats = collectStats(folder.documentId, 1, children, isProtected, idToPath, maxDepth)
            if (!stats.complete) return@mapNotNull null
            FolderSummary(
                path = path,
                name = folder.name,
                fileCount = stats.fileCount,
                totalBytes = stats.totalBytes,
                newestModifiedEpochSeconds = stats.newestModifiedEpochSeconds,
            )
        }

private fun collectStats(
    documentId: String,
    depth: Int,
    children: DocumentChildren,
    isProtected: (String) -> Boolean,
    idToPath: (String) -> String?,
    maxDepth: Int,
): FolderStats {
    if (depth > maxDepth) return INCOMPLETE
    val nodes = children.children(documentId) ?: return INCOMPLETE

    var fileCount = 0
    var totalBytes = 0L
    var newest: Long? = null

    for (node in nodes) {
        val path = idToPath(node.documentId)
        if (path == null || isProtected(path)) return INCOMPLETE
        if (node.isDirectory) {
            val nested = collectStats(node.documentId, depth + 1, children, isProtected, idToPath, maxDepth)
            if (!nested.complete) return INCOMPLETE
            fileCount += nested.fileCount
            totalBytes += nested.totalBytes
            nested.newestModifiedEpochSeconds?.let { newest = maxOf(newest ?: it, it) }
        } else {
            fileCount += 1
            if (node.sizeBytes > 0) totalBytes += node.sizeBytes
            val modified = node.lastModifiedMillis / 1000
            if (modified > 0) newest = maxOf(newest ?: modified, modified)
        }
    }

    return FolderStats(
        fileCount = fileCount,
        totalBytes = totalBytes,
        newestModifiedEpochSeconds = newest,
        complete = true,
    )
}

private val INCOMPLETE =
    FolderStats(fileCount = 0, totalBytes = 0, newestModifiedEpochSeconds = null, complete = false)

/** Consulta de solo lectura sobre el árbol SAF elegido; nunca crea, mueve ni borra documentos. */
class SafDocumentChildren(
    private val context: Context,
    private val treeUri: Uri,
) : DocumentChildren {
    override fun children(documentId: String): List<DocumentNode>? =
        try {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DocumentNode(
                                documentId = cursor.getString(idIndex),
                                name = cursor.getString(nameIndex) ?: "",
                                isDirectory =
                                    cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR,
                                sizeBytes = if (cursor.isNull(sizeIndex)) 0 else cursor.getLong(sizeIndex),
                                lastModifiedMillis =
                                    if (cursor.isNull(modifiedIndex)) 0 else cursor.getLong(modifiedIndex),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
}

class SafFolderSourceFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(treeUri: Uri): FolderSource = FolderSource {
        withContext(Dispatchers.IO) {
            summarizeTopLevelFolders(
                rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri),
                children = SafDocumentChildren(context, treeUri),
            )
        }
    }
}
