package com.aiavatar.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiavatar.app.audio.AudioManager
import com.aiavatar.app.audio.RecordingState
import com.aiavatar.app.audio.VoiceGender
import com.aiavatar.app.avatar.AvatarManager
import com.aiavatar.app.avatar.AvatarStyle
import com.aiavatar.app.avatar.CustomSkin
import com.aiavatar.app.network.ChatMessage
import com.aiavatar.app.network.OllamaApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatEntry(
    val id: Long = System.currentTimeMillis(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false
)

sealed class ActiveAvatar {
    data class BuiltIn(val style: AvatarStyle) : ActiveAvatar()
    data class Custom(val skin: CustomSkin) : ActiveAvatar()
}

data class AppState(
    val messages: List<ChatEntry> = emptyList(),
    val serverUrl: String = "http://192.168.0.177:11434",
    val isConnected: Boolean = false,
    val currentModel: String = "llama3.2:3b",
    val availableModels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val inputMode: InputMode = InputMode.CHAT,
    val avatarStyle: AvatarStyle = AvatarStyle.DARK_KNIGHT,
    val activeAvatar: ActiveAvatar = ActiveAvatar.BuiltIn(AvatarStyle.DARK_KNIGHT),
    val customSkins: List<CustomSkin> = emptyList(),
    val voiceGender: VoiceGender = VoiceGender.FEMALE
)

enum class InputMode { CHAT, VOICE }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // apiClient is now dynamic - see _apiClient below
    val audioManager          = AudioManager(application)
    private val avatarManager = AvatarManager(application)

    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    val recordingState: StateFlow<RecordingState> = audioManager.recordingState
    val amplitude:      StateFlow<Float>          = audioManager.amplitude
    val isSpeaking:     StateFlow<Boolean>        = audioManager.isSpeaking

    private val conversationHistory = mutableListOf<ChatMessage>()
    private var _apiClient = OllamaApiClient("http://192.168.0.177:11434")

    init {
        conversationHistory.add(ChatMessage("system",
            "Jesteś pomocnym asystentem AI. Rozmawiasz po polsku. Bądź zwięzły i pomocny."))
        val saved = avatarManager.loadCustomSkins()
        // Restore saved server URL from SharedPreferences
        val prefs = application.getSharedPreferences("aiavatar", android.content.Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "http://192.168.0.177:11434")!!
        _apiClient = OllamaApiClient(savedUrl)
        _appState.update { it.copy(customSkins = saved, serverUrl = savedUrl) }
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("VM", "checkConnection start")
            val connected = _apiClient.checkConnection()
            android.util.Log.d("VM", "checkConnection result: $connected")
            if (connected) {
                val models = _apiClient.fetchModels()
                android.util.Log.d("VM", "models: $models")
                withContext(Dispatchers.Main) {
                    _appState.update {
                        it.copy(
                            isConnected = true,
                            availableModels = models,
                            currentModel = models.firstOrNull() ?: it.currentModel
                        )
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _appState.update { it.copy(isConnected = false) }
                }
            }
        }
    }

    fun selectModel(model: String) = _appState.update { it.copy(currentModel = model) }
    fun setInputMode(mode: InputMode) = _appState.update { it.copy(inputMode = mode) }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        _appState.update { it.copy(serverUrl = cleanUrl) }
        // Reinitialize API client with new URL and reconnect
        reinitClient(cleanUrl)
        checkConnection()
    }

    private fun reinitClient(url: String) {
        _apiClient = OllamaApiClient(url)
        // Persist to SharedPreferences
        getApplication<Application>().getSharedPreferences("aiavatar", android.content.Context.MODE_PRIVATE)
            .edit().putString("server_url", url).apply()
    }

    fun setAvatarStyle(style: AvatarStyle) =
        _appState.update { it.copy(avatarStyle = style, activeAvatar = ActiveAvatar.BuiltIn(style)) }

    fun setCustomAvatar(skin: CustomSkin) =
        _appState.update { it.copy(activeAvatar = ActiveAvatar.Custom(skin)) }

    fun importGlbSkin(uri: Uri, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val skin = avatarManager.importGlb(uri, name)
            if (skin != null) {
                val updated = avatarManager.loadCustomSkins()
                withContext(Dispatchers.Main) {
                    _appState.update { it.copy(customSkins = updated, activeAvatar = ActiveAvatar.Custom(skin)) }
                }
            }
        }
    }

    fun deleteSkin(skin: CustomSkin) {
        avatarManager.deleteSkin(skin.id)
        val updated = avatarManager.loadCustomSkins()
        val active = _appState.value.activeAvatar
        val newActive = if (active is ActiveAvatar.Custom && active.skin.id == skin.id)
            ActiveAvatar.BuiltIn(_appState.value.avatarStyle) else active
        _appState.update { it.copy(customSkins = updated, activeAvatar = newActive) }
    }

    fun setVoiceGender(gender: VoiceGender) {
        audioManager.setVoiceGender(gender)
        _appState.update { it.copy(voiceGender = gender) }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        _appState.update {
            it.copy(messages = it.messages + ChatEntry(role = "user", content = text), isLoading = true)
        }
        conversationHistory.add(ChatMessage("user", text))

        viewModelScope.launch {
            val assistantId = System.currentTimeMillis() + 1
            var full = ""
            _appState.update { it.copy(messages = it.messages + ChatEntry(id = assistantId, role = "assistant", content = "", isStreaming = true)) }

            try {
                _apiClient.streamChat(_appState.value.currentModel, conversationHistory).collect { chunk ->
                    full += chunk
                    _appState.update { s ->
                        s.copy(messages = s.messages.map { if (it.id == assistantId) it.copy(content = full) else it })
                    }
                }
                _appState.update { s ->
                    s.copy(
                        messages = s.messages.map { if (it.id == assistantId) it.copy(content = full, isStreaming = false) else it },
                        isLoading = false
                    )
                }
                conversationHistory.add(ChatMessage("assistant", full))
                if (_appState.value.inputMode == InputMode.VOICE) audioManager.speak(full)
            } catch (e: Exception) {
                android.util.Log.e("VM", "sendMessage error: ${e.message}")
                _appState.update { s ->
                    s.copy(
                        messages = s.messages.map { if (it.id == assistantId) it.copy(content = "Błąd: ${e.message}", isStreaming = false) else it },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun toggleVoiceListening() {
        if (audioManager.isContinuousListening) {
            audioManager.stopContinuousListening()
            audioManager.stopSpeaking()
        } else {
            audioManager.stopSpeaking()
            audioManager.startListeningContinuous { transcribed ->
                if (transcribed.isNotBlank()) sendMessage(transcribed)
            }
        }
    }

    fun stopVoiceListening() = audioManager.stopContinuousListening()

    fun clearConversation() {
        conversationHistory.clear()
        conversationHistory.add(ChatMessage("system", "Jesteś pomocnym asystentem AI. Rozmawiasz po polsku."))
        _appState.update { it.copy(messages = emptyList()) }
    }

    override fun onCleared() { super.onCleared(); audioManager.destroy() }
}
