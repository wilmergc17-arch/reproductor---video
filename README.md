# Reproductor de Video (Android)

App Android en Kotlin con Jetpack Compose y Media3 (ExoPlayer).

## Qué hace (versión 0.2.1)

### Biblioteca
- Tres formas de encontrar videos, combinables desde el menú ⋮ > "Carpetas a escanear…":
  - **Galería de Android** (permiso de videos; en Android 14 también acceso parcial).
  - **Escanear todo el teléfono**: recorre memoria interna y tarjeta SD archivo por archivo, incluidas carpetas que la galería no indexa (pide "acceso a todos los archivos").
  - **Elegir carpetas**: el selector del sistema; las carpetas quedan guardadas y se vuelven a revisar al abrir la app.
- Miniaturas y duración; si no aparece nada, la pantalla dice cuántos videos dio cada fuente y el error, si hubo.
- Pestañas: **Videos** (ordenar por fecha, nombre, duración o tamaño), **Carpetas**, **Películas**, **Series** y **Listas**.
- Búsqueda sin distinguir mayúsculas ni tildes.
- Se actualiza sola cuando agregas o borras videos.
- Abrir cualquier archivo, una dirección de red (http, https, rtsp, HLS .m3u8, DASH) o el video de ejemplo desde el menú ⋮.

### Clasificación automática por nombre de archivo
Funciona en el teléfono, sin internet (`classify/VideoClassifier.kt`):
- **Series:** `Serie S01E02`, `Serie 1x02`, `Serie Temporada 1 Capitulo 2`, `Serie Cap 105` (T1 E05), anime `[Grupo] Serie - 05`, o carpetas `Serie/Temporada 1/Episodio 3.mp4`. Se agrupan por serie y temporada.
- **Películas:** año en el nombre (`Coco (2017).mp4`), etiquetas como `BluRay`, `WEB-DL`, `x264`, carpeta `Películas`/`Movies`, o duración de una hora o más.
- **Otros:** videos de cámara, WhatsApp, grabaciones de pantalla y clips cortos.

### Listas de reproducción
- Crear, renombrar y borrar listas; agregar videos, temporadas completas o carpetas.
- Reordenar (subir/bajar) y quitar videos; reproducir en orden o aleatorio.
- "Crear lista" desde una serie arma la lista con todos sus episodios en orden.

### Reproductor (estilo VLC)
- Gestos: deslizar horizontal para adelantar/atrasar, vertical a la izquierda para brillo y a la derecha para volumen, doble toque a los lados para ±10 s y al centro para pausa.
- Velocidad de 0.25x a 4x, pista de audio, subtítulos internos o desde archivo (srt, vtt, ass, ttml).
- Ajuste de imagen (ajustar, llenar, estirar), rotación (según el video, libre, horizontal, vertical), bloqueo de pantalla.
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
