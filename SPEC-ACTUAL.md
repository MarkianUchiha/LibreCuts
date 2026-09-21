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

## Sin verificar

- Si el cuadro congelado coincide visualmente con el del playhead: `OPTION_CLOSEST_SYNC` sugiere
  que no, en videos con keyframes espaciados.
- Congelar sobre un clip que es imagen (`isImage = true`).
- Congelar sobre un clip invertido: el instante en la fuente no toma en cuenta la reversa.

## Qué NO tocar todavía

- Export (`buildConsolidatedFFmpegCommand`): no mapeado en este documento.
