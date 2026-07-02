package com.janadhikar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.janadhikar.R
import com.janadhikar.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * Slide-to-cancel for the SOS countdown. A deliberate-motion control: a panic
 * grip or pocket brush cannot cancel an emergency SMS, but a calm intentional
 * swipe can. Releasing early snaps the thumb back.
 */
@Composable
fun CancelSlider(
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = 72.dp
    val thumbSize = 60.dp
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
    val paddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - paddingPx).coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .background(Color(0xFF262626), RoundedCornerShape(trackHeight / 2))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .testTag("sos_cancel_slider"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sos_cancel),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.DimGray,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(6.dp)
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .background(Palette.DangerRed, RoundedCornerShape(50))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(0f, maxDragPx)
                    },
                    onDragStopped = {
                        if (offsetPx >= maxDragPx * COMPLETE_FRACTION) {
                            onCancelled()
                        }
                        offsetPx = 0f
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.sos_cancel),
                tint = Palette.White,
            )
        }
    }
}

private const val COMPLETE_FRACTION = 0.85f
