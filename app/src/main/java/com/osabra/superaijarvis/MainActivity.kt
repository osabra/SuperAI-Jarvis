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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { JarvisV2App() }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
            tts?.setSpeechRate(1.0f)
        }
    }
    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisV2App() {
    val context = LocalContext.current
    val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("Hola Oskar, soy Jarvis V2. Ahora puedo escucharte y hablarte. Pulsa el micro.") }
    var modelName by remember { mutableStateOf("gemini-2.5-flash") }
    var isLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val models = listOf("gemini-3.6-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash-001")
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.GEMINI_API_KEY

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (!spokenText.isNullOrEmpty()) { inputText = spokenText }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            }
            speechLauncher.launch(intent)
        }
    }

    fun sendToGemini(prompt: String) {
        if (prompt.isBlank() || apiKey.isBlank()) return
        isLoading = true
        responseText = "Pensando con $modelName..."
        scope.launch {
            try {
                val model = GenerativeModel(modelName, apiKey)
                val result = model.generateContent(prompt)
                val answer = result.text ?: "Sin respuesta"
                responseText = answer
                activity.speak(answer)
            } catch (e: Exception) { responseText = "Error: ${e.message}" } 
            finally { isLoading = false }
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF050A14)).padding(16.dp)) {
            Text("SUPER AI JARVIS V2", color = Color.Cyan, style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (apiKey.isEmpty()) "⚠️ Sin API Key" else "✅ Conectado", color = Color.Green, style = MaterialTheme.typography.labelSmall)
                Box {
                    Button(onClick = { expanded = true }, contentPadding = PaddingValues(8.dp)) { Text(modelName, style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { modelName = m; expanded = false }) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF101828))) {
                Text(responseText, color = Color.White, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it }, label = { Text("Orden para Jarvis...") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.Cyan))
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                        }
                        speechLauncher.launch(intent)
                    } else { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                }) { Text("🎤") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { sendToGemini(inputText); inputText = "" }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text(if (isLoading) "Procesando..." else "Enviar a $modelName") }
        }
    }
}
