# Fase 4 – Duplicados exactos

Objetivo: encontrar archivos con contenido idéntico (SHA-256) y proponer borrar las copias sobrantes **conservando siempre al menos una**. Los duplicados «similares» (imágenes parecidas) quedan fuera de esta fase y se descartan por ahora: dan falsos positivos y no se pueden verificar con cifras reales.

## Decisiones de diseño (de la sesión principal; el mantenedor puede cambiarlas)

1. **Agrupar barato, hashear lo mínimo.** Primero por tamaño (descartando tamaño 0 y grupos de 1); solo los grupos con ≥2 archivos se hashean. Lectura en streaming con SHA-256 completo (no hash parcial como prueba final), en `Dispatchers.IO`, cancelable y con progreso (archivos/bytes procesados).
2. **Dominio puro:** `ContentHasher` (`fun interface`, `suspend fun sha256(file: ScanFile): String?`; `null` = ilegible, el archivo se ignora, nunca se supone igual). `DuplicatesScanner(source: FileSource, hasher: ContentHasher)` devuelve `DuplicateGroup(sha256, files, sizeBytes)` con `reclaimableBytes = sizeBytes × (n − 1)`.
3. **Qué conservar:** `KeepPolicy` determinista: se conserva la copia con ruta fuera de `Download`/capturas/papelera si la hay; si no, la más antigua (`modifiedEpochSeconds`), y desempate por ruta. El usuario puede cambiar la conservada; **invariante comprobada en dominio y en la selección: nunca se puede marcar para borrar todas las copias de un grupo**. Una copia que se vuelve ilegible o cambia (tamaño/fecha) desde el escaneo invalida su grupo.
4. **Seguridad de borrado:** nada nuevo. Las copias a borrar pasan por `DeletionPolicy`/`ConfirmedDeletion`/papelera de la Fase 2. Antes de mover a la papelera se **vuelve a hashear** la copia a borrar y la conservada y deben coincidir; si no, no se mueve y se informa.
5. **Multimedia (regla 5):** la regla vigente sigue mandando. Las copias multimedia salen como `NEEDS_BACKUP` y no se pueden borrar hasta la Fase 5, aunque quede otra idéntica en el móvil (no es una copia verificada en un destino elegido por el usuario). **Decisión pendiente del mantenedor:** si prefiere permitir borrar duplicados multimedia cuando se conserva una copia con hash idéntico (todo va igualmente a la papelera reversible), habrá que cambiar la regla 5 en `CLAUDE.md`; no se hace sin su aprobación.
6. **Totales honestos:** los duplicados entran en el informe como una categoría más (`ReportCategory.DUPLICATES`) pero `RecoverableCalculator` ya deduplica por identidad: una copia ya contada en otra categoría no suma dos veces.
7. **Alcance:** los mismos archivos que ya ve `FileSource` (MediaStore + Descargas con `MANAGE_EXTERNAL_STORAGE`). Se ignoran `.criba-trash`, `Android/data`, `Android/obb` y rutas protegidas (`isProtected`). Sin permisos nuevos.

## Pasos (ejecutor: AgentRelay; revisión y pruebas en el Pixel: sesión principal)

1. [ ] **Dominio (0.17.0)** · `ContentHasher`, `DuplicateGroup`, `DuplicatesScanner` (agrupación por tamaño, hash, ilegibles ignorados, grupos <2 descartados, orden por `reclaimableBytes` desc.), `KeepPolicy` y `DuplicateSelection` (invariante «al menos una conservada»), con tests exhaustivos (tamaño 0, mismo tamaño distinto contenido, ilegible, tres copias, invariante, desempates, cancelación, progreso).
2. [ ] **Datos (0.18.0)** · `FileContentHasher` (SHA-256 en streaming sobre la ruta absoluta, 64 KiB de búfer, comprobación de que el archivo no es symlink ni protegido), verificación previa al borrado (`DuplicateRecheck`), módulo Hilt. Prueba en el Pixel con archivos duplicados creados a propósito y comparación con `sha256sum` de adb.
3. [ ] **Interfaz y cierre (0.19.0)** · categoría «Duplicados» en informe y revisión (grupos expandibles, copia conservada marcada, no se puede desmarcar la última, progreso con cancelar, multimedia `NEEDS_BACKUP`), integración con `ConfirmedDeletion` y papelera, textos es/ca/en. Revisión completa en el Pixel.

Imprevistos de diseño: el ejecutor se para y los anota aquí.
