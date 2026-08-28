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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { JarvisV5HeyApp() }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es","ES")
            voicesList = tts?.voices?.filter { it.locale.language == "es" || it.name.contains("es", true) }?.sortedBy { it.name } ?: emptyList()
        }
    }
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
    Canvas(Modifier.size(220.dp)) {
        val c = center; val base = size.minDimension / 2
        val col = when { isSpeaking -> Color(0xFF00FFFF); isWake -> Color(0xFF00FF88); else -> Color(0xFF004466) }
        drawCircle(col.copy(alpha = if (isListening) 0.35f*glow else 0.08f), radius = base * pulse * 1.1f, center = c)
        drawCircle(Color(0xFF001A33), radius = base * 0.95f, center = c)
        drawCircle(col, radius = base*0.85f, center = c, style = Stroke(width = if (isWake) 4f else 3f))
        rotate(rotation) { for (i in 0..2) { rotate(i*120f) { drawArc(Color(0xFF7CFCFF), 20f, 60f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - base*0.6f, c.y - base*0.6f), size = Size(base*1.2f, base*1.2f), style = Stroke(width = 6f)) } } }
        drawCircle(Color.White, radius = base*0.18f * pulse, center = c)
        drawCircle(col.copy(alpha = 0.9f), radius = base*0.25f * pulse, center = c, style = Stroke(width = 8f))
        if (isListening) drawArc(Color(0xFF00FF88), -90f, 270f*glow, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - base*0.9f, c.y - base*0.9f), size = Size(base*1.8f, base*1.8f), style = Stroke(width = 4f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisV5HeyApp() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("V5 HEY JARVIS ONLINE. Di 'Hey Jarvis' para despertarme, Oskar.") }
    var modelName by remember { mutableStateOf("gemini-3.6-flash") }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isListeningWake by remember { mutableStateOf(false) }
    var heyEnabled by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }
    var rate by remember { mutableStateOf(0.95f) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var expandedModel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.GEMINI_API_KEY

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun sendAuto(prompt: String) {
        if (prompt.isBlank()) return
        history = history + ChatMsg("TÚ", prompt)
        isLoading = true; isSpeaking = true
        scope.launch {
            try {
                activity.tts?.let { tts -> selectedVoice?.let { tts.voice = it }; tts.setPitch(pitch); tts.setSpeechRate(rate) }
                val model = GenerativeModel(modelName, apiKey)
                val result = model.generateContent("Eres JARVIS, responde corto en español: $prompt")
                val ans = result.text?: "Sin datos"
                responseText = ans; history = history + ChatMsg("JARVIS", ans); activity.speak(ans)
            } catch (e: Exception) { responseText = "FALLO: ${e.message}" } finally { isLoading = false; inputText = "" }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spoken.isNullOrEmpty()) sendAuto(spoken)
    }

    fun startWakeListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { isListeningWake = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningWake = false; if (heyEnabled) startWakeListening() }
            override fun onError(e: Int) { isListeningWake = false; if (heyEnabled) { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startWakeListening() }, 800) } }
            override fun onResults(res: Bundle?) {
                val txt = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                if (txt.contains("hey jarvis") || txt.contains("hey") && txt.contains("jarvis") || txt.contains("oye jarvis")) {
                    responseText = "Sí, Oskar. Te escucho."
                    activity.speak("Sí, Oskar. Te escucho.")
                    // Lanza el reconocimiento normal para la orden
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    }
                    speechLauncher.launch(intent)
                } else { if (heyEnabled) startWakeListening() }
                isListeningWake = false
            }
            override fun onPartialResults(p: Bundle?) {
                val txt = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                if (txt.contains("jarvis")) {
                    // Detectado parcial, corta y pasa a orden
                    recognizer.stopListening()
                }
            }
            override fun onEvent(type: Int, p: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g && heyEnabled) startWakeListening() }

    // Auto-inicia escucha al entrar si hay permiso
    LaunchedEffect(heyEnabled) {
        if (heyEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startWakeListening()
        } else if (heyEnabled) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) { onDispose { speechRecognizer?.destroy() } }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF01050A)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ARC V5 HEY JARVIS", color = Color.Cyan, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (heyEnabled) "HEY ON" else "HEY OFF", color = if (heyEnabled) Color(0xFF00FF88) else Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Switch(checked = heyEnabled, onCheckedChange = { heyEnabled = it; if (!it) { speechRecognizer?.stopListening(); isListeningWake = false } })
                }
            }
            Spacer(Modifier.height(6.dp))
            ReactorCore(isSpeaking, isListeningWake, heyEnabled)
            Text(if (isListeningWake) "● ESCUCHANDO 'HEY JARVIS'..." else if (isLoading) "● PROCESANDO..." else if (isSpeaking) "● HABLANDO..." else "● STANDBY", color = if (isListeningWake) Color(0xFF00FF88) else if (isLoading) Color.Yellow else Color.Gray, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))) { Text(responseText, color = Color(0xFFCCFFFF), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF060E1E)).padding(6.dp), reverseLayout = true) {
                items(history.reversed()) { msg -> Text("${msg.role}: ${msg.text}", color = if (msg.role=="TÚ") Color.Gray else Color(0xFF00FFFF), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=2.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, label = { Text("Orden...") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan))
                Button(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }
                    speechLauncher.launch(intent)
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AABB))) { Text("🎤") }
            }
            Button(onClick = { sendAuto(inputText) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color.Black)) { Text("⚡ ENVIAR") }
        }
    }
}
