/*
 * Tideline Android shell — Phase 1 hello-world screen.
 * Goal: verify Compose render pipeline works in our fork before wiring LLM in Phase 2.
 */

package com.google.ai.edge.gallery.ui.tideline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TidelineScreen() {
  Scaffold { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(64.dp))
      Text(text = "Tideline", style = MaterialTheme.typography.displayMedium)
      Spacer(modifier = Modifier.height(12.dp))
      Text(text = "本地翻译 + 涌现学习", style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.height(32.dp))
      Text(
        text = "Phase 1 hello-world. Phase 2 这里会变成翻译输入框,Phase 3 加 SQLite drawer 持久化。",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}
