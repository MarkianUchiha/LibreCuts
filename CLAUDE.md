# LibreCuts (fork) — memoria del proyecto

Editor de video Android, FOSS, 100% local. Fork de `tharunbirla/LibreCuts` (remote `upstream`); el nuestro es `origin` (`MarkianUchiha/LibreCuts`).
Distribución de upstream: GitHub Releases, F-Droid, Obtainium. **La nuestra es Google Play (canal principal) + GitHub Releases** (ver `SPEC.md` y `Decisiones.md` D13); eso obliga a mantener el `targetSdk` al día, hoy incumplido (M-236).

## Nivel SDD: Medio

Evaluado el 2026-09-20 con la rúbrica de la skill `sdd`: **11/18** — costo del error 2, densidad de reglas 3, vida útil 3, estado de la base 3, acuerdo externo 0, superficie de integración 0. El override de "código heredado" fija el mismo piso, así que no sube.

Artefactos que corresponden a Medio, todos en la raíz salvo las specs de feature:

| Archivo | Qué es | Estado |
|---|---|---|
| `SPEC.md` | Producto: qué queremos que sea la app | Propuesta, pendiente de aprobación |
| `SPEC-ACTUAL.md` | Descriptiva: qué hace hoy | En progreso (secuencia, congelar, máscara, transiciones) |
| `CONSTITUTION.md` | Stack, prohibiciones, convenciones | Propuesta, pendiente de aprobación |
| `Decisiones.md` | Una entrada por decisión que costaría volver a tomar | D1–D8 más las abiertas |
| `specs/<feature>.md` | Una por feature | `congelar-cuadro`, `mascara-edicion`, `transicion-preview` |

Tareas como issues de Linear (ver Seguimiento). Checkpoint humano entre spec y plan.

Por ser base heredada, la primera spec va **descriptiva**: `SPEC-ACTUAL.md` con lo que la app hace hoy, marcando `[SIN VERIFICAR]` lo que no se confirmó contra el código o el dispositivo. La spec de lo deseado viene después, con un diff explícito entre ambas.

Comando de verificación: los tres de la sección Comandos. Los tests instrumentados corren aparte, en la tablet, y no entran en esa corrida.

## Meta del fork

Fork **propio** (no se planea mandar PRs a upstream). En orden:
1. Base limpia: quitar APIs deprecadas y código muerto.
2. Corregir los muchos bugs de la app (la mayoría sencillos pero molestos) y traducir al español.
3. Agregar funciones tipo CapCut; un par de diferenciadores, por definir.

## Seguimiento (Linear)

- Proyecto `librecuts`, equipo `MarkiDev`. Un issue por bug o función.
- Título: `[APP-ANDROID][LibreCuts] ...` (el Linear es compartido con otros proyectos del usuario; el tag deja claro de cuál es).
- Labels: `app-android` + `Bug` / `Feature` / `Improvement`.
- Dispositivo de prueba: tablet Honor ELN-W09, Android 13 (API 33), MagicOS 7.1, arm64, ~3.7 GB RAM. Instalar con `./gradlew installDebug`.
- Segundo dispositivo, **Android 16**: teléfono Honor ELP-NX9 (build ELP-N09), Android 16 (API 36), MagicOS 10.0, arm64-v8a, 1224×2700 px a 520 dpi (~377×831 dp), ~11 GB RAM. Necesario para todo lo que dependa del comportamiento de API 35/36 (M-236). Es teléfono, no tablet: sirve también para probar pantallas angostas. Su `persist.log.tag` es `M`, no `S` como en la tablet [SIN VERIFICAR si silencia los logs].
- Con los dos conectados, `adb` necesita `-s <serial>`.

## Probar en la tablet (lecciones aprendidas)

- **Los logs de las apps están silenciados** (`persist.log.tag=S` de MagicOS). Un `logcat` vacío NO prueba que no haya errores. Para diagnosticar: `adb shell setprop log.tag.<TAG> D` (se pierde al reiniciar; regresarlo con `S`).
- **Abrir el editor sin tocar la pantalla:** `am start -a android.intent.action.SEND -t video/mp4 --eu android.intent.extra.STREAM content://media/external/video/media/<id> -n com.tharunbirla.librecuts/.MainActivity`. Hay videos sintéticos de prueba en `/sdcard/Movies/librecuts_test*.mp4` (16:9 y 9:16); no usar los videos personales del usuario.
- **Girar:** `settings put system accelerometer_rotation 0` + `settings put system user_rotation 1|0`. Reinstalar la app la regresa a 0. Al terminar, dejar `accelerometer_rotation 1`.
- **Multitoque:** `sendevent` requiere root (bloqueado). Los gestos de dos dedos se prueban con tests instrumentados (`androidTest`, ver `TextOverlayPinchTest`).
- **Tests instrumentados:** `node scripts/test-tablet.mjs` (toda la suite), `node scripts/test-tablet.mjs <NombreTest>` (una clase, basta el nombre) o `--skip-build` para reusar los APK. Hace el ciclo compilar → `adb install -r -t` → `am instrument`. NO usar `connectedAndroidTest`: desinstala la app y borra sus datos.
- **Rendimiento en reposo:** `dumpsys gfxinfo com.tharunbirla.librecuts reset`, esperar 3 s y leer `Total frames rendered`. En reposo debe ser ~0.

Todo lo de este archivo se verificó contra el código el 2026-09-17. Lo que no se pudo confirmar está en **No confirmado**.

## Comandos (los mismos que corre CI en `.github/workflows/ci.yml`)

```bash
./gradlew assembleDebug        # build
./gradlew lintDebug            # lint
./gradlew testDebugUnitTest    # unit tests (hoy solo el template 2+2)
```

- JDK 17 obligatorio (CI usa Temurin 17).
- `local.properties` (gitignored) necesita `sdk.dir` con **slashes normales y los dos puntos escapados**: `sdk.dir=C\:/Users/.../Android/Sdk`. Con `\U` sin escapar Gradle falla con `Malformed \uxxxx encoding`; con `C:` sin escapar, el lint de AGP 8.13 da error `PropertyEscape`.
- El build de debug genera 3 APKs por ABI (`splits.abi`: armeabi-v7a, arm64-v8a, x86_64; sin universal).

## Versiones confirmadas

| Pieza | Versión | Fuente |
|---|---|---|
| Gradle wrapper | 8.13 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 8.13.2 (la 8.x más alta; la 9.x arrastra Gradle 9) | `gradle/libs.versions.toml` |
| Kotlin (plugin y stdlib resuelta) | 2.0.21 | `libs.versions.toml` + `dependencies` |
| kotlinx-coroutines | 1.8.1 (**transitiva**, no declarada) | `:app:dependencies` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 | `app/build.gradle` |
| Java target | 17 | `app/build.gradle` |
| ExoPlayer | **2.19.1 legacy** (`com.google.android.exoplayer2`), NO Media3 | `app/build.gradle` |
| ffmpeg-kit | `com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` → **FFmpeg 8.0** | POM de Maven Central + binario |
| Material | declarada 1.9.0, **resuelve 1.12.0** (la sube aboutlibraries) | `:app:dependencies` |
| Lottie | 3.4.0 | `app/build.gradle` |
| Gson | 2.10.1 | `app/build.gradle` |
| AboutLibraries | 11.2.3 (plugin por `buildscript`, no por catálogo) | `build.gradle` raíz |
| Lifecycle (ViewModel/runtime) | 2.8.6 | `libs.versions.toml` |

Las dependencias están mezcladas: unas en el catálogo `libs.versions.toml`, otras hardcodeadas en `app/build.gradle`. Si agregas una nueva, va al catálogo.

## ffmpeg-kit: qué binario es

- **No** es el original `com.arthenica` (archivado en GitHub). Es el fork mantenido de Anton Karpenko (`sk3llo/ffmpeg_kit_flutter`, activo en 2026), publicado en Maven Central.
- **Paquete Java distinto**: `com.antonkarpenko.ffmpegkit.*` (no `com.arthenica.ffmpegkit.*`). La API es la misma que la de arthenica; al copiar ejemplos, cambia el import.
- **No hay `.aar` en el repo** (`app/libs/` no existe). El wiki upstream ("Developer Setup") está desactualizado en eso.
- Versiones más nuevas del fork: 2.2.0 / 2.2.1 → FFmpeg 8.1.1. No se ha evaluado subir.
- ABIs en el `.aar`: arm64-v8a, armeabi-v7a (+ variantes `_neon`), x86, x86_64.

### Capacidades verificadas en el binario (arm64, `libavfilter.so` / `libavcodec.so`)

Configure: `--enable-gpl --enable-version3 --enable-mediacodec` + libx264, libx265, libmp3lame, libass, libfreetype, libfontconfig, libfribidi, libharfbuzz, libvidstab, librubberband, libzimg, libopus, libvpx, libaom, libdav1d, libwebp, libtesseract, libsoxr, libsrt, libopenh264, entre otras.

- Filtros presentes: `xfade`, `acrossfade`, `overlay`, `colorkey`, `chromakey`, `drawtext`, `subtitles`, `ass`, `vidstabdetect`/`vidstabtransform`, `rubberband`, `zscale`, `lut3d`, `gblur`, `boxblur`, `loudnorm`, `afade`, `sidechaincompress`, `colorchannelmixer`, `geq`, `curves`, `atempo`, `reverse`/`areverse`, `ocr`, `colordetect`.
- Encoders presentes: `h264_mediacodec`, `hevc_mediacodec`, `libx264`, `libx265`, `libmp3lame`, `aac`, `libopus`, `libvpx`, `libvpx-vp9`, `libaom-av1`, `libwebp`, `gif`, `libopenh264`.
- **Regla**: antes de usar un filtro/encoder que no esté en esta lista, verifícalo en el binario (extraer el `.aar` de `~/.gradle/caches/modules-2/files-2.1/com.antonkarpenko/` y buscar el nombre en `jni/arm64-v8a/libavfilter.so`). `--enable-small` está activo: los textos de ayuda de los filtros vienen recortados.

### Licencia — ojo

El repo es MIT, pero enlaza un FFmpeg `--enable-gpl --enable-version3` (GPLv3). El APK distribuido es, en la práctica, GPLv3. No cambies a `ffmpeg-kit-full` (LGPL) sin revisar: se pierde `libx264`, que es el fallback de software del export.

## Arquitectura

Módulo único `:app`, paquete `com.tharunbirla.librecuts` (`app/src/main/java/com/tharunbirla/librecuts/`). MVVM "lite": ViewModel + `StateFlow`, sin DI (ni Hilt ni Koin), sin Repository.

| Ruta | Qué vive ahí |
|---|---|
| `VideoEditingActivity.kt` | **God class (~9000 líneas)**: UI del editor, timeline, player, toolbars, arma el export. |
| `MainActivity.kt` | Launcher, pickers, intents `SEND`/`VIEW`. Única que usa ViewBinding. |
| `ProjectImportActivity.kt` | Importa `.lcprj`. |
| `ErrorDisplayActivity.kt` | Pantalla de error (copiar log, compartir, abrir issue en GitHub upstream). |
| `LibreCutsApplication.kt` | Handler global de crashes → `ErrorDisplayActivity` con LC-500. |
| `viewmodels/VideoEditingViewModel.kt` | Estado del proyecto, undo/redo, y **construcción de los comandos FFmpeg de export** (`buildConsolidatedFFmpegCommand` l.650, `buildPreviewCommand`, `buildMergeCommand`). |
| `viewmodels/VideoEditingViewModelExt.kt` | Extension functions de mutación de operaciones. |
| `models/EditOperation.kt` | `sealed class` con todas las operaciones (Trim, Crop, AddText, Merge, Transition, Subtitles, ...). Es lo que se serializa. |
| `models/` | `VideoProject`, `EditRecipe`, `MergeItem`, `MaskConfig`, `TextPosition`. |
| `commands/EditCommand.kt` | Patrón Command para undo/redo. |
| `services/FFmpegRenderEngine.kt` | **Único punto que ejecuta FFmpeg** para render (excepto `AudioAnalyzer`/`AudioWaveformExtractor`). |
| `services/ExportService.kt` | Foreground service (`dataSync`) que corre el export, notifica progreso y guarda en galería. |
| `services/ProxyGenerationService.kt` | Genera proxies (speed/reverse). |
| `customviews/` | ~14 Custom Views: timeline (`TrackTrimView`, `TimeRulerView`, `CustomVideoSeeker`), overlays draggables, máscara, crop, dibujo libre, bottom sheets. |
| `utils/` | `ErrorCode`, `ProjectSerializer` (Gson + deserializer polimórfico), `SubtitleParser`, `FontManager`, `AudioAnalyzer`, `AudioWaveformExtractor`, `ViewExtensions`. |

### Flujo de export

1. `VideoEditingActivity` (~l.4752) llama `viewModel.buildConsolidatedFFmpegCommand(...)` → `String` con todo el `-filter_complex`.
2. Pasa el comando por `Intent` extra (`ExportService.EXTRA_COMMAND`) a `ExportService`.
3. `ExportService` → `FFmpegRenderEngine.exportFinal(command, totalDurationSecs, onProgress)`.
4. Progreso vuelve por broadcast (`EXTRA_PROGRESS`, `EXTRA_SAVED_URI`, `EXTRA_ERROR`).

## Patrón FFmpeg (síguelo al agregar funciones)

- **Comandos como `String` único**, concatenado, ejecutado con `FFmpegKit.execute(String)` / `executeAsync(String, ...)`. No se usa `executeWithArguments`. Rutas entre comillas dobles escapadas: `-i \"$path\"`.
- Grafos complejos: lista `filterParts` de etapas `[in]filtro[out]` unidas con `;` dentro de `-filter_complex "..."`.
- Texto en `drawtext`: escapar `\` → `\\\\`, `'` → `\\\\'`, `:` → `\\:`. Siempre `fontfile=` con ruta real; `font=` no existe en drawtext (ver KDoc de `FFmpegRenderEngine`).
- **Subtítulos se queman con `drawtext` por línea**, no con el filtro `subtitles`/libass (aunque el binario lo trae).
- Ejecución: `withContext(Dispatchers.IO)`. Export async con `suspendCancellableCoroutine`; cancelación vía `FFmpegKit.cancel(sessionId)` en `invokeOnCancellation`.
- Progreso: `statistics.time` (ms) / duración total esperada.
- URIs: `file://` → `.path`; `content://` → `FFmpegKitConfig.getSafParameterForRead()`, y si falla, copia a `cacheDir` con extensión según MIME. Para imágenes se fuerza la copia (`resolveUriToFilePath(uri, forceCopy = true)` en `generateVideoFromImage`) porque el demuxer `image2` no entiende `saf:X`.
- Resultado: `sealed class RenderResult { Success, Failure, Cancelled }`.
- **Fallback de encoder**: si falla y el comando contiene `h264_mediacodec` → reintenta una vez con `String.replace` a `libx264`; y al revés (`libx264` → `h264_mediacodec -b:v 8M`). Además marca fallo si los logs dicen `video:0kB` / `frame= 0` aunque el return code sea 0.
- Audio export: `-c:a libmp3lame -b:a 192k` (MP3 real). Video: `aac` para audio.

## UI

- **Views tradicionales + XML. No hay Jetpack Compose** (ni plugin, ni `@Composable`, ni `setContent {}`).
- 44 layouts en `res/layout/`. Nombres de archivo en snake_case con prefijo por tipo: `activity_*`, `dialog_*`, `item_*`, `bottom_sheet_*` / `*_bottom_sheet_dialog`, `*_editing_toolbar`.
- **IDs de vista en camelCase** (`btnPlayPause`, `tvErrorCode`; ~500 camel vs ~9 snake). Sigue camelCase.
- `viewBinding true` está activo pero solo `MainActivity` lo usa. `VideoEditingActivity` usa `findViewById` (~415 llamadas). En código nuevo fuera de esa Activity, usa ViewBinding.
- Fragments: solo `BottomSheetDialogFragment` para pickers. El resto son Activities + Custom Views.
- Player: `ExoPlayer` + `StyledPlayerView` (API de ExoPlayer 2).
- Strings: `res/values/strings.xml` + 16 traducciones (`values-ar, cs, de, el, es, et, hi, in, it, nl, pt-rBR, ru, sk, ta, tr, zh-rCN`) gestionadas por **Weblate** upstream. Solo edita `values/`; las traducciones las hace Weblate.
- Hay textos hardcodeados en inglés (p. ej. descripciones de `ErrorCode`, algunos diálogos). No agregues más.

## Convenciones de código

- Kotlin `official` (`gradle.properties`). Clases PascalCase, funciones camelCase, backing fields `_foo` + `foo: StateFlow` con `asStateFlow()`.
- Comentarios explican el porqué. Los heredados de upstream están en inglés; los nuevos van en español (fork propio, sin PRs a upstream).
- APIs deprecadas sin reemplazo equivalente se aíslan en un archivo con `@file:Suppress("DEPRECATION")` y un comentario del porqué (ej. `utils/GifFrameSource.kt` envuelve `android.graphics.Movie`, única API que dibuja un GIF en un instante arbitrario). No suprimir a nivel de clases grandes.
- Logs: `private val TAG = "NombreClase"`.

## Códigos de error (`utils/ErrorCode.kt`, enum)

`LC-101` FFMPEG_EXECUTION_FAILED · `LC-102` FONT_MISSING · `LC-201` FILE_NOT_FOUND · `LC-202` GALLERY_SAVE_FAILED · `LC-301` OUT_OF_MEMORY · `LC-500` UNEXPECTED_CRASH.
Se muestran en `ErrorDisplayActivity` vía extras `ERROR_CODE`, `ERROR_LOG`, `ERROR_DESCRIPTION`. Si agregas un código, documéntalo también en el wiki (página "Error Codes & Troubleshooting").

## Limitaciones y deuda conocida

- `VideoEditingActivity` de 9000 líneas: cambios ahí, quirúrgicos. No refactorizar en masa.
- ExoPlayer 2.19.1 está deprecado (reemplazado por AndroidX Media3). Migrar es un cambio grande de imports/API; no mezclar Media3 y ExoPlayer 2.
- Fallback de encoder por `String.replace`: frágil si una ruta o filtro contiene `libx264`/`h264_mediacodec`.
- `FFmpegKit.execute(String)` parte el comando por espacios respetando comillas; una ruta con `"` lo rompe. `executeWithArguments(Array)` sería más robusto.
- `activeSessions` en `FFmpegRenderEngine` es `mutableListOf` (no thread-safe) y se toca desde varios hilos.
- Tests: `app/src/test` solo tiene el template; `androidTest` tiene `KeyframeOpacityPreviewTest` (custom views). Ningún test cubre la construcción de comandos FFmpeg.
- Build limpio pero con 6 warnings de Kotlin en build limpio (5 de ExoPlayer 2 deprecado y un `Condition is always 'true'` en `VideoEditingActivity.kt:8643`), contados el 2026-09-23.
- `minifyEnabled false` en release; `proguard-rules.pro` es el template.
- Basura en la raíz: `test_exo.kt`, `test_ext.kt`, `test_heavy.kt` (no compilan ni se referencian). `src/images/` en la raíz son imágenes del README, no código.
- Wiki upstream: no existe página "Tech Stack". Solo Home, User Guide, Developer Setup (desactualizada: habla de `app/libs/ffmpeg-kit.aar`) y Error Codes.

## No confirmado

- La versión exacta de FFmpeg (8.0.x) sale del POM ("FFmpeg v8.0.0 Full-GPL"); el binario no expone la cadena de versión, pero trae `colordetect` (añadido en FFmpeg 8.0), consistente con 8.x.
- Si `h264_mediacodec` funciona en cada dispositivo: depende del hardware; por eso existe el fallback.
- Comportamiento de `ProxyGenerationService` más allá de generar proxies de speed/reverse: no revisado a fondo.
