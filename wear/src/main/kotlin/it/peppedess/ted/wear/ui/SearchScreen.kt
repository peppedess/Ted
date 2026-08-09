package it.peppedess.ted.wear.ui

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import it.peppedess.ted.protocol.ChatSummary

private const val KEY_QUERY = "ted_query"

@Composable
fun SearchScreen(
    query: String,
    results: List<ChatSummary>,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onSelect: (Long) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spacing = LocalTedSpacing.current

    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)
            ?.getCharSequence(KEY_QUERY)
            ?.toString()
            ?.trim()
        if (!text.isNullOrEmpty()) onQuery(text)
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.listGap),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "header") {
                ListHeader { Text("Nuova chat") }
            }

            item(key = "input") {
                Button(
                    onClick = { inputLauncher.launch(buildQueryIntent()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = query.ifBlank { "Cerca un nome" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (searching) {
                item(key = "spinner") {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (!searching && query.isNotBlank() && results.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nessun risultato",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(
                count = results.size,
                key = { index -> results[index].chatId }
            ) { index ->
                val chat = results[index]
                Card(
                    onClick = { onSelect(chat.chatId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = chat.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.preview.isNotBlank()) {
                            Text(
                                text = chat.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stesso input di sistema della risposta: dettatura, tastiera o scrittura. */
private fun buildQueryIntent(): Intent {
    val inputs = arrayOf(
        RemoteInput.Builder(KEY_QUERY)
            .setLabel("Cerca")
            .build()
    )
    return Intent(ACTION_REMOTE_INPUT).apply {
        putExtra(EXTRA_REMOTE_INPUTS, inputs)
    }
}

private const val ACTION_REMOTE_INPUT = "android.support.wearable.input.action.REMOTE_INPUT"
private const val EXTRA_REMOTE_INPUTS = "android.support.wearable.input.extra.REMOTE_INPUTS"
