---
tipo: producto
estado: propuesta — pendiente de aprobación
actualizado: 2026-09-22
---

# LibreCuts — Spec de producto

Qué queremos que sea la app. Lo que **hoy** hace está en `SPEC-ACTUAL.md`; al final de este
documento va el diff entre ambas. Las specs por feature viven en `specs/`.

## Qué es

Un editor de video para Android que funciona entero en el teléfono o la tablet: se abre un video,
se corta, se le agregan clips, texto, audio y efectos, y se exporta un archivo. Sin cuenta, sin
conexión, sin enviar nada a ningún servidor.

Es un fork propio de `tharunbirla/LibreCuts`. No se planean PRs a upstream, así que no hay
compromiso de mantener sus decisiones.

## Para quién

Alguien que edita videos cortos en su tablet o teléfono y no quiere pagar una suscripción, ver
anuncios, ni subir su material a la nube. Hoy compara contra CapCut: esa es la vara de lo que
espera poder hacer y de lo fácil que espera que sea.

## Principios

1. **Todo es local.** Ninguna función depende de una conexión ni de una cuenta. Si una función no
   se puede hacer en el dispositivo, no entra.
2. **Lo que ves es lo que sale.** La vista previa y el archivo exportado muestran cada clip en la
   misma posición, tamaño y forma. Cuando no se puede (por costo de render), se dice en pantalla.
3. **Nada se pierde en silencio.** Una edición que no se puede guardar, un archivo que falta o un
   export que falla se avisan con un mensaje que nombra qué pasó.
4. **El trabajo del usuario es suyo.** Sin marca de agua, sin límite de duración, sin funciones
   detrás de un pago.

## Qué hace

- Abrir uno o varios videos, imágenes y audios del dispositivo, y ordenarlos en un timeline.
- Editar cada clip: recortar, dividir, reordenar, borrar, velocidad, reversa, espejo, máscara,
  encuadre (tamaño y posición dentro del lienzo), congelar un cuadro, filtros y ajustes de color.
- Agregar encima: texto, subtítulos, imágenes, dibujo a mano alzada, audio y voz.
- Transiciones entre clips.
- Guardar el proyecto en un archivo `.lcprj` y volver a abrirlo.
- Exportar a un archivo de video en la galería, con la resolución y los cuadros por segundo que
  se elijan.
- Interfaz en español, además de los idiomas que ya mantiene Weblate upstream.

## Qué NO hace

- **No sube nada.** Sin respaldo en la nube, sin cuentas, sin compartir a redes desde la app.
- **No telemetría.** Ningún evento de uso sale del dispositivo.
- **No anuncios ni compras dentro de la app.**
- **No edición multipista de video.** Una sola pista de video; el texto, las imágenes y el audio
  van encima, no en pistas de video paralelas.
- **No Play Store.** La distribución es GitHub Releases, F-Droid y Obtainium, como upstream.
- **No funciones que requieran un modelo en la nube** (quitar fondo por IA, subtítulos
  automáticos por servicio remoto). Si se hacen, se hacen en el dispositivo o no se hacen.

## Orden de trabajo

Tres fases, en este orden:

1. **Base limpia.** Quitar APIs deprecadas y código muerto, para que lo demás se construya sobre
   algo que compila sin ruido.
2. **Que lo que existe funcione.** Corregir los bugs conocidos y traducir la interfaz al español.
   Es la fase actual.
3. **Funciones tipo CapCut.** Las que falten respecto de esa vara, más un par de diferenciadores.

> **Decisión abierta:** cuáles son esos diferenciadores. Hasta que se definan, la fase 3 no tiene
> alcance y no se planea. No los invento aquí.

## Criterios de aceptación

Del producto, no de una feature. Cada uno es comprobable:

- **CA1.** La app instalada y con permiso de archivos edita y exporta un video con el modo avión
  activado, de principio a fin.
- **CA2.** Un cuadro cualquiera de la vista previa y el cuadro del mismo instante del archivo
  exportado muestran cada clip en la misma posición y tamaño relativos al lienzo, con una
  tolerancia del 1 % del alto.
- **CA3.** Un proyecto guardado se vuelve a abrir con todos sus clips después de reiniciar el
  dispositivo y de borrar la caché de la app.
- **CA4.** Ningún fallo termina en un cierre silencioso: todo error llega a `ErrorDisplayActivity`
  con su código `LC-xxx` y su registro.
- **CA5.** La interfaz no tiene texto en inglés en una instalación en español.
- **CA6.** El APK no incluye ninguna dependencia que abra conexiones de red por su cuenta.

## Diff contra el estado actual

| Aspecto | Hoy (`SPEC-ACTUAL.md`) | Deseado |
|---|---|---|
| Preview vs export | Coinciden en secuencia, encuadre, máscara, espejo y transiciones (verificado); el resto sin documentar | CA2 para toda la app |
| Proyecto guardado | Apunta a archivos de la caché; si se limpia, pierde los clips (M-229) | CA3 |
| Idioma | Quedan textos en inglés (códigos de error, algunos diálogos) | CA5 |
| Errores | Hay códigos `LC-xxx` y pantalla de error; falta confirmar que todo camino de fallo llega ahí | CA4 |
| Cobertura de la spec descriptiva | Solo secuencia, congelar, máscara y transiciones | Texto, audio, subtítulos, filtros y export completo |
| Fase 3 | No empezada | Sin alcance hasta definir los diferenciadores |

## Verificación

Los tres comandos de `CLAUDE.md` (`assembleDebug`, `lintDebug`, `testDebugUnitTest`) más los
tests instrumentados en la tablet (`node scripts/test-tablet.mjs`). Los criterios CA1 a CA6 se
comprueban a mano en el dispositivo, uno por release.
