package io.github.catlinux.criba.domain

import java.util.Locale

/** Formatea bytes en unidades decimales (1 GB = 10^9 B), como muestra Android en Ajustes. */
object ByteFormat {
    private val units = arrayOf("B", "kB", "MB", "GB", "TB")

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String {
        require(bytes >= 0) { "bytes no puede ser negativo" }
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(locale, "%.1f %s", value, units[unit])
    }
}
