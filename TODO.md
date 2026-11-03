# TODO

## Alta prioridad

- Crear en GitHub las milestones y los issues de `docs/ISSUES.md` (falta `gh` en este equipo).
- Definir cómo se firma el APK de release (clave fuera del repo) y el workflow de Release.

## Media

- Pluralizar los textos de la pantalla inicial (hoy sale «1 carpetas» o «1 archivos») con `plurals` en los tres idiomas; encaja en el paso 7 de la Fase 1 (informe).
- Migrar `hiltViewModel` al paquete `androidx.hilt.lifecycle.viewmodel.compose` (aviso de obsolescencia al compilar).
- Decidir `QUERY_ALL_PACKAGES` y el acceso de uso en la Fase 3: sin ellos «ya instalada» no siempre se detecta.
- Decidir papelera propia frente a nativa (ver `docs/ARCHITECTURE.md`).
- Actualizar dependencias señaladas por lint (AGP 8.13.x, Compose BOM, Kotlin, Hilt) cuando se apruebe la Fase 0.

## Descartado

- F-Droid y Google Play: decisión del mantenedor, solo GitHub Releases.
- Funciones de «acelerar» o «limpiar RAM»: no sirven en Android moderno.
