# Fase 1 – Escáner de lectura

Objetivo: informe de espacio recuperable (archivos grandes, descargas y APKs viejos, capturas, carpetas huérfanas). **Solo lectura: ningún código de esta fase borra, mueve ni modifica archivos.** Reglas en `CLAUDE.md`.

Diseño: los escáneres viven en `domain`, son Kotlin puro y leen de interfaces `FileSource` (sin métodos de escritura). `data` implementa esas interfaces con MediaStore/SAF. Cada paso = una rama `feat/...`, tests unitarios, versión menor y entrada en CHANGELOG. Los números de versión son los de la lista; `versionCode` sube 1 por paso (`app/build.gradle.kts`).

Pasos (ejecutor por defecto: AgentRelay, esfuerzo medio; revisa la sesión principal):

1. [x] **LargeFilesScanner (domain)** · `ScanFile`, `FileSource`, `ScanResult`, `LargeFilesScanner` + tests. Versión 0.2.0.
2. [x] **MediaStoreFileSource (data)** · implementa `FileSource` con MediaStore (Downloads, Images, Video, Audio, Files), binding Hilt, solicitud de permisos de lectura mínimos. Probar en dispositivo real (no verificable por tests unitarios). 0.3.0.
3. [x] **isProtected + escáneres de Descargas y APKs (domain)** · `isProtected(path)` con tests (Android/data, Android/obb, sistema, bases de datos, claves), `OldDownloadsScanner` y `OldApksScanner` sobre `FileSource` e interfaces. 0.4.0.
4. [x] **Acceso a Descargas (data + ui)** · `MANAGE_EXTERNAL_STORAGE` (aprobado), `DownloadsFileSource` con `java.io.File` (solo lectura, pasando por `isProtected`), detección de APKs con `PackageManager`, flujo de permiso y resultados en la pantalla. Probar en Pixel. 0.5.0.
5. [x] **Capturas de pantalla** · agrupadas por antigüedad. 0.6.0.
6. [x] **Carpetas vacías o sin uso (domain + data + ui)** · el usuario elige una carpeta con SAF (`OpenDocumentTree`, sin permiso persistente); se resumen sus subcarpetas directas (archivos, bytes, última modificación) y se marcan vacías o sin cambios en más de un año. Se sustituye «carpetas huérfanas de apps desinstaladas»: sin ver todas las apps instaladas daría falsos positivos y `Android/data` y `Android/obb` son intocables. Toda ruta reconstruida pasa por `isProtected`. 0.7.0.
7. [ ] **Informe de espacio recuperable (ui)** · pantalla con cifras medidas y sin botón de borrar. 0.8.0.

Imprevistos de diseño: el ejecutor se para y los anota aquí.
