package it.peppedess.ted.wear.ui

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import it.peppedess.ted.protocol.ChatSummary
import kotlin.math.absoluteValue

@Composable
fun ChatListScreen(
    chats: List<ChatSummary>,
    now: Long,
    loadAvatar: suspend (String) -> ImageBitmap?,
    onChatClick: (Long) -> Unit,
    onNewChat: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spacing = LocalTedSpacing.current

    // Le card si scalano e sbiadiscono avvicinandosi ai bordi curvi:
    // e la firma visiva dell'Expressive su Wear.
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onNewChat,
                buttonSize = EdgeButtonSize.Medium
            ) {
                Text("Nuova chat", maxLines = 1)
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") {
                ListHeader(
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Chat")
                }
            }

            items(
                count = chats.size,
                key = { index -> chats[index].chatId }
            ) { index ->
                val chat = chats[index]
                ChatRow(
                    chat = chat,
                    now = now,
                    loadAvatar = loadAvatar,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
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
    loadAvatar: suspend (String) -> ImageBitmap?,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        transformation = transformation,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                key = chat.avatar,
                title = chat.title,
                chatId = chat.chatId,
                loadAvatar = loadAvatar
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
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
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = relativeTime(chat.date, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (chat.unread > 0) {
                        UnreadDot(
                            count = chat.unread,
                            muted = chat.muted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Text(
                    text = chat.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Tavolozza per i ripieghi: stabile per chat, cosi il colore non balla. */
private val FallbackColors = listOf(
    Color(0xFF3EAEE8),
    Color(0xFF6BC178),
    Color(0xFFE0A458),
    Color(0xFFD9788F),
    Color(0xFF9B8AE6),
    Color(0xFF4FC4C0)
)

@Composable
private fun Avatar(
    key: String?,
    title: String,
    chatId: Long,
    loadAvatar: suspend (String) -> ImageBitmap?
) {
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = key?.let { loadAvatar(it) }
    }

    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    } else {
        // Iniziale su fondo colorato: meglio di un cerchio vuoto quando
        // la foto non c'e o non e ancora scesa dal telefono.
        val color = FallbackColors[(chatId.hashCode().absoluteValue) % FallbackColors.size]
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF06222E)
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
            .size(18.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
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
        delta < 3600 -> "${delta / 60}m"
        delta < 86_400 -> "${delta / 3600}h"
        delta < 7 * 86_400 -> "${delta / 86_400}g"
        else -> "${delta / (7 * 86_400)}sett"
    }
}
