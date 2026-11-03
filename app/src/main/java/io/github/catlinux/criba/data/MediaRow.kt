package io.github.catlinux.criba.data

import io.github.catlinux.criba.domain.ScanFile

data class MediaRow(
    val id: Long,
    val displayName: String?,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val relativePath: String?,
)

fun MediaRow.toScanFile(contentUriBase: String): ScanFile? {
    val name = displayName
    if (sizeBytes < 0 || name.isNullOrEmpty()) return null
    return ScanFile(
        id = "$contentUriBase/$id",
        displayName = name,
        sizeBytes = sizeBytes,
        modifiedEpochSeconds = modifiedEpochSeconds,
        relativePath = relativePath,
    )
}
