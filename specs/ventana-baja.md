---
feature: el editor se puede usar en una ventana baja
issue: M-254
estado: propuesta
actualizado: 2026-09-23
---

# El editor en una ventana baja

## Por qué

En un teléfono girado, o con la pantalla dividida, **el preview del video desaparece**. Queda en
0 de alto: el usuario edita sin ver lo que edita. Al apuntar a API 36 (M-236) el sistema puede
poner la app en ventana partida o redimensionarla. Se decidió el 2026-09-23 no fijar la orientación
vertical en teléfonos: eso arreglaría el giro, pero no la pantalla dividida. El problema de fondo es
el alto de la ventana, no la orientación.

## Qué pasa hoy

Medido en el Honor ELP-NX9 (Android 16, 1224×2700 px, 520 dpi = 3.25 px/dp), con `uiautomator dump`.

El editor apila en vertical cuatro franjas; tres tienen alto fijo:

| Franja | Alto | Fuente |
| --- | --- | --- |
| Barra superior (atrás, deshacer, exportar) | 56dp | `activity_video_editing.xml` |
| Preview (`workspaceRow`, peso 1) | lo que sobre | ídem |
| `seekerContainer`: controles de reproducción (44dp) + regla + timeline (140dp) | 216dp | volcado |
| Barra de herramientas (`editingControlsScroll`) | 88dp | volcado |

Lo fijo suma **~360dp**, más los insets del sistema. Lo que queda es para el preview:

| Caso | Alto de ventana | Preview |
| --- | --- | --- |
| Teléfono vertical | 831dp | 427dp |
| Teléfono horizontal | 376dp | **0** (la barra de herramientas además queda cortada: 61dp de 88) |
| Teléfono en media pantalla (vertical, 1224×1350 px) | 415dp | **0**; con un clip seleccionado, ~3dp |
| Tablet horizontal (layout lateral) | 685dp | ~401dp (`playerContainer` de 703 px a 1.75 px/dp) |

`isSidePanelLayout` (`VideoEditingActivity.kt:7927-7929`) solo se activa con
`smallestScreenWidthDp >= 600`, así que en un teléfono nunca se usa el riel ni el panel lateral.

Además, en media pantalla el bottom sheet "Cortar video" se sale de la ventana. Su botón "Listo" solo
aparece si el usuario arrastra el sheet hacia arriba.

Antecedente en el código: al abrir el teclado editando texto, el editor ya oculta el timeline para
ceder espacio (`updateTimelineVisibilityForEditing`, l.8041). Hoy es el único caso en que una franja
cede alto.

## Reglas

1. **El video siempre se ve.** En cualquier ventana, el preview conserva un alto mínimo (ver
   Decisiones pendientes, 1).
2. **Lo que cede es el timeline.** Cuando falta alto, primero se compacta el timeline, y en el caso
   extremo se colapsa detrás de un control visible para desplegarlo (ver Decisiones pendientes, 2).
   La barra superior y la de herramientas no se ocultan.
3. **Nada queda inalcanzable.** Toda herramienta, el botón de exportar y el cabezal siguen
   accesibles, aunque sea desplazándose o desplegando.
4. **Los diálogos caben.** Un bottom sheet que no cabe en la ventana abre expandido, con su botón
   de confirmar visible sin arrastrarlo.
5. **Cambiar de tamaño no pierde nada.** Clip seleccionado, posición del cabezal, encuadre y
   herramienta abierta siguen igual (como la regla 4 de `specs/rotacion-horizontal.md`).
6. **Con alto suficiente no cambia nada.** Teléfono vertical a pantalla completa y tablet en
   cualquier orientación se ven exactamente como hoy.

## Qué NO hace (fuera de alcance)

- Rediseñar el editor para horizontal en teléfonos (preview y timeline lado a lado).
- Fijar la orientación: se descartó por decisión del 2026-09-23.
- Cambiar el layout lateral de la tablet (`specs/rotacion-horizontal.md`).
- Ventanas angostas (menos ancho): hoy no hay un problema observado ahí.

## Decisiones pendientes (antes de aprobar)

1. **Alto mínimo del preview.** Propuesta: **el 40 % del alto de la ventana**. En el teléfono
   horizontal serían ~150dp, y en media pantalla ~166dp. Una alternativa es un mínimo fijo en dp,
   pero no escala con la ventana.
2. **Cómo cede el timeline.** Propuesta, en dos escalones:
   - **Compacto:** menos alto de pista y controles de reproducción en una fila más baja, si con eso
     alcanza el mínimo.
   - **Colapsado:** si no alcanza, el timeline se oculta y queda un control visible para desplegarlo.
     Ya existe un botón para lo contrario (`toggleTimelineExpandedMode`, l.7624, oculta el preview
     para agrandar el timeline); el control nuevo puede seguir su mismo estilo.

   Con la propuesta 1, lo que le queda al timeline (hoy 216dp) es: teléfono horizontal
   376 − 150 − 56 − 88 = **~82dp**, y media pantalla 415 − 166 − 56 − 88 = **~105dp**. Un timeline
   compacto (controles de 44dp + regla + una pista) ronda los 100dp: alcanza en media pantalla, pero
   en el teléfono horizontal tocaría el escalón de colapsado.
3. **Umbral.** A partir de qué alto se considera "ventana baja": propuesta, cuando el preview
   quedaría por debajo del mínimo de la decisión 1, medido con el alto real y no con la orientación.

## Criterios de aceptación

Todos en el teléfono Android 16. La media pantalla se arma con
`am start --windowingMode 6 ...` + `am task resize <tarea> 0 0 1224 1350`.

- **CA1.** Teléfono horizontal: el preview mide al menos el mínimo acordado, y el video se ve.
- **CA2.** Media pantalla: igual que CA1, también con un clip seleccionado (toolbar del clip abierta).
- **CA3.** En los dos casos anteriores, cada herramienta de la barra se puede alcanzar y pulsar, y
  el timeline se puede usar (desplegándolo si está colapsado): mover el cabezal y seleccionar un clip.
- **CA4.** En media pantalla, "Cortar video" abre con "Listo" visible sin arrastrar el sheet.
- **CA5.** Con un clip seleccionado y el cabezal en la mitad, pasar de vertical a horizontal y de
  vuelta: mismo clip, mismo tiempo, misma herramienta abierta.
- **CA6.** Teléfono vertical a pantalla completa y tablet (vertical y horizontal): capturas del
  editor idénticas a las de antes del cambio, sin contar la barra de estado.

CA1 a CA3 se miden con `uiautomator dump` (alto de `playerContainer` y límites de cada botón).

## Diff contra el estado actual

| Aspecto | Hoy | Deseado |
| --- | --- | --- |
| Preview en ventana baja | 0 de alto | Al menos el mínimo acordado (regla 1) |
| Timeline en ventana baja | Alto fijo de 216dp | Compacto o colapsado (regla 2) |
| Barra de herramientas en ventana baja | Cortada (61dp de 88) | Completa (regla 3) |
| Bottom sheets en ventana baja | Se salen; hay que arrastrarlos | Abren expandidos (regla 4) |
| Ventana con alto suficiente | Como hoy | Sin cambios (regla 6) |
