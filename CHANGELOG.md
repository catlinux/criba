# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y [SemVer](https://semver.org/lang/es/).

## [Sin publicar]

## [0.20.0] - 2026-11-04

### Añadido

- Modelo de dominio de la copia verificada (BackupVerifier, BackupGate, política de destino); todavía sin copia real ni interfaz.

## [0.19.0] - 2026-11-04

### Añadido

- Pantalla Duplicados (búsqueda de duplicados exactos por SHA-256 con progreso y cancelación, conservación de una copia, movimiento a la papelera tras verificación previa); las copias multimedia quedan bloqueadas hasta la copia verificada.

## [0.18.0] - 2026-11-04

### Añadido

- Hasher SHA-256 de archivos para duplicados, verificación previa al borrado (DuplicateRecheck) e inyección del escáner; todavía sin interfaz.

## [0.17.0] - 2026-11-04

### Añadido

- Modelo y escáner de dominio de duplicados exactos (DuplicatesScanner, KeepPolicy, DuplicateSelection); todavía sin datos reales ni interfaz.

## [0.16.0] - 2026-11-04

### Añadido

- Pantalla Apps con candidatas sin uso, tamaño desglosado y último uso, acceso de uso guiado y botón a los ajustes de la app; Criba no desinstala.

## [0.15.0] - 2026-11-04

### Añadido

- Fuente de datos real de apps (tamaño, último uso) sobre PackageManager, StorageStatsManager y UsageStatsManager, con comprobación del acceso de uso; permiso PACKAGE_USAGE_STATS y consulta de apps con icono de lanzador; sin QUERY_ALL_PACKAGES; todavía sin interfaz.

## [0.14.0] - 2026-11-04

### Añadido

- Modelo y escáner de dominio para detectar apps sin uso (UnusedAppsScanner); todavía sin datos reales ni interfaz.

## [0.13.0] - 2026-11-04

### Añadido

- Pantalla de papelera (restaurar, vaciar con confirmación, vaciado automático a los 30 días), historial de acciones y cifra de «en papelera» en la pantalla inicial; con esto la Fase 2 queda completa.

### Corregido

- La cabecera de las pantallas de revisión, papelera e historial ya no queda bajo la barra de estado; la cifra «En papelera» de la pantalla inicial se actualiza al volver.

## [0.12.0] - 2026-11-04

### Añadido

- Pantalla de revisión por categoría para elegir archivos y moverlos a la papelera con confirmación que muestra el número y los MB reales; los archivos multimedia aparecen bloqueados hasta tener copia verificada.

### Cambiado

- Los textos de la pantalla de Descargas y del informe ya no afirman que Criba nunca borra: analizar solo lee y mover a la papelera siempre pide confirmación.

## [0.11.0] - 2026-11-04

### Añadido

- TrashManager (mover a la papelera propia en `.criba-trash` de la raíz del almacenamiento, restaurar y vaciar) con comprobaciones previas y posteriores y registro de cada acción; todavía sin interfaz, la app no ofrece borrar.

## [0.10.0] - 2026-11-04

### Añadido

- Registro de acciones y índice de la papelera con Room (nueva dependencia AndroidX, sin red); la app todavía no borra nada.

## [0.9.0] - 2026-11-04

### Añadido

- Política de elegibilidad de borrado (DeletionPolicy) y valor de confirmación ConfirmedDeletion, base de la papelera; la app todavía no borra nada.

## [0.8.0] - 2026-11-03

### Añadido

- Pantalla «Informe de espacio recuperable» con total sin duplicados entre categorías.

### Cambiado

- Las búsquedas pasan de la pantalla inicial al informe; los textos con recuento usan plurales correctos.

## [0.7.0] - 2026-11-03

### Añadido

- Análisis de carpetas vacías y sin uso en una carpeta elegida con el selector del sistema (solo lectura, rutas protegidas omitidas).

## [0.6.0] - 2026-11-03

### Añadido

- ScreenshotsScanner (capturas de pantalla agrupadas por antigüedad, solo lectura) y botón en la pantalla inicial.

## [0.5.0] - 2026-11-03

### Añadido

- Acceso de solo lectura a Descargas con MANAGE_EXTERNAL_STORAGE (se pide al pulsar el botón), DownloadsFileSource, detección de APKs con PackageManager y resultados en la pantalla inicial.

### Corregido

- La pantalla inicial se desplaza: en horizontal o con más contenido, la sección de Descargas quedaba fuera de la pantalla.

## [0.4.0] - 2026-11-03

### Añadido

- isProtected(path) (rutas protegidas) y escáneres de solo lectura OldDownloadsScanner y OldApksScanner en domain.

## [0.3.0] - 2026-11-03

### Añadido

- MediaStoreFileSource (solo lectura, imágenes, vídeo y audio), permisos de lectura de medios y botón para buscar archivos grandes en la pantalla inicial.

## [0.2.0] - 2026-11-03

### Añadido

- LargeFilesScanner (solo lectura) y modelos ScanFile, FileSource y ScanResult en domain.

## [0.1.0] - 2026-11-03

### Añadido

- Estructura base del proyecto Android: Kotlin, Jetpack Compose (Material 3), Navigation Compose y Hilt, con minSdk 26 y target 36.
- Pantalla inicial que muestra el espacio libre y usado real del almacenamiento interno.
- Recursos de texto en español, catalán e inglés.
- CI con GitHub Actions (tests, lint, build y comprobación de que no se declara `INTERNET`).
- Licencia GPLv3, README, `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` y `docs/ISSUES.md`.
