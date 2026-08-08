package it.peppedess.ted.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import it.peppedess.ted.protocol.ChatSummary

@Composable
fun ChatListScreen(
    chats: List<ChatSummary>,
    now: Long,
    onChatClick: (Long) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ListHeader {
                    Text("Chat")
                }
            }
            items(chats.size) { index ->
                val chat = chats[index]
                ChatRow(
                    chat = chat,
                    now = now,
                    onClick = { onChatClick(chat.chatId) }
                )
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    now: Long,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.unread > 0) {
                    UnreadDot(
                        count = chat.unread,
                        muted = chat.muted,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            Text(
                text = chat.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeTime(chat.date, now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnreadDot(
    count: Int,
    muted: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (muted) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.primary
    }
    val foreground = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1
        )
    }
}

/** Etichetta temporale compatta: sul quadrante non c'e spazio per le date estese. */
internal fun relativeTime(epochSeconds: Long, now: Long): String {
    val delta = (now - epochSeconds).coerceAtLeast(0)
    return when {
        delta < 60 -> "ora"
        delta < 3600 -> "${delta / 60} min"
        delta < 86_400 -> "${delta / 3600} h"
        delta < 7 * 86_400 -> "${delta / 86_400} g"
        else -> "${delta / (7 * 86_400)} sett"
    }
}
