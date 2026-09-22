---
feature: editar la máscara de un clip
issue: M-211 (punto 1)
estado: implementada (2026-09-21)
actualizado: 2026-09-21
---

# Editar la máscara de un clip

## Qué hace

Con un clip seleccionado y la herramienta de máscara abierta, el usuario ve el contorno de la
máscara sobre el video y lo arrastra con un dedo para moverla o lo pellizca para cambiar su
tamaño. El video se recorta en vivo con la máscara.

## Reglas

1. **El contorno coincide con la máscara.** Posición, tamaño, forma y rotación del contorno son
   exactamente los del área que la máscara deja ver (o esconde, si está invertida), sin importar
   la proporción del video ni la del lienzo.
2. **El contorno sigue al clip.** Si el clip tiene encuadre (escala y desplazamiento), el
   contorno se escala y se desplaza igual que el video.
3. **La máscara se mueve bajo el dedo.** Arrastrar mueve la máscara en pantalla exactamente lo
   que se movió el dedo, en ambos ejes, con o sin encuadre.
4. **Pellizcar cambia el tamaño en la misma proporción que el gesto**, con o sin encuadre.
5. **Lo que se guarda no cambia de significado.** Una máscara ya guardada en un proyecto se ve
   igual antes y después del arreglo, en preview y en export.
6. **Lo que se hace con el dedo se conserva.** Lo que se arrastra o pellizca sobre el video
   queda guardado al cerrar el panel, y mover después un deslizador (difuminado, tamaño,
   rotación) o cambiar la forma no lo regresa a su valor anterior. (Añadida el 2026-09-21: al
   verificar se encontró que el panel guardaba su copia local y descartaba los gestos.)

## Qué NO hace (fuera de alcance)

- Cambiar cómo se dibuja la máscara en el preview o en el export (ya coinciden entre sí).
- Keyframes de máscara (M-197) y su desfase al dividir (M-218).
- Rotar la máscara con dos dedos (hoy solo con el deslizador).
- El preview de transiciones (M-211 punto 3, spec aparte).
- La máscara de las imágenes superpuestas (usa otro overlay y otro camino de guardado).
- La velocidad del export con máscara.

## Criterios de aceptación

Los CA1–CA4 se verifican con un test instrumentado sobre la conversión entre la pantalla y las
coordenadas de la máscara; CA5 y CA6, en la tablet.

- **CA1.** Clip 16:9 en un preview más alto que ancho, sin encuadre, máscara rectangular al
  50 %: el contorno mide la mitad del ancho y la mitad del alto **del video**, centrado en él.
- **CA2.** Mismo caso con encuadre (escala 0.5, desplazado): el contorno coincide con el
  rectángulo recortado del video en pantalla.
- **CA3.** Arrastrar 100 px a la derecha y 100 px abajo mueve el centro del contorno 100 px en
  cada eje, sin encuadre y con encuadre de escala 0.5.
- **CA4.** Pellizcar al doble hace crecer el ancho y el alto del contorno en pantalla en la
  misma proporción con y sin encuadre, cerca del doble (el detector de pellizco de Android
  arranca tarde por la tolerancia del toque y se queda algo corto).
- **CA5.** (Tablet.) Con encuadre, el contorno cian queda pegado al borde del área visible
  mientras se arrastra y se pellizca.
- **CA6.** (Tablet.) Un proyecto con máscara guardado antes del arreglo exporta igual que antes.
- **CA7.** (Tablet.) Arrastrar la máscara, mover el deslizador de difuminado y cerrar el panel:
  la máscara queda donde se arrastró, con el difuminado nuevo.

## Diff contra el estado actual

| Aspecto | Hoy (`SPEC-ACTUAL.md`) | Deseado |
|---|---|---|
| Contorno sin encuadre | Mide el preview completo: el doble de alto en un video 16:9 | Mide el lienzo del video (regla 1) |
| Contorno con encuadre | No sigue escala ni desplazamiento | Los sigue (regla 2) |
| Arrastre | Velocidad según el tamaño del overlay y sin considerar la escala | 1:1 con el dedo (regla 3) |
| Máscara guardada, preview y export | Coinciden | Sin cambio (regla 5) |
| Gestos al cerrar el panel o mover un deslizador | Se pierden: el panel guarda su copia local | Se conservan (regla 6) |
