package com.osabra.superaijarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.util.*
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); tts = TextToSpeech(this, this); setContent { JarvisV11() } }
    override fun onInit(status: Int) { if(status==TextToSpeech.SUCCESS){ tts?.language = Locale("es","ES"); tts?.setPitch(0.65f); tts?.setSpeechRate(0.92f) } }
    fun speak(t: String) { tts?.stop(); tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
data class ChatMsg(val role: String, val text: String)
data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float)

@Composable
fun ReactorV11(isListening: Boolean, isLoading: Boolean, rms: Float) {
    val inf = rememberInfiniteTransition(label="reactor")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if(isLoading) 900 else 3500, easing = LinearEasing)), label="r1")
    val pulse by inf.animateFloat(1f, if(isListening) 1.08f else 1.03f, infiniteRepeatable(tween(if(isListening) 500 else 2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="p")
    var smoothRms by remember { mutableStateOf(0f) }
    LaunchedEffect(rms){ smoothRms = (smoothRms*0.8f + rms*0.2f).coerceIn(0f,5f) }
    Canvas(Modifier.size(200.dp)) {
        val c = center; val base = size.minDimension/2.2f
        val col = when { isListening -> Color(0xFF00FF88); isLoading -> Color(0xFFFFAA00); else -> Color(0xFF00E5FF) }
        val p = pulse + (if(isListening) smoothRms*0.02f else 0f)
        drawCircle(col.copy(alpha=if(isListening) 0.15f else 0.06f), radius=base*p*1.25f, center=c)
        drawCircle(Color(0xFF0A2A4A), radius=base, center=c, style=Stroke(10f))
        drawCircle(col.copy(alpha=0.9f), radius=base, center=c, style=Stroke(2.5f))
        rotate(rot) { for(i in 0..3){ rotate(i*90f){ drawArc(col.copy(alpha=0.9f), 15f, 30f, false, topLeft=Offset(c.x-base*0.7f, c.y-base*0.7f), size=Size(base*1.4f, base*1.4f), style=Stroke(3f)) } } }
        drawCircle(Color(0xFF001E38), radius=base*0.45f, center=c); drawCircle(Color.White, radius=base*0.18f*p, center=c)
    }
}

suspend fun getWeatherV11(city: String): String = withContext(Dispatchers.IO) {
    try {
        val clean = city.replace(" ","%20")
        val geo = JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?name=$clean&count=1&language=es").readText())
        if(!geo.has("results")) return@withContext "No encuentro $city"
        val f = geo.getJSONArray("results").getJSONObject(0)
        val lat = f.getDouble("latitude"); val lon = f.getDouble("longitude"); val name = f.getString("name")
        val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true").readText())
        val cur = w.getJSONObject("current_weather")
        "En $name: ${cur.getDouble("temperature")}°C, viento ${cur.getDouble("windspeed")} km/h"
    } catch(e: Exception){ "Error clima: ${e.message}" }
}

@Composable
fun JarvisV11(){
    val context = LocalContext.current
    val activity = context as MainActivity
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("STARK OS V11.6 GOD - FECHA EXACTA - SIN ALARMA") }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var listening by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableStateOf(0f) }
    var heyOn by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf("--:--") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit){ while(true){ clock = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(1000) } }
    fun getApiKey(): String { return try { BuildConfig.GEMINI_API_KEY } catch(e: Exception) { "" } }

    fun toggleFlash(c: Context){
        try{ val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager; val id = cm.cameraIdList[0]; cm.setTorchMode(id, true); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({cm.setTorchMode(id,false)}, 3000) }catch(_:Exception){}
    }

    fun send(p: String){
        if(p.isBlank()) return
        coroutineScope.launch {
        // === FECHA EXACTA LOCAL V11.6 GOD SIN ALARMA ===
        val lc = p.lowercase()
        if(lc.contains("qué día") || lc.contains("que dia") || lc.contains("fecha de hoy") || lc.contains("que fecha") || lc.contains("qué fecha") || lc.contains("dia de hoy") || lc.contains("qué día es hoy") || lc.contains("que dia es hoy")){
            val locale = java.util.Locale("es","ES")
            val hoy = java.time.LocalDate.now()
            val hora = java.time.LocalTime.now()
            val fmtDia = java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", locale)
            val fmtHora = java.time.format.DateTimeFormatter.ofPattern("HH:mm", locale)
            val resp = "Buenos días, señor Oskar. Hoy es ${hoy.format(fmtDia)}. Son las ${hora.format(fmtHora)} en Vitoria-Gasteiz. Sensores actualizados, condiciones óptimas."
            output = resp
            history = history + ChatMsg("JARVIS [local exacto]", resp)
            activity.speak(resp)
            loading = false
            input = ""
            return@launch
        }

            val key = getApiKey()
            if(key.isBlank()){ output="Falta API KEY en BuildConfig"; return@launch }
            loading=true; history=history+ChatMsg("TU", p)
            // Control local sin IA
            val lower = p.lowercase()
            if(lower.contains("linterna") || lower.contains("flash")){ toggleFlash(context); output="Linterna activada 3s"; history=history+ChatMsg("JARVIS","Linterna ON"); activity.speak("Linterna activada"); loading=false; input=""; return@launch }
            if(lower.contains("tiempo") || lower.contains("clima")){ val city = if(lower.contains("vitoria")) "Vitoria-Gasteiz" else "Vitoria-Gasteiz"; val w = getWeatherV11(city); output=w; history=history+ChatMsg("JARVIS",w); activity.speak(w); loading=false; input=""; return@launch }

            val modelsToTry = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-flash-latest")
            for(modelName in modelsToTry){
                try{
                    val model = GenerativeModel(modelName, key)
                    val r = model.generateContent("Eres JARVIS de Tony Stark, español, corto, hablas con Oskar: $p")
                    val ans = r.text?: "Sin datos"
                    output=ans; history=history+ChatMsg("JARVIS [$modelName]", ans); activity.speak(ans); break
                }catch(e: Exception){
                    if(modelName==modelsToTry.last()){
                        if(e.message?.contains("quota",true)==true || e.message?.contains("429")==true){
                            output="JARVIS [local]: Cuota temporal, ${java.time.LocalDate.now()} Vitoria"
                        } else output="Error IA: ${e.message}"
                    }
                }
            }
            loading=false; input=""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res -> val txt = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0); if(!txt.isNullOrEmpty()) send(txt) }
    fun startWake() {
        if(!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer?.destroy(); val rec = SpeechRecognizer.createSpeechRecognizer(context); recognizer = rec
        rec.setRecognitionListener(object: RecognitionListener{
            override fun onReadyForSpeech(p: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) { rmsLevel = r }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; rmsLevel=0f; if(heyOn) startWake() }
            override fun onError(e: Int) { listening = false; rmsLevel=0f; if(heyOn) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({startWake()}, 600) }
            override fun onResults(b: Bundle?) {
                val txt = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?: ""
                if(txt.contains("jarvis")){ activity.speak("Dime señor"); val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) } else if(heyOn) startWake(); listening = false; rmsLevel=0f
            }
            override fun onPartialResults(p: Bundle?) { val txt = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase()?: ""; if(txt.contains("jarvis")) rec.stopListening() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if(g && heyOn) startWake() }
    LaunchedEffect(heyOn) { if(heyOn && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWake() else if(heyOn) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF010A18), Color(0xFF021E3A))))){
        Column(Modifier.fillMaxSize().padding(12.dp)){
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001E38).copy(alpha=0.9f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween){
                Text("STARK OS V11.6 GOD SIN ALARMA", color=Color(0xFF00FFFF), fontSize=10.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)
                Text(clock, color=Color(0xFF00FF88), fontFamily=FontFamily.Monospace)
                Text(if(listening) "● HEY" else "○", color=if(listening) Color(0xFF00FF88) else Color.Gray, fontFamily=FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){ ReactorV11(listening, loading, rmsLevel) }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001A33).copy(alpha=0.95f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.5f), RoundedCornerShape(12.dp)).padding(12.dp)){ Text(output, color=Color(0xFFE0FFFF), fontSize=13.sp, fontFamily=FontFamily.Monospace) }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.5f)).padding(8.dp), reverseLayout=true){ items(history.reversed()){ m -> Text("${m.role}: ${m.text}", color=if(m.role=="TU") Color(0xFF88FF88) else Color(0xFF00FFFF), fontSize=11.sp, fontFamily=FontFamily.Monospace) } }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(value=input, onValueChange={input=it}, modifier=Modifier.weight(1f), placeholder={Text("Hey Jarvis, que dia es hoy...", fontSize=11.sp)}, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White, focusedBorderColor=Color(0xFF00FFFF)))
                Button(onClick={ val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }, shape=CircleShape, modifier=Modifier.size(48.dp), contentPadding=PaddingValues(0.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6C5CE7))){ Text("🎙") }
                Button(onClick={send(input)}, shape=CircleShape, modifier=Modifier.size(48.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00FFFF)), contentPadding=PaddingValues(0.dp)){ Text("➤", color=Color.Black) }
            }
        }
    }
}
