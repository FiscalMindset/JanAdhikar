package com.janadhikar.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.janadhikar.R
import com.janadhikar.ui.theme.Palette

/**
 * The Trigger state's centerpiece: a massive pulsing mic occupying ~50% of the
 * screen. The ENTIRE circle (and its pulse halo) is the tap target — a shaking
 * hand cannot miss it.
 */
@Composable
fun PulsingMicButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val haloScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100), RepeatMode.Reverse),
        label = "haloScale",
    )
    val haloAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100), RepeatMode.Reverse),
        label = "haloAlpha",
    )

    val label = stringResource(R.string.mic_button)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("mic_button")
            .semantics { contentDescription = label }
            .clickable(onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        // Pulse halo
        Box(
            Modifier
                .fillMaxSize()
                .scale(haloScale)
                .graphicsLayer { alpha = haloAlpha }
                .background(Palette.DirectiveYellow, CircleShape),
        )
        // Solid button
        Box(
            Modifier
                .fillMaxSize(0.78f)
                .background(Palette.DirectiveYellow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null, // announced by the parent semantics
                tint = Palette.InkBlack,
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}
