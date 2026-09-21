---
feature: congelar cuadro
issue: M-211 (punto 2)
estado: aprobada (2026-09-21)
actualizado: 2026-09-21
---

# Congelar cuadro

## Qué hace

Con un clip seleccionado, el usuario congela el cuadro bajo el playhead. La app inserta en la
secuencia un clip fijo de 3 s con ese cuadro, en la posición del playhead, y el clip de origen
queda intacto alrededor de él.

## Reglas

1. **El freeze se ve igual que el clip de donde salió.** Hereda del clip de origen:
   - encuadre (escala y posición en el lienzo);
   - espejo;
   - máscara, **congelada en su estado del instante congelado**: posición, tamaño, rotación
     y difuminado fijos, sin keyframes. Así la máscara no se anima encima de una imagen fija.
2. **No hereda velocidad ni reversa.** Un cuadro fijo no tiene movimiento; su duración es
   siempre 3 s.
3. **Posición en la secuencia:**
   - Playhead dentro del clip → el clip se parte en dos y el freeze queda en medio.
   - Playhead al inicio del clip → el freeze va antes del clip.
   - Playhead al final del clip → el freeze va después del clip.
4. **Si el freeze queda primero en la secuencia, se convierte en el clip principal.** El clip
   que era principal pasa a segunda posición con todas sus propiedades.
5. **Ningún clip se duplica, se pierde ni cambia su recorte** salvo el que se partió en dos.
6. **Un solo undo** deshace el freeze completo y deja la secuencia como estaba.

## Qué NO hace (fuera de alcance)

- Elegir la duración del freeze (sigue fija en 3 s).
- Tomar el cuadro exacto del playhead en vez del keyframe más cercano. Anotado en
  `SPEC-ACTUAL.md` → Sin verificar; si se confirma, va en issue aparte.
- Congelar sobre imágenes o clips invertidos (comportamiento sin mapear).
- Guardar y reimportar un `.lcprj` con freeze (M-211 punto 4).

## Criterios de aceptación

Cada uno se verifica con un test instrumentado sobre el ViewModel, salvo el último.

- **CA1.** Secuencia `[P]`, freeze al inicio de P → secuencia `[F, P]`; el principal es F,
  P conserva su uri y su recorte original, y hay exactamente 2 clips.
- **CA2.** Secuencia `[P, M]`, freeze a la mitad de P → `[P₁, F, P₂, M]`; P₁ y P₂ juntos
  cubren el recorte original de P sin huecos ni solapes.
- **CA3.** Freeze sobre un clip en posición > 0 → el principal no cambia.
- **CA4.** Origen con encuadre, espejo y máscara → F tiene el mismo encuadre y espejo.
- **CA4b.** Origen con máscara animada por keyframes → la máscara de F no tiene keyframes y sus
  valores fijos son los que la máscara del origen tenía en el instante congelado.
- **CA5.** Origen con velocidad 2× y reversa → F tiene velocidad 1× y sin reversa.
- **CA6.** Un undo después del freeze → secuencia, principal y recortes idénticos al estado
  previo.
- **CA7.** (En la tablet.) Congelar sobre un clip encuadrado: preview y export muestran el
  freeze con el mismo encuadre que el clip, sin brinco a cuadro completo.

## Diff contra el estado actual

| Aspecto | Hoy (`SPEC-ACTUAL.md`) | Deseado |
|---|---|---|
| Freeze al inicio del principal | Recorta el principal a 3 s, lo duplica y pierde el freeze | Freeze pasa a principal (regla 4) |
| Encuadre, espejo, máscara | No se heredan | Se heredan (regla 1) |
| Undo | Dos pasos | Uno (regla 6) |
| Resto de posiciones | Correctas | Sin cambio |
