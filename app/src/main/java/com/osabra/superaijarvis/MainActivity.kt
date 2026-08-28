package com.osabra.superaijarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.*
import java.net.URL
import java.util.*
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    var voicesList by mutableStateOf<List<Voice>>(emptyList())
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { JarvisV8() } }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("es","ES"); voicesList = tts?.voices?.filter { it.locale.language == "es" }?.sortedBy { it.name }?: emptyList() } }
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

suspend fun getWeatherVitoria(): String = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=42.85&longitude=-2.68&current_weather=true")
        val json = JSONObject(url.readText())
        val cur = json.getJSONObject("current_weather")
        val temp = cur.getDouble("temperature")
        val wind = cur.getDouble("windspeed")
        "En Vitoria-Gasteiz ahora mismo ${temp}°C con viento de ${wind} km/h. Datos en tiempo real."
    } catch(e:Exception){ "No pude conectar con el tiempo real, pero pareces estar en Vitoria con fresco del norte." }
}

@Composable
fun JarvisV8() {
    val context = LocalContext.current; val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }; var responseText by remember { mutableStateOf("V8 24/7 + TIEMPO REAL ONLINE. Servicio activo.") }
    var modelName by remember { mutableStateOf("gemini-1.5-flash") }; var isLoading by remember { mutableStateOf(false) }; var isSpeaking by remember { mutableStateOf(false) }
    var isListeningWake by remember { mutableStateOf(false) }; var heyEnabled by remember { mutableStateOf(true) }; var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }; var rate by remember { mutableStateOf(0.95f) }; var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var showSettings by remember { mutableStateOf(false) }; var settingsPage by remember { mutableStateOf(0) }
    val models = listOf("gemini-1.5-flash", "gemini-1.5-flash-001", "gemini-2.0-flash")
    val scope = rememberCoroutineScope(); val apiKey = BuildConfig.GEMINI_API_KEY
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Iniciar servicio 24/7
    LaunchedEffect(Unit){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)!= PackageManager.PERMISSION_GRANTED){
                // no pedimos aqui, lo hace el launcher
            }
        }
        val serviceIntent = Intent(context, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
    }

    fun doAction(prompt: String): Boolean {
        val p = prompt.lowercase()
        try {
            when {
                p.contains("linterna") && (p.contains("enciende") || p.contains("on") || p.contains("prende")) -> {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], true)
                    responseText = "Linterna ON"; activity.speak("Linterna encendida"); return true
                }
                p.contains("linterna") && (p.contains("apaga") || p.contains("off")) -> {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], false)
                    responseText = "Linterna OFF"; activity.speak("Linterna apagada"); return true
                }
                p.contains("youtube") -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))); return true }
                p.contains("whatsapp") -> { context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let{context.startActivity(it)}; return true }
                p.contains("hora") -> { val hora = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); responseText="Son las $hora en Vitoria"; activity.speak(responseText); return true }
            }
        } catch(e:Exception){}
        return false
    }

    fun sendAuto(p: String) {
        if(p.isBlank()) return
        history = history + ChatMsg("TÚ", p)
        if(doAction(p)) { inputText=""; return }
        // TIEMPO REAL
        if(p.lowercase().contains("tiempo") || p.lowercase().contains("temperatura") || p.lowercase().contains("vitoria hace")){
            isLoading=true
            scope.launch{
                val weather = getWeatherVitoria()
                responseText = weather; history = history + ChatMsg("JARVIS", weather); activity.speak(weather); isLoading=false; inputText=""
            }
            return
        }
        isLoading = true; isSpeaking = true
        scope.launch {
            var success = false; var lastError = ""
            val tryModels = listOf(modelName, "gemini-1.5-flash").distinct()
            for(mId in tryModels){
                try{
                    activity.tts?.let{ t-> selectedVoice?.let{ t.voice=it }; t.setPitch(pitch); t.setSpeechRate(rate) }
                    val m = GenerativeModel(mId, apiKey)
                    val r = m.generateContent("Eres JARVIS de Oskar en Vitoria, corto en español: $p")
                    val ans = r.text?: "Sin datos"
                    responseText = ans; history = history + ChatMsg("JARVIS", ans); activity.speak(ans); success = true; break
                }catch(e:Exception){ lastError = e.message?: "error"; }
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
        override fun onResults(res: Bundle?){ val txt=res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")){ activity.speak("Si Oskar, te escucho"); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }else if(heyEnabled) startWake(); isListeningWake=false }
        override fun onPartialResults(p: Bundle?){ val txt=p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")) rec.stopListening() }
        override fun onEvent(t:Int,p:Bundle?){}
    }); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ g-> if(g&&heyEnabled) startWake() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(heyEnabled){
        if(heyEnabled&&ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startWake() else if(heyEnabled) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    DisposableEffect(Unit){ onDispose{ speechRecognizer?.destroy() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF01050A)).padding(10.dp)){
        Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){ Text("JARVIS V8 24/7 + CLIMA", color=Color.Cyan); Button(onClick={showSettings=true}){ Text("AJUSTES") } }
            ReactorCore(isSpeaking, isListeningWake)
            Text(if(isListeningWake) "HEY JARVIS 24/7 ESCUCHANDO..." else "STANDBY", color=if(isListeningWake) Color(0xFF00FF88) else Color.Gray)
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color(0xFF0D1B2A))){ Text(responseText, color=Color(0xFFCCFFFF), modifier=Modifier.padding(10.dp)) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF060E1E)).padding(6.dp), reverseLayout=true){ items(history.reversed()){ msg-> Text(msg.role + ": " + msg.text, color=if(msg.role=="TÚ") Color.Gray else Color(0xFF00FFFF)) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){ OutlinedTextField(value=inputText, onValueChange={inputText=it}, label={Text("Orden...")}, modifier=Modifier.weight(1f), colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White, focusedBorderColor=Color.Cyan)); Button(onClick={ val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }){ Text("MIC") } }
            Button(onClick={sendAuto(inputText)}, modifier=Modifier.fillMaxWidth(), enabled=!isLoading, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00FFFF), contentColor=Color.Black)){ Text("ENVIAR") }
        }
        if(showSettings){
            Card(Modifier.fillMaxSize().padding(10.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF0A1E2F))){
                Column(Modifier.padding(12.dp).fillMaxSize()){
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){ Text("V8 AJUSTES", color=Color.Cyan); Button(onClick={showSettings=false}){ Text("X") } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)){ Button(onClick={settingsPage=0}){ Text("VOZ") }; Button(onClick={settingsPage=1}){ Text("GOOGLE") }; Button(onClick={settingsPage=2}){ Text("24/7") } }
                    Spacer(Modifier.height(10.dp))
                    if(settingsPage==0){
                        Text("Voz: ${selectedVoice?.name?.take(25)?: "Defecto"}", color=Color.White)
                        LazyColumn(Modifier.height(120.dp).background(Color(0xFF061425))){ items(activity.voicesList){ v-> Text(v.name.take(35), color=if(selectedVoice==v) Color.Cyan else Color.Gray, modifier=Modifier.padding(6.dp).clickable{ selectedVoice=v; activity.tts?.voice=v; activity.speak("Hola Oskar") }) } }
                        Text("Grave ${pitch}", color=Color.Gray); Slider(value=pitch, onValueChange={pitch=it}, valueRange=0.5f..2f)
                    } else if(settingsPage==1){
                        models.forEach{ m-> Card(Modifier.fillMaxWidth().padding(4.dp).clickable{modelName=m}, colors=CardDefaults.cardColors(containerColor=if(modelName==m) Color(0xFF004466) else Color(0xFF101828))){ Row(Modifier.padding(10.dp)){ RadioButton(selected=modelName==m, onClick={modelName=m}); Text(m, color=Color.White) } } }
                    } else {
                        Text("V8 24/7: Cuando cierras la app, veras una notificacion permanente 'JARVIS V8 ACTIVO'. Mientras esté ahí, el móvil no mata a JARVIS. Es como Alexa.", color=Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Text("TIEMPO REAL: Di 'Que tiempo hace en Vitoria' y consulta temperatura real de Open-Meteo.", color=Color(0xFF00FFFF))
                    }
                }
            }
        }
    }
}
