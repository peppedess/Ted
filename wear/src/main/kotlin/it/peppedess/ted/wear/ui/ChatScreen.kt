package it.peppedess.ted.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onSend: (String) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()

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
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") {
                ListHeader { Text(title, maxLines = 1) }
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
                MessageBubble(message = messages[index], now = now)
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
private fun MessageBubble(message: ChatMessage, now: Long) {
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
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
                Text(
                    text = bodyText(message.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground
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

private fun bodyText(content: MessageContent): String = when (content) {
    is MessageContent.Text -> content.text
    is MessageContent.Voice -> "Vocale ${content.seconds}s"
    is MessageContent.Sticker -> content.emoji
    is MessageContent.Photo -> content.caption.ifBlank { "Foto" }
    is MessageContent.Unsupported -> content.label
}
