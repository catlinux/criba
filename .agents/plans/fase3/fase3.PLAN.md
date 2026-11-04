# Fase 3 – Apps: tamaño, último uso y candidatas a desinstalar

Objetivo: mostrar qué apps ocupan más y cuáles no se usan, con cifras medibles, y guiar al usuario a los ajustes del sistema. **Criba no desinstala nada** (regla 8): solo informa y abre la pantalla de ajustes de la app. Las apps no son archivos: no entran en el total recuperable del informe ni en la papelera.

## Decisiones de diseño (de la sesión principal; el mantenedor puede cambiarlas)

1. **Sin `QUERY_ALL_PACKAGES`.** Se declara `<queries>` con el intent `MAIN`/`LAUNCHER`: Android 11+ devuelve así las apps con icono de lanzador, que son las que un usuario desinstalaría. Menos permisos (regla 6) y suficiente. Las apps sin icono no se listan (es una limitación que se documenta, no se disimula).
2. **Acceso de uso (`PACKAGE_USAGE_STATS`)**, concedido por el usuario en ajustes (ya previsto en la regla 6): da el último uso (`UsageStatsManager`) y el tamaño de otras apps (`StorageStatsManager.queryStatsForPackage`). Se pide solo cuando el usuario lo inicia, con una explicación. Sin él: lista de apps sin cifras de tamaño ni de uso, y un aviso claro. Nunca se inventan datos: «Sin datos» cuando no hay.
3. **Tamaño por app = `appBytes + dataBytes + cacheBytes`** de `StorageStats` (medible). Se muestra desglosado.
4. **Último uso:** el máximo `lastTimeUsed` de `queryUsageStats` en `INTERVAL_BEST` sobre el último año. Android conserva el histórico limitado: si no hay registro, es «Sin datos de uso» (no «nunca usada»). Una app es candidata («sin uso») solo si hay dato de último uso anterior al umbral (90 días por defecto) **o** si la fecha de instalación es anterior al umbral y no consta ningún uso en el año consultado; en ese segundo caso el motivo se muestra como «Sin uso registrado en el último año», con su límite explícito.
5. **Excluidas siempre:** apps del sistema (`FLAG_SYSTEM`, salvo actualizadas por el usuario que se muestran como no candidatas), la propia Criba y apps de lanzador/teclado/servicios por defecto (rol de lanzador, teclado activo). Lo no excluible por seguridad se marca «Protegida».
6. **Acción guiada:** botón «Abrir ajustes de la app» (`ACTION_APPLICATION_DETAILS_SETTINGS`) desde donde el usuario desinstala. Nada de `ACTION_DELETE`/`ACTION_UNINSTALL_PACKAGE` ni automatizar toques.

## Pasos (ejecutor: AgentRelay esfuerzo alto; revisión y pruebas en el Pixel: sesión principal)

1. [ ] **Dominio (0.14.0)** · `AppUsageInfo`, `AppSource`, `UnusedAppsScanner` (reloj inyectable, umbral en días, orden por tamaño, motivos), exclusiones y tests exhaustivos (borde del umbral, sin datos, nunca usada, sistema, Criba, orden y desempate).
2. [ ] **Datos (0.15.0)** · `PackageManager` + `StorageStatsManager` + `UsageStatsManager`, comprobación del acceso de uso (`AppOpsManager`), `<queries>` y `PACKAGE_USAGE_STATS` en el manifiesto, módulo Hilt. Prueba en el Pixel contrastando tamaños con `dumpsys diskstats`/ajustes del sistema.
3. [ ] **Interfaz y cierre (0.16.0)** · pantalla «Apps» (lista con tamaño desglosado y último uso, candidatas arriba con su motivo), explicación + botón a «Acceso de uso», botón «Abrir ajustes de la app», estados sin permiso/sin datos, enlace desde la pantalla inicial y el informe. Revisión completa en el Pixel.

Imprevistos de diseño: el ejecutor se para y los anota aquí.
