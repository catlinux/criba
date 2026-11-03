# Hoja de ruta

Cada fase es una milestone de GitHub con sus issues (ver [ISSUES.md](ISSUES.md)).

| Fase | Contenido | Estado |
|---|---|---|
| 0 – Base | Estructura, Gradle con version catalog, CI, licencia GPLv3, README, CLAUDE.md, docs | En curso (PR de bootstrap) |
| 1 – Escáner de lectura | Archivos grandes, descargas y APKs viejos, capturas, carpetas huérfanas. Informe de espacio recuperable. Sin borrar | Completa (solo lectura) |
| 2 – Papelera y borrado seguro | Papelera, `isProtected`, registro, deshacer | Pendiente |
| 3 – Apps | Tamaño por app, última vez usada, candidatas a desinstalar | Pendiente |
| 4 – Duplicados | Hash de contenido (exactos primero; similares después) | Pendiente |
| 5 – Copia verificada | Backup a destino elegido con SHA-256 y puerta de seguridad para multimedia | Pendiente |
| 6 – Segundo plano (informativo) | Apps activas, consumo y acciones guiadas a ajustes | Pendiente |
| 7 – Pulido | Pruebas en Samsung, Xiaomi y Pixel, accesibilidad, versión 1.0 | Pendiente |

No se empieza una fase hasta que el mantenedor aprueba la anterior.
