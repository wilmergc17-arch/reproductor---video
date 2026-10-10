# Reproductor de Video (Android)

App Android en Kotlin con Jetpack Compose y Media3 (ExoPlayer).

## Qué hace (versión base 0.1.0)
- Pantalla de inicio con dos botones: elegir un video del teléfono (selector del sistema, sin pedir permisos) o probar con un video de ejemplo en línea.
- Reproduce el video a pantalla completa con los controles estándar de Media3 (play/pausa, barra de progreso, adelantar/atrasar).
- Se puede abrir un video desde otras apps ("Abrir con… Reproductor de Video").
- Pausa al salir de la app; el botón Atrás vuelve al inicio.

## Requisitos
- Android 8.0 (API 26) o superior.
- Para compilar: JDK 17 y Android SDK 35 (Android Studio los trae).

## Compilar el APK
- **Android Studio:** abrir esta carpeta y pulsar Run, o `Build > Build APK(s)`.
- **Terminal:** `./gradlew assembleDebug` → el APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
- **GitHub:** al subir esta carpeta a un repositorio, el workflow `.github/workflows/build-apk.yml` compila el APK automáticamente y lo deja descargable en la pestaña Actions.

## Estructura
- `MainActivity.kt`: punto de entrada; recibe videos abiertos desde otras apps.
- `ui/ReproductorApp.kt`: pantalla de inicio y navegación simple.
- `ui/PlayerScreen.kt`: el reproductor ExoPlayer dentro de Compose.
- `ui/theme/Theme.kt`: colores de la app.
