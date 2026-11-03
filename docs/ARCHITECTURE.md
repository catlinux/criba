# Arquitectura

## Visión general

Una sola `Activity` (`MainActivity`) con Jetpack Compose y Navigation Compose. Un único módulo Gradle (`:app`); se dividirá por capas cuando el tamaño lo justifique.

```
ui  →  domain  →  data
```

- **ui**: pantallas Compose, `ViewModel`s (Hilt) y navegación. No accede a Android de bajo nivel.
- **domain**: modelos y casos de uso en Kotlin puro, sin dependencias de Android siempre que sea posible. Define interfaces (p. ej. `StorageInfoSource`) que implementa `data`.
- **data**: implementaciones sobre APIs de Android (`MediaStore`, `StorageStatsManager`, `UsageStatsManager`, SAF, Room para registro y papelera).

La dependencia apunta siempre hacia abajo; `data` se enlaza con `domain` mediante módulos Hilt (`DataModule`).

## Componentes previstos

Escáneres como casos de uso independientes y testeables: `LargeFilesScanner`, `DuplicatesScanner`, `UnusedAppsScanner`, `BackupVerifier`. Más: papelera, registro de acciones y `isProtected(path)` (punto único de decisión sobre rutas protegidas).

## Decisiones técnicas

| Tema | Decisión | Motivo |
|---|---|---|
| Lenguaje y UI | Kotlin, Compose, Material 3 | Estándar actual de Android |
| SDK | minSdk 26, target/compile 36 | Android 8+ cubre la gran mayoría; target = última estable instalada |
| `applicationId` | `io.github.catlinux.criba` | Convención para proyectos sin dominio propio: usuario de GitHub |
| DI | Hilt (con KSP) | Estándar de Google; integración con Compose y ViewModel |
| Asincronía | Corrutinas y Flow | Sin dependencias extra |
| Dependencias | Version catalog (`gradle/libs.versions.toml`) | Versiones en un solo sitio |
| Red | Ninguna; sin permiso `INTERNET` | Privacidad por diseño. CI lo verifica |
| Copias de seguridad de Android | `allowBackup=false` y reglas de extracción vacías | La app no guarda datos que deban salir del dispositivo |
| Distribución | GitHub Releases, APK firmado | Decisión del mantenedor |

## Pendiente de decidir

- Papelera propia frente a papelera nativa (`MediaStore.createTrashRequest` solo aplica a medios del propio `MediaStore`; hay que comprobar el comportamiento por versión y fabricante en dispositivo real).
- Forma de firmar los APK de release (gestión de la clave fuera del repo).
