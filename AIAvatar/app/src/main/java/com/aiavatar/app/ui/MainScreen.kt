package com.aiavatar.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.aiavatar.app.audio.RecordingState
import com.aiavatar.app.audio.VoiceGender
import com.aiavatar.app.avatar.*
import com.aiavatar.app.viewmodel.*
import kotlinx.coroutines.launch

// ── Palette ───────────────────────────────────────────────────────────────────
val NavyDeep      = Color(0xFF050D1F)
val NavyMid       = Color(0xFF0A1535)
val NavyLight     = Color(0xFF0D1B4B)
val NavyAccent    = Color(0xFF1230A0)
val CyanGlow      = Color(0xFF00D4FF)
val TextPrimary   = Color(0xFFE8F0FF)
val TextSecondary = Color(0xFF8899CC)

// ── Main Screen ───────────────────────────────────────────────────────────────
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val appState       by viewModel.appState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val amplitude      by viewModel.amplitude.collectAsState()
    val isSpeaking     by viewModel.isSpeaking.collectAsState()

    var inputText      by remember { mutableStateOf("") }
    var showModelPanel by remember { mutableStateOf(false) }
    var showSettings   by remember { mutableStateOf(false) }
    val focusManager   = LocalFocusManager.current
    val listState      = rememberLazyListState()
    val scope          = rememberCoroutineScope()

    // GLB file picker
    val glbPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "Custom Skin"
            viewModel.importGlbSkin(uri, name)
        }
    }

    LaunchedEffect(appState.messages.size) {
        if (appState.messages.isNotEmpty())
            scope.launch { listState.animateScrollToItem(appState.messages.size - 1) }
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyDeep)) {
        BackgroundGrid()
        Column(modifier = Modifier.fillMaxSize()) {

            TopBar(
                appState       = appState,
                showModelPanel = showModelPanel,
                onStatusClick  = { showModelPanel = !showModelPanel; showSettings = false },
                onClearChat    = { viewModel.clearConversation() },
                onRefreshConn  = { viewModel.checkConnection() },
                onSettingsClick= { showSettings = !showSettings; showModelPanel = false }
            )

            AnimatedVisibility(visible = showModelPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                ModelPanel(appState = appState, onSelectModel = { viewModel.selectModel(it); showModelPanel = false })
            }
            AnimatedVisibility(visible = showSettings, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                SettingsPanel(
                    appState       = appState,
                    onSelectAvatar = { viewModel.setAvatarStyle(it) },
                    onSelectCustom = { viewModel.setCustomAvatar(it) },
                    onDeleteCustom = { viewModel.deleteSkin(it) },
                    onImportGlb    = { glbPicker.launch("*/*") },
                    onVoiceGender  = { viewModel.setVoiceGender(it) },
                    onSetServerUrl = { viewModel.setServerUrl(it) }
                )
            }

            if (appState.inputMode == InputMode.VOICE) {
                VoiceScreen(
                    appState       = appState,
                    recordingState = recordingState,
                    amplitude      = amplitude,
                    isSpeaking     = isSpeaking,
                    showSettings   = showSettings || showModelPanel,
                    onToggleListen = { viewModel.toggleVoiceListening() },
                    onStopListen   = { viewModel.stopVoiceListening() },
                    onSwitchToChat = { viewModel.setInputMode(InputMode.CHAT) }
                )
            } else {
                ChatScreen(
                    appState       = appState,
                    recordingState = recordingState,
                    amplitude      = amplitude,
                    isSpeaking     = isSpeaking,
                    listState      = listState,
                    inputText      = inputText,
                    onInputChange  = { inputText = it },
                    onSend         = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    onSwitchToVoice = {
                        viewModel.setInputMode(InputMode.VOICE)
                        viewModel.toggleVoiceListening()
                    }
                )
            }
        }
    }
}

// ── Composable avatar switcher ────────────────────────────────────────────────
@Composable
fun AvatarComposable(
    activeAvatar: ActiveAvatar,
    recordingState: RecordingState,
    amplitude: Float,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    when (activeAvatar) {
        is ActiveAvatar.BuiltIn -> AnimatedAvatar(
            recordingState = recordingState, amplitude = amplitude,
            isSpeaking = isSpeaking, style = activeAvatar.style, modifier = modifier
        )
        is ActiveAvatar.Custom -> GlbAvatarView(
            glbUri = android.net.Uri.fromFile(java.io.File(activeAvatar.skin.filePath)),
            recordingState = recordingState, amplitude = amplitude,
            isSpeaking = isSpeaking, modifier = modifier
        )
    }
}

// ── Background Grid ───────────────────────────────────────────────────────────
@Composable
fun BackgroundGrid() {
    val t = rememberInfiniteTransition(label = "bg")
    val offset by t.animateFloat(0f, 60f, infiniteRepeatable(tween(8000, easing = LinearEasing)), "grid")
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val g = 60f; val a = 0.05f
        var x = -g + (offset % g)
        while (x < size.width + g) { drawLine(Color(0xFF1A3A8F).copy(alpha=a), Offset(x,0f), Offset(x,size.height), 1f); x+=g }
        var y = 0f
        while (y < size.height + g) { drawLine(Color(0xFF1A3A8F).copy(alpha=a), Offset(0f,y), Offset(size.width,y), 1f); y+=g }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
fun TopBar(
    appState: AppState, showModelPanel: Boolean,
    onStatusClick: () -> Unit, onClearChat: () -> Unit,
    onRefreshConn: () -> Unit, onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("AI AVATAR", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Spacer(Modifier.weight(1f))

        listOf(
            "⚙️" to onSettingsClick,
            "↺" to onClearChat
        ).forEach { (icon, action) ->
            Box(
                Modifier.size(34.dp).background(NavyLight, CircleShape).clickable(onClick = action),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 15.sp, color = TextSecondary) }
            Spacer(Modifier.width(5.dp))
        }

        if (!appState.isConnected) {
            Box(Modifier.size(34.dp).background(NavyLight, CircleShape).clickable(onClick = onRefreshConn), contentAlignment = Alignment.Center) {
                Text("⟳", color = Color(0xFFFF6644), fontSize = 16.sp)
            }
            Spacer(Modifier.width(5.dp))
        }

        // Status pill
        Box(
            Modifier.clickable(onClick = onStatusClick)
                .background(if (showModelPanel) NavyLight else NavyMid, RoundedCornerShape(20.dp))
                .border(1.dp, if (showModelPanel) CyanGlow.copy(0.5f) else Color.Transparent, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val dot = if (appState.isConnected) Color(0xFF44FF88) else Color(0xFFFF4444)
                val tr = rememberInfiniteTransition(label = "dot")
                val da by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), "da")
                Box(Modifier.size(8.dp).background(dot.copy(alpha = da), CircleShape))
                Text(appState.currentModel.take(16), color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

// ── Model Panel ───────────────────────────────────────────────────────────────
@Composable
fun ModelPanel(appState: AppState, onSelectModel: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(NavyMid, RoundedCornerShape(16.dp))
            .border(1.dp, CyanGlow.copy(0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text("WYBIERZ MODEL", color = CyanGlow, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (appState.availableModels.isEmpty()) {
                Text(if (appState.isConnected) "Brak modeli" else "Brak połączenia z serwerem", color = TextSecondary, fontSize = 12.sp)
            } else {
                appState.availableModels.forEach { model ->
                    val sel = model == appState.currentModel
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectModel(model) }
                            .background(if (sel) NavyAccent.copy(0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .padding(10.dp, 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (sel) Box(Modifier.size(6.dp).background(CyanGlow, CircleShape))
                        Text(model, color = if (sel) CyanGlow else TextSecondary, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// ── Settings Panel (Avatar + Voice) ─────────────────────────────────────────
@Composable
fun SettingsPanel(
    appState: AppState,
    onSelectAvatar: (AvatarStyle) -> Unit,
    onSelectCustom: (CustomSkin) -> Unit,
    onDeleteCustom: (CustomSkin) -> Unit,
    onImportGlb: () -> Unit,
    onVoiceGender: (VoiceGender) -> Unit,
    onSetServerUrl: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) }  // 0=avatar, 1=voice, 2=serwer

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(NavyMid, RoundedCornerShape(20.dp))
            .border(1.dp, CyanGlow.copy(0.18f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Tab row
        Row(Modifier.fillMaxWidth().background(NavyLight, RoundedCornerShape(12.dp)).padding(4.dp)) {
            listOf("🎭 Avatar", "🎤 Głos", "🌐 Serwer").forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f)
                        .background(if (tab == i) NavyAccent else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable { tab = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (tab == i) CyanGlow else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> AvatarTab(appState, onSelectAvatar, onSelectCustom, onDeleteCustom, onImportGlb)
            1 -> VoiceTab(appState, onVoiceGender)
            2 -> ServerTab(appState, onSetServerUrl)
        }
    }
}

@Composable
fun AvatarTab(
    appState: AppState,
    onSelectAvatar: (AvatarStyle) -> Unit,
    onSelectCustom: (CustomSkin) -> Unit,
    onDeleteCustom: (CustomSkin) -> Unit,
    onImportGlb: () -> Unit
) {
    Text("WBUDOWANE SKINY", color = TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(8.dp))

    // Built-in styles grid (2 cols)
    AvatarStyle.values().toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { style ->
                val sel = appState.activeAvatar is ActiveAvatar.BuiltIn && (appState.activeAvatar as ActiveAvatar.BuiltIn).style == style
                Box(
                    Modifier.weight(1f)
                        .clickable { onSelectAvatar(style) }
                        .background(
                            if (sel) Brush.linearGradient(listOf(NavyAccent, Color(style.armorColor1)))
                            else Brush.linearGradient(listOf(NavyLight, NavyLight)),
                            RoundedCornerShape(14.dp)
                        )
                        .border(if (sel) 1.5.dp else 1.dp, if (sel) CyanGlow else TextSecondary.copy(0.15f), RoundedCornerShape(14.dp))
                        .padding(10.dp, 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(style.emoji, fontSize = 16.sp)
                        Column {
                            Text(style.displayName, color = if (sel) TextPrimary else TextSecondary, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            Text(style.description, color = TextSecondary.copy(0.6f), fontSize = 9.sp)
                        }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
    }

    Spacer(Modifier.height(12.dp))
    Text("WŁASNE SKINY (GLB)", color = TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(8.dp))

    // Custom skins list
    if (appState.customSkins.isEmpty()) {
        Text("Brak własnych skinów. Zaimportuj plik GLB.", color = TextSecondary.copy(0.6f), fontSize = 12.sp)
    } else {
        appState.customSkins.forEach { skin ->
            val sel = appState.activeAvatar is ActiveAvatar.Custom && (appState.activeAvatar as ActiveAvatar.Custom).skin.id == skin.id
            Row(
                Modifier.fillMaxWidth()
                    .background(if (sel) NavyAccent.copy(0.4f) else NavyLight, RoundedCornerShape(12.dp))
                    .border(1.dp, if (sel) CyanGlow.copy(0.6f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelectCustom(skin) }
                    .padding(12.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🧊", fontSize = 20.sp)
                Column(Modifier.weight(1f)) {
                    Text(skin.name, color = if (sel) CyanGlow else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("GLB • ${java.io.File(skin.filePath).length() / 1024}KB", color = TextSecondary.copy(0.6f), fontSize = 10.sp)
                }
                if (sel) Box(Modifier.size(6.dp).background(CyanGlow, CircleShape))
                // Delete button
                Box(
                    Modifier.size(28.dp).background(Color(0xFF330A0A), CircleShape)
                        .border(1.dp, Color(0xFF881111), CircleShape)
                        .clickable { onDeleteCustom(skin) },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color(0xFFFF4444), fontSize = 11.sp) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(10.dp))

    // Import button
    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(NavyAccent.copy(0.7f), NavyLight)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, CyanGlow.copy(0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onImportGlb)
            .padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📁", fontSize = 22.sp)
        Column {
            Text("Importuj własny skin", color = CyanGlow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Obsługiwany format: GLB (3D model)", color = TextSecondary, fontSize = 11.sp)
        }
    }

    Spacer(Modifier.height(8.dp))
    // Info note
    Box(
        Modifier.fillMaxWidth().background(Color(0x220055AA), RoundedCornerShape(10.dp)).padding(10.dp)
    ) {
        Text(
            "💡 GLB to najlepszy format dla skórek 3D — skompresowany, jeden plik. " +
            "Możesz pobrać darmowe modele 3D np. z Sketchfab lub Ready Player Me i wyeksportować jako GLB.",
            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

@Composable
fun VoiceTab(appState: AppState, onVoiceGender: (VoiceGender) -> Unit) {
    Text("GŁOS ASYSTENTA", color = TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))

    VoiceGender.values().forEach { gender ->
        val sel = appState.voiceGender == gender
        Row(
            Modifier.fillMaxWidth()
                .background(
                    if (sel) Brush.linearGradient(listOf(NavyAccent, NavyLight))
                    else Brush.linearGradient(listOf(NavyLight, NavyLight)),
                    RoundedCornerShape(16.dp)
                )
                .border(if (sel) 1.5.dp else 1.dp, if (sel) CyanGlow else TextSecondary.copy(0.15f), RoundedCornerShape(16.dp))
                .clickable { onVoiceGender(gender) }
                .padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(gender.emoji, fontSize = 28.sp)
            Column(Modifier.weight(1f)) {
                Text("Głos ${gender.displayName}", color = if (sel) TextPrimary else TextSecondary,
                    fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                Text(
                    if (gender == VoiceGender.FEMALE) "Wyższy ton głosu, szybsze tempo"
                    else "Niższy ton głosu, wolniejsze tempo",
                    color = TextSecondary.copy(0.7f), fontSize = 11.sp
                )
            }
            if (sel) Box(Modifier.size(20.dp).background(CyanGlow, CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().background(Color(0x220055AA), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(
            "💡 Jakość głosu zależy od zainstalowanego silnika TTS w systemie Android. " +
            "Dla najlepszego efektu zainstaluj Google Text-to-Speech z Głosami Premium (ustawienia systemu → Głos lektora).",
            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

// ── Voice Screen ──────────────────────────────────────────────────────────────
@Composable
fun VoiceScreen(
    appState: AppState, recordingState: RecordingState,
    amplitude: Float, isSpeaking: Boolean,
    showSettings: Boolean = false,
    onToggleListen: () -> Unit, onStopListen: () -> Unit, onSwitchToChat: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val statusText = when (recordingState) {
            RecordingState.LISTENING  -> "Słucham..."
            RecordingState.PROCESSING -> "Przetwarzam..."
            RecordingState.SPEAKING   -> "Mówię..."
            RecordingState.IDLE       -> if (appState.isLoading) "Myślę..." else "Dotknij mikrofonu"
        }
        Text(statusText, color = CyanGlow, fontSize = 14.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))

        // Avatar hidden when settings panel open
        AnimatedVisibility(
            visible = !showSettings,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically(),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            AvatarComposable(
                activeAvatar = appState.activeAvatar, recordingState = recordingState,
                amplitude = amplitude, isSpeaking = isSpeaking,
                modifier = Modifier.fillMaxSize()
            )
        }
        // When settings open, just a spacer so mic stays at bottom
        if (showSettings) Spacer(Modifier.weight(1f))

        // Last message as small overlay text (doesn't shrink avatar)
        appState.messages.lastOrNull()?.let { msg ->
            if (msg.role == "assistant" && msg.content.isNotBlank()) {
                Text(
                    text = msg.content,
                    color = TextPrimary.copy(alpha = 0.8f),
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onSwitchToChat, border = BorderStroke(1.dp, NavyAccent),
                shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)) {
                Text("💬 Czat", fontSize = 13.sp)
            }
            val isRec = recordingState == RecordingState.LISTENING
            val isBusy = recordingState == RecordingState.PROCESSING || recordingState == RecordingState.SPEAKING
            val pMic = rememberInfiniteTransition(label = "mv")
            val micPulse by pMic.animateFloat(1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), "mp")
            val mScale = if (isRec) micPulse else 1f

            Box(
                Modifier.size(76.dp).scale(mScale)
                    .background(
                        Brush.radialGradient(
                            when {
                                isRec  -> listOf(Color(0xFFDD2222), Color(0xFF991111))
                                isBusy -> listOf(Color(0xFF224499), NavyLight)
                                else   -> listOf(NavyAccent, NavyLight)
                            }
                        ), CircleShape
                    )
                    .border(
                        2.dp,
                        when {
                            isRec  -> Color(0xFFFF5555)
                            isBusy -> CyanGlow.copy(0.4f)
                            else   -> CyanGlow
                        },
                        CircleShape
                    )
                    .clickable(enabled = !isBusy) { onToggleListen() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            isRec  -> "⏹"
                            isBusy -> "⋯"
                            else   -> "🎤"
                        },
                        fontSize = if (isRec || isBusy) 24.sp else 28.sp
                    )
                    if (isRec) {
                        Spacer(Modifier.height(2.dp))
                        Text("STOP", color = Color(0xFFFFAAAA), fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Chat Screen ───────────────────────────────────────────────────────────────
@Composable
fun ChatScreen(
    appState: AppState, recordingState: RecordingState,
    amplitude: Float, isSpeaking: Boolean,
    listState: LazyListState, inputText: String,
    onInputChange: (String) -> Unit, onSend: () -> Unit, onSwitchToVoice: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(appState.messages) { msg -> MessageBubble(msg) }
            if (appState.isLoading && appState.messages.lastOrNull()?.isStreaming == false) {
                item {
                    Row(Modifier.padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(3) { i ->
                            val inf = rememberInfiniteTransition(label = "d$i")
                            val dy by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(400, delayMillis = i * 130), RepeatMode.Reverse), "dy$i")
                            Box(Modifier.offset(y = dy.dp).size(7.dp).background(CyanGlow, CircleShape))
                        }
                    }
                }
            }
        }
        ChatInput(inputText, onInputChange, onSend, onSwitchToVoice, appState.isLoading)
    }
}

// ── Message Bubble ────────────────────────────────────────────────────────────
@Composable
fun MessageBubble(message: ChatEntry) {
    val isUser = message.role == "user"
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = EaseOutCubic)) }

    Row(
        Modifier.fillMaxWidth().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 20f },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                Modifier.padding(end = 6.dp, bottom = 2.dp).size(28.dp)
                    .background(Brush.radialGradient(listOf(NavyAccent, NavyLight)), CircleShape)
                    .border(1.dp, CyanGlow.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("A", color = CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        }

        Column(
            Modifier.widthIn(max = 270.dp)
                .background(
                    if (isUser) Brush.linearGradient(listOf(Color(0xFF1E3C9A), Color(0xFF0E1F60)))
                    else Brush.linearGradient(listOf(Color(0xFF0E1840), Color(0xFF080F2E))),
                    if (isUser) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
                    else        RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp)
                )
                .border(1.dp,
                    if (isUser) Color(0xFF2A4DB8).copy(0.6f) else CyanGlow.copy(0.12f),
                    if (isUser) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
                    else        RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.content, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
            if (message.isStreaming) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(1.5.dp).clip(RoundedCornerShape(1.dp)), CyanGlow, NavyLight)
            }
        }

        if (isUser) {
            Box(
                Modifier.padding(start = 6.dp, bottom = 2.dp).size(28.dp)
                    .background(Brush.radialGradient(listOf(NavyAccent, NavyLight)), CircleShape)
                    .border(1.dp, Color(0xFF4466DD).copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("Ty", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ── Chat Input ────────────────────────────────────────────────────────────────
@Composable
fun ChatInput(
    inputText: String, onInputChange: (String) -> Unit,
    onSend: () -> Unit, onSwitchToVoice: () -> Unit, isLoading: Boolean
) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC050D1F), Color(0xFF050D1F))))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(NavyMid.copy(alpha = 0.95f), RoundedCornerShape(28.dp))
                .border(1.dp, CyanGlow.copy(0.12f), RoundedCornerShape(28.dp))
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = inputText, onValueChange = onInputChange,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                maxLines = 5, cursorBrush = SolidColor(CyanGlow),
                decorationBox = { inner ->
                    if (inputText.isEmpty()) Text("Napisz coś...", color = TextSecondary, fontSize = 15.sp)
                    inner()
                }
            )

            // Send — animated appearance
            AnimatedVisibility(inputText.isNotBlank() && !isLoading, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(
                    Modifier.size(38.dp).background(Brush.radialGradient(listOf(CyanGlow, Color(0xFF006688))), CircleShape).clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) { Text("↑", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
            }
            if (isLoading) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), CyanGlow, strokeWidth = 2.5.dp)
                }
            }

            // Mic — always rightmost
            Box(
                Modifier.size(38.dp).background(NavyLight, CircleShape).border(1.dp, CyanGlow.copy(0.25f), CircleShape).clickable(onClick = onSwitchToVoice),
                contentAlignment = Alignment.Center
            ) { Text("🎤", fontSize = 17.sp) }
        }
    }
}

@Composable
fun ServerTab(appState: AppState, onSetServerUrl: (String) -> Unit) {
    var urlInput by remember { mutableStateOf(appState.serverUrl) }
    var saved by remember { mutableStateOf(false) }

    Text("ADRES SERWERA OLLAMA", color = TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))

    // Current status
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (appState.isConnected) Color(0x2200FF88) else Color(0x22FF4444),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(10.dp).background(
            if (appState.isConnected) Color(0xFF44FF88) else Color(0xFFFF4444),
            androidx.compose.foundation.shape.CircleShape
        ))
        Text(
            if (appState.isConnected) "Połączono z ${appState.currentModel}"
            else "Brak połączenia z serwerem",
            color = if (appState.isConnected) Color(0xFF44FF88) else Color(0xFFFF6666),
            fontSize = 13.sp
        )
    }

    Spacer(Modifier.height(14.dp))

    Text("Adres IP i port serwera:", color = TextSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))

    // URL input field
    Box(
        Modifier.fillMaxWidth()
            .background(NavyLight, RoundedCornerShape(14.dp))
            .border(1.dp,
                if (saved) Color(0xFF44FF88).copy(0.6f) else CyanGlow.copy(0.2f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = urlInput,
            onValueChange = { urlInput = it; saved = false },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary, fontSize = 14.sp
            ),
            cursorBrush = SolidColor(CyanGlow),
            singleLine = true,
            decorationBox = { inner ->
                if (urlInput.isEmpty()) Text("http://192.168.0.xxx:11434", color = TextSecondary, fontSize = 14.sp)
                inner()
            }
        )
    }

    Spacer(Modifier.height(10.dp))

    // Save button
    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(NavyAccent, Color(0xFF0A1F6B))),
                RoundedCornerShape(14.dp)
            )
            .clickable {
                val url = if (urlInput.startsWith("http")) urlInput else "http://$urlInput"
                onSetServerUrl(url)
                saved = true
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (saved) "✓ Zapisano — łączę..." else "Zapisz i połącz",
            color = if (saved) Color(0xFF44FF88) else CyanGlow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(Modifier.height(12.dp))

    // Quick presets
    Text("SZYBKIE USTAWIENIA", color = TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(8.dp))

    listOf(
        "💻 Mac Mini (domyślny)" to "http://192.168.0.177:11434",
        "🏠 Localhost (emulator)" to "http://10.0.2.2:11434",
        "📱 Localhost (USB debug)" to "http://localhost:11434"
    ).forEach { (label, url) ->
        Row(
            Modifier.fillMaxWidth()
                .clickable { urlInput = url; saved = false }
                .background(
                    if (urlInput == url) NavyAccent.copy(0.4f) else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = if (urlInput == url) CyanGlow else TextSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
    }

    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth().background(Color(0x220055AA), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(
            "💡 Telefon i Mac Mini muszą być w tej samej sieci WiFi. " +
            "Sprawdź IP Mac Mini: System Preferences → Network. " +
            "Upewnij się że Ollama działa z OLLAMA_HOST=0.0.0.0",
            color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}
