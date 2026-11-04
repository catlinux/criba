package io.github.catlinux.criba.domain

/** Acceso de uso que el usuario concede en los ajustes del sistema; solo lectura. */
fun interface UsageAccess {
    fun isGranted(): Boolean
}
