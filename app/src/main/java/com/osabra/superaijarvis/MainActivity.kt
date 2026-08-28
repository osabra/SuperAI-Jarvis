package com.osabra.superaijarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    var voicesList by mutableStateOf<List<Voice>>(emptyList())
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { JarvisV6MenuApp() } }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("es","ES"); voicesList = tts?.voices?.filter { it.locale.language == "es" || it.name.contains("es", true) }?.sortedBy { it.name } ?: emptyList() } }
    fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    fun stop() { tts?.stop() }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
data class ChatMsg(val role: String, val text: String)

@Composable
fun ReactorCore(isSpeaking: Boolean, isListening: Boolean, isWake: Boolean) {
    val infinite = rememberInfiniteTransition(label = "reactor")
    val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isSpeaking) 800 else if (isWake) 1500 else 4000, easing = LinearEasing)), label="rot")
    val pulse by infinite.animateFloat(0.8f, 1.25f, infiniteRepeatable(tween(if (isSpeaking) 250 else 800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val glow by infinite.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label="glow")
    Canvas(Modifier.size(240.dp)) {
        val c = center; val base = size.minDimension / 2; val col = when { isSpeaking -> Color(0xFF00FFFF); isListening -> Color(0xFF00FF88); else -> Color(0xFF004466) }
        drawCircle(col.copy(alpha = if (isListening || isSpeaking) 0.35f*glow else 0.08f), radius = base * pulse * 1.1f, center = c)
        drawCircle(Color(0xFF001A33), radius = base * 0.95f, center = c)
        drawCircle(col, radius = base*0.85f, center = c, style = Stroke(width = if (isListening) 4f else 3f))
        rotate(rotation) { for (i in 0..2) { rotate(i*120f) { drawArc(Color(0xFF7CFCFF), 20f, 60f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - base*0.6f, c.y - base*0.6f), size = Size(base*1.2f, base*1.2f), style = Stroke(width = 6f)) } } }
        drawCircle(Color.White, radius = base*0.18f * pulse, center = c)
        drawCircle(col.copy(alpha = 0.9f), radius = base*0.25f * pulse, center = c, style = Stroke(width = 8f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisV6MenuApp() {
    val context = LocalContext.current; val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }; var responseText by remember { mutableStateOf("V6.1 MENU FIX ONLINE. Pulsa ☰ arriba para ajustes.") }
    var modelName by remember { mutableStateOf("gemini-3.6-flash") }; var isLoading by remember { mutableStateOf(false) }; var isSpeaking by remember { mutableStateOf(false) }
    var isListeningWake by remember { mutableStateOf(false) }; var heyEnabled by remember { mutableStateOf(true) }; var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }; var rate by remember { mutableStateOf(0.95f) }; var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var showMenu by remember { mutableStateOf(false) }; var selectedTab by remember { mutableStateOf(0) }
    val models = listOf("gemini-3.6-flash" to "🧠 Más Inteligente", "gemini-2.5-flash" to "⚡ Más Rápido", "gemini-2.0-flash" to "💰 Más Barato", "gemini-1.5-flash-001" to "🛡️ Estable")
    val scope = rememberCoroutineScope(); val apiKey = BuildConfig.GEMINI_API_KEY
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun sendAuto(prompt: String) {
        if (prompt.isBlank()) return; history = history + ChatMsg("TÚ", prompt); isLoading = true; isSpeaking = true
        scope.launch {
            try {
                activity.tts?.let { tts -> selectedVoice?.let { tts.voice = it }; tts.setPitch(pitch); tts.setSpeechRate(rate) }
                val model = GenerativeModel(modelName, apiKey); val result = model.generateContent("Eres JARVIS, corto en español: $prompt")
                val ans = result.text?: "Sin datos"; responseText = ans; history = history + ChatMsg("JARVIS", ans); activity.speak(ans)
            } catch (e: Exception) { responseText = "FALLO: ${e.message}" } finally { isLoading = false; inputText = "" }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0); if (!spoken.isNullOrEmpty()) sendAuto(spoken) }
    fun startWakeListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return; speechRecognizer?.destroy(); val recognizer = SpeechRecognizer.createSpeechRecognizer(context); speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { isListeningWake = true }; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(r: Float) {}; override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningWake = false; if (heyEnabled) startWakeListening() }
            override fun onError(e: Int) { isListeningWake = false; if (heyEnabled) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startWakeListening() }, 800) }
            override fun onResults(res: Bundle?) {
                val txt = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                if (txt.contains("jarvis")) {
                    responseText = "Sí, Oskar. Te escucho."; activity.speak("Sí, Oskar. Te escucho.")
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }
                    speechLauncher.launch(intent)
                } else if (heyEnabled) startWakeListening(); isListeningWake = false
            }
            override fun onPartialResults(p: Bundle?) { val txt = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""; if (txt.contains("jarvis")) recognizer.stopListening() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }
        recognizer.startListening(intent)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g && heyEnabled) startWakeListening() }
    LaunchedEffect(heyEnabled) { if (heyEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWakeListening() else if (heyEnabled) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) { onDispose { speechRecognizer?.destroy() } }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(title = { Text("JARVIS V6.1", color = Color.Cyan) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF01050A)), navigationIcon = { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Cyan) } }, actions = { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.Gray) } })
        }, containerColor = Color(0xFF01050A)) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).background(Color(0xFF01050A)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ReactorCore(isSpeaking, isListeningWake, heyEnabled)
                Text(if (isListeningWake) "● ESCUCHANDO 'HEY JARVIS'..." else if (isLoading) "● PROCESANDO..." else if (isSpeaking) "● HABLANDO..." else "● STANDBY", color = if (isListeningWake) Color(0xFF00FF88) else if (isLoading) Color.Yellow else Color.Gray, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))) { Text(responseText, color = Color(0xFFCCFFFF), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF060E1E)).padding(6.dp), reverseLayout = true) { items(history.reversed()) { msg -> Text("${msg.role}: ${msg.text}", color = if (msg.role=="TÚ") Color.Gray else Color(0xFF00FFFF), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=2.dp)) } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = inputText, onValueChange = { inputText = it }, label = { Text("Orden...") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan))
                    Button(onClick = { val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(intent) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AABB))) { Text("🎤") }
                }
                Button(onClick = { sendAuto(inputText) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color.Black)) { Text("⚡ ENVIAR") }
            }
        }

        if (showMenu) {
            ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = Color(0xFF0A1E2F)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("AJUSTES JARVIS V6.1", color = Color.Cyan, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF0A1E2F), contentColor = Color.Cyan) {
                        Tab(selected = selectedTab==0, onClick = { selectedTab=0 }, text = { Text("🎙️ VOZ") })
                        Tab(selected = selectedTab==1, onClick = { selectedTab=1 }, text = { Text("🧠 GOOGLE") })
                        Tab(selected = selectedTab==2, onClick = { selectedTab=2 }, text = { Text("🎧 HEY") })
                    }
                    Spacer(Modifier.height(12.dp))
                    when (selectedTab) {
                        0 -> {
                            Text("Voz: ${selectedVoice?.name?.take(30) ?: "Defecto"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            LazyColumn(Modifier.height(120.dp).background(Color(0xFF061425)).fillMaxWidth()) {
                                items(activity.voicesList) { v -> TextButton(onClick = { selectedVoice = v; activity.tts?.voice = v; activity.speak("Hola Oskar, soy ${v.name}") }) { Text(v.name.take(40), color = if (selectedVoice==v) Color.Cyan else Color.Gray, style = MaterialTheme.typography.labelSmall) } }
                            }
                            Text("Gravedad: ${String.format("%.2f", pitch)} (0.70=Jarvis)", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Slider(value = pitch, onValueChange = { pitch = it; activity.tts?.setPitch(it) }, valueRange = 0.5f..2f)
                            Text("Velocidad: ${String.format("%.2f", rate)}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Slider(value = rate, onValueChange = { rate = it; activity.tts?.setSpeechRate(it) }, valueRange = 0.5f..1.8f)
                            Button(onClick = { activity.speak("Hola Oskar, reactor ARC al cien por cien") }, modifier = Modifier.fillMaxWidth()) { Text("🔊 PROBAR VOZ") }
                        }
                        1 -> {
                            models.forEach { (id, desc) ->
                                Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { modelName = id }, colors = CardDefaults.cardColors(containerColor = if (modelName==id) Color(0xFF004466) else Color(0xFF101828))) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Column { Text(id, color = Color.White, style = MaterialTheme.typography.labelSmall); Text(desc, color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
                                        RadioButton(selected = modelName==id, onClick = { modelName = id })
                                    }
                                }
                            }
                        }
                        2 -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Activar 'Hey Jarvis'", color = Color.White); Switch(checked = heyEnabled, onCheckedChange = { heyEnabled = it })
                            }
                            Text("Escucha en segundo plano. Di 'Hey Jarvis' para despertarme.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=8.dp))
                            Button(onClick = { activity.stop(); isSpeaking = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.fillMaxWidth().padding(top=12.dp)) { Text("■ PARAR VOZ") }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
