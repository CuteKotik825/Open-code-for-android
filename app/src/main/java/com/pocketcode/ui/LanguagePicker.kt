package com.pocketcode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Выбор языка. При первом запуске ([first] = true) заранее выбран язык системы, но спрашиваем всё равно.
 * Языки показаны своими названиями, чтобы их можно было найти на любом языке.
 */
@Composable
fun LanguagePicker(first: Boolean, onDone: () -> Unit) {
    val langs = remember { I18n.available }
    var sel by remember { mutableStateOf(if (first) I18n.systemDefault() else I18n.code) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(if (first) "Language · Язык · اللغة" else "Язык", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (first) "Choose the app language. You can change it later in Settings." else "Выберите язык приложения.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                for (l in langs) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = sel == l.code, role = Role.RadioButton, onClick = { sel = l.code }).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = sel == l.code, onClick = { sel = l.code })
                        androidx.compose.material3.Text(l.native, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Button(onClick = { I18n.choose(sel); onDone() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                androidx.compose.material3.Text(
                    when (sel) { "ru" -> "Продолжить"; "ar" -> "متابعة"; "es" -> "Continuar"; "de" -> "Weiter"; "zh" -> "继续"; "tr" -> "Devam"; "uk" -> "Продовжити"; else -> "Continue" },
                )
            }
        }
    }
}
