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
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { JarvisV10() }
    }
    override fun onInit(status: Int) { if(status==TextToSpeech.SUCCESS) tts?.language = Locale("es","ES") }
    fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null) }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
data class ChatMsg(val role: String, val text: String)

@Composable
fun ReactorV10(isListening: Boolean, isLoading: Boolean) {
    val inf = rememberInfiniteTransition(label="r")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if(isLoading) 800 else 3000, easing=LinearEasing)), label="r1")
    val rot2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(if(isLoading) 1200 else 5000, easing=LinearEasing)), label="r2")
    val pulse by inf.animateFloat(0.9f, 1.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label="p")
    Canvas(Modifier.size(210.dp)) {
        val c = center; val base = size.minDimension/2.2f
        val col = when { isListening -> Color(0xFF00FF88); isLoading -> Color(0xFFFFAA00); else -> Color(0xFF00E5FF) }
        drawCircle(col.copy(alpha=0.2f), radius=base*pulse*1.25f, center=c)
        drawCircle(Color(0xFF0A2A4A), radius=base, center=c, style=Stroke(10f))
        drawCircle(col, radius=base, center=c, style=Stroke(2.5f))
        rotate(rot) { for(i in 0..3){ rotate(i*90f){ drawArc(col, 15f, 40f, false, topLeft=Offset(c.x-base*0.7f, c.y-base*0.7f), size=Size(base*1.4f, base*1.4f), style=Stroke(3f)) } } }
        rotate(rot2) { drawCircle(Color.White.copy(alpha=0.15f), radius=base*0.6f, center=c, style=Stroke(1f)) }
        drawCircle(Color(0xFF001E38), radius=base*0.45f, center=c)
        drawCircle(Color.White, radius=base*0.2f*pulse, center=c)
    }
}

suspend fun getWeatherV10(city: String): String = withContext(Dispatchers.IO) {
    try {
        val clean = city.replace(" ","%20")
        val geo = JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?name=$clean&count=1&language=es").readText())
        if(!geo.has("results")) return@withContext "No encuentro $city"
        val f = geo.getJSONArray("results").getJSONObject(0)
        val lat = f.getDouble("latitude"); val lon = f.getDouble("longitude"); val name = f.getString("name"); val country = f.optString("country","")
        val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true").readText()).getJSONObject("current_weather")
        val temp = w.getDouble("temperature"); val wind = w.getDouble("windspeed")
        "$name $country: ${temp}°C viento ${wind}km/h"
    } catch(e: Exception) { "Error clima $city" }
}

@Composable
fun JarvisV10() {
    val context = LocalContext.current; val activity = context as MainActivity
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("STARK OS V10 - CEREBRO ONLINE\nHey Jarvis activo\nPrueba: tiempo en Tokyo") }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var listening by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var heyOn by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var clock by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    LaunchedEffect(Unit) { while(true){ clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(1000) } }
    LaunchedEffect(Unit) { try{ val i = Intent(context, JarvisService::class.java); if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i) }catch(e: Exception){} }

    fun getApiKey(): String { return try { BuildConfig.GEMINI_API_KEY } catch(e: Exception) { "" } }

    fun send(p: String) {
        if(p.isBlank()) return
        history = history + ChatMsg("TU", p)
        val lower = p.lowercase()
        // ACCIONES REALES
        try{
            if(lower.contains("linterna")) {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val on = lower.contains("enciende") || lower.contains("on") || lower.contains("prende")
                cm.setTorchMode(cm.cameraIdList[0], on)
                val msg = if(on) "Linterna ON" else "Linterna OFF"
                output = ">>> $msg"; activity.speak(msg); input=""; return
            }
            if(lower.contains("youtube")) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))); output=">>> Abriendo YouTube"; activity.speak("YouTube"); input=""; return }
            if(lower.contains("whatsapp")) { context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.let{ context.startActivity(it) }; output=">>> Abriendo WhatsApp"; input=""; return }
        }catch(e: Exception){}

        // CLIMA
        if(lower.contains("tiempo") || lower.contains("clima")) {
            var city = "Vitoria-Gasteiz"
            if(lower.contains(" en ")) city = lower.substringAfter(" en ").replace("?","").replace(".","").trim()
            loading = true
            scope.launch{ val res = getWeatherV10(city); output=">>> CLIMA: $res"; history=history+ChatMsg("JARVIS", res); activity.speak(res); loading=false; input="" }
            return
        }

        // IA GEMINI
        val key = getApiKey()
        if(key.isBlank()){
            output = "Falta API KEY: añade GEMINI_API_KEY en Secrets del repo y en build.gradle"
            activity.speak("Falta clave API, añádela en Secrets")
            input=""; return
        }
        loading = true
        scope.launch{
            try{
                val model = GenerativeModel("gemini-1.5-flash", key)
                val r = model.generateContent("Eres JARVIS de Oskar de Vitoria, español corto, Stark style: $p")
                val ans = r.text ?: "Sin respuesta"
                output = ans; history = history + ChatMsg("JARVIS", ans); activity.speak(ans)
            }catch(e: Exception){ output = "Error IA: ${e.message}"; activity.speak("Error de conexión") }
            loading = false; input=""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res -> val txt = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0); if(!txt.isNullOrEmpty()) send(txt) }
    fun startWake() {
        if(!SpeechRecognizer.isRecognitionAvailable(context)) return
        recognizer?.destroy(); val rec = SpeechRecognizer.createSpeechRecognizer(context); recognizer = rec
        rec.setRecognitionListener(object: RecognitionListener{
            override fun onReadyForSpeech(p: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false; if(heyOn) startWake() }
            override fun onError(e: Int) { listening = false; if(heyOn) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({startWake()}, 800) }
            override fun onResults(b: Bundle?) {
                val txt = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                if(txt.contains("jarvis")){ activity.speak("Dime Oskar"); val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) } else if(heyOn) startWake(); listening = false
            }
            override fun onPartialResults(p: Bundle?) { val txt = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""; if(txt.contains("jarvis")) rec.stopListening() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if(g && heyOn) startWake() }
    LaunchedEffect(heyOn) { if(heyOn && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWake() else if(heyOn) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF010A18), Color(0xFF021E3A)))).padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF001E38).copy(alpha=0.9f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.3f), RoundedCornerShape(10.dp)).padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("STARK OS V10", color=Color(0xFF00FFFF), fontSize=12.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)
                Text(clock, color=Color(0xFF00FF88), fontFamily=FontFamily.Monospace)
                Text(if(listening) "● HEY" else if(loading) "● IA" else "○", color=if(listening) Color(0xFF00FF88) else if(loading) Color(0xFFFFAA00) else Color.Gray, fontFamily=FontFamily.Monospace)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){ ReactorV10(listening, loading) }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001A33).copy(alpha=0.95f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(12.dp)){ Text(output, color=Color(0xFFE0FFFF), fontSize=13.sp, fontFamily=FontFamily.Monospace) }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.45f)).padding(8.dp), reverseLayout=true){ items(history.reversed()){ m -> Text("${m.role}: ${m.text}", color=if(m.role=="TU") Color(0xFF88FF88) else Color(0xFF00FFFF), fontSize=11.sp, fontFamily=FontFamily.Monospace, modifier=Modifier.padding(vertical=2.dp)) } }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
                OutlinedTextField(value=input, onValueChange={input=it}, modifier=Modifier.weight(1f), placeholder={Text("Habla con JARVIS...", fontSize=11.sp, fontFamily=FontFamily.Monospace)}, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White, focusedBorderColor=Color(0xFF00FFFF), unfocusedBorderColor=Color.Gray))
                Button(onClick={ val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }, shape=CircleShape, modifier=Modifier.size(48.dp), contentPadding=PaddingValues(0.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6C5CE7))){ Text("🎙") }
                Button(onClick={send(input)}, shape=CircleShape, modifier=Modifier.size(48.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00FFFF)), contentPadding=PaddingValues(0.dp)){ Text("➤", color=Color.Black, fontWeight=FontWeight.Bold) }
            }
        }
    }
}
