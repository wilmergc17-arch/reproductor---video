# Repropower (Android)

Reproductor de video para Android en Kotlin con Jetpack Compose y Media3 (ExoPlayer).

## Qué hace (versión 0.7.0)

### Diseño
- Tema oscuro con el degradado de la marca (naranja, rosa, violeta) y un ícono nuevo: botón de reproducir con un rayo.
- Barra de navegación abajo con las secciones: Videos, Carpetas, Películas, Series, Reels y Listas.
- Filas con miniaturas redondeadas y etiqueta de color por tipo (Película, Serie, Reel); Películas en cuadrícula de 2 columnas y Reels en cuadrícula vertical de 3.
- Controles del reproductor con sombras degradadas, botón de reproducir con el degradado y botones de cristal.
- El paquete sigue siendo `com.wilmer.reproductor`, así que se instala encima de la versión anterior sin perder la biblioteca ni las listas.

### Filtros de videos parecidos
- Fila de filtros arriba en Videos, Películas, Reels y dentro de una carpeta o serie: **Peso similar** y **Duración similar**, con tolerancia de ±5 %, ±10 % o ±20 %.
- Sin elegir un video, agrupa los que se parecen entre sí: sirve para encontrar copias repetidas y borrarlas con la selección múltiple.
- Desde el menú ⋮ de un video, "Ver de peso similar" o "Ver de duración similar" muestra los parecidos a ese video, del más cercano al más lejano, con la diferencia en %.

### Biblioteca
- Tres formas de encontrar videos, combinables desde el menú ⋮ > "Carpetas a escanear…":
  - **Galería de Android** (permiso de videos; en Android 14 también acceso parcial).
  - **Escanear todo el teléfono**: recorre memoria interna y tarjeta SD archivo por archivo, incluidas carpetas que la galería no indexa (pide "acceso a todos los archivos").
  - **Elegir carpetas**: el selector del sistema; las carpetas quedan guardadas y se vuelven a revisar al abrir la app.
- Miniaturas y duración; si no aparece nada, la pantalla dice cuántos videos dio cada fuente y el error, si hubo.
- Pestañas: **Videos** (ordenar por fecha, nombre A-Z/Z-A, duración o tamaño, también en Películas, Reels y dentro de cada carpeta), **Carpetas**, **Películas**, **Series**, **Reels** y **Listas**.
- Búsqueda sin distinguir mayúsculas ni tildes.
- Se actualiza sola cuando agregas o borras videos.
- Abrir cualquier archivo, una dirección de red (http, https, rtsp, HLS .m3u8, DASH) o el video de ejemplo desde el menú ⋮.

### Clasificación automática
Funciona en el teléfono, sin internet (`classify/VideoClassifier.kt`). La duración decide el tipo:
- **Películas:** una hora o más.
- **Series:** de 10 a 59 minutos. El nombre agrupa los episodios por serie y temporada (`Serie S01E02`, `1x02`, `Temporada 1 Capitulo 2`, `Cap 105`, anime `- 05`, carpetas `Serie/Temporada 1/`); si el nombre no trae número de episodio, la serie es la carpeta.
- **Reels:** menos de 10 minutos.
- Sin duración conocida se decide solo por el nombre (año, etiquetas como `BluRay`).

La biblioteca se guarda en el teléfono: al abrir la app aparece al instante y se actualiza en segundo plano. Las carpetas se vuelven a recorrer si pasó más de una hora, si cambian los ajustes o con "Actualizar biblioteca".

### Listas de reproducción
- Crear, renombrar y borrar listas; agregar videos, temporadas completas o carpetas.
- Eliminar videos del teléfono desde el menú ⋮ de cada video o con la selección múltiple, siempre con confirmación (Android puede pedir la suya también).
- Compartir videos con otras apps (WhatsApp, Telegram, Drive…) desde el menú ⋮, la selección múltiple o el reproductor.
- Selección múltiple: mantén presionado un video (o menú ⋮ > Seleccionar varios), marca otros y agrégalos juntos a una lista o reprodúcelos.
- Reordenar (subir/bajar) y quitar videos; reproducir en orden o aleatorio.
- "Crear lista" desde una serie arma la lista con todos sus episodios en orden.

### Reproductor (estilo VLC)
- Gestos: deslizar horizontal para adelantar/atrasar, vertical a la izquierda para brillo y a la derecha para volumen, doble toque a los lados para ±10 s y al centro para pausa.
- Velocidad de 0.25x a 4x, pista de audio, subtítulos internos o desde archivo (srt, vtt, ass, ttml).
- Ajuste de imagen (ajustar, llenar, estirar), rotación (abre en vertical por defecto; el botón cambia a horizontal, según el video o libre), bloqueo de pantalla.
- Cola con anterior/siguiente, repetir (todo / uno), aleatorio.
- Continúa donde lo dejaste en cada video.
- Ventana flotante (picture-in-picture) con el botón o al pulsar Inicio.
- Se puede abrir un video desde otras apps ("Abrir con… Reproductor de Video").

## Requisitos
- Android 8.0 (API 26) o superior.
- Para compilar: JDK 17 y Android SDK 35 (Android Studio los trae).

## Compilar el APK
- **Android Studio:** abrir esta carpeta y pulsar Run, o `Build > Build APK(s)`.
- **Terminal:** `./gradlew testDebugUnitTest assembleDebug` → el APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub:** al subir esta carpeta a un repositorio, el workflow `.github/workflows/build-apk.yml` compila el APK automáticamente y lo deja descargable en la pestaña Actions.

## Estructura
- `MainActivity.kt`: punto de entrada; videos abiertos desde otras apps y ventana flotante.
- `classify/VideoClassifier.kt`: reconoce películas y series por el nombre (pruebas en `app/src/test`).
- `data/`: lectura de videos (MediaStore), miniaturas, listas y posiciones guardadas.
- `ui/ReproductorApp.kt`, `ui/LibraryTabs.kt`: biblioteca, pestañas y listas.
- `ui/player/`: reproductor con gestos y controles.
- `ui/theme/Theme.kt`: colores de la app.
