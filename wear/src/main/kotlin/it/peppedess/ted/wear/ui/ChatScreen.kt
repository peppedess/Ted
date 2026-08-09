package it.peppedess.ted.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import android.content.Intent
import it.peppedess.ted.protocol.ChatMessage
import it.peppedess.ted.protocol.MessageContent

private const val KEY_REPLY = "ted_reply"

/** Risposte rapide: sul polso battere testo e l'ultima risorsa. */
private val QUICK_REPLIES = listOf("Ok", "Arrivo", "Ti richiamo", "Grazie", "No")

@Composable
fun ChatScreen(
    title: String,
    messages: List<ChatMessage>,
    now: Long,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onSend: (String) -> Unit,
    loadImage: suspend (String) -> ImageBitmap?,
    onPlayVoice: (Long) -> Unit,
    playingId: Long?
) {
    val listState = rememberTransformingLazyColumnState()
    val spacing = LocalTedSpacing.current

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

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.bubbleGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") {
                ListHeader { Text(title, maxLines = 1) }
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
                        "Nessun messaggio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                count = messages.size,
                key = { index -> messages[index].messageId }
            ) { index ->
                MessageBubble(
                    message = messages[index],
                    now = now,
                    loadImage = loadImage,
                    onPlayVoice = onPlayVoice,
                    playingId = playingId
                )
            }

            item(key = "reply") {
                Button(
                    onClick = { inputLauncher.launch(buildInputIntent()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Rispondi")
                }
            }

            item(key = "quick") {
                QuickReplies(onSend = onSend)
            }
        }
    }
}

/**
 * Apre l'input di sistema di Wear OS: dettatura, tastiera, scrittura a mano
 * ed emoji in un colpo solo. Costruiamo l'intent a mano invece di usare
 * RemoteInputIntentHelper, la cui superficie API cambia fra le versioni.
 */
private fun buildInputIntent(): Intent {
    val inputs = arrayOf(
        RemoteInput.Builder(KEY_REPLY)
            .setLabel("Rispondi")
            .build()
    )
    return Intent(ACTION_REMOTE_INPUT).apply {
        putExtra(EXTRA_REMOTE_INPUTS, inputs)
    }
}

private const val ACTION_REMOTE_INPUT = "android.support.wearable.input.action.REMOTE_INPUT"
private const val EXTRA_REMOTE_INPUTS = "android.support.wearable.input.extra.REMOTE_INPUTS"

@Composable
private fun QuickReplies(onSend: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QUICK_REPLIES.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { reply ->
                    CompactButton(
                        onClick = { onSend(reply) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(reply, maxLines = 1)
                    }
                }
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    now: Long,
    loadImage: suspend (String) -> ImageBitmap?,
    onPlayVoice: (Long) -> Unit,
    playingId: Long?
) {
    val outgoing = message.outgoing
    val background = if (outgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val foreground = if (outgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .padding(horizontal = 10.dp, vertical = LocalTedSpacing.current.bubblePadding)
        ) {
            Column {
                if (!outgoing && message.sender.isNotBlank()) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                BubbleContent(
                    message = message,
                    color = foreground,
                    loadImage = loadImage,
                    onPlayVoice = onPlayVoice,
                    playingId = playingId
                )
            }
        }
        Text(
            text = relativeTime(message.date, now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
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
        is MessageContent.Text -> Text(
            text = content.text,
            style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
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
            style = MaterialTheme.typography.titleMedium,
            color = color
        )

        is MessageContent.Unsupported -> Text(
            text = content.label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
