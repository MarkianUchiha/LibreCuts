---
tipo: decisiones
actualizado: 2026-09-22
---

# Decisiones

Una entrada por decisión que costaría tiempo volver a tomar o que alguien podría deshacer sin
saber por qué. Formato: qué se decidió, por qué, y qué se pierde. Las nuevas van al final.

Las de esta primera tanda se escribieron el 2026-09-22, a toro pasado: la fecha de cada una es
cuando se tomó, no cuando se anotó. Las que se dedujeron del código y no de una conversación van
marcadas **[heredada]** — se documentan para poder discutirlas, no porque se hayan elegido.

---

## D1 — El fork no manda PRs a upstream · 2026-09-17

Se mantiene `tharunbirla/LibreCuts` como remote `upstream` solo para traer cambios, no para
contribuir.

**Por qué:** el objetivo es una app propia con funciones tipo CapCut y la interfaz en español,
no mejorar el proyecto original. Mantener compatibilidad con upstream obligaría a discutir cada
cambio de arquitectura y a escribir los comentarios en inglés.

**Se pierde:** las mejoras propias no vuelven a la comunidad, y cada `merge` de upstream costará
más conforme las bases diverjan.

---

## D2 — ffmpeg-kit del fork de Anton Karpenko · [heredada] · 2026-09-17

`com.antonkarpenko:ffmpeg-kit-full-gpl:2.1.0` en vez del original `com.arthenica`.

**Por qué:** arthenica archivó el proyecto en GitHub y sus binarios dejaron de publicarse. El
fork de Karpenko sigue activo y publica en Maven Central.

**Se pierde:** el paquete Java cambia a `com.antonkarpenko.ffmpegkit.*`, así que cualquier ejemplo
copiado de la documentación de arthenica necesita cambiar el import. La API es la misma.

---

## D3 — El APK es GPLv3 en la práctica · [heredada] · 2026-09-17

El repo es MIT, pero enlaza un FFmpeg compilado con `--enable-gpl --enable-version3`.

**Por qué:** esa compilación trae `libx264`, que es el fallback de software del export cuando el
encoder por hardware del dispositivo falla. Sin él, un dispositivo cuyo `h264_mediacodec` no
funcione se queda sin poder exportar.

**Se pierde:** la licencia efectiva del binario distribuido es GPLv3, más restrictiva que el MIT
del código. Cambiar a `ffmpeg-kit-full` (LGPL) devolvería la libertad de licencia a costa del
fallback; no se ha evaluado.

---

## D4 — Los subtítulos se queman con `drawtext`, línea por línea · [heredada] · 2026-09-17

Aunque el binario incluye `libass` y los filtros `subtitles` y `ass`.

**Por qué:** no está documentado en el código. Se deduce que evita tener que escribir un archivo
`.ass` temporal y manejar sus rutas dentro del comando.

**Se pierde:** el estilo de los subtítulos queda limitado a lo que ofrece `drawtext`, y cada línea
suma una etapa al `-filter_complex`. `[SIN VERIFICAR]` si con muchos subtítulos el comando se
vuelve impracticable.

---

## D5 — Fallback de encoder por reemplazo de texto · [heredada] · 2026-09-17

Si el export falla, `FFmpegRenderEngine` reintenta una vez cambiando `h264_mediacodec` por
`libx264` (o al revés) con `String.replace` sobre el comando completo.

**Por qué:** el soporte de `h264_mediacodec` depende del hardware y no se puede saber de antemano
si funciona en un dispositivo.

**Se pierde:** es frágil. Una ruta de archivo que contenga `libx264` o `h264_mediacodec` se
reescribiría también. Mientras el comando se arme como un solo `String` no hay forma limpia de
arreglarlo; la alternativa sería `executeWithArguments(Array)`.

---

## D6 — El encuadre del clip se aplica transformando el contenedor del preview · 2026-09-18

En vez de reescalar el contenido del player, `applyClipFramePreview` mueve y escala
`mainVideoMaskContainer`, con el pivote en el centro del video completo.

**Por qué:** así el video, la foto y la máscara se mueven juntos con una sola transformación, y el
pivote coincide con el del export, que encuadra antes de recortar. Es lo que hace que preview y
export coincidan con un recorte activo.

**Se pierde:** todo lo que se dibuje dentro de ese contenedor hereda el encuadre del clip activo,
aunque no le corresponda. Eso causó dos bugs (M-211 puntos 1 y 3), ambos resueltos sacando el
overlay del contenedor. **Regla que queda:** un overlay que no represente al clip activo no va
dentro de `mainVideoMaskContainer`.

---

## D7 — La geometría de un clip se lee del proyecto, no del estado de las vistas · 2026-09-22

Al dibujar la captura del clip saliente de una transición, su encuadre, espejo y máscara se leen
de `sequenceItems[i]` y se pasan explícitamente a la vista (`SnapshotGeometry`).

**Por qué:** el estado de las vistas corresponde siempre al clip activo, que durante una
transición ya no es el que se está dibujando. Leerlo del modelo hace que el resultado no dependa
del instante en que se tomó la captura, y permite probarlo sin reproducir video.

**Se pierde:** hay que mantener sincronizada una segunda fuente de la misma información. A cambio,
los criterios CA1 a CA3 de `specs/transicion-preview.md` se verifican con tests instrumentados en
vez de a ojo en el dispositivo.

---

## D8 — Los tests instrumentados corren con un script, no con `connectedAndroidTest` · 2026-09-22

`node scripts/test-tablet.mjs` hace compilar → `adb install -r -t` → `am instrument`.

**Por qué:** `connectedAndroidTest` desinstala la app antes de correr y se lleva sus datos,
incluidos los archivos de `cacheDir` que el proyecto abierto necesita. Reconstruir el escenario de
prueba en la tablet cuesta más que el ciclo completo de tests.

**Se pierde:** el script no corre en CI (CI no tiene dispositivo) y hay que mantenerlo si cambian
las rutas de los APK. A cambio, el ciclo pasó de seis comandos a uno.

---

## Decisiones abiertas

Anotadas para que no se pierdan; no están tomadas.

- **Cuáles son los diferenciadores del fork** (fase 3 del `SPEC.md`). Sin esto la fase 3 no tiene
  alcance.
- **Si migrar a AndroidX Media3.** ExoPlayer 2.19.1 está deprecado. Es un cambio grande de imports
  y API, y no puede hacerse a medias.
- **Si subir ffmpeg-kit a 2.2.x** (FFmpeg 8.1.1). Sin evaluar.
- **De quién es la proporción del lienzo del export** (M-230): del proyecto o del clip principal.
