package io.github.catlinux.criba.domain

/** Origen de archivos de solo lectura: no expone operaciones de escritura, borrado ni movido. */
fun interface FileSource {
    suspend fun listFiles(): List<ScanFile>
}
