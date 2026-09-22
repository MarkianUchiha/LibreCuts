---
feature: preview de transiciones entre clips
issue: M-211 (punto 3)
estado: aprobada
actualizado: 2026-09-22
---

# Preview de transiciones entre clips

## Qué hace

Cuando hay una transición entre dos clips, el preview la muestra al reproducir o recorrer el
corte: el clip saliente desaparece con el efecto elegido (fundido, cortina, deslizamiento…)
mientras entra el siguiente.

## Reglas

1. **Cada clip se ve como es durante toda la transición.** El clip saliente conserva su
   encuadre, espejo, máscara y recorte de principio a fin, aunque el playhead ya haya cruzado el
   corte; el entrante se ve con los suyos.
2. **Sin deformaciones.** La imagen del clip saliente mantiene su proporción y ocupa el mismo
   lugar del lienzo que ocupaba justo antes de la transición.
3. **El preview se parece al export.** Un cuadro del preview a mitad de la transición muestra a
   cada clip en la misma posición y tamaño que el cuadro equivalente del export.
4. **Sin transición, nada cambia.** Los cortes sin transición y los clips fuera de la ventana de
   transición se ven exactamente igual que hoy.

## Qué NO hace (fuera de alcance)

- Mostrar el clip saliente en movimiento: sigue siendo una imagen fija durante la transición.
- Cambiar los efectos disponibles, su curva o la duración de la ventana.
- Las transiciones del export.
- El difuminado de la máscara (M-228), que tampoco se ve fuera de las transiciones.

## Criterios de aceptación

CA1–CA3 se verifican con un test instrumentado sobre la vista de la transición (la captura del
saliente se dibuja con la geometría que se le indique); CA4–CA6, en la tablet.

- **CA1.** Con un saliente encuadrado a escala 0.5 desplazado y el contenedor ya con el encuadre
  del entrante, la captura se dibuja en el rectángulo del saliente, no en el del entrante.
- **CA2.** Con un saliente espejado, la captura se dibuja espejada.
- **CA3.** La captura conserva la proporción del video: un cuadro 16:9 no se estira a un área de
  otra proporción.
- **CA4.** (Tablet.) La escena de M-211: A abajo-derecha, B arriba-izquierda, "Wipe L". En las
  capturas antes y después del corte, la franja de A está en el mismo lugar.
- **CA5.** (Tablet.) Transición entre dos clips sin encuadre ni espejo: se ve igual que antes
  del arreglo.
- **CA6.** (Tablet.) Un cuadro del export a mitad de la transición de CA4 **sin máscara** (solo
  encuadre y espejo) muestra a A y B en las mismas posiciones que el preview. Se deja fuera la
  máscara porque el export con máscara es impracticablemente lento (M-227).

## Diff contra el estado actual

| Aspecto | Hoy (`SPEC-ACTUAL.md`) | Deseado |
|---|---|---|
| Saliente después del corte | Toma el encuadre del entrante: brinca | Conserva el suyo (regla 1) |
| Espejo y máscara del saliente | No viajan en la captura `[SIN VERIFICAR]` | Se conservan (regla 1) |
| Proporción de la captura | Se estira a todo el overlay | Se conserva (regla 2) |
| Cortes sin transición | — | Sin cambio (regla 4) |
