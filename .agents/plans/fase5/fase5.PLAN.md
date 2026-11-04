# Fase 5 – Copia verificada y puerta de seguridad para multimedia

Objetivo: poder liberar espacio de fotos, vídeos y audio (regla 5) solo cuando existe una copia **verificada con SHA-256 en un destino que elige el usuario**. Hoy esos archivos salen como «Necesita copia verificada» (revisión y duplicados) y no se pueden marcar; esta fase los desbloquea, y solo con prueba.

## Decisiones de diseño (de la sesión principal; el mantenedor puede cambiarlas)

1. **Solo la opción (a) de la regla 5:** copia propia verificada. La opción (b) «confirmación manual guiada» (p. ej. Google Fotos) queda fuera de esta fase y se apunta en `TODO.md`: no se puede comprobar desde Criba y no se finge.
2. **Destino:** carpeta elegida con el selector SAF (`ACTION_OPEN_DOCUMENT_TREE`, permiso persistible). Se copia a una subcarpeta `Criba-copia/` dentro de ella; nunca se sobrescribe (si existe un nombre, se renombra con sufijo) y nunca se borra nada del destino. El destino no puede estar dentro de `.criba-trash`, de la carpeta de origen del archivo ni coincidir con ella.
3. **Copia verificada = hash del origen leído mientras se copia == hash del destino releído desde cero + mismo tamaño.** Una copia a medias o con hash distinto se marca fallida y no vale. Copia en streaming (64 KiB), cancelable, con progreso.
4. **Registro de copias** en Room (`backup_copy`: ruta de origen, tamaño, fecha de modificación, SHA-256, URI del destino, fecha de verificación). Migración 1→2 con esquema exportado; sin migración destructiva. Cada copia y cada fallo quedan en el historial de acciones (regla 7).
5. **Puerta de seguridad (`BackupGate`, dominio puro):** un archivo multimedia pasa de `NEEDS_BACKUP` a `SELECTABLE` solo si existe un registro cuyo origen coincide en ruta, tamaño y fecha **y**, justo antes de mover a la papelera, se **re-verifica** hasheando de nuevo origen y destino (el destino debe seguir accesible y con el mismo hash). Si el destino ya no está (tarjeta sacada, carpeta borrada) o algo difiere, el archivo vuelve a bloqueado y se explica por qué.
6. **Aviso honesto:** si el destino está en el almacenamiento interno del propio móvil, se avisa de que no protege contra pérdida o rotura del dispositivo (se permite, pero se dice). No se afirma «a salvo» salvo «copia verificada en <carpeta>».
7. **Nada nuevo en permisos:** SAF no requiere permisos. `MANAGE_EXTERNAL_STORAGE` sigue siendo necesario para mover a la papelera.
8. **Fuera de alcance:** restaurar desde la copia, sincronización, copias periódicas, cifrado.

## Pasos (ejecutor: AgentRelay; revisión y pruebas en el Pixel: sesión principal)

1. [ ] **Dominio (0.20.0)** · `BackupRecord`, `BackupIndex` (interfaz), `BackupCopier`/`BackupDestination` (interfaces de lectura/escritura abstractas), `BackupVerifier` (copia + hash origen vs destino releído, nombres sin colisión, fallos tipificados), `BackupGate` (qué multimedia queda desbloqueada, re-verificación previa), con tests exhaustivos con fakes (hash distinto, destino desaparecido, origen cambiado, colisión de nombres, cancelación, destino dentro de la papelera o del origen).
2. [ ] **Datos (0.21.0)** · copia por SAF (`DocumentFile`/`ContentResolver`) con hash en streaming y relectura, `RoomBackupIndex` y migración 1→2 (test de migración), registro en `action_log`, módulo Hilt. Prueba en el Pixel con una carpeta SAF real y comparación con `sha256sum` de adb.
3. [ ] **Interfaz y cierre (0.22.0)** · pantalla «Copia de seguridad» (elegir destino, seleccionar multimedia, progreso, cancelar, resultado por archivo), integración de `BackupGate` en la revisión y en Duplicados (multimedia desbloqueada solo con copia verificada y re-verificada), textos es/ca/en, avisos de destino interno. Revisión completa en el Pixel.

Imprevistos de diseño: el ejecutor se para y los anota aquí.
