# Arquitectura

## Visión general

Una sola `Activity` (`MainActivity`) con Jetpack Compose y Navigation Compose. Un único módulo Gradle (`:app`); se dividirá por capas cuando el tamaño lo justifique.

```
ui  →  domain  →  data
```

- **ui**: pantallas Compose, `ViewModel`s (Hilt) y navegación. No accede a Android de bajo nivel.
- **domain**: modelos y casos de uso en Kotlin puro, sin dependencias de Android siempre que sea posible. Define interfaces (p. ej. `StorageInfoSource`) que implementa `data`.
- **data**: implementaciones sobre APIs de Android (`MediaStore`, `StorageStatsManager`, `UsageStatsManager`, SAF, Room para registro y papelera). Incluye la base de datos Room `criba.db` con las tablas `action_log` y `trash_item`, esquema exportado en `app/schemas` y sin migración destructiva.

La dependencia apunta siempre hacia abajo; `data` se enlaza con `domain` mediante módulos Hilt (`DataModule`).

## Componentes previstos

Escáneres como casos de uso independientes y testeables: `LargeFilesScanner`, `DuplicatesScanner`, `UnusedAppsScanner`, `BackupVerifier`. Más: papelera, registro de acciones y `isProtected(path)` (punto único de decisión sobre rutas protegidas).

## Fuente de apps

`PackageManagerAppSource` lista solo apps con icono de lanzador (bloque `<queries>` con `MAIN`/`LAUNCHER` y `MAIN`/`HOME`; sin `QUERY_ALL_PACKAGES`) y marca como protegidas el lanzador y el teclado activos y los titulares de los roles de lanzador, teléfono y SMS. El tamaño y el último uso exigen el acceso de uso (`PACKAGE_USAGE_STATS`, comprobado con `AppOpsManager`): sin él, `sizes` y `lastUsedEpochSeconds` quedan a `null` y no se consultan `StorageStatsManager` ni `UsageStatsManager`. En `StorageStats`, `dataBytes` ya incluye la caché, así que la capa data mapea `AppSizes` restando la caché de `dataBytes`; de lo contrario `totalBytes` la contaría dos veces.

## Pantalla Duplicados

La pantalla escanea con `DuplicatesScanner` mostrando progreso y permitiendo cancelar. Cada grupo conserva una copia elegida por `KeepPolicy` o por el usuario; `DuplicatesReview` calcula el estado de cada copia restante (seleccionable, necesita copia verificada o protegida) y solo deja marcar las elegibles, sin poder marcar nunca la conservada. Al confirmar, `DuplicateRecheck` vuelve a hashear la copia conservada y las marcadas; solo las que coinciden se mueven con `ConfirmedDeletion` a la papelera, y el resto se cuenta como no verificado. El espacio no se libera hasta vaciar la papelera.

## Duplicados exactos

`FileContentHasher` calcula SHA-256 en streaming (búfer de 64 KiB, cancelable) sobre la ruta absoluta que reconstruye `ScanFile.toDeletionCandidate(storageRoot)`. Antes de leer exige que `DeletionPolicy` no marque la ruta como protegida, que ningún tramo sea un enlace simbólico, que sea un archivo regular y que su tamaño actual coincida con el del escaneo; si algo falla devuelve `null` y el archivo se ignora (nunca se supone igual). Antes de borrar, `DuplicateRecheck` vuelve a hashear la copia conservada y las candidatas: si la conservada no coincide con el hash del grupo no se confirma nada, y cada copia que cambió o no se puede leer se excluye.

## Pantalla Apps

La pantalla Apps llama a `UsageAccess.isGranted()` al reanudarse. Sin acceso de uso muestra una tarjeta explicativa con un botón a los ajustes del sistema y la lista de apps solo con nombre y «Sin datos de uso»: no se ejecuta `UnusedAppsScanner` ni se muestran candidatas ni cifras. Con acceso, muestra las candidatas de `UnusedAppsScanner` con tamaño total y desglose app/datos/caché, el motivo (sin usar desde hace N días o sin uso registrado en el último año) y un botón a los ajustes de la app. Criba nunca desinstala: solo informa y guía.

## Papelera propia

Los archivos que el usuario confirma se mueven con un `rename` atómico en el mismo volumen a `<almacenamiento>/.criba-trash/<id>/` (raíz del almacenamiento compartido: `Android/data` es otro montaje y el `rename` falla con EXDEV desde Descargas, comprobado en un Pixel; no se cae a copiar+borrar); nunca se copian ni se sobrescriben, y el espacio no se libera hasta vaciarla. El orden es: registrar la intención (`PENDING`) en `action_log` con la ruta original y el id de papelera, comprobar que la ruta canónica no es protegida, que es un archivo regular y que tamaño y fecha coinciden con el escaneo, mover con `ATOMIC_MOVE`, verificar origen y destino, añadir el elemento a `trash_item` y registrar el resultado. Si el proceso muere entre el movimiento y el índice, el registro queda `PENDING` con la ruta original y el archivo está en `.criba-trash/<id>/`: es una situación recuperable. Restaurar y vaciar vuelven a comprobar que la ruta canónica sigue dentro de la papelera; solo el vaciado borra definitivamente, y solo dentro de `.criba-trash/<id>/`.

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

| Acceso a Descargas | `MANAGE_EXTERNAL_STORAGE` (aprobado el 2026-11-03), lectura y, con confirmación, mover a la papelera propia | En Android 10+ MediaStore no devuelve a una app normal los archivos no multimedia (APKs, zips, documentos) de Descargas, y SAF no permite elegir la carpeta Descargas en Android 11+ |
| Rutas | Toda ruta pasa por `isProtected(path)` (domain) antes de analizarse o tocarse | Regla de seguridad 4 |

## Pendiente de decidir

- Papelera propia frente a papelera nativa (`MediaStore.createTrashRequest` solo aplica a medios del propio `MediaStore`; hay que comprobar el comportamiento por versión y fabricante en dispositivo real).
- Forma de firmar los APK de release (gestión de la clave fuera del repo).
