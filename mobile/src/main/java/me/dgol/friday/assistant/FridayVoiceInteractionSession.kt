package me.dgol.friday.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight assistant overlay shown when Friday is invoked via the system Assist gesture.
 * Appears over other apps inside the VoiceInteractionSession window.
 */
class FridayVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {

    private var isShowing by mutableStateOf(false)

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    AssistOverlay(
                        visible = isShowing,
                        onClose = { hide() }
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        isShowing = true
        // Hook STT warm-up here if desired.
    }

    override fun onHide() {
        super.onHide()
        isShowing = false
    }

    override fun onCloseSystemDialogs() {
        super.onCloseSystemDialogs()
        hide()
    }
}

@Composable
private fun AssistOverlay(
    visible: Boolean,
    onClose: () -> Unit
) {
    val targetHeight = 220.dp
    val heightPx by animateFloatAsState(
        targetValue = if (visible) targetHeight.value else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "overlayHeight"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = heightPx > 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightPx.dp)
                    .align(Alignment.BottomCenter)
            ) {
                SemicirclePanel(
                    modifier = Modifier.fillMaxSize(),
                    background = MaterialTheme.colorScheme.surface,
                    accent = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MicPulse(size = 56.dp)
                    Text("Listening…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onClose) { Text("Dismiss") }
                }
            }
        }
    }
}

/**
 * Draw a semi-circle popping from the bottom of the window.
 */
@Composable
private fun SemicirclePanel(
    modifier: Modifier = Modifier,
    background: Color,
    accent: Color
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val radius = (w * 0.65f).coerceAtLeast(h * 0.9f)
        val left = (w / 2f) - radius
        val top = h - (radius * 2f)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        val topLeft = androidx.compose.ui.geometry.Offset(left, top)

        // Filled hump
        drawArc(
            color = background,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = arcSize
        )

        // Subtle ring
        drawArc(
            color = accent.copy(alpha = 0.06f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(left + 8f, top + 8f),
            size = androidx.compose.ui.geometry.Size(arcSize.width - 16f, arcSize.height - 12f),
            style = Stroke(width = 6f)
        )
    }
}

/** Pulsing mic dot. */
@Composable
private fun MicPulse(size: Dp) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size(size * pulse.value)
                .clip(CircleShape)
                .alpha(0.25f)
                .background(MaterialTheme.colorScheme.primary)
        )
        // Solid inner dot
        Box(
            modifier = Modifier
                .size(size * 0.5f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
