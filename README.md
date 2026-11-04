# Criba

Limpiador de almacenamiento para Android, seguro y sin publicidad. Encuentra archivos grandes, duplicados y apps sin uso, y verifica una copia de seguridad antes de borrar nada.

> **Estado: Fase 2 completa.** La papelera permite restaurar archivos, vaciarla con confirmación e incluye el historial de acciones. La carpeta oculta `.criba-trash` persiste si se desinstala Criba.

## Filosofía

Criba ayuda a recuperar espacio de forma **segura y reversible**:

- Escanear nunca borra; el modo por defecto es solo lectura.
- Todo borrado va primero a una papelera y exige confirmación explícita, con los archivos y los MB reales.
- Las fotos, vídeos y audio no se proponen para borrar sin una copia verificada con SHA-256.
- Sin internet, sin cuentas, sin analítica, sin publicidad. La app no declara el permiso `INTERNET`.
- Cada acción queda en un registro local auditable, con posibilidad de deshacer.

## Lo que Criba NO hace

- No acelera el móvil ni «limpia la RAM»: en Android moderno no sirve y no lo vamos a fingir.
- No cierra otras apps ni mata procesos. Solo informa y te guía a los ajustes del sistema (forzar detención, restringir batería, desinstalar).
- No sabe si algo está en Google Fotos (no hay API pública fiable): ofrece copia propia verificada o confirmación manual guiada.
- No muestra cifras que no sean medibles y reales.

## Requisitos

- Para usarla: Android 8.0 (API 26) o superior.
- Permisos: solo lectura de medios (fotos, vídeo y audio) en Android 13 o superior; en Android 8–12, lectura del almacenamiento externo. No declara `INTERNET`. Para analizar la carpeta Descargas (APKs, zips, documentos) pedirá, solo cuando tú lo inicies, el acceso a todos los archivos (`MANAGE_EXTERNAL_STORAGE`) y lo usa para leer y, solo si tú eliges y confirmas, para mover archivos a la papelera.
- Para compilarla: JDK 17 y Android SDK con la plataforma 36 (`ANDROID_HOME` definido).

## Compilar y probar

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/`. En Windows usa `gradlew.bat`.

## Distribución

Solo mediante GitHub Releases (APK firmado). No hay publicación en F-Droid ni en Google Play por ahora.

## Documentación

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): capas y decisiones técnicas.
- [docs/ROADMAP.md](docs/ROADMAP.md): fases del proyecto.
- [docs/ISSUES.md](docs/ISSUES.md): milestones e issues listos para importar.
- [CHANGELOG.md](CHANGELOG.md) y [TODO.md](TODO.md).

## Licencia

GPLv3: consulta [LICENSE](LICENSE). Titular: catlinux.
