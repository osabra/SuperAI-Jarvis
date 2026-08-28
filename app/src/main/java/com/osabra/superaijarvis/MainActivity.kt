package com.osabra.superaijarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
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
import java.text.SimpleDateFormat
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
fun ParticlesBg() {
    var particles by remember { mutableStateOf(List(40){ Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()*0.002f-0.001f, Random.nextFloat()*0.002f-0.001f) }) }
    val inf = rememberInfiniteTransition(label="p")
    val tick by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(50, easing=LinearEasing)), label="tick")
    LaunchedEffect(tick){ particles = particles.map{ it.copy(x=(it.x+it.vx).let{if(it>1)0f else if(it<0)1f else it}, y=(it.y+it.vy).let{if(it>1)0f else if(it<0)1f else it}) } }
    Canvas(Modifier.fillMaxSize()){
        particles.forEach{ p ->
            drawCircle(Color(0xFF00FFFF).copy(alpha=0.15f), radius=2f, center=Offset(p.x*size.width, p.y*size.height))
            particles.forEach{ q -> val d = kotlin.math.hypot((p.x-q.x)*size.width, (p.y-q.y)*size.height); if(d<120f) drawLine(Color(0xFF00FFFF).copy(alpha=0.05f*(1-d/120f)), Offset(p.x*size.width, p.y*size.height), Offset(q.x*size.width, q.y*size.height), 0.5f) }
        }
    }
}


@Composable
fun ReactorV11(isListening: Boolean, isLoading: Boolean, rms: Float) {
    val inf = rememberInfiniteTransition(label="reactorBRUTAL")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label="r1")
    val rot2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label="r2")
    val pulse by inf.animateFloat(0.95f, 1.18f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val plasmaRot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label="plasma")
    var smoothRms by remember { mutableStateOf(0f) }
    LaunchedEffect(rms){ smoothRms = (smoothRms*0.85f + rms*0.15f).coerceIn(0f, 8f) }

    Canvas(Modifier.size(260.dp)) {
        val c = center
        val base = size.minDimension/2.25f
        val col = when { isListening -> Color(0xFF00FF88); isLoading -> Color(0xFFFFAA00); else -> Color(0xFF00E5FF) }
        val p = pulse + smoothRms*0.025f

        // 1. SOMBRA VOLUMÉTRICA PROFUNDA
        drawCircle(Color.Black.copy(alpha=0.8f), radius=base*1.45f, center=Offset(c.x+16f, c.y+16f))

        // 2. CHASIS EXTERIOR INDUSTRIAL (como la foto - metal oscuro con tornillos)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF4A5A6A), Color(0xFF2A3A4A), Color(0xFF101820)), center=c, radius=base*1.45f), radius=base*1.45f, center=c)
        // Anillo exterior biselado con desgaste
        drawCircle(Color(0xFF0A0F18), radius=base*1.38f, center=c, style=Stroke(22f))
        drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha=0.35f), Color.Transparent, Color.Black.copy(alpha=0.5f))), radius=base*1.38f, center=c, style=Stroke(2.5f))

        // 3. ANILLOS TÉCNICOS GIRATORIOS (como en la foto con HUDs)
        rotate(rot) {
            for(i in 0..7){
                rotate(i*45f){
                    // Segmentos técnicos con glow verde
                    drawArc(col.copy(alpha=0.25f), 8f, 18f, false, Offset(c.x-base*1.2f, c.y-base*1.2f), Size(base*2.4f, base*2.4f), style=Stroke(2.5f))
                    drawArc(Color(0xFF1A2A3A), 10f, 14f, false, Offset(c.x-base*1.2f, c.y-base*1.2f), Size(base*2.4f, base*2.4f), style=Stroke(6f))
                }
            }
        }

        // 4. HALO DE ENERGÍA VOLUMÉTRICO
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha=0.35f), col.copy(alpha=0.1f), Color.Transparent), center=c, radius=base*1.7f), radius=base*1.7f*p, center=c)

        // 5. ANILLO PRINCIPAL INTERIOR PROFUNDO
        drawCircle(Color(0xFF00101E), radius=base*0.98f, center=c, style=Stroke(16f))
        drawCircle(col.copy(alpha=0.9f), radius=base*0.98f, center=c, style=Stroke(1.8f))
        // Brillo bisel superior
        drawArc(Brush.linearGradient(listOf(Color.White.copy(alpha=0.5f), Color.Transparent)), 220f, 80f, false, Offset(c.x-base*0.98f, c.y-base*0.98f), Size(base*1.96f, base*1.96f), style=Stroke(3f))

        // 6. SEGUNDO ANILLO INTERIOR CONTRAROTATORIO
        rotate(rot2){
            drawCircle(Color(0xFF0A2A3A), radius=base*0.78f, center=c, style=Stroke(10f))
            drawCircle(col.copy(alpha=0.4f), radius=base*0.78f, center=c, style=Stroke(1f))
            for(i in 0..3){ rotate(i*90f){ drawCircle(col.copy(alpha=0.6f), radius=3f, center=Offset(c.x, c.y-base*0.78f)) } }
        }

        // 7. CRISTAL HEXAGONAL FACETADO (IGUAL QUE LA FOTO)
        rotate(plasmaRot*0.3f){
            val hexPath = androidx.compose.ui.graphics.Path().apply{
                for(i in 0..5){
                    val ang = Math.toRadians((i*60).toDouble())
                    val r = base*0.68f*p
                    val x = c.x + kotlin.math.cos(ang)*r
                    val y = c.y + kotlin.math.sin(ang)*r
                    if(i==0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
            // Sombra cristal
            val shadowPath = androidx.compose.ui.graphics.Path().apply{
                for(i in 0..5){
                    val ang = Math.toRadians((i*60).toDouble())
                    val r = base*0.68f*p
                    val x = c.x+3 + kotlin.math.cos(ang)*r
                    val y = c.y+3 + kotlin.math.sin(ang)*r
                    if(i==0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
            drawPath(shadowPath, Color.Black.copy(alpha=0.5f))
            // Cristal con gradiente facetado
            drawPath(hexPath, Brush.linearGradient(listOf(Color.White.copy(alpha=0.25f), Color(0xFF88FFE5).copy(alpha=0.3f), col.copy(alpha=0.15f)), start=Offset(c.x-base*0.5f, c.y-base*0.5f), end=Offset(c.x+base*0.5f, c.y+base*0.5f)))
            drawPath(hexPath, Color.White.copy(alpha=0.35f), style=Stroke(2.2f))
            // Facetas interiores
            for(i in 0..5){
                val ang = Math.toRadians((i*60).toDouble())
                val x = c.x + kotlin.math.cos(ang)*base*0.68f*p
                val y = c.y + kotlin.math.sin(ang)*base*0.68f*p
                drawLine(Color.White.copy(alpha=0.15f), c, Offset(x.toFloat(), y.toFloat()), 1f)
            }
        }

        // 8. PLASMA INTERIOR BRUTAL (núcleo de energía como la foto)
        rotate(plasmaRot){
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF88FFE5), Color(0xFF00E5FF), Color(0xFF00FF88), Color(0xFF004422)), center=Offset(c.x-8f, c.y-8f), radius=base*0.55f), radius=base*0.55f*p, center=c)
            // Remolinos de plasma
            for(i in 0..2){
                rotate(i*120f){
                    drawArc(Color.White.copy(alpha=0.4f), 0f, 90f, false, Offset(c.x-base*0.35f, c.y-base*0.35f), Size(base*0.7f, base*0.7f), style=Stroke(1.5f))
                }
            }
        }

        // 9. NÚCLEO CENTRAL HIPER BRILLANTE
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha=0.8f), Color.Transparent), center=c, radius=base*0.12f), radius=base*0.12f*p, center=c)
        
        // 10. REFLEJO ESPECULAR CRISTAL (efecto vidrio 3D de la foto)
        drawCircle(Color.White.copy(alpha=0.95f), radius=base*0.11f, center=Offset(c.x-base*0.18f, c.y-base*0.2f))
        drawCircle(Color.White.copy(alpha=0.5f), radius=base*0.05f, center=Offset(c.x-base*0.15f, c.y-base*0.18f))

        // 11. ANILLO ENERGÍA AL HABLAR
        if(isListening){
            drawCircle(col.copy(alpha=0.2f + smoothRms*0.08f), radius=base*0.88f, center=c, style=Stroke(2.5f + smoothRms*1.2f))
        }
    }
}


suspend fun getWeatherV11(city: String): String = withContext(Dispatchers.IO) {
    try {
        val clean = city.replace(" ","%20")
        val geo = JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?name=$clean&count=1&language=es").readText())
        if(!geo.has("results")) return@withContext "No encuentro $city"
        val f = geo.getJSONArray("results").getJSONObject(0)
        val lat = f.getDouble("latitude"); val lon = f.getDouble("longitude"); val name = f.getString("name"); val country = f.optString("country","")
        val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true").readText()).getJSONObject("current_weather")
        val temp = w.getDouble("temperature")
        "$name $country: ${temp}°C"
    } catch(e: Exception) { "Error clima $city" }
}

@Composable
fun JarvisV11() {
    val context = LocalContext.current; val activity = context as MainActivity
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("STARK OS V11.7 BRUTAL FIX GOD MODE\nVoz robot + Partículas + Control total\nDi: pon alarma 7:30") }
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var listening by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var heyOn by remember { mutableStateOf(true) }
    var rmsLevel by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var clock by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }

    LaunchedEffect(Unit) { while(true){ clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()); delay(1000) } }
    LaunchedEffect(Unit) { try{ val i = Intent(context, JarvisService::class.java); if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i) }catch(e: Exception){} }

    fun getApiKey(): String { return try { BuildConfig.GEMINI_API_KEY } catch(e: Exception) { "" } }

    fun handleControl(p: String): Boolean {
        val lower = p.lowercase()
        try{
            if(false && lower.contains("alarma")) {
                val regex = Regex("(\\d{1,2})[:h](\\d{2})?")
                val m = regex.find(lower)
                val h = m?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val mm = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply{ putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, mm); putExtra(AlarmClock.EXTRA_MESSAGE, "Alarma JARVIS") }
                context.startActivity(intent); output=">>> Alarma $h:${mm.toString().padStart(2,'0')} puesta"; activity.speak("Alarma puesta a las $h $mm"); return true
            }
            if(lower.contains("brillo")) {
                val v = Regex("(\\d{1,3})").find(lower)?.value?.toIntOrNull()
                if(v!=null && Settings.System.canWrite(context)){ Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(10,255)); output=">>> Brillo $v"; activity.speak("Brillo al $v por ciento"); return true }
                else { val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")); context.startActivity(i); output=">>> Dale permiso de brillo"; return true }
            }
            if(lower.contains("llama")) {
                val name = lower.substringAfter("llama").trim()
                output=">>> Llamando a $name - di el número"; activity.speak("Llamando a $name")
                // para demo llama a un numero si detecta digitos
                val num = Regex("\\+?\\d{9,}").find(lower)?.value
                if(num!=null){ val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")); context.startActivity(i) }
                return true
            }
            if(lower.contains("linterna")) {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val on = lower.contains("enciende") || lower.contains("on") || lower.contains("prende")
                cm.setTorchMode(cm.cameraIdList[0], on); val msg = if(on) "Linterna ON" else "Linterna OFF"; output=">>> $msg"; activity.speak(msg); return true
            }
            if(lower.contains("youtube")) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))); output=">>> YouTube"; return true }
        }catch(e: Exception){ output="Error control: ${e.message}" }
        return false
    }

    fun send(p: String) {
        if(p.isBlank()) return
        history = history + ChatMsg("TU", p)
        if(handleControl(p)){ input=""; return }
        if(p.lowercase().contains("tiempo") || p.lowercase().contains("clima")) {
            var city = "Vitoria-Gasteiz"
            if(p.lowercase().contains(" en ")) city = p.lowercase().substringAfter(" en ").replace("?","").trim()
            loading = true; scope.launch{ val res = getWeatherV11(city); output=">>> CLIMA: $res"; history=history+ChatMsg("JARVIS", res); activity.speak(res); loading=false; input="" }; return
        }

        val key = getApiKey()
        if(key.isBlank()){ output="Falta API KEY"; activity.speak("Falta clave"); input=""; return }
        loading=true
        scope.launch{
            var success = false
            val modelsToTry = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-flash-latest", "gemini-1.5-flash", "gemini-pro", "gemini-1.5-flash")
            for(modelName in modelsToTry){
                try{
                    val model = GenerativeModel(modelName, key)
                    val r = model.generateContent("Eres JARVIS de Tony Stark, voz grave robot, español corto, hablas con Oskar: $p")
                    val ans = r.text ?: "Sin datos"
                    output=ans; history=history+ChatMsg("JARVIS [$modelName]", ans); activity.speak(ans)
                    success = true
                    break
                }catch(e: Exception){
                    val msg = e.message ?: ""
                    if(modelName == modelsToTry.last()){
                        output="Error IA final: $msg"; activity.speak("Error IA")
                    }
                    continue
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
            override fun onError(e: Int) { listening = false; rmsLevel=0f; if(heyOn) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({startWake()}, 700) }
            override fun onResults(b: Bundle?) {
                val txt = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                if(txt.contains("jarvis")){ activity.speak("Dime"); val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) } else if(heyOn) startWake(); listening = false; rmsLevel=0f
            }
            override fun onPartialResults(p: Bundle?) { val txt = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""; if(txt.contains("jarvis")) rec.stopListening() }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }; rec.startListening(it)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if(g && heyOn) startWake() }
    LaunchedEffect(heyOn) { if(heyOn && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWake() else if(heyOn) permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF010A18), Color(0xFF021E3A))))){
        ParticlesBg()
        Column(Modifier.fillMaxSize().padding(12.dp)){
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001E38).copy(alpha=0.9f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.4f), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                Text("STARK OS V11.7 BRUTAL FIX GOD", color=Color(0xFF00FFFF), fontSize=12.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)
                Text(clock, color=Color(0xFF00FF88), fontFamily=FontFamily.Monospace)
                Text(if(listening) "● HEY" else if(loading) "● IA" else "○", color=if(listening) Color(0xFF00FF88) else if(loading) Color(0xFFFFAA00) else Color.Gray, fontFamily=FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){ ReactorV11(listening, loading, rmsLevel) }
            Spacer(Modifier.height(8.dp))
            // Visualizador voz
            Canvas(Modifier.fillMaxWidth().height(24.dp)){ val mid=size.height/2; for(i in 0..40){ val h = if(listening) Random.nextFloat()*rmsLevel*2f + 2f else 2f; drawLine(if(listening) Color(0xFF00FF88) else Color(0xFF00FFFF).copy(alpha=0.3f), Offset(i*size.width/40, mid-h), Offset(i*size.width/40, mid+h), 2.5f) } }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF001A33).copy(alpha=0.95f)).border(1.dp, Color(0xFF00FFFF).copy(alpha=0.5f), RoundedCornerShape(12.dp)).padding(12.dp)){ Text(output, color=Color(0xFFE0FFFF), fontSize=13.sp, fontFamily=FontFamily.Monospace) }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.5f)).padding(8.dp), reverseLayout=true){ items(history.reversed()){ m -> Text("${m.role}: ${m.text}", color=if(m.role=="TU") Color(0xFF88FF88) else Color(0xFF00FFFF), fontSize=11.sp, fontFamily=FontFamily.Monospace, modifier=Modifier.padding(vertical=2.dp)) } }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
                OutlinedTextField(value=input, onValueChange={input=it}, modifier=Modifier.weight(1f), placeholder={Text("Hey Jarvis, pon alarma 7:30...", fontSize=11.sp, fontFamily=FontFamily.Monospace)}, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White, focusedBorderColor=Color(0xFF00FFFF)))
                Button(onClick={ val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{ putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") }; speechLauncher.launch(it) }, shape=CircleShape, modifier=Modifier.size(48.dp), contentPadding=PaddingValues(0.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF6C5CE7))){ Text("🎙") }
                Button(onClick={send(input)}, shape=CircleShape, modifier=Modifier.size(48.dp), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF00FFFF)), contentPadding=PaddingValues(0.dp)){ Text("➤", color=Color.Black, fontWeight=FontWeight.Bold) }
            }
        }
    }
}
