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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    var voicesList by mutableStateOf<List<Voice>>(emptyList())
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { JarvisV9() } }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("es","ES"); voicesList = tts?.voices?.filter { it.locale.language == "es" }?.sortedBy { it.name }?: emptyList() } }
    fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    fun stop() { tts?.stop() }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
data class ChatMsg(val role: String, val text: String)

@Composable
fun ReactorV9(isSpeaking: Boolean, isListening: Boolean) {
    val inf = rememberInfiniteTransition(label="r")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if(isSpeaking)600 else 4000, easing=LinearEasing)), label="rot")
    val rot2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(if(isSpeaking)900 else 6000, easing=LinearEasing)), label="rot2")
    val pulse by inf.animateFloat(0.85f, 1.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label="pulse")
    val glowAlpha by inf.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label="glow")
    Box(Modifier.size(260.dp), contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val c=center; val base=size.minDimension/2.2f
            val mainCol = if(isListening) Color(0xFF00FF88) else Color(0xFF00D4FF)
            // Glow exterior
            drawCircle(mainCol.copy(alpha=glowAlpha*0.25f), radius=base*pulse*1.3f, center=c)
            drawCircle(mainCol.copy(alpha=0.15f), radius=base*pulse*1.15f, center=c)
            // Anillo exterior tecnico
            drawCircle(Color(0xFF0A2A4A), radius=base, center=c, style=Stroke(12f))
            drawCircle(mainCol.copy(alpha=0.6f), radius=base, center=c, style=Stroke(2f))
            // Anillos giratorios
            rotate(rot){ for(i in 0..2){ rotate(i*120f){ drawArc(mainCol, 25f, 45f, false, topLeft=Offset(c.x-base*0.75f, c.y-base*0.75f), size=Size(base*1.5f, base*1.5f), style=Stroke(4f)) } } }
            rotate(rot2){ drawCircle(Color.White.copy(alpha=0.2f), radius=base*0.65f, center=c, style=Stroke(1f)); for(i in 0..3){ rotate(i*90f){ drawLine(Color.White.copy(alpha=0.5f), start=Offset(c.x, c.y-base*0.65f), end=Offset(c.x, c.y-base*0.55f), strokeWidth=2f) } } }
            // Nucleo
            drawCircle(Color(0xFF001E38), radius=base*0.5f, center=c)
            drawCircle(mainCol, radius=base*0.5f, center=c, style=Stroke(2f))
            drawCircle(Color.White, radius=base*0.2f*pulse, center=c)
            drawCircle(mainCol.copy(alpha=0.8f), radius=base*0.2f*pulse, center=c, style=Stroke(3f))
        }
        // Puntos orbitales
        if(isSpeaking) {
            Box(Modifier.fillMaxSize()) {
                repeat(3){ i ->
                    val angle = rot + i*120f
                    Box(Modifier.offset((kotlin.math.cos(Math.toRadians(angle.toDouble()))*110).dp, (kotlin.math.sin(Math.toRadians(angle.toDouble()))*110).dp).size(8.dp).clip(CircleShape).background(Color(0xFF00FFFF)).align(Alignment.Center))
                }
            }
        }
    }
}

suspend fun getWeatherCity(city: String): String = withContext(Dispatchers.IO) {
    try {
        val cleanCity = city.trim().replace(" ", "%20")
        val geoUrl = URL("https://geocoding-api.open-meteo.com/v1/search?name=$cleanCity&count=1&language=es&format=json")
        val geoJson = JSONObject(geoUrl.readText())
        if(!geoJson.has("results")) return@withContext "No encuentro $city"
        val first = geoJson.getJSONArray("results").getJSONObject(0)
        val lat = first.getDouble("latitude"); val lon = first.getDouble("longitude")
        val name = first.getString("name"); val country = first.optString("country","")
        val wUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
        val wJson = JSONObject(wUrl.readText()).getJSONObject("current_weather")
        val temp = wJson.getDouble("temperature"); val wind = wJson.getDouble("windspeed"); val code = wJson.getInt("weathercode")
        val estado = when(code){0->"despejado";1,2,3->"parcial";45,48->"niebla";51,53,55->"llovizna";61,63,65->"lluvia";71,73,75->"nieve";80,81,82->"chubascos";95->"tormenta"; else->"variable"}
        "$name ($country): ${temp}°C $estado viento ${wind}km/h"
    } catch(e:Exception){ "Error $city" }
}
fun extractCity(prompt: String): String {
    val p = prompt.lowercase()
    val patterns = listOf("tiempo en ","clima en ","tiempo de ","tiempo hace en ","que tiempo hace en ","qué tiempo hace en ")
    for(pat in patterns){ if(p.contains(pat)){ var city = p.substringAfter(pat).trim().replace("?","").replace(".",""); if(city.isNotEmpty()) return city } }
    if(p.contains("tiempo") || p.contains("clima")) return "Vitoria-Gasteiz"
    return ""
}

@Composable
fun JarvisV9() {
    val context = LocalContext.current; val activity = context as MainActivity
    var inputText by remember { mutableStateOf("") }; var responseText by remember { mutableStateOf("JARVIS MARK IX ONLINE\nSistema holográfico activo\nDi: Hey Jarvis") }
    var modelName by remember { mutableStateOf("gemini-1.5-flash") }; var isLoading by remember { mutableStateOf(false) }; var isListeningWake by remember { mutableStateOf(false) }
    var heyEnabled by remember { mutableStateOf(true) }; var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0.75f) }; var rate by remember { mutableStateOf(0.95f) }; var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val apiKey = BuildConfig.GEMINI_API_KEY
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val timeNow = remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit){ while(true){ timeNow.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()); delay(1000) } }

    LaunchedEffect(Unit){
        val serviceIntent = Intent(context, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
    }

    fun doAction(prompt: String): Boolean {
        val p = prompt.lowercase()
        try {
            when {
                p.contains("linterna") && (p.contains("enciende") || p.contains("on")) -> { val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], true); responseText=">>> SISTEMA ILUMINACIÓN: ON"; activity.speak("Linterna encendida"); return true }
                p.contains("linterna") && (p.contains("apaga") || p.contains("off")) -> { val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.setTorchMode(cm.cameraIdList[0], false); responseText=">>> SISTEMA ILUMINACIÓN: OFF"; activity.speak("Linterna apagada"); return true }
                p.contains("youtube") -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))); return true }
                p.contains("whatsapp") -> { context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let{context.startActivity(it)}; return true }
            }
        } catch(e:Exception){}
        return false
    }

    fun sendAuto(p: String) {
        if(p.isBlank()) return
        history = history + ChatMsg("OSKAR", p)
        if(doAction(p)) { inputText=""; return }
        val city = extractCity(p)
        if(city.isNotEmpty()){
            scope.launch{
                isLoading=true
                val weather = getWeatherCity(city)
                responseText = ">>> CLIMA :: $weather"; history = history + ChatMsg("JARVIS", weather); activity.speak(weather); isLoading=false; inputText=""
            }
            return
        }
        isLoading = true
        scope.launch {
            try{
                activity.tts?.let{ t-> selectedVoice?.let{ t.voice=it }; t.setPitch(pitch); t.setSpeechRate(rate) }
                val m = GenerativeModel(modelName, apiKey)
                val r = m.generateContent("Eres JARVIS de Tony Stark, hablas con Oskar en Vitoria, respuestas cortas holográficas en español: $p")
                val ans = r.text?: "Sin datos"
                responseText = ans; history = history + ChatMsg("JARVIS", ans); activity.speak(ans)
            }catch(e:Exception){ responseText="ERROR: ${e.message}" }
            isLoading = false; inputText = ""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ r-> val s=r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0); if(!s.isNullOrEmpty()) sendAuto(s) }
    fun startWake(){ if(!SpeechRecognizer.isRecognitionAvailable(context)) return; speechRecognizer?.destroy(); val rec=SpeechRecognizer.createSpeechRecognizer(context); speechRecognizer=rec; rec.setRecognitionListener(object: RecognitionListener{
        override fun onReadyForSpeech(p: Bundle?){ isListeningWake=true }; override fun onBeginningOfSpeech(){}
        override fun onRmsChanged(r: Float){}; override fun onBufferReceived(b: ByteArray?){}
        override fun onEndOfSpeech(){ isListeningWake=false; if(heyEnabled) startWake() }
        override fun onError(e: Int){ isListeningWake=false; if(heyEnabled) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({startWake()},800) }
        override fun onResults(res: Bundle?){ val txt=res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")){ activity.speak("Dime Oskar"); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }else if(heyEnabled) startWake(); isListeningWake=false }
        override fun onPartialResults(p: Bundle?){ val txt=p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?:""; if(txt.contains("jarvis")) rec.stopListening() }
        override fun onEvent(t:Int,p:Bundle?){}
    }); val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ g-> if(g&&heyEnabled) startWake() }
    LaunchedEffect(heyEnabled){ if(heyEnabled&&ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startWake() else if(heyEnabled) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit){ onDispose{ speechRecognizer?.destroy() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF010A18), Color(0xFF00182E), Color(0xFF000B14))))){
        // Grid de fondo
        Canvas(Modifier.fillMaxSize()){ val step=60f; for(x in 0..size.width.toInt() step step.toInt()){ drawLine(Color(0xFF00FFFF).copy(alpha=0.05f), Offset(x.toFloat(),0f), Offset(x.toFloat(), size.height), 1f) } for(y in 0..size.height.toInt() step step.toInt()){ drawLine(Color(0xFF00FFFF).copy(alpha=0.05f), Offset(0f,y.toFloat()), Offset(size.width, y.toFloat()), 1f) } }
        Column(Modifier.fillMaxSize().padding(12.dp)){
            // HUD Superior
            Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFFF).copy(alpha=0.3f), RoundedCornerShape(8.dp)).background(Color(0xFF001E38).copy(alpha=0.6f), RoundedCornerShape(8.dp)).padding(10.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                Column{ Text("STARK OS v9.0", color=Color(0xFF00FFFF), fontSize=10.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold); Text("MARK IX", color=Color.White, fontSize=14.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace) }
                Column(horizontalAlignment=Alignment.End){ Text(timeNow.value, color=Color(0xFF00FF88), fontFamily=FontFamily.Monospace, fontSize=14.sp); Text(if(isListeningWake) "● ESCUCHANDO" else "○ STANDBY", color=if(isListeningWake) Color(0xFF00FF88) else Color.Gray, fontSize=10.sp, fontFamily=FontFamily.Monospace) }
                Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, Color(0xFF00FFFF), CircleShape).clickable{showSettings=true}.background(Color(0xFF002244)), contentAlignment=Alignment.Center){ Text("⚙", color=Color.Cyan) }
            }
            Spacer(Modifier.height(12.dp))
            // Reactor centrado
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){ ReactorV9(isLoading, isListeningWake) }
            // Panel respuesta
            Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFFF).copy(alpha=0.4f), RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF002244).copy(alpha=0.8f), Color(0xFF001122).copy(alpha=0.9f))), shape=RoundedCornerShape(12.dp)).padding(12.dp)){
                Column{
                    Row{ Box(Modifier.size(6.dp).clip(CircleShape).background(if(isListeningWake) Color(0xFF00FF88) else Color(0xFF00FFFF)).align(Alignment.CenterVertically)); Spacer(Modifier.width(6.dp)); Text("JARVIS OUTPUT", color=Color(0xFF00FFFF), fontSize=9.sp, fontFamily=FontFamily.Monospace) }
                    Spacer(Modifier.height(6.dp))
                    Text(responseText, color=Color(0xFFE0FFFF), fontSize=13.sp, fontFamily=FontFamily.Monospace, lineHeight=16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Logs
            LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Color(0xFF00FFFF).copy(alpha=0.15f), RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.4f), RoundedCornerShape(8.dp)).padding(8.dp), reverseLayout=true){
                items(history.reversed()){ msg->
                    Row(Modifier.padding(vertical=2.dp)){
                        Text(if(msg.role=="OSKAR") ">> USR: " else ">> JARVIS: ", color=if(msg.role=="OSKAR") Color(0xFF88FF88) else Color(0xFF00FFFF), fontSize=10.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)
                        Text(msg.text.take(120), color=Color.White.copy(alpha=0.8f), fontSize=11.sp, fontFamily=FontFamily.Monospace)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Input futurista
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.weight(1f).border(1.dp, Color(0xFF00FFFF), RoundedCornerShape(20.dp)).background(Color(0xFF001A33).copy(alpha=0.7f), RoundedCornerShape(20.dp)).padding(horizontal=12.dp, vertical=2.dp)){
                    TextField(value=inputText, onValueChange={inputText=it}, placeholder={Text("COMANDO...", color=Color.Gray, fontFamily=FontFamily.Monospace, fontSize=11.sp)}, colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent, unfocusedContainerColor=Color.Transparent, focusedTextColor=Color.White, unfocusedTextColor=Color.White, focusedIndicatorColor=Color.Transparent, unfocusedIndicatorColor=Color.Transparent), modifier=Modifier.fillMaxWidth(), singleLine=true)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF00FFFF), Color(0xFF0066AA))).clickable{ val it=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }, contentAlignment=Alignment.Center){ Text("🎙", fontSize=18.sp) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF00FFFF), Color(0xFF004466))).clickable{sendAuto(inputText)}, contentAlignment=Alignment.Center){ Text("➤", color=Color.Black, fontWeight=FontWeight.Bold) }
            }
        }
        if(showSettings){
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.85f)).clickable{showSettings=false}, contentAlignment=Alignment.Center){
                Card(Modifier.fillMaxWidth(0.9f), colors=CardDefaults.cardColors(containerColor=Color(0xFF001E38)), shape=RoundedCornerShape(16.dp)){ Column(Modifier.padding(16.dp)){ Text("SISTEMA V9", color=Color.Cyan, fontFamily=FontFamily.Monospace); Spacer(Modifier.height(10.dp)); Text("Interfaz holográfica + Clima mundial + Acciones reales + Hey Jarvis 24/7", color=Color.Gray, fontSize=12.sp); Spacer(Modifier.height(10.dp)); Button(onClick={showSettings=false}, modifier=Modifier.fillMaxWidth()){ Text("CERRAR") } } }
            }
        }
    }
}
