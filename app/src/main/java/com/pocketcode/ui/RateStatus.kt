package com.pocketcode.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcode.llm.RateGate
import kotlinx.coroutines.delay

/** Тонкая строка над чатом: сколько запросов из лимита занято и когда освободится место. Видна только при включённом лимите. */
@Composable
fun RateStatusLine() {
    var st by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            st = RateGate.status()
            delay(1000)
        }
    }
    val s = st ?: return
    val limit = RateGate.limit
    Text(
        if (s.first >= limit && s.second > 0) "Лимит запросов занят ($limit из $limit): следующий через ${s.second} с"
        else "Лимит запросов: занято ${s.first} из $limit",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}
