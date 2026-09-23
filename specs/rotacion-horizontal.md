---
feature: el editor se usa completo en horizontal
issue: M-194
estado: borrador
actualizado: 2026-09-22
---

# El editor en horizontal

## Por qué ahora

Deja de ser opcional: al apuntar a API 36 (M-236), en pantallas de 600dp o más el sistema decide
la orientación y el tamaño de la ventana, y la app no puede negarse. La tablet de prueba mide
685dp. Ver `specs/target-sdk-36.md`.

## Qué pasa hoy

Observado en la tablet el 2026-09-22, con la app girada a mano (Honor ELN-W09, 2000x1200, sw685dp).

La app **sí** entra en su modo de tablet horizontal: `isSidePanelLayout()` exige orientación
horizontal y `smallestScreenWidthDp >= 600`, y la tablet cumple las dos
(`VideoEditingActivity.kt:7894-7896`). Las herramientas se mueven a un riel vertical a la
izquierda, como está previsto. Lo que falla es otra cosa:

1. **El riel de herramientas parece cortado.** Se ve hasta "Subtítulos" y el siguiente botón queda
   partido por el borde inferior. **Sí se puede desplazar** —es un `ScrollView`
   (`activity_video_editing.xml:144`) y al arrastrarlo aparecen "Recortar" y "Canvas"—, pero nada
   lo indica: no hay barra de desplazamiento ni degradado en el borde. El usuario concluye que las
   herramientas no están. Es el "menús se esconden" del issue, y es un problema de **descubrimiento,
   no de layout**.
2. **La mitad izquierda de la zona del timeline queda vacía.** La pista arranca en el centro de la
   pantalla, porque el desplazamiento del timeline conserva un relleno de media pantalla calculado
   para vertical (`onTimelineWidthChanged`, `VideoEditingActivity.kt:6808-6824`). En horizontal eso
   deja la mitad del ancho sin usar.
3. **El panel de opciones de una herramienta sigue apareciendo abajo.** Con la herramienta de
   dibujo abierta, su paleta ocupa el ancho completo en la parte inferior y comprime el timeline
   contra ella, en vez de usar el panel lateral derecho que el modo horizontal prevé.
   `[SIN VERIFICAR]` si las demás herramientas se comportan igual: solo se probó la de dibujo.

También se confirmó que la Activity **no se recrea** al girar (`configChanges` en
`AndroidManifest.xml:66`), así que no hay estado que se pierda: lo que falta es recalcular lo que
depende del tamaño.

## Reglas

1. **Toda herramienta es alcanzable.** Si una lista de herramientas no cabe, se ve que continúa.
2. **El timeline empieza donde empieza su zona.** Ningún tamaño de ventana deja media pista vacía.
3. **Las opciones de una herramienta no tapan el timeline.** Con una herramienta abierta se siguen
   viendo la pista y el cabezal de reproducción.
4. **Girar no pierde nada.** El clip seleccionado, la posición del cabezal, el encuadre y el modo
   de edición activo siguen igual después de girar.
5. **En vertical no cambia nada.** La app se ve y se usa exactamente como hoy.

## Qué NO hace (fuera de alcance)

- Rediseñar la interfaz para aprovechar el ancho (previsualización más grande, dos paneles a la
  vez, atajos nuevos). Aquí solo se persigue que todo sea usable.
- Layouts alternativos por orientación: la Activity no se recrea, así que el acomodo se decide en
  código, como ya se hace.
- El comportamiento en ventana partida o redimensionada: es de `specs/target-sdk-36.md`.
- Los textos sin traducir que aparecen de paso (el título "Handwriting"): son de otro issue.

## Criterios de aceptación

CA1 y CA2 se verifican con un test instrumentado sobre las vistas, midiendo con la pantalla
configurada como horizontal; CA3 a CA5 en la tablet, girándola.

- **CA1.** Con la altura disponible de una pantalla horizontal, la lista de herramientas muestra
  una señal visible de que continúa más allá del borde.
- **CA2.** Tras un cambio de tamaño de la ventana, el relleno inicial del timeline corresponde a
  la mitad del **ancho nuevo**, no del anterior.
- **CA3.** (Tablet.) En horizontal, cada herramienta de la lista se puede alcanzar y pulsar.
- **CA4.** (Tablet.) Con una herramienta abierta en horizontal, la pista del timeline y el cabezal
  siguen visibles.
- **CA5.** (Tablet.) Seleccionar un clip, mover el cabezal a la mitad, girar: sigue seleccionado el
  mismo clip, el cabezal en el mismo tiempo y el encuadre igual.
- **CA6.** (Tablet.) En vertical, una sesión de edición y un export no muestran diferencia alguna
  respecto de antes del cambio.

## Diff contra el estado actual

| Aspecto | Hoy | Deseado |
|---|---|---|
| Lista de herramientas en horizontal | Se desplaza, pero nada lo indica | Se ve que continúa (regla 1) |
| Inicio del timeline | Relleno de media pantalla en vertical, media pantalla vacía en horizontal | Relleno recalculado con el ancho actual (regla 2) |
| Opciones de herramienta | Abajo, comprimiendo el timeline | Sin tapar la pista (regla 3) |
| Estado al girar | No se pierde (la Activity no se recrea) | Igual, verificado por CA5 |
