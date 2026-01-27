package net.damian.tablethub.ui.screens.slideshow.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.random.Random

/**
 * Ken Burns effect parameters for a single animation cycle.
 */
private data class KenBurnsParams(
    val startScale: Float,
    val endScale: Float,
    val startTranslationX: Float,
    val endTranslationX: Float,
    val startTranslationY: Float,
    val endTranslationY: Float
)

/**
 * Generate random Ken Burns effect parameters.
 * The effect involves subtle panning and zooming.
 */
private fun generateKenBurnsParams(): KenBurnsParams {
    // Randomize whether we zoom in or zoom out
    val zoomIn = Random.nextBoolean()
    val startScale = if (zoomIn) 1.0f else 1.15f
    val endScale = if (zoomIn) 1.15f else 1.0f

    // Random pan directions (small movements, about 5% of the image)
    val maxTranslation = 50f
    val startTranslationX = Random.nextFloat() * maxTranslation * 2 - maxTranslation
    val endTranslationX = Random.nextFloat() * maxTranslation * 2 - maxTranslation
    val startTranslationY = Random.nextFloat() * maxTranslation * 2 - maxTranslation
    val endTranslationY = Random.nextFloat() * maxTranslation * 2 - maxTranslation

    return KenBurnsParams(
        startScale = startScale,
        endScale = endScale,
        startTranslationX = startTranslationX,
        endTranslationX = endTranslationX,
        startTranslationY = startTranslationY,
        endTranslationY = endTranslationY
    )
}

/**
 * Composable that displays an image with the Ken Burns effect.
 * The Ken Burns effect is a type of panning and zooming animation
 * commonly used in documentaries and slideshows.
 *
 * @param imageUrl The URL of the image to display
 * @param durationMs The duration of the animation in milliseconds
 * @param enabled Whether the Ken Burns effect is enabled
 * @param contentDescription Optional content description for accessibility
 */
@Composable
fun KenBurnsImage(
    imageUrl: String,
    durationMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val animationProgress = remember { Animatable(0f) }
    val params = remember(imageUrl) { generateKenBurnsParams() }

    LaunchedEffect(imageUrl, enabled, durationMs) {
        if (enabled) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs.toInt(),
                    easing = LinearEasing
                )
            )
        } else {
            animationProgress.snapTo(0f)
        }
    }

    val progress = animationProgress.value

    // Interpolate values based on animation progress
    val scale = if (enabled) {
        params.startScale + (params.endScale - params.startScale) * progress
    } else {
        1f
    }

    val translationX = if (enabled) {
        params.startTranslationX + (params.endTranslationX - params.startTranslationX) * progress
    } else {
        0f
    }

    val translationY = if (enabled) {
        params.startTranslationY + (params.endTranslationY - params.startTranslationY) * progress
    } else {
        0f
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.translationX = translationX
                    this.translationY = translationY
                }
        )
    }
}
