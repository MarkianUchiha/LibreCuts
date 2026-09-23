---
tipo: constitución
estado: aprobada
aprobada: 2026-09-22
actualizado: 2026-09-22
---

# LibreCuts — Constitución

Las reglas que no se rompen sin una decisión explícita anotada en `Decisiones.md`. El detalle de
rutas, versiones y arquitectura vive en `CLAUDE.md`; aquí van las prohibiciones y el porqué.

## Stack

Fijo mientras no haya una decisión que lo cambie:

- Kotlin 2.0.21, JDK 17, AGP 8.7.1, Gradle 8.9. `minSdk` 26.
- `compileSdk`/`targetSdk`: 34 hoy, **36 en cuanto se cierre M-236**. No es una versión que se
  elija: la fija el plazo vigente de Google Play (ver `SPEC.md`, *Distribución*), así que sube
  cuando Play lo exija y no se congela con el resto del stack.
- Views tradicionales con XML. **No Jetpack Compose.**
- ExoPlayer 2.19.1 legacy (`com.google.android.exoplayer2`), **no** AndroidX Media3.
- `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` (FFmpeg 8.0).
- MVVM "lite": ViewModel + `StateFlow`. Sin inyección de dependencias, sin Repository.

## Prohibiciones

1. **No mezclar Media3 con ExoPlayer 2.** Comparten conceptos y no los tipos; mezclarlos deja el
   player a medio migrar y sin forma de probarlo. La migración, si se hace, es un cambio propio y
   completo.
2. **No cambiar a `ffmpeg-kit-full` (LGPL) sin revisar la licencia.** Se pierde `libx264`, que es
   el fallback de software del export cuando `h264_mediacodec` falla. Ver `Decisiones.md`.
3. **No refactorizar `VideoEditingActivity` en masa.** Son ~9000 líneas sin tests que las cubran.
   Los cambios ahí son quirúrgicos; lo que se extrae, se extrae con un test que lo respalde.
4. **No `connectedAndroidTest`.** Desinstala la app y se lleva sus datos, incluidos los archivos
   de `cacheDir` de los que depende el proyecto abierto. Se usa `node scripts/test-tablet.mjs`.
5. **No agregar texto en inglés a la interfaz.** Todo string nuevo va a `res/values/strings.xml`.
   Las traducciones las hace Weblate upstream: **solo se edita `values/`**, nunca `values-<lang>/`.
6. **No suprimir deprecaciones a nivel de clase grande.** Una API deprecada sin reemplazo se aísla
   en su propio archivo con `@file:Suppress("DEPRECATION")` y un comentario del porqué.
7. **No meter secretos ni rutas personales en el repo.** No aplica hoy (la app no tiene backend),
   y si algún día lo tiene, sigue sin aplicar dentro del APK.
8. **No romper el preview contra el export.** Cualquier cambio que afecte cómo se ve un clip tiene
   que dejarlos coincidiendo (CA2 del `SPEC.md`), y se verifica antes de cerrar la tarea.

## Cómo se construyen los comandos de FFmpeg

- Un solo `String` concatenado, ejecutado con `FFmpegKit.execute(String)`. Las rutas van entre
  comillas dobles escapadas.
- Los grafos complejos se arman como lista de etapas `[in]filtro[out]` unidas con `;` dentro de
  `-filter_complex`.
- Antes de usar un filtro o encoder que no esté en la lista verificada de `CLAUDE.md`, se
  comprueba que exista en el binario. No se asume por estar en la documentación de FFmpeg.
- Todo el render pasa por `services/FFmpegRenderEngine.kt`. Nada de ejecutar FFmpeg suelto desde
  una Activity.

## Convenciones

- Clases PascalCase, funciones camelCase, backing fields `_foo` + `foo: StateFlow` con
  `asStateFlow()`.
- IDs de vista en camelCase. Layouts en snake_case con prefijo por tipo (`activity_`, `dialog_`,
  `item_`, `bottom_sheet_`).
- ViewBinding en código nuevo fuera de `VideoEditingActivity` (que usa `findViewById` y se deja
  así por la regla 3).
- Comentarios en español, explican el **porqué**. Los heredados en inglés se dejan salvo que se
  toque esa línea.
- Dependencias nuevas al catálogo `gradle/libs.versions.toml`, no a `app/build.gradle`.
- Logs con `private val TAG = "NombreClase"`.
- Un código de error nuevo se agrega a `utils/ErrorCode.kt` y se documenta en el wiki.

## Verificación

Ninguna tarea se cierra sin correr y mostrar la salida real de:

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest   # lo mismo que corre CI
node scripts/test-tablet.mjs                          # instrumentados, en la tablet
```

Lo que toca la vista previa o el export se verifica además en el dispositivo, con capturas o
medición sobre ellas. "Se ve bien" no es verificación.

## Proceso

- **Nivel SDD: Medio.** No se escribe código de producción sin spec aprobada en `specs/`.
- El orden es spec → checkpoint humano → plan (skill `architect`) → tareas (issues de Linear,
  skill `issue-protocol`) → implementación con TDD por tarea.
- Un cambio que contradice una spec obliga a actualizar la spec en el mismo turno.
- Las decisiones que cambian algo de este documento se anotan en `Decisiones.md`, con fecha.
