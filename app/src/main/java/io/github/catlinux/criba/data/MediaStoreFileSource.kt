package io.github.catlinux.criba.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.ScanFile
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee imágenes, vídeo y audio del almacenamiento externo con MediaStore (solo lectura). */
class MediaStoreFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileSource {
    override suspend fun listFiles(): List<ScanFile> = withContext(Dispatchers.IO) {
        val collection = filesCollection()
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (hasRelativePath) add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }.toTypedArray()

        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)",
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString(),
            ),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val relativePathIndex =
                if (hasRelativePath) cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH) else -1
            val contentUriBase = collection.toString()

            buildList {
                while (cursor.moveToNext()) {
                    MediaRow(
                        id = cursor.getLong(idIndex),
                        displayName = cursor.getString(nameIndex),
                        sizeBytes = cursor.getLong(sizeIndex),
                        modifiedEpochSeconds = cursor.getLong(modifiedIndex),
                        relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null,
                    ).toScanFile(contentUriBase)?.let { add(it) }
                }
            }
        } ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun filesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri(LEGACY_EXTERNAL_VOLUME)
        }

    private companion object {
        const val LEGACY_EXTERNAL_VOLUME = "external"
    }
}
