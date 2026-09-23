---
feature: la app cumple lo que Android 16 exige a quien apunta a API 36
issue: M-236
estado: aprobada
actualizado: 2026-09-22
---

# Apuntar a Android 16 (API 36)

## Por qué

Google Play no acepta apps nuevas que apunten por debajo de API 36 desde el 31 de agosto de 2026.
Sin esto no hay distribución por Play, que es el canal principal (`SPEC.md`, *Distribución*).

Subir el número es lo de menos: el sistema cambia cómo trata a la app en cuanto lo ve, y hay dos
cambios que la afectan de verdad.

## Qué cambia cuando la app apunta a API 36

1. **La app ya no decide su orientación ni su tamaño en pantallas grandes.** En cualquier pantalla
   de 600dp o más de ancho mínimo —la tablet de prueba mide 685dp— el sistema ignora la
   orientación y las restricciones de redimensionado que declare la app, y la hace ocupar la
   ventana completa. Existe una exención temporal, pero deja de funcionar al apuntar a API 37.
2. **La app dibuja de borde a borde y no puede evitarlo.** Su contenido pasa por debajo de la
   barra de estado y de la barra de navegación; es la app quien tiene que apartar sus controles de
   ahí. Desde API 35 es el comportamiento por omisión y en API 36 ya no hay forma de desactivarlo.

## Reglas

1. **Nada queda tapado.** Ningún control, texto ni indicador queda debajo de la barra de estado,
   de la barra de navegación ni del área de gestos, en ninguna orientación.
2. **Nada queda inalcanzable.** Toda herramienta que exista en vertical se puede alcanzar y usar
   en horizontal y en una ventana reducida, aunque sea desplazándose.
3. **Editar y exportar no cambian.** Lo que la app hace con un video —el timeline, el preview, el
   export— se comporta igual que antes en el dispositivo de prueba actual.
4. **Nada nuevo se pierde en silencio.** Si el sistema interrumpe un trabajo en segundo plano, el
   usuario se entera; no queda un export a medias sin aviso.

## Qué NO hace (fuera de alcance)

- Rediseñar la interfaz para pantallas grandes. Aquí solo se persigue que nada quede tapado ni
  inalcanzable; el aprovechamiento del espacio es M-194.
- Migrar el trabajo en segundo plano a otra API. El límite que Android 15 impone a los servicios
  de sincronización es de **6 horas acumuladas en 24**, y un export dura minutos: no es el
  problema que parecía. Basta con que la app reaccione bien si alguna vez lo alcanza.
- Subir `minSdk`. Sigue en 26.
- Cambiar el aspecto visual de la app más allá de lo que exijan las reglas 1 y 2.

## Criterios de aceptación

Los de dispositivo se verifican en el teléfono con Android 16 del equipo: la tablet es Android 13
y ahí estos cambios no se manifiestan. Los insets sí se pueden comprobar en la tablet forzando el
dibujo de borde a borde.

- **CA1.** La app compila y empaqueta con `targetSdk 36`, sin advertencias nuevas del compilador,
  y la suite instrumentada completa sigue en verde.
- **CA2.** (Android 16.) En vertical y en horizontal, ningún elemento de la interfaz del editor
  queda debajo de las barras del sistema. Comprobable comparando capturas contra la posición de
  las barras.
- **CA3.** (Android 16, pantalla ≥600dp.) Con la app en horizontal, todas las herramientas de la
  barra de edición se pueden alcanzar y pulsar. Depende de M-194.
- **CA4.** (Android 16.) Con la app en media pantalla, sigue siendo posible abrir un video,
  recortarlo y exportarlo.
- **CA5.** (Android 16.) Un export completo termina y el archivo aparece en la galería.
- **CA6.** (Tablet, Android 13.) Un export completo y una sesión de edición no muestran ninguna
  diferencia respecto de antes del cambio.

## Diff contra el estado actual

| Aspecto | Hoy | Deseado |
|---|---|---|
| `targetSdk` | 34: Play no acepta la app | 36 |
| Orientación en pantalla grande | La decide la app | La decide el sistema; la app se adapta |
| Barras del sistema | La app dibuja dentro de ellas | Dibuja por debajo y apartando sus controles |
| Interfaz en horizontal | Herramientas cortadas (M-194) | Todas alcanzables (regla 2) |
| Verificación | Solo en la tablet (Android 13) | Tablet (Android 13) + dispositivo con Android 16 |

## Notas de riesgo

- **El build con `compileSdk 36` ya funciona** con el AGP actual (probado el 2026-09-22:
  `BUILD SUCCESSFUL`), pero avisa que recomienda una versión más nueva del plugin. Subir AGP entra
  en el plan, no en esta spec.
- **Este cambio convierte M-194 en obligatorio.** Hoy la app se ve mal en horizontal pero es
  evitable; con API 36, en la tablet, el sistema la puede poner en horizontal o en media pantalla
  cuando quiera y la app no tiene forma de negarse.
