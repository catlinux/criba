@AGENTS.md

# Criba

App Android (Kotlin + Jetpack Compose, Material 3) de liberación de espacio, segura y sin publicidad. Repo: `catlinux/criba` (cuenta `catlinux`, alias SSH `github-catlinux`). Detalle en `docs/ARCHITECTURE.md` y `docs/ROADMAP.md`.

## Decisiones cerradas

- minSdk 26, target la última estable. `applicationId` `io.github.catlinux.criba`. Licencia GPLv3.
- Distribución solo por GitHub Releases (APK firmado). No F-Droid, no Google Play.
- Interfaz en español, con recursos preparados para catalán (`values-ca`) e inglés (`values-en`).
- Sin internet, analítica, cuentas, publicidad ni telemetría. **Nunca declarar el permiso `INTERNET`** (CI lo comprueba).

## Qué NO es Criba

No acelera el móvil, no «limpia la RAM», no mata procesos y no muestra MB de RAM liberada. Toda cifra mostrada debe ser medible y real.

## Reglas de seguridad no negociables

1. Escanear nunca borra: el modo por defecto es solo lectura.
2. Todo borrado es reversible: papelera nativa (`MediaStore.createTrashRequest`) o papelera propia con restauración; nunca borrado definitivo inmediato.
3. Confirmación explícita del usuario antes de toda acción destructiva, mostrando qué archivos y cuántos MB reales.
4. Lista protegida: jamás tocar archivos de sistema, `Android/data` ni `Android/obb` de otras apps, bases de datos, claves, ni carpetas no elegidas por el usuario. Toda ruta nueva pasa por la función central `isProtected(path)`, con tests.
5. Multimedia: no se propone borrar sin copia verificada (SHA-256 íntegro en un destino elegido por el usuario). No fingir comprobación de Google Fotos: ofrecer (a) copia propia verificada o (b) confirmación manual guiada.
6. Permisos mínimos: `MediaStore`, `StorageStatsManager`, `UsageStatsManager` (acceso a uso concedido por el usuario) y el selector de carpetas SAF. `MANAGE_EXTERNAL_STORAGE` solo con decisión explícita del mantenedor: el 2026-11-03 la aprobó, únicamente para leer Descargas (APKs, zips, documentos, que MediaStore no muestra a una app normal). Se pide solo cuando el usuario lo inicia y con una explicación; hasta la Fase 2 se usa solo para leer.
7. Registro local auditable de cada acción (qué, cuándo, resultado), con deshacer.
8. Segundo plano: Criba solo informa y guía a ajustes del sistema; nunca automatiza cerrar apps, restringir batería ni desinstalar.

## Arquitectura

Una Activity, Compose, Navigation Compose. Capas `ui` → `domain` (casos de uso) → `data` (MediaStore, StorageStats, SAF, Room). Hilt, corrutinas y Flow. Escáneres como casos de uso independientes y testeables. Empezar con un solo módulo Gradle.

## Forma de trabajar

- Una rama por tarea (`feat/…`, `fix/…`, `docs/…`, `chore/…`), Conventional Commits, PR contra `main`. Nada de push directo a `main`.
- Cada funcionalidad nueva lleva tests unitarios; la lógica de seguridad (`isProtected`, verificación de copias, papelera) con cobertura alta.
- No añadir dependencias sin justificarlas en el PR; preferir la biblioteca estándar y AndroidX. Todas van en `gradle/libs.versions.toml`.
- No inventar APIs de Android: si hay duda por versión o fabricante, indicarlo y proponer cómo comprobarlo en un dispositivo real.
- Mantener este archivo, `ARCHITECTURE.md` y `ROADMAP.md` al día cuando cambie una decisión.

## Entorno de compilación

- JDK 17 y `ANDROID_HOME` definido. No dejar `local.properties` con `sdk.dir` en Windows: lint lo marca (`PropertyEscape`) en cualquier formato.
- Si el Kotlin daemon se queda sin memoria: `-Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.compiler.execution.strategy=in-process`.
- Comprobación completa: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`.
- La versión está en `versionName`/`versionCode` de `app/build.gradle.kts`.
