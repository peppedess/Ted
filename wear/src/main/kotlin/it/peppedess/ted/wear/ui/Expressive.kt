package it.peppedess.ted.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Il numero dei non letti scorre verso l'alto quando cresce e verso il basso
 * quando cala. Piccolo dettaglio, ma e quello che distingue un'app viva.
 */
@Composable
fun AnimatedCount(
    count: Int,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInVertically { it } + fadeIn()) togetherWith
                    (slideOutVertically { -it } + fadeOut())
            } else {
                (slideInVertically { -it } + fadeIn()) togetherWith
                    (slideOutVertically { it } + fadeOut())
            }
        },
        modifier = modifier,
        label = "unread"
    ) { value ->
        Text(
            text = if (value > 9) "9+" else value.toString(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

/**
 * Raggio degli angoli che si stringe alla pressione.
 *
 * E il morphing di forma dell'Expressive: alla pressione la superficie
 * si "raccoglie" invece di limitarsi a schiarire.
 */
@Composable
fun pressMorphRadius(
    interactionSource: InteractionSource,
    resting: Dp = 16.dp,
    pressed: Dp = 26.dp
): Dp {
    val isPressed by interactionSource.collectIsPressedAsState()
    val transition = updateTransition(targetState = isPressed, label = "press")
    val radius by transition.animateDp(
        transitionSpec = { tween(durationMillis = 180) },
        label = "radius"
    ) { down -> if (down) pressed else resting }
    return radius
}

/** Rettangolo con luccichio scorrevole, al posto dello spinner. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -400f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )

    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(shift, 0f),
                    end = Offset(shift + 320f, 0f)
                )
            )
    )
}

/** Scheletro della lista chat mentre il telefono si sveglia. */
@Composable
fun ChatListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(4) { index ->
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index % 2 == 0) 52.dp else 46.dp)
                    .padding(vertical = 3.dp)
            )
        }
    }
}
