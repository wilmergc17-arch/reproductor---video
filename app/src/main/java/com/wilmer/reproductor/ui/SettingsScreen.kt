package com.wilmer.reproductor.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.data.AppSettings
import com.wilmer.reproductor.data.ResumeStore
import com.wilmer.reproductor.data.SEEK_STEP_OPTIONS
import com.wilmer.reproductor.data.SPEED_OPTIONS
import com.wilmer.reproductor.data.SUBTITLE_SCALES
import com.wilmer.reproductor.ui.player.AspectMode
import com.wilmer.reproductor.ui.player.speedLabel
import com.wilmer.reproductor.data.TapMode
import com.wilmer.reproductor.data.ThemeChoice
import com.wilmer.reproductor.ui.theme.backgroundFor
import com.wilmer.reproductor.ui.theme.brandColorsFor

/** Ajustes: tema, gestos del reproductor y reproducción. Se guardan al instante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppSettings.get(context) }
    val settings by store.state.collectAsState()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val themes = ThemeChoice.entries.filter { it != ThemeChoice.FONDO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SectionTitle("Tema")
            themes.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    row.forEach { t ->
                        ThemeSwatch(t, selected = settings.theme == t, modifier = Modifier.weight(1f)) {
                            store.update { it.copy(theme = t) }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            SectionTitle("Gestos del reproductor")
            SwitchRow("Volumen", "Deslizar arriba o abajo en la mitad derecha", settings.gestureVolume) { v ->
                store.update { it.copy(gestureVolume = v) }
            }
            SwitchRow("Brillo", "Deslizar arriba o abajo en la mitad izquierda", settings.gestureBrightness) { v ->
                store.update { it.copy(gestureBrightness = v) }
            }
            SwitchRow("Adelantar o atrasar", "Deslizar a la izquierda o a la derecha", settings.gestureSeek) { v ->
                store.update { it.copy(gestureSeek = v) }
            }
            SwitchRow("Mantener presionado para 2x", "Mientras sostienes el dedo el video va al doble", settings.holdForSpeed) { v ->
                store.update { it.copy(holdForSpeed = v) }
            }
            Hint(
                "Los bordes de la pantalla quedan libres para los gestos de Android (inicio, atrás, multitarea). " +
                    "En pausa aparecen los botones de Android y los gestos del video se apagan."
            )

            SectionTitle("Doble toque a los lados")
            TapMode.entries.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { store.update { it.copy(tapMode = m) } }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.tapMode == m, onClick = { store.update { it.copy(tapMode = m) } })
                    Text(m.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            ChoiceRow(
                "Salto de adelantar o atrasar",
                SEEK_STEP_OPTIONS.map { it to "$it segundos" },
                settings.seekStepSec,
            ) { v -> store.update { it.copy(seekStepSec = v) } }

            SectionTitle("Reproducción")
            SwitchRow("Reproducir el siguiente", "Al terminar un video empieza solo el siguiente de la lista", settings.autoNext) { v ->
                store.update { it.copy(autoNext = v) }
            }
            SwitchRow("Recordar dónde me quedé", "Cada video continúa desde donde lo dejaste", settings.rememberPosition) { v ->
                store.update { it.copy(rememberPosition = v) }
            }
            ChoiceRow("Velocidad al abrir", SPEED_OPTIONS.map { it to speedLabel(it) }, settings.defaultSpeed) { v ->
                store.update { it.copy(defaultSpeed = v) }
            }
            ChoiceRow(
                "Formato de imagen al abrir",
                AspectMode.entries.map { it.name to it.label },
                settings.defaultAspect,
            ) { v -> store.update { it.copy(defaultAspect = v) } }
            SwitchRow(
                "Audio en segundo plano",
                "Sigue sonando con la pantalla apagada o mientras usas otra app",
                settings.backgroundAudio,
            ) { v -> store.update { it.copy(backgroundAudio = v) } }
            SwitchRow(
                "Ventana flotante al salir",
                "Si sales de la app con un video sonando, sigue en una ventana pequeña",
                settings.autoPip,
            ) { v -> store.update { it.copy(autoPip = v) } }
            Hint("El temporizador para apagar está en el menú ⋮ del reproductor.")

            SectionTitle("Reels")
            SwitchRow("Repetir cada reel", "El reel se repite hasta que deslizas al siguiente", settings.loopReels) { v ->
                store.update { it.copy(loopReels = v) }
            }
            Hint(
                "En Reels desliza arriba para el siguiente y abajo para el anterior; un toque pausa. " +
                    "El volumen y el brillo se manejan con los botones y ajustes del teléfono."
            )

            SectionTitle("Subtítulos")
            ChoiceRow("Tamaño del texto", SUBTITLE_SCALES, settings.subtitleScale) { v ->
                store.update { it.copy(subtitleScale = v) }
            }

            SectionTitle("Privacidad")
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                OutlinedButton(onClick = {
                    ResumeStore(context).clearAll()
                    Toast.makeText(context, "Historial de Seguir viendo borrado", Toast.LENGTH_SHORT).show()
                }) { Text("Borrar historial de Seguir viendo") }
            }

            SectionTitle("Acerca de")
            Hint("Repropower $version")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/** Fila con el valor elegido a la derecha; al tocarla se abre la lista de opciones. */
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                options.firstOrNull { it.first == selected }?.second.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { open = false; onSelect(value) },
                    trailingIcon = if (value == selected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Muestra de un tema: círculo con su degradado y, al centro, su color de fondo. */
@Composable
private fun ThemeSwatch(choice: ThemeChoice, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val brand = brandColorsFor(choice)
    Column(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(brand.start, brand.middle, brand.end)))
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(backgroundFor(choice)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    val tint = if (choice == ThemeChoice.CLARO) Color.Black else Color.White
                    Icon(Icons.Default.Check, contentDescription = "Elegido", tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            choice.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
