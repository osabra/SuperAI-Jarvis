package com.osabra.superaijarvis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
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
fun JarvisV11() {
    val context = LocalContext.current; val activity = context as MainActivity
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("STARK OS V11.12 PRO VERDE GOD MODE\nVoz robot + Partículas + Control total\nDi: pon alarma 7:30") }
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
            if(lower.contains("alarma")) {
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
                Text("STARK OS V11.12 PRO VERDE GOD", color=Color(0xFF00FFFF), fontSize=12.sp, fontFamily=FontFamily.Monospace, fontWeight=FontWeight.Bold)
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



@Composable
fun ReactorV11(isListening: Boolean, isLoading: Boolean, rms: Float) {
    // 3D REAL REACTOR BRUTAL - Filament Engine
    var modelLoaded by remember { mutableStateOf(false) }
    
    // Animación de rotación brutal
    val inf = rememberInfiniteTransition(label="brutal3D")
    val rotY by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing=LinearEasing)), label="rotY")
    val rotX by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(22000, easing=LinearEasing)), label="rotX")
    val pulse by inf.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(800, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    
    var smoothRms by remember { mutableStateOf(0f) }
    LaunchedEffect(rms){ smoothRms = (smoothRms*0.75f + rms*0.25f).coerceIn(0f, 10f) }

    Box(Modifier.size(360.dp).graphicsLayer {
        // Efecto 3D brutal con inclinación según escucha
        rotationY = rotY * 0.15f
        rotationX = rotX * 0.08f
        scaleX = pulse * (1f + smoothRms*0.03f)
        scaleY = pulse * (1f + smoothRms*0.03f)
    }, contentAlignment = Alignment.Center) {
        
        // Si tienes SceneView instalado, usa esto:
        // Scene(modifier=Modifier.fillMaxSize(), nodes=listOf(ModelNode(modelInstanceName="reactor_brutal_3d.glb", scaleToUnits=1.8f)))
        
        // FALLBACK BRUTAL CANVAS 3D (por si no tienes SceneView aún) - 4 anillos gyro reales
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val baseR = size.minDimension * 0.42f
            val listeningBoost = if(isListening) 1f + smoothRms*0.05f else 1f
            val col = if(isListening) Color(0xFF00FF88) else Color(0xFF00E5FF)
            val col2 = if(isListening) Color(0xFF00FFAA) else Color(0xFF00FFFF)

            // Glow externo
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha=0.35f*listeningBoost), col.copy(alpha=0.08f), Color.Transparent), center=c, radius=baseR*1.6f), radius=baseR*1.6f, center=c)

            // 4 ANILLOS 3D GYRO
            draw3DRing(c, baseR*0.98f, 20f, rotY, col, 0.95f, tiltX=0f)
            draw3DRing(c, baseR*0.84f, 17f, -rotY*0.8f, col2, 0.85f, tiltX=78f)
            draw3DRing(c, baseR*0.70f, 14f, rotX, Color(0xFF88FFCC), 0.75f, tiltX=35f, tiltY=45f)
            draw3DRing(c, baseR*0.54f, 11f, -rotX*1.3f, Color.White, 0.55f, tiltY=-30f)

            // Cubo cristal central
            val crystalSize = baseR*0.33f * pulse * listeningBoost
            drawHexCrystal(c, crystalSize, rotY*0.4f, col, pulse, isListening)

            // Plasma core
            val plasmaR = crystalSize*0.46f * pulse
            drawCircle(Brush.radialGradient(listOf(Color.White, col, col2.copy(alpha=0.6f), Color.Transparent), center=c, radius=plasmaR), radius=plasmaR, center=c)
            
            // Rayos energía
            for(i in 0..5){
                val ang = (rotY + i*60f) * PI/180f
                val len = crystalSize*1.9f
                val x1 = c.x + cos(ang).toFloat() * crystalSize*0.5f
                val y1 = c.y + sin(ang).toFloat() * crystalSize*0.5f
                val x2 = c.x + cos(ang).toFloat() * len
                val y2 = c.y + sin(ang).toFloat() * len
                drawLine(col.copy(alpha=0.28f + smoothRms*0.06f), Offset(x1,y1), Offset(x2,y2), strokeWidth=1.6f + smoothRms*0.4f, cap=StrokeCap.Round)
            }
        }
    }
}

// Helpers 3D
fun DrawScope.draw3DRing(center: Offset, radius: Float, stroke: Float, rotation: Float, color: Color, alpha: Float, tiltX: Float = 0f, tiltY: Float = 0f){
    val segments = 64
    for(i in 0 until segments){
        val a1 = (i*360f/segments + rotation) * PI/180f
        val a2 = ((i+1)*360f/segments + rotation) * PI/180f
        val yTilt = cos(Math.toRadians(tiltX.toDouble())).toFloat()
        val xTilt = cos(Math.toRadians(tiltY.toDouble())).toFloat()
        val x1 = center.x + cos(a1).toFloat()*radius*xTilt
        val y1 = center.y + sin(a1).toFloat()*radius*yTilt
        val x2 = center.x + cos(a2).toFloat()*radius*xTilt
        val y2 = center.y + sin(a2).toFloat()*radius*yTilt
        val depth = (sin(a1).toFloat()*0.5f + 0.5f)
        val a = alpha*(0.25f + depth*0.75f)
        if(a>0.08f){
            drawLine(color.copy(alpha=a), Offset(x1,y1), Offset(x2,y2), strokeWidth=stroke, cap=StrokeCap.Round)
            if(i % 14 == 0){
                drawCircle(color.copy(alpha=a*0.9f), radius=stroke*0.65f, center=Offset(x1,y1))
            }
        }
    }
}

fun DrawScope.drawHexCrystal(center: Offset, size: Float, rot: Float, color: Color, pulse: Float, isListening: Boolean){
    val points = 6
    val path = Path()
    for(i in 0 until points){
        val ang = (i*60f + rot) * PI/180f - PI/6
        val r = size * (if(i%2==0) 1f else 0.88f)
        val x = center.x + cos(ang).toFloat()*r
        val y = center.y + sin(ang).toFloat()*r*0.92f
        if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
    }
    path.close()
    drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha=0.92f), color.copy(alpha=0.62f), color.copy(alpha=0.32f)), start=Offset(center.x-size, center.y-size), end=Offset(center.x+size, center.y+size)))
    drawPath(path, color.copy(alpha=0.28f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.6f))
    val innerPath = Path()
    for(i in 0 until points){
        val ang = (i*60f + rot + 15f) * PI/180f - PI/6
        val r = size*0.56f*pulse
        val x = center.x + cos(ang).toFloat()*r
        val y = center.y + sin(ang).toFloat()*r*0.92f
        if(i==0) innerPath.moveTo(x,y) else innerPath.lineTo(x,y)
    }
    innerPath.close()
    drawPath(innerPath, Brush.radialGradient(listOf(Color.White, color.copy(alpha=0.52f), Color.Transparent), center=center, radius=size*0.52f))
}
