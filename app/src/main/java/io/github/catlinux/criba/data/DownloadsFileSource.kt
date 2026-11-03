package io.github.catlinux.criba.data

import android.os.Environment
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.ScanFile
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee la carpeta Descargas del almacenamiento externo con acceso directo de solo lectura. */
class DownloadsFileSource @Inject constructor() : FileSource {
    @Suppress("DEPRECATION")
    override suspend fun listFiles(): List<ScanFile> = withContext(Dispatchers.IO) {
        walkDownloads(
            root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            storageRoot = Environment.getExternalStorageDirectory(),
        )
    }
}
