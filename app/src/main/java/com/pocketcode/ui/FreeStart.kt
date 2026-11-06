package com.pocketcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Карточка для новичка без ключей: одна кнопка ставит OpenCode с бесплатными моделями. */
@Composable
fun FreeStartCard(vm: ChatController, onOpenModels: () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Нет ключа? Начни бесплатно", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Приложение само поставит мини-Linux и OpenCode с его бесплатными моделями. Ключ и регистрация не нужны. " +
                    "Это займёт несколько минут и нужен интернет (в России может понадобиться VPN). " +
                    "У бесплатных моделей есть лимиты, а запросы идут через сервис OpenCode, поэтому не отправляй туда секретный код.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            vm.freeStartMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (vm.freeStartFailed && !vm.freeStartBusy) {
                Text(
                    "Запасной путь: возьми бесплатный ключ у провайдера (например OpenRouter с моделями «:free» или Groq) " +
                        "и вставь его в «Модели и ключи», там есть готовые шаблоны.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = onOpenModels) { Text("Открыть «Модели и ключи»") }
            }
            if (vm.freeStartBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startFree() }, enabled = !vm.freeStartBusy) {
                    Text(if (vm.freeStartBusy) "Идёт установка…" else "Начать бесплатно")
                }
                TextButton(onClick = { vm.hideFreeStart() }, enabled = !vm.freeStartBusy) { Text("У меня есть ключ") }
            }
        }
    }
}
