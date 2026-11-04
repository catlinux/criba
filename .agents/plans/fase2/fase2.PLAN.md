# Fase 2 – Papelera y borrado seguro

Objetivo: poder liberar espacio **de forma reversible**. Es la primera fase que borra, así que cada paso se revisa a fondo y se prueba en el Pixel con archivos de prueba propios (nunca con los del usuario). Reglas en `CLAUDE.md` (2, 3, 4, 5 y 7 son las que aplican).

## Decisiones de diseño (cerradas por la sesión principal; el mantenedor puede cambiarlas)

1. **Papelera propia, no la nativa, en esta fase.** `MediaStore.createTrashRequest` solo sirve para medios y exige un diálogo del sistema por lote; además todo lo multimedia queda bloqueado hasta la Fase 5 (regla 5). Lo que sí se puede borrar ya: archivos **no multimedia** (APKs, zips, documentos, otros) que Criba ve por acceso a todos los archivos.
2. **Política de elegibilidad (`DeletionPolicy`, domain, Kotlin puro).** Para cada archivo devuelve `Allowed`, `RequiresVerifiedBackup` (imagen, vídeo o audio por extensión/MIME: se muestra pero no se puede seleccionar hasta la Fase 5) o `Protected` (`isProtected`, o ruta no absoluta, o dentro de la propia papelera). Nada borrable sin pasar por aquí.
3. **Borrar = mover a la papelera de la app**, en el mismo volumen (`renameTo`/`Files.move` atómico), en `Android/data/<applicationId>/files/trash/<id>/`. Nunca se sobrescribe; nombre único por id. El espacio **no se libera hasta vaciar la papelera**: la UI lo dice siempre («En papelera: X; se libera al vaciarla»). Ninguna cifra de «liberado» antes de ese momento.
3b. **Vaciado definitivo solo desde la papelera**, con confirmación explícita y mostrando los MB reales; automático solo tras la retención (por defecto 30 días, configurable más adelante). Nunca borrado definitivo inmediato de un archivo del usuario.
4. **Confirmación no eludible.** Un valor `ConfirmedDeletion` (domain) con constructor privado solo lo crea la pantalla de confirmación al aceptar el usuario, con la lista exacta de archivos y los bytes reales; `TrashManager.moveToTrash` solo acepta ese valor. Así ningún código puede borrar sin pasar por la confirmación.
5. **Registro auditable (regla 7) con Room.** Tabla `action_log` (id, momento, tipo: MOVED_TO_TRASH / RESTORED / PURGED / FAILED, ruta original, id de papelera, bytes, resultado, mensaje) y `trash_item` (id, ruta original, ruta en papelera, bytes, SHA-256 opcional, momento, estado). Se escribe la intención **antes** de mover y el resultado **después**. Room es una dependencia nueva (justificar en el CHANGELOG): estándar de AndroidX, sin red, y ya hay KSP.
6. **Restaurar**: a la ruta original si está libre y su carpeta existe; si no, con sufijo « (restaurado)» y se avisa. Se vuelve a comprobar `isProtected` en la ruta destino.
7. **Comprobaciones antes de mover**: la ruta canónica pasa `isProtected`; el archivo existe, es un archivo regular y no un enlace simbólico; el tamaño y la fecha coinciden con lo que vio el escáner (si cambió desde el escaneo, se omite y se registra). Tras mover se verifica que el origen ya no existe y el destino tiene el mismo tamaño.

## Pasos (ejecutor por defecto: AgentRelay con esfuerzo medio o alto; revisión y pruebas en dispositivo: sesión principal)

1. [x] **`DeletionPolicy` y `ConfirmedDeletion` (domain)** · reglas 1, 2 y 4 de arriba, con tests exhaustivos (extensiones multimedia en mayúsculas, rutas protegidas, dentro de la papelera, sin extensión). 0.9.0.
2. [ ] **Room: registro y papelera (data)** · entidades, DAO, base de datos con esquema exportado, interfaces `ActionLog` y `TrashIndex` en domain con fakes para tests; migración inicial v1. Verificar en el Pixel. 0.10.0.
3. [ ] **`TrashManager` (data)** · mover, restaurar y vaciar con todas las comprobaciones del punto 7, registro antes/después y tests con `TemporaryFolder` (colisiones de nombre, origen que desaparece, enlace simbólico, tamaño que cambia, destino ocupado, fallo a mitad). Probar en el Pixel con archivos de prueba. 0.11.0.
4. [ ] **Selección y confirmación (ui)** · listas por categoría con casillas (los multimedia aparecen bloqueados con el motivo), diálogo de confirmación con archivos y MB reales, progreso y resultado. 0.12.0.
5. [ ] **Pantallas de papelera e historial (ui)** · restaurar, vaciar (con confirmación), ver el registro, cifras honestas de «en papelera». Cierre de la fase y revisión de seguridad completa en el Pixel. 0.13.0.

Imprevistos de diseño: el ejecutor se para y los anota aquí.
