---
tipo: producto
estado: propuesta — pendiente de aprobación
actualizado: 2026-09-22
---

# LibreCuts — Spec de producto

Qué queremos que sea la app. Lo que **hoy** hace está en `SPEC-ACTUAL.md`; al final de este
documento va el diff entre ambas. Las specs por feature viven en `specs/`.

## Qué es

Un editor de video para Android donde todo el trabajo ocurre en el teléfono o la tablet: se abre
un video, se corta, se le agregan clips, texto, audio y efectos, y se exporta un archivo. Sin
cuenta y sin subir nada.

Es un fork propio de `tharunbirla/LibreCuts`. No se planean PRs a upstream, así que no hay
compromiso de mantener sus decisiones.

## Para quién

Alguien que edita videos cortos en su tablet o teléfono y no quiere pagar una suscripción, ver
anuncios, ni subir su material a la nube. Hoy compara contra CapCut: esa es la vara de lo que
espera poder hacer y de lo fácil que espera que sea.

## Principios

1. **El material del usuario nunca sale del dispositivo.** Sus videos, fotos, audio y proyectos
   se editan y se exportan en el aparato. Nada se sube, ni para procesar ni para respaldar.
2. **La red solo trae, nunca lleva.** La app puede descargar contenido opcional —plantillas,
   efectos, transiciones—, y eso es todo lo que hace con una conexión. Editar y exportar funcionan
   con el modo avión activado; lo ya descargado sigue funcionando sin conexión.
3. **Lo que ves es lo que sale.** La vista previa y el archivo exportado muestran cada clip en la
   misma posición, tamaño y forma. Cuando no se puede (por costo de render), se dice en pantalla.
4. **Nada se pierde en silencio.** Una edición que no se puede guardar, un archivo que falta o un
   export que falla se avisan con un mensaje que nombra qué pasó.
5. **El trabajo del usuario es suyo.** Sin marca de agua, sin límite de duración, sin funciones
   detrás de un pago.

## Qué hace

Lo marcado **(fase 3)** todavía no existe; ver *Orden de trabajo*.

- Abrir uno o varios videos, imágenes y audios del dispositivo, y ordenarlos en un timeline.
- Editar cada clip: recortar, dividir, reordenar, borrar, velocidad, reversa, espejo, máscara,
  encuadre (tamaño y posición dentro del lienzo), congelar un cuadro, filtros y ajustes de color.
- Agregar encima: texto, subtítulos, imágenes, dibujo a mano alzada, audio y voz.
- Transiciones entre clips.
- **Superponer varios clips de video a la vez, en pistas paralelas (fase 3).**
- **Empezar un proyecto desde una plantilla (fase 3):** se elige una del catálogo, la app pide las
  fotos o videos que la plantilla necesita, se editan sus textos, y el editor abre con todo
  aplicado y editable como cualquier otro proyecto.
- **Descargar efectos, transiciones y filtros además de los que trae la app (fase 3).**
- Guardar el proyecto en un archivo `.lcprj` y volver a abrirlo.
- Exportar a un archivo de video en la galería, con la resolución y los cuadros por segundo que
  se elijan.
- Interfaz en español, además de los idiomas que ya mantiene Weblate upstream.

## Qué NO hace

- **No sube el material del usuario.** Sin respaldo en la nube, sin edición en servidor, sin
  compartir a redes desde la app.
- **No telemetría.** Ningún evento de uso sale del dispositivo, tampoco al usar el catálogo.
- **No cuentas.** Descargar una plantilla no pide registro ni identifica al usuario.
- **No anuncios ni compras dentro de la app.** El catálogo arranca gratis; si alguna vez deja de
  serlo, es una decisión que se anota y cambia este documento.
- **No procesa nada en un servidor.** Una función que necesite un modelo remoto no entra; si se
  puede correr en el dispositivo, sí.
- **No Play Store.** La distribución es GitHub Releases, F-Droid y Obtainium, como upstream.

## Orden de trabajo

1. **Base limpia.** Quitar APIs deprecadas y código muerto, para que lo demás se construya sobre
   algo que compila sin ruido.
2. **Que lo que existe funcione.** Corregir los bugs conocidos y traducir la interfaz al español.
   Es la fase actual.
3. **Crecer.** Empieza al cerrar las fases 1 y 2. Tres bloques, en este orden:

### 3a — Multipista de video

Superponer clips de video en pistas paralelas, no solo en una secuencia. Es lo que hoy separa a
la app de la vara de CapCut y lo que habilita buena parte de las plantillas.

**Sobre el tope de pistas:** el decodificador H.264 por hardware de la tablet de prueba declara 16
instancias concurrentes (`OMX.qcom.video.decoder.avc`, verificado el 2026-09-22), así que el techo
no lo pone el códec. Lo pondrán la memoria y la fluidez de la vista previa. El tope se fija
**midiendo** en el dispositivo más modesto que soportemos, no eligiendo un número de antemano, y
se declara en la interfaz cuando se alcanza en vez de dejar que el preview se arrastre.

### 3b — Plantillas

Un botón en la pantalla de inicio abre un catálogo de plantillas. El usuario elige una, la app le
pide las fotos o videos que esa plantilla ocupa, le deja cambiar los textos, y abre el editor con
la plantilla ya aplicada: a partir de ahí es un proyecto normal, editable en todo.

Al inicio todas las plantillas son gratuitas.

**Quién las hace:** las plantillas y sus videos de ejemplo los crea el equipo del proyecto. El
usuario solo descarga y aplica; **no envía nada**, ni sus materiales ni plantillas propias. Que un
usuario pueda publicar las suyas es una posibilidad futura y lejana, fuera de esta spec.

**Descargar o aplicar en línea:** la spec dice descargar. Aplicar en línea obligaría a subir el
material del usuario, que contradice el principio 1; una plantilla descargada además se reusa sin
conexión y no vuelve a costar tráfico. Queda abierto el resto del diseño (ver `Decisiones.md`).

**Cuando el catálogo no responde:** por ahora un mensaje genérico ("Disculpa, estamos teniendo
dificultades técnicas") y el resto de la app sigue funcionando. El texto definitivo se decide con
la retroalimentación de la beta.

### 3c — Efectos y transiciones descargables

La app trae un juego básico incluido, que funciona sin haber descargado nunca nada, y el catálogo
ofrece más. Mismo modelo que las plantillas.

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
- **CA6.** Ninguna dependencia del APK abre conexiones por su cuenta, y las únicas peticiones que
  hace la app son las del catálogo, a iniciativa del usuario. Pendiente de comprobar: M-232.
- **CA7 (fase 3).** Una plantilla ya descargada se aplica y se edita con el modo avión activado.
- **CA8 (fase 3).** Con el número máximo de pistas que declare la app, la vista previa se
  reproduce sin saltarse cuadros en el dispositivo de referencia.
- **CA9 (fase 3).** Todo el contenido del catálogo se distribuye bajo una licencia libre explícita,
  propia o cedida por quien lo aportó. La app no incluye ni descarga material de terceros sin ella.
- **CA10 (fase 3).** Publicar una plantilla nueva no requiere compilar ni publicar una versión de
  la app.

## Diff contra el estado actual

| Aspecto | Hoy (`SPEC-ACTUAL.md`) | Deseado |
|---|---|---|
| Pistas de video | Una sola secuencia de clips | Varias pistas superpuestas, con tope medido (3a) |
| Punto de partida de un proyecto | Siempre desde cero | También desde una plantilla (3b) |
| Efectos y transiciones | Los que trae el APK | Los del APK más los descargables (3c) |
| Uso de red | Ninguno | Solo para traer contenido del catálogo, sin cuenta |
| Preview vs export | Coinciden en secuencia, encuadre, máscara, espejo y transiciones (verificado); el resto sin documentar | CA2 para toda la app |
| Proyecto guardado | Apunta a archivos de la caché; si se limpia, pierde los clips (M-229) | CA3 |
| Idioma | Quedan textos en inglés (códigos de error, algunos diálogos) | CA5 |
| Errores | Hay códigos `LC-xxx` y pantalla de error; falta confirmar que todo camino de fallo llega ahí | CA4 |
| Cobertura de la spec descriptiva | Solo secuencia, congelar, máscara y transiciones | Texto, audio, subtítulos, filtros y export completo |

## Verificación

Los tres comandos de `CLAUDE.md` (`assembleDebug`, `lintDebug`, `testDebugUnitTest`) más los
tests instrumentados en la tablet (`node scripts/test-tablet.mjs`). Los criterios CA1 a CA10 se
comprueban a mano en el dispositivo, uno por release.
