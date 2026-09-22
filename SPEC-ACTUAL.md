---
tipo: descriptiva
estado: en progreso
actualizado: 2026-09-21
---

# LibreCuts — Estado actual

Documenta **qué hace la app hoy**, no qué debería hacer. Lo no confirmado contra el código o
la tablet va marcado `[SIN VERIFICAR]`.

Alcance de esta primera versión: **modelo de secuencia de clips** y **congelar cuadro**. El
resto de la app (texto, audio, stickers, export completo) está pendiente de documentar.

## Qué es

Editor de video para Android, 100 % local. El usuario abre un video (el *clip principal*), le
agrega más clips a continuación y los edita en un timeline: recortar, dividir, reordenar, borrar,
cambiar velocidad, invertir, espejar, enmascarar, encuadrar y congelar un cuadro. El export
arma un único comando FFmpeg con toda la secuencia.

Stack, comandos de build y convenciones: ver `CLAUDE.md`.

## Cómo se corre

```
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

Los tests del modelo de secuencia son instrumentados (`app/src/androidTest`) y corren en la
tablet con el procedimiento de `CLAUDE.md` (sin `connectedAndroidTest`).

## Modelo de secuencia

La secuencia no es una lista uniforme. Tiene dos representaciones distintas:

| Posición | Dónde vive | Verificado |
|---|---|---|
| Clip 0 (principal) | `VideoProject.sourceUri` + operaciones sueltas `Trim`, `SpeedMain`, `ReverseMain`, `MirrorMain`, `MaskMain`, `FrameMain` | sí |
| Clips 1..n | `EditOperation.Merge.items: List<MergeItem>` | sí |

`MergeItem` (`models/EditOperation.kt:224-243`) junta en un solo objeto uri, duración, trim,
velocidad, proxies, reversa, espejo, máscara, `isImage` y `frame` (encuadre).

Invariante que la app mantiene desde M-210: **el clip principal nunca aparece también en
`Merge.items`.**

Para operar sobre la secuencia completa, la Activity construye una vista uniforme con
`getSequenceItems()` (`VideoEditingActivity.kt:5411`): un `MergeItem` "virtual" del principal,
armado leyendo sus operaciones `*Main`, seguido de `Merge.items`.

### Operaciones sobre la secuencia (`viewmodels/VideoEditingViewModelExt.kt`)

| Función | Qué hace | Entradas de undo |
|---|---|---|
| `updateMainVideoTrim` (l.9) | Reemplaza el `Trim` del principal. No toca `sourceUri`. | 1 |
| `updateSequenceOrder` (l.115) | Reemplaza `Merge.items`. No toca el principal. | 1 |
| `splitVideoSegment` (l.278) | Divide un clip en dos. Si es el principal, la segunda mitad pasa a `Merge.items[0]` heredando velocidad, reversa, espejo, máscara, encuadre y proxy. | 1 |
| `reorderSequence` (l.365) | Recibe la secuencia **completa**: el primer item se promueve a principal (`promoteToMain`, l.336) y cambia `sourceUri`; el resto va a `Merge.items`. | 1 |
| `deleteSequenceSegment` (l.387) | Borra un clip; si es el principal, promueve al siguiente. | 1 |

`promoteToMain` borra todas las operaciones `*Main` y las regenera desde el item. No copia
`scrubProxyUri`, que vive a nivel de proyecto (abierto como M-212).

### Clip principal en la Activity

El reproductor no lee `project.sourceUri`: reproduce `tempInputFile`, una copia local del video.
Las operaciones que cambian el principal llaman antes a `adoptMainClip(item)` en la Activity.
Deshacer y rehacer se sincronizan en el observador de `project`: si `sourceUri` es `file://` usa
su ruta; si es `content://` (video importado) busca su copia en `uriToFilePathCache`. Verificado
en la tablet con congelar → deshacer → rehacer → deshacer (2026-09-21).

## Congelar cuadro

Implementa `specs/congelar-cuadro.md`. `freezeFrameAtCurrentPosition()` en la Activity:

1. Calcula el instante en el archivo fuente: `trimStartMs + (posición relativa × speed)`,
   acotado al trim del clip.
2. Extrae el cuadro con `MediaMetadataRetriever.getFrameAtTime(..., OPTION_CLOSEST_SYNC)`:
   toma el **keyframe más cercano**, no el cuadro exacto.
3. Guarda un PNG y lo convierte con FFmpeg en un MP4 de 3 s (`h264_mediacodec`).
4. `insertFreezeFrame(...)` (`VideoEditingViewModelExt.kt`) arma la secuencia nueva: el freeze
   hereda encuadre, espejo y la máscara congelada en ese instante (`MaskConfig.frozenAt`), no
   velocidad ni reversa. Playhead dentro del clip → `[A, freeze, B]`; al inicio →
   `[freeze, clip]`; al final → `[clip, freeze]`.
5. `adoptMainClip(newItems[0])` + `reorderSequence(newItems)`: si el freeze quedó primero pasa a
   principal. Una sola entrada de undo.

Cubierto por `androidTest/.../FreezeFrameTest.kt` (CA1–CA6) y verificado en la tablet (CA1, CA6,
CA7: preview y export de un clip encuadrado, 2026-09-21).

## Máscara del clip (preview y edición)

- **Dónde se dibuja:** `MaskedFrameLayout.dispatchDraw()` recorta `mainVideoMaskContainer` con
  `relativeX/Y/Width/Height` como fracciones **del contenedor**. Ese contenedor vive en
  `canvasContainer`, que se redimensiona a la caja del lienzo (`VideoEditingActivity.kt:2346-2350`,
  `:2420-2424`), y es el que recibe el encuadre (`applyClipFramePreview`, l.2464: pivote en el
  centro de `clipFrameReference`, `scale`, `translation = offset × tamaño de la referencia`). La
  máscara viaja con el clip, igual que en el export, donde se aplica al clip antes de encuadrarlo.
- **Dónde se edita:** `VideoMaskOverlayView` es hermano de `canvasContainer` y ocupa todo el
  preview (`activity_video_editing.xml:244`), pero desde M-211 (punto 1) sigue al contenedor
  (`followContainer`): en cada dibujo y toque arma la matriz contenedor → overlay recorriendo la
  jerarquía de vistas, dibuja el contorno con ella y pasa los toques por su inversa. Así el
  contorno coincide con la máscara, sigue el encuadre y el arrastre es 1:1 con el dedo.
  Implementa `specs/mascara-edicion.md`; cubierto por `MaskOverlaySpaceTest` y verificado en la
  tablet (2026-09-21).
- **Panel de máscara:** los deslizadores, la forma y el cierre parten de la máscara guardada en
  el proyecto (`latestMask()`), porque el overlay guarda cada gesto por su cuenta
  (`onMaskChanged` → `updateMergeItemMask`). Antes partían de una copia local y descartaban los
  gestos. Cada paso de un arrastre es una entrada de undo aparte.
- **Difuminado en el preview:** con `feather > 0` la máscara se dibuja con `saveLayer` +
  `DST_IN` + `BlurMaskFilter` (`MaskedFrameLayout.kt:44-62`) y en la tablet el video se ve
  completo, sin máscara (2026-09-21). El valor sí se guarda. `[SIN VERIFICAR]` en el export.
- **Export con máscara:** muy lento en la tablet: 5 % en unos 3 minutos para 30 s de video
  (2026-09-21). Causa `[SIN VERIFICAR]`.
- **Espejo:** se aplica solo a la superficie de video (`scaleX = -1`, l.5678) dentro del
  contenedor; la máscara no se espeja, y en el export `hflip` va antes de la máscara. Preview y
  export coinciden. `[SIN VERIFICAR]` en la tablet.

## Preview de transiciones

- **Cuándo:** en cada actualización del preview, si el playhead está a ±500 ms de un corte que
  tiene `EditOperation.Transition` (`VideoEditingActivity.kt:5683-5730`, ventana fija de 1 s
  aunque el panel permite elegir la duración `[SIN VERIFICAR]` cuál usa el export).
- **Cómo:** al entrar en la ventana se toma una captura del clip saliente con
  `textureView.getBitmap(ancho/2, alto/2)` (l.5707) y `TransitionPreviewOverlayView` la dibuja
  encima del video en vivo con el efecto (fade, wipe, slide, circlecrop, zoom…) y el avance
  `prog` de 0 a 1.
- **Dónde:** el overlay es hermano de `mainVideoMaskContainer`, no hijo
  (`activity_video_editing.xml:202`), así que no hereda el encuadre ni la máscara del clip activo.
- **Geometría del saliente:** junto con la captura se guarda una `SnapshotGeometry` con el
  encuadre, el espejo y la máscara del clip que sale, leídos del proyecto (`sequenceItems[i]`),
  más el rectángulo del cuadro de video dentro del lienzo (`videoRectInCanvas()`). El overlay
  arma con eso la matriz con que dibuja la captura; los efectos se siguen midiendo sobre el
  lienzo, como `xfade` en el export.
- **Resultado, verificado en la tablet (2026-09-22, M-211 punto 3):** con un clip encuadrado
  arriba-izquierda saliendo hacia otro encuadrado abajo-derecha y "Wipe L" entre ambos, la
  franja del saliente se queda en su sitio durante toda la transición. Medido sobre las capturas:
  su borde inferior está en 0.774 del alto del lienzo antes y después del corte, y en 0.777 en el
  cuadro equivalente del export.
- Sin encuadre ni espejo el dibujo es el mismo de antes del arreglo: la captura llena el lienzo
  (verificado en la tablet, 2026-09-22).
- El difuminado de la máscara no se aplica a la captura: el overlay solo recorta. Es coherente
  con M-228, donde el difuminado tampoco se ve fuera de las transiciones.
- El export arma cada clip ya encuadrado y enmascarado antes del `xfade`.
- **Limitación conocida:** con un *seek* directo a mitad de la transición, la geometría es la
  correcta pero la imagen capturada puede ser ya la del clip entrante, porque sale del
  `TextureView` en ese instante. Pasaba igual antes del arreglo.

## Sin verificar

- Si el cuadro congelado coincide visualmente con el del playhead: `OPTION_CLOSEST_SYNC` sugiere
  que no, en videos con keyframes espaciados.
- Congelar sobre un clip que es imagen (`isImage = true`).
- Congelar sobre un clip invertido: el instante en la fuente no toma en cuenta la reversa.

## Qué NO tocar todavía

- Export (`buildConsolidatedFFmpegCommand`): no mapeado en este documento.
