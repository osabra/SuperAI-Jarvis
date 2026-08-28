package com.osabra.superaijarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.sin

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { JarvisV3App() }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("es", "ES"); tts?.setSpeechRate(1.0f) }
    }
    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
    fun stop() { tts?.stop() }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

data class ChatMsg(val role: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisV3App() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("V3 ONLINE. Sistemas HUD activos. ¿Órdenes, Oskar?") }
    var modelName by remember { mutableStateOf("gemini-3.6-flash") }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var expanded by remember { mutableStateOf(false) }
    val models = listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash-001")
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.GEMINI_API_KEY

    // Animación HUD
    val infinite = rememberInfiniteTransition(label = "hud")
    val wavePhase by infinite.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "wave")

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spoken.isNullOrEmpty()) inputText = spoken
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            }
            speechLauncher.launch(i)
        }
    }

    fun send(prompt: String) {
        if (prompt.isBlank()) return
        history = history + ChatMsg("TÚ", prompt)
        isLoading = true; isSpeaking = true
        scope.launch {
            try {
                val model = GenerativeModel(modelName, apiKey)
                val result = model.generateContent("Responde como JARVIS de Iron Man, corto, técnico y en español: $prompt")
                val ans = result.text?: "Sin datos"
                responseText = ans
                history = history + ChatMsg("JARVIS", ans)
                activity.speak(ans)
            } catch (e: Exception) { responseText = "Error: ${e.message}" }
            finally { isLoading = false }
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF02060F)).padding(12.dp)) {
            // HEADER HUD
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JARVIS V3 // HUD", color = Color.Cyan)
                Box {
                    Button(onClick = { expanded = true }, contentPadding = PaddingValues(6.dp)) { Text(modelName, style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { modelName = m; expanded = false }) }
                    }
                }
            }
            // ONDAS
            Canvas(Modifier.fillMaxWidth().height(60.dp).background(Color(0xFF0A1A2F))) {
                val w = size.width; val h = size.height / 2
                for (i in 0..3) {
                    val path = mutableListOf<Offset>()
                    for (x in 0..w.toInt() step 10) {
                        val y = h + sin(Math.toRadians((x + wavePhase + i*40).toDouble())).toFloat() * (20 + i*10) * (if (isLoading || isSpeaking) 1f else 0.1f)
                        path.add(Offset(x.toFloat(), y))
                    }
                    for (j in 0 until path.size-1) drawLine(Color(0xFF00FFFF).copy(alpha = 0.6f - i*0.15f), path[j], path[j+1], strokeWidth = 2f)
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().height(100.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF101828))) {
                Text(responseText, color = Color(0xFF7CFCFF), modifier = Modifier.padding(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { activity.stop(); isSpeaking = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("■ STOP") }
                Text(if (isLoading) "● PROCESANDO..." else if (isSpeaking) "● HABLANDO..." else "● STANDBY", color = if (isLoading) Color.Yellow else Color.Green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Spacer(Modifier.height(8.dp))
            // HISTORIAL
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080F1E)).padding(8.dp), reverseLayout = true) {
                items(history.reversed()) { msg ->
                    Text("${msg.role}: ${msg.text}", color = if (msg.role=="TÚ") Color.Gray else Color.Cyan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, label = { Text("Orden...") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan))
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                        }
                        speechLauncher.launch(intent)
                    } else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("🎤") }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = { send(inputText); inputText = "" }, modifier = Modifier.fillMaxWidth(), enabled =!isLoading) { Text("ENVIAR A JARVIS") }
        }
    }
}
