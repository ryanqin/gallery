/*
 * Tideline translate engine wrapper — Phase 2.
 *
 * Goal: minimal direct LiteRT-LM usage. We don't piggyback on AI Edge Gallery's
 * Model / Task / Hilt infrastructure here — Phase 2 is just a wire-up proof. If
 * we keep this screen for Phase 3+ we can decide whether to migrate into the
 * shared infrastructure or keep this lean.
 *
 * Model is sideloaded at MODEL_PATH via `adb push`. Phase 4 will polish download
 * UX (or HF OAuth) — until then, dev cycle is: download once on Mac, push, run.
 */

package com.google.ai.edge.gallery.ui.tideline

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.tideline.TidelineDatabase
import com.google.ai.edge.gallery.data.tideline.TranslationDao
import com.google.ai.edge.gallery.data.tideline.TranslationEntity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TidelineTranslateVM"

// Sideload path. Push the model with:
//   adb push gemma-4-E2B-it.litertlm /data/local/tmp/
private const val MODEL_PATH = "/data/local/tmp/gemma-4-E2B-it.litertlm"

// Mirrors tideline/core/src/tideline/bench/atoms/a1_word_translation.py and a2_sentence_translation.py.
// Same prompt is used by Tideline's Python core in production — keep them in sync.
private const val SYSTEM_PROMPT =
  "You are a precise translator. Respond with only the translation — " +
    "no preamble, no explanation, no quotation marks."

// Sampler defaults from model_allowlists/1_0_15.json :: Gemma-4-E2B-it.defaultConfig
private const val DEFAULT_TOP_K = 64
private const val DEFAULT_TOP_P = 0.95
private const val DEFAULT_TEMPERATURE = 1.0
private const val DEFAULT_MAX_TOKENS = 4000

enum class EngineState { IDLE, INITIALIZING, READY, INFERRING, ERROR }

data class TidelineUiState(
  val engineState: EngineState = EngineState.IDLE,
  val sourceText: String = "",
  val targetLang: String = "Japanese",
  val translation: String = "",
  val errorMessage: String? = null,
)

@OptIn(ExperimentalApi::class)
class TidelineTranslateViewModel(application: Application) : AndroidViewModel(application) {

  private val _ui = MutableStateFlow(TidelineUiState())
  val ui = _ui.asStateFlow()

  private val dao: TranslationDao = TidelineDatabase.get(application).translationDao()

  // App-launch session UUID. MVP shortcut; real Tideline "outing" semantics
  // (GPS / time-window grouping) lands in Phase 5.
  private val sessionId: String = UUID.randomUUID().toString()

  val history = dao.observeLatest().stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
    initialValue = emptyList(),
  )

  private var engine: Engine? = null
  private var conversation: Conversation? = null

  fun initEngine() {
    if (_ui.value.engineState != EngineState.IDLE) return
    _ui.value = _ui.value.copy(engineState = EngineState.INITIALIZING, errorMessage = null)

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d(TAG, "Initializing engine from $MODEL_PATH")
        // When loading model from /data/local/tmp (read-only-ish sideload location),
        // LiteRT-LM needs an app-owned writable scratch path. Mirrors the special
        // case in LlmChatModelHelper.kt line 120.
        val cacheDir = getApplication<Application>().getExternalFilesDir(null)?.absolutePath
        val cfg = EngineConfig(
          modelPath = MODEL_PATH,
          backend = Backend.GPU(),
          maxNumTokens = DEFAULT_MAX_TOKENS,
          cacheDir = cacheDir,
        )
        val e = Engine(cfg)
        e.initialize()
        val sys: Contents = Contents.of(listOf(Content.Text(SYSTEM_PROMPT)))
        val c = e.createConversation(
          ConversationConfig(
            samplerConfig = SamplerConfig(
              topK = DEFAULT_TOP_K,
              topP = DEFAULT_TOP_P,
              temperature = DEFAULT_TEMPERATURE,
            ),
            systemInstruction = sys,
          )
        )
        engine = e
        conversation = c
        Log.d(TAG, "Engine ready")
        _ui.value = _ui.value.copy(engineState = EngineState.READY)
      } catch (t: Throwable) {
        Log.e(TAG, "Engine init failed", t)
        _ui.value = _ui.value.copy(
          engineState = EngineState.ERROR,
          errorMessage = "Init failed: ${t.message}",
        )
      }
    }
  }

  fun onSourceTextChange(text: String) {
    _ui.value = _ui.value.copy(sourceText = text)
  }

  fun translate() {
    val state = _ui.value
    if (state.engineState != EngineState.READY) return
    val src = state.sourceText.trim()
    if (src.isEmpty()) return
    val conv = conversation ?: return

    _ui.value = state.copy(
      engineState = EngineState.INFERRING,
      translation = "",
      errorMessage = null,
    )

    val userText = "Translate the following to ${state.targetLang}: $src"
    try {
      conv.sendMessageAsync(
        Contents.of(listOf(Content.Text(userText))),
        object : MessageCallback {
          override fun onMessage(message: Message) {
            _ui.value = _ui.value.copy(translation = message.toString())
          }

          override fun onDone() {
            val finalState = _ui.value
            _ui.value = finalState.copy(engineState = EngineState.READY)
            val translated = finalState.translation.trim()
            if (translated.isNotEmpty()) {
              viewModelScope.launch(Dispatchers.IO) {
                try {
                  dao.insert(
                    TranslationEntity(
                      original = src,
                      targetLang = finalState.targetLang,
                      translated = translated,
                      source = "text",
                      contextSnippet = null,
                      sessionId = sessionId,
                    )
                  )
                } catch (t: Throwable) {
                  Log.e(TAG, "Persist translation failed", t)
                }
              }
            }
          }

          override fun onError(throwable: Throwable) {
            Log.e(TAG, "Inference error", throwable)
            _ui.value = _ui.value.copy(
              engineState = EngineState.ERROR,
              errorMessage = "Inference failed: ${throwable.message}",
            )
          }
        },
        emptyMap(),
      )
    } catch (t: Throwable) {
      Log.e(TAG, "sendMessageAsync threw", t)
      _ui.value = _ui.value.copy(
        engineState = EngineState.ERROR,
        errorMessage = "Send failed: ${t.message}",
      )
    }
  }

  override fun onCleared() {
    try {
      conversation?.close()
    } catch (_: Throwable) {}
    conversation = null
    engine = null
    super.onCleared()
  }
}
