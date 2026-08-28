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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { JarvisV63() } }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("es","ES"); voicesList = tts?.voices?.filter { it.locale.language == "es" || it.name.contains("es", true) }?.sortedBy { it.name } ?: emptyList() } }
    fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    fun stop() { tts?.stop() }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
data class ChatMsg(val role: String, val text: String)

@Composable
fun ReactorCore(isSpeaking: Boolean, isListening: Boolean) {
    val inf = rememberInfiniteTransition(label="r")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if(isSpeaking)800 else 3000, easing=LinearEasing)), label="rot")
    val pulse by inf.animateFloat(0.8f, 1.3f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label="pulse")
    Canvas(Modifier.size(220.dp)) {
        val c=center; val base=size.minDimension/2; val col = if(isListening) Color(0xFF00FF88) else Color(0xFF00FFFF)
        drawCircle(col.copy(alpha=0.3f), radius=base*pulse, center=c)
        drawCircle(Color(0xFF001A33), radius=base*0.95f, center=c)
        drawCircle(col, radius=base*0.85f, center=c, style=Stroke(3f))
        rotate(rot){ for(i in 0..2){ rotate(i*120f){ drawArc(Color(0xFF7CFCFF), 20f, 60f, false, topLeft=androidx.compose.ui.geometry.Offset(c.x-base*0.6f, c.y-base*0.6f), size=Size(base*1.2f, base*1.2f), style=Stroke(6f)) } } }
        drawCircle(Color.White, radius=base*0.18f*pulse, center=c)
    }
}

@Composable
fun JarvisV63() {
    val context = LocalContext.current; val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }; var responseText by remember { mutableStateOf("V6.3 FIX ONLINE. Ahora con Gemini 1.5 - Di Hey Jarvis") }
    var modelName by remember { mutableStateOf("gemini-1.5-flash") }; var isLoading by remember { mutableStateOf(false) }; var isSpeaking by remember { mutableStateOf(false) }
    var isListeningWake by remember { mutableStateOf(false) }; var heyEnabled by remember { mutableStateOf(true) }; var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }; var rate by remember { mutableStateOf(0.95f) }; var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var showSettings by remember { mutableStateOf(false) }; var settingsPage by remember { mutableStateOf(0) }
    val models = listOf("gemini-1.5-flash", "gemini-1.5-flash-001", "gemini-2.0-flash", "gemini-2.0-flash-exp")
    val scope = rememberCoroutineScope(); val apiKey = BuildConfig.GEMINI_API_KEY
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun sendAuto(p: String) {
        if(p.isBlank()) return
        history = history + ChatMsg("TÚ", p)
        isLoading = true; isSpeaking = true
        scope.launch {
            var success = false; var lastError = ""
            val tryModels = listOf(modelName, "gemini-1.5-flash", "gemini-1.5-flash-001", "gemini-2.0-flash").distinct()
            for(mId in tryModels){
                try{
                    activity.tts?.let{ t-> selectedVoice?.let{ t.voice=it }; t.setPitch(pitch); t.setSpeechRate(rate) }
                    val m = GenerativeModel(mId, apiKey)
                    val r = m.generateContent("Eres JARVIS de Oskar en Vitoria-Gasteiz, responde corto, util y en español. Hoy es ${Date()}: $p")
                    val ans = r.text ?: "Sin datos"
                    responseText = ans; history = history + ChatMsg("JARVIS ($mId)", ans); modelName = mId; activity.speak(ans); success = true; break
                }catch(e:Exception){ lastError = e.message ?: "desconocido"; }
            }
            if(!success) responseText = "FALLO: $lastError"
            isLoading = false; inputText = ""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ r-> val s=r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0); if(!s.isNullOrEmpty()) sendAuto(s) }
    fun startWake(){ if(!SpeechRecognizer.isRecognitionAvailable(context)) return; speechRecognizer?.destroy(); val rec=SpeechRecognizer.createSpeechRecognizer(context); speechRecognizer=rec; rec.setRecognitionListener(object: RecognitionListener{
        override fun onReadyForSpeech(p: Bundle?){ isListeningWake=true }; override fun onBeginningOfSpeech(){}
        override fun onRmsChanged(r: Float){}; override fun onBufferReceived(b: ByteArray?){}
        override fun onEndOfSpeech(){ isListeningWake=false; if(heyEnabled) startWake() }
        override fun onError(e: Int){ isListeningWake=false; if(heyEnabled) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({startWake()},800) }
        override fun onResults(res: Bundle?){ val txt=res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")){ responseText="Sí Oskar, te escucho"; activity.speak("Sí Oskar, te escucho"); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }else if(heyEnabled) startWake(); isListeningWake=false }
        override fun onPartialResults(p: Bundle?){ val txt=p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")) rec.stopListening() }
        override fun onEvent(t:Int,p:Bundle?){}
    }); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ g-> if(g&&heyEnabled) startWake() }
    LaunchedEffect(heyEnabled){ if(heyEnabled&&ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startWake() else if(heyEnabled) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit){ onDispose{ speechRecognizer?.destroy() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF01050A)).padding(10.dp)){
        Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){ Text("JARVIS V6.3", color=Color.Cyan); Button(onClick={showSettings=true}){ Text("☰ AJUSTES") } }
            ReactorCore(isSpeaking, isListeningWake)
            Text(if(isListeningWake) "● HEY JARVIS ESCUCHANDO..." else if(isLoading) "● PROCESANDO..." else "● STANDBY", color=if(isListeningWake) Color(0xFF00FF88) else Color.Gray, style
