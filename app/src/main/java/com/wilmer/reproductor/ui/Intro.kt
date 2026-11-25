package com.wilmer.reproductor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.ui.theme.BrandGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animación al abrir la app: el logo entra girando y rebotando, sale una onda de color
 * y aparece el nombre. Tocar la pantalla la salta.
 */
@Composable
fun IntroAnimation(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val logoScale = remember { Animatable(0f) }
    val logoTurn = remember { Animatable(-200f) }
    val wave = remember { Animatable(0f) }
    val wave2 = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
        launch { logoTurn.animateTo(0f, tween(750, easing = FastOutSlowInEasing)) }
        delay(380)
        launch { wave.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(160)
        launch { wave2.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { text.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
        delay(1_150)
        fade.animateTo(0f, tween(380))
        done()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Dos ondas que se expanden desde el logo
                listOf(wave.value, wave2.value).forEach { w ->
                    Box(
                        Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = 1f + w * 1.6f
                                scaleY = 1f + w * 1.6f
                                alpha = if (w == 0f) 0f else (1f - w) * 0.8f
                            }
                            .border(4.dp, BrandGradient, CircleShape),
                    )
                }
                BrandLogo(
                    Modifier.graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        rotationZ = logoTurn.value
                    },
                    size = 120.dp,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                brandName(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 40f
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tus videos, con power",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 60f
                },
            )
        }
    }
}
