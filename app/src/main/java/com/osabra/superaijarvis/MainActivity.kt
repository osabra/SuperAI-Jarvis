package com.osabra.superaijarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.sin

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    var voicesList by mutableStateOf<List<Voice>>(emptyList())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { JarvisReactorVoiceApp() }
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
fun ReactorCore(isSpeaking: Boolean, isLoading: Boolean) {
    val infinite = rememberInfiniteTransition(label = "reactor")
    val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isSpeaking) 800 else 4000, easing = LinearEasing)), label="rot")
    val pulse by infinite.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(if (isSpeaking) 300 else 1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val glow by infinite.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label="glow")
    Canvas(Modifier.size(200.dp)) {
        val c = center; val base = size.minDimension / 2
        drawCircle(Color(0xFF00E5FF).copy(alpha = if (isSpeaking) 0.3f*glow else 0.08f), radius = base * pulse, center = c)
        drawCircle(Color(0xFF001A33), radius = base * 0.95f, center = c)
        drawCircle(Color(0xFF00FFFF), radius = base*0.85f, center = c, style = Stroke(width = 3f))
        rotate(rotation) { for (i in 0..2) { rotate(i*120f) { drawArc(Color(0xFF7CFCFF), 20f, 60f, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - base*0.6f, c.y - base*0.6f), size = Size(base*1.2f, base*1.2f), style = Stroke(width = 6f, cap = StrokeCap.Round)) } } }
        rotate(-rotation*0.6f) { for (i in 0..5) { rotate(i*60f) { drawLine(Color(0xFF00FFFF).copy(alpha=0.7f), start = androidx.compose.ui.geometry.Offset(c.x, c.y - base*0.45f), end = androidx.compose.ui.geometry.Offset(c.x, c.y - base*0.62f), strokeWidth = 3f) } } }
        drawCircle(Color.White, radius = base*0.18f * pulse, center = c)
        drawCircle(Color(0xFF00FFFF).copy(alpha = 0.9f), radius = base*0.25f * pulse, center = c, style = Stroke(width = 8f))
        if (isLoading) drawArc(Color.Yellow, -90f, 270f*glow, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - base*0.9f, c.y - base*0.9f), size = Size(base*1.8f, base*1.8f), style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisReactorVoiceApp() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("V4.2 VOICE ONLINE. Elige mi voz arriba, Oskar.") }
    var modelName by remember { mutableStateOf("gemini-3.6-flash") }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }
    var rate by remember { mutableStateOf(0.95f) }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var expandedModel by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    val models = listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash-001")
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.GEMINI_API_KEY

    fun sendAuto(prompt: String) {
        if (prompt.isBlank()) return
        history = history + ChatMsg("TÚ", prompt)
        isLoading = true; isSpeaking = true
        scope.launch {
            try {
                // Aplicar voz
                activity.tts?.let { tts ->
                    selectedVoice?.let { tts.voice = it }
                    tts.setPitch(pitch)
                    tts.setSpeechRate(rate)
                }
                val model = GenerativeModel(modelName, apiKey)
                val result = model.generateContent("Eres JARVIS, responde corto en español: $prompt")
                val ans = result.text?: "Sin datos"
                responseText = ans
                history = history + ChatMsg("JARVIS", ans)
                activity.speak(ans)
            } catch (e: Exception) { responseText = "FALLO: ${e.message}" }
            finally { isLoading = false; inputText = "" }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val spoken = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spoken.isNullOrEmpty()) sendAuto(spoken)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            }
            speechLauncher.launch(i)
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF01050A)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // MODELO
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ARC V4.2 VOICE", color = Color.Cyan, style = MaterialTheme.typography.labelMedium)
                Box {
                    Button(onClick = { expandedModel = true }, contentPadding = PaddingValues(6.dp)) { Text(modelName, style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(expanded = expandedModel, onDismissRequest = { expandedModel = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { modelName = m; expandedModel = false }) }
                    }
                }
            }
            // VOZ
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1E2F))) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Voz:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Box {
                            Button(onClick = { expandedVoice = true }, contentPadding = PaddingValues(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004466))) {
                                Text(selectedVoice?.name?.take(20) ?: "Voz por defecto", style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(expanded = expandedVoice, onDismissRequest = { expandedVoice = false }) {
                                activity.voicesList.forEach { v ->
                                    DropdownMenuItem(text = { Text("${v.name.take(25)}") }, onClick = { selectedVoice = v; expandedVoice = false; activity.tts?.voice = v })
                                }
                            }
                        }
                    }
                    Text("Grave [${String.format("%.2f", pitch)}] - Jarvis = 0.70", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Slider(value = pitch, onValueChange = { pitch = it; activity.tts?.setPitch(it) }, valueRange = 0.5f..2f)
                    Text("Velocidad [${String.format("%.2f", rate)}]", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Slider(value = rate, onValueChange = { rate = it; activity.tts?.setSpeechRate(it) }, valueRange = 0.5f..1.5f)
                }
            }
            Spacer(Modifier.height(6.dp))
            ReactorCore(isSpeaking, isLoading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLoading) "● SOBRECARGA..." else if (isSpeaking) "● HABLANDO..." else "● ESTABLE", color = if (isLoading) Color.Yellow else if (isSpeaking) Color.Cyan else Color(0xFF00FF88), style = MaterialTheme.typography.labelSmall)
                Button(onClick = { activity.stop(); isSpeaking = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA0000)), contentPadding = PaddingValues(4.dp)) { Text("STOP VOZ") }
                Button(onClick = { sendAuto("Di hola Oskar con tu voz actual") }, contentPadding = PaddingValues(4.dp)) { Text("PROBAR VOZ") }
            }
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A))) { Text(responseText, color = Color(0xFFCCFFFF), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF060E1E)).padding(6.dp), reverseLayout = true) {
                items(history.reversed()) { msg -> Text("${msg.role}: ${msg.text}", color = if (msg.role=="TÚ") Color.Gray else Color(0xFF00FFFF), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=2.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, label = { Text("Orden...") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan))
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }
                        speechLauncher.launch(intent)
                    } else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AABB))) { Text("🎤") }
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = { sendAuto(inputText) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color.Black)) { Text("⚡ ENVIAR") }
        }
    }
}
