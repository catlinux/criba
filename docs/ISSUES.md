# Milestones e issues

Pensado para importarse con `gh` (no disponible al crear este archivo). Cada `##` es una milestone; cada viñeta, un issue (título · descripción breve). Etiquetas sugeridas: `fase-N`, `seguridad`, `docs`, `ci`.

Ejemplo de creación con `gh`:

```
gh api repos/catlinux/criba/milestones -f title="Fase 1 – Escáner de lectura"
gh issue create --repo catlinux/criba --title "..." --body "..." --milestone "Fase 1 – Escáner de lectura"
```

## Fase 0 – Base

- **Estructura base del proyecto** · Kotlin, Compose, Hilt, minSdk 26, version catalog. *(hecho en `chore/bootstrap`)*
- **CI en GitHub Actions** · tests, lint, build y comprobación de que no se declara `INTERNET`. *(hecho)*
- **Licencia, README, CLAUDE.md y docs** · GPLv3, límites honestos, reglas de seguridad para agentes. *(hecho)*
- **Firma de APK y workflow de Release** · clave fuera del repo; publicar APK firmado en GitHub Releases.
- **Plantillas de issue y PR** · incluir la lista de reglas de seguridad como checklist.

## Fase 1 – Escáner de lectura

- **Modelo de resultados y caso de uso base de escáner** · interfaz común, solo lectura, cancelable, con progreso por Flow.
- **`LargeFilesScanner`** · archivos grandes vía MediaStore, umbral configurable.
- **Descargas y APKs viejos** · detectar APKs ya instalados y descargas antiguas.
- **Capturas de pantalla** · agrupar por antigüedad y tamaño.
- **Carpetas huérfanas** · carpetas de apps desinstaladas, sin tocar `Android/data` ni `Android/obb` de otras apps.
- **Informe de espacio recuperable** · pantalla con cifras medidas, sin estimaciones inventadas.
- **Flujo de permisos de lectura** · permisos mínimos de MediaStore; selector SAF para carpetas elegidas.

## Fase 2 – Papelera y borrado seguro

- **`isProtected(path)` con tests** · punto único de decisión; cobertura alta (sistema, `Android/data`, `Android/obb`, bases de datos, claves).
- **Papelera** · `MediaStore.createTrashRequest` o papelera propia con restauración (Room); decidir y documentar.
- **Confirmación explícita** · diálogo con archivos y MB reales antes de cualquier borrado.
- **Registro auditable** · qué, cuándo, resultado (Room); pantalla de historial.
- **Deshacer** · restaurar desde papelera y desde el registro.
- **Vaciado de papelera con retención** · nunca borrado definitivo inmediato.

## Fase 3 – Apps

- **Tamaño por app** · `StorageStatsManager`.
- **Última vez usada** · `UsageStatsManager` con flujo de concesión del permiso de acceso a uso.
- **`UnusedAppsScanner`** · candidatas a desinstalar, solo informativo.
- **Guía a ajustes del sistema** · intents a la pantalla de la app para desinstalar; nunca automatizado.

## Fase 4 – Duplicados

- **`DuplicatesScanner` exactos** · agrupar por tamaño, luego hash de contenido, en segundo plano y cancelable.
- **Selección segura de qué conservar** · nunca proponer borrar todas las copias.
- **Duplicados similares** · imágenes parecidas (después de los exactos).

## Fase 5 – Copia verificada

- **Copia a destino elegido (SAF)** · el usuario elige la carpeta.
- **`BackupVerifier` con SHA-256** · una copia vale solo si el hash coincide, con tests de integridad.
- **Puerta de seguridad para multimedia** · sin copia verificada no se propone borrar fotos, vídeos ni audio.
- **Confirmación manual guiada** · alternativa honesta para quien usa Google Fotos, sin fingir la comprobación.

## Fase 6 – Segundo plano (informativo)

- **Apps activas y consumo** · solo informar.
- **Acciones guiadas a ajustes** · forzar detención, restringir batería, desinstalar; el usuario las hace.

## Fase 7 – Pulido

- **Pruebas en Samsung, Xiaomi y Pixel** · comportamiento de MediaStore, papelera y permisos por fabricante.
- **Accesibilidad** · TalkBack, contraste, tamaños de fuente.
- **Traducciones al catalán e inglés** · completar y revisar.
- **Versión 1.0** · revisión de seguridad, documentación y release firmada.
