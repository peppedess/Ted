package it.peppedess.ted.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import it.peppedess.ted.protocol.ChatMessage
import it.peppedess.ted.protocol.MessageContent

private const val KEY_REPLY = "ted_reply"

/** Poche e brevi: una risposta rapida che non entra su una riga non serve. */
private val QUICK_REPLIES = listOf("Ok", "Arrivo", "Ti richiamo", "Grazie")

/** Oltre questo scarto fra due messaggi mostriamo un separatore orario. */
private const val TIME_GAP_SECONDS = 30 * 60

@Composable
fun ChatScreen(
    title: String,
    messages: List<ChatMessage>,
    now: Long,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    anchorKey: Long,
    onSend: (String) -> Unit,
    onRecord: () -> Unit,
    loadImage: suspend (String) -> ImageBitmap?,
    onPlayVoice: (Long) -> Unit,
    playingId: Long?
) {
    val listState = rememberTransformingLazyColumnState()
    val spacing = LocalTedSpacing.current
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(anchorKey, messages.size) {
        if (anchorKey == 0L || messages.isEmpty()) return@LaunchedEffect
        val header = 1 + if (canLoadMore) 1 else 0
        runCatching { listState.scrollToItem(header + messages.size - 1) }
    }

    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) onSend(text)
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { inputLauncher.launch(buildInputIntent()) },
                buttonSize = EdgeButtonSize.Large
            ) {
                Text("Rispondi", maxLines = 1)
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.bubbleGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") {
                ListHeader(transformation = SurfaceTransformation(transformationSpec)) {
                    Text(title, maxLines = 1)
                }
            }

            if (canLoadMore) {
                item(key = "more") {
                    CompactButton(
                        onClick = onLoadMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Carica altri", maxLines = 1)
                    }
                }
            }

            if (messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nessun messaggio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(
                count = messages.size,
                key = { index -> messages[index].messageId }
            ) { index ->
                val message = messages[index]
                val previous = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)

                // Il nome compare solo quando cambia interlocutore: in una
                // conversazione fitta ripeterlo a ogni riga e solo rumore.
                val showSender = !message.outgoing &&
                    message.sender.isNotBlank() &&
                    (previous == null || previous.outgoing || previous.sender != message.sender)

                // L'ora solo in fondo a un gruppo, non su ogni bolla.
                val showTime = next == null ||
                    next.outgoing != message.outgoing ||
                    next.date - message.date > TIME_GAP_SECONDS

                val gapBefore = previous != null &&
                    message.date - previous.date > TIME_GAP_SECONDS

                MessageBlock(
                    message = message,
                    now = now,
                    showSender = showSender,
                    showTime = showTime,
                    separator = gapBefore,
                    loadImage = loadImage,
                    onPlayVoice = onPlayVoice,
                    playingId = playingId,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                )
            }

            item(key = "record") {
                CompactButton(
                    onClick = onRecord,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Messaggio vocale", maxLines = 1)
                }
            }

            // Gruppo connesso: angoli esterni ampi, interni quasi vivi.
            // Si leggono come un blocco unico invece che come quattro pulsanti
            // sparsi, ed e il trattamento che Wear usa per le liste di azioni.
            items(
                count = QUICK_REPLIES.size,
                key = { index -> "quick-${QUICK_REPLIES[index]}" }
            ) { index ->
                QuickReply(
                    text = QUICK_REPLIES[index],
                    first = index == 0,
                    last = index == QUICK_REPLIES.lastIndex,
                    onClick = { onSend(QUICK_REPLIES[index]) }
                )
            }
        }
    }
}

/**
 * Un elemento del gruppo connesso. La forma dipende dalla posizione:
 * il raggio ampio va solo sui bordi esterni del blocco.
 */
@Composable
private fun QuickReply(
    text: String,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    val outer = 22.dp
    val inner = 6.dp
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(),
        shape = RoundedCornerShape(
            topStart = if (first) outer else inner,
            topEnd = if (first) outer else inner,
            bottomStart = if (last) outer else inner,
            bottomEnd = if (last) outer else inner
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

private fun buildInputIntent(): Intent {
    val inputs = arrayOf(
        RemoteInput.Builder(KEY_REPLY).setLabel("Rispondi").build()
    )
    return Intent(ACTION_REMOTE_INPUT).apply {
        putExtra(EXTRA_REMOTE_INPUTS, inputs)
    }
}

private const val ACTION_REMOTE_INPUT = "android.support.wearable.input.action.REMOTE_INPUT"
private const val EXTRA_REMOTE_INPUTS = "android.support.wearable.input.extra.REMOTE_INPUTS"

@Composable
private fun MessageBlock(
    message: ChatMessage,
    now: Long,
    showSender: Boolean,
    showTime: Boolean,
    separator: Boolean,
    loadImage: suspend (String) -> ImageBitmap?,
    onPlayVoice: (Long) -> Unit,
    playingId: Long?,
    modifier: Modifier = Modifier
) {
    val outgoing = message.outgoing

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
    ) {
        if (separator) {
            Text(
                text = relativeTime(message.date, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        if (showSender) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (outgoing) 0.80f else 0.88f)
                .clip(bubbleShape(outgoing))
                .background(
                    if (outgoing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                )
                .padding(horizontal = 11.dp, vertical = LocalTedSpacing.current.bubblePadding)
        ) {
            BubbleContent(
                message = message,
                color = if (outgoing) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                loadImage = loadImage,
                onPlayVoice = onPlayVoice,
                playingId = playingId
            )
        }

        if (showTime) {
            Text(
                text = relativeTime(message.date, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp)
            )
        }
    }
}

/** Angolo raccolto dal lato del mittente: dice chi parla prima del colore. */
private fun bubbleShape(outgoing: Boolean) = if (outgoing) {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp)
} else {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
}

@Composable
private fun BubbleContent(
    message: ChatMessage,
    color: Color,
    loadImage: suspend (String) -> ImageBitmap?,
    onPlayVoice: (Long) -> Unit,
    playingId: Long?
) {
    when (val content = message.content) {
        // Il corpo del messaggio e l'elemento piu grande: e quello che leggi.
        is MessageContent.Text -> Text(
            text = content.text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )

        is MessageContent.Photo -> {
            val bitmap by produceState<ImageBitmap?>(null, content.asset) {
                value = loadImage(content.asset)
            }
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = content.caption.ifBlank { "Foto" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Text(
                    text = "Foto...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
            if (content.caption.isNotBlank()) {
                Text(
                    text = content.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        is MessageContent.Voice -> {
            val loading = playingId == message.messageId
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactButton(onClick = { onPlayVoice(message.messageId) }) {
                    Text(if (loading) "..." else "Ascolta", maxLines = 1)
                }
                Text(
                    text = "${content.seconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        is MessageContent.Sticker -> Text(
            text = content.emoji,
            style = MaterialTheme.typography.displaySmall,
            color = color
        )

        is MessageContent.Unsupported -> Text(
            text = content.label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Light
        )
    }
}
