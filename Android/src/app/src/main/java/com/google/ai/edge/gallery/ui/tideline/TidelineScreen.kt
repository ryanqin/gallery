/*
 * Tideline translate screen — Phase 2.
 *
 * Wires TidelineTranslateViewModel into a minimal Compose surface: source-text
 * input, "Translate" button, streamed result, engine-state indicator. No model
 * picker, no history, no airplane-mode toggle — all of that is Phase 3+.
 */

package com.google.ai.edge.gallery.ui.tideline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TidelineScreen(viewModel: TidelineTranslateViewModel = viewModel()) {
  val state by viewModel.ui.collectAsState()

  // Kick off engine load on first composition.
  LaunchedEffect(Unit) { viewModel.initEngine() }

  Scaffold { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 24.dp, vertical = 16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = "Tideline", style = MaterialTheme.typography.displaySmall)
      Text(
        text = "本地翻译 — Phase 2",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(4.dp))
      EngineStatusBar(state.engineState, state.errorMessage)

      OutlinedTextField(
        value = state.sourceText,
        onValueChange = viewModel::onSourceTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Source text") },
        placeholder = { Text("e.g. 寿司") },
        minLines = 2,
        maxLines = 6,
        enabled = state.engineState != EngineState.INFERRING,
      )

      Button(
        onClick = viewModel::translate,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.engineState == EngineState.READY && state.sourceText.isNotBlank(),
      ) {
        Text("Translate to ${state.targetLang}")
      }

      if (state.translation.isNotEmpty() || state.engineState == EngineState.INFERRING) {
        TranslationCard(state.translation, streaming = state.engineState == EngineState.INFERRING)
      }
    }
  }
}

@Composable
private fun EngineStatusBar(engineState: EngineState, errorMessage: String?) {
  val (text, color) = when (engineState) {
    EngineState.IDLE -> "Engine idle" to MaterialTheme.colorScheme.onSurfaceVariant
    EngineState.INITIALIZING -> "Loading Gemma E2B from /data/local/tmp/…" to
      MaterialTheme.colorScheme.primary
    EngineState.READY -> "Engine ready" to Color(0xFF2E7D32)
    EngineState.INFERRING -> "Translating…" to MaterialTheme.colorScheme.primary
    EngineState.ERROR -> (errorMessage ?: "Engine error") to MaterialTheme.colorScheme.error
  }
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    if (engineState == EngineState.INITIALIZING || engineState == EngineState.INFERRING) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun TranslationCard(text: String, streaming: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = if (streaming) "Translating" else "Translation",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = text.ifEmpty { "…" },
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}
