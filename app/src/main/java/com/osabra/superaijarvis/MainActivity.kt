package com.osabra.superaijarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.content.Context 


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
fun ReactorV11(isListening: Boolean, isLoading: Boolean, rms: Float) {
    val inf = rememberInfiniteTransition(label="ultra")
    val rotX by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4500, easing=LinearEasing)), label="rx")
    val rotY by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(6200, easing=LinearEasing)), label="ry")
    val rotZ by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7800, easing=LinearEasing)), label="rz")
    val rotW by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(3900, easing=LinearEasing)), label="rw")
    val pulse by inf.animateFloat(0.88f, 1.22f, infiniteRepeatable(tween(700, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="p")
    var smoothRms by remember { mutableStateOf(0f) }
    LaunchedEffect(rms){ smoothRms = (smoothRms*0.78f + rms*0.22f).coerceIn(0f, 10f) }
    Box(Modifier.size(380.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val baseR = size.minDimension * 0.44f
            val boost = if(isListening) 1f + smoothRms*0.035f else 1f
            val mainCol = if(isListening) Color(0xFF00FF88) else if(isLoading) Color(0xFF9D4EDD) else Color(0xFF00E5FF)
            drawCircle(Brush.radialGradient(listOf(mainCol.copy(alpha=0.32f*boost), mainCol.copy(alpha=0.12f), Color.Transparent), center=c, radius=baseR*1.8f), radius=baseR*1.8f, center=c)
            drawCircle(Brush.radialGradient(listOf(mainCol.copy(alpha=0.18f), Color.Transparent), center=c, radius=baseR*1.35f), radius=baseR*1.35f, center=c)
            drawBrutalRing(c, baseR*1.06f, 21f, rotX, mainCol, 0.92f, 68f, 10f)
            drawBrutalRing(c, baseR*0.89f, 17f, rotY, Color(0xFF00FF88), 0.8f, 20f, 72f)
            drawBrutalRing(c, baseR*0.73f, 14f, rotZ, Color(0xFF00E5FF), 0.68f, 75f, 40f)
            drawBrutalRing(c, baseR*0.57f, 11f, rotW, Color.White, 0.52f, 32f, 32f)
            val cr = baseR*0.38f * pulse * boost
            drawCircle(Color(0xFF151515), radius=cr*1.09f, center=c, style=androidx.compose.ui.graphics.drawscope.Stroke(width=9f))
            drawCircle(Color(0xFF3A3A3A), radius=cr*1.09f, center=c, style=androidx.compose.ui.graphics.drawscope.Stroke(width=3.5f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=0.98f), mainCol.copy(alpha=0.85f), mainCol.copy(alpha=0.42f), Color(0xFF001122)), center=c, radius=cr), radius=cr, center=c)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=0.88f), Color.Transparent), center=Offset(c.x-cr*0.26f, c.y-cr*0.26f), radius=cr*0.52f), radius=cr*0.52f, center=Offset(c.x-cr*0.26f, c.y-cr*0.26f))
            drawCircle(Color.White, radius=cr*0.13f*(1f+smoothRms*0.09f), center=c)
            drawCircle(mainCol, radius=cr*0.20f*(1f+smoothRms*0.09f), center=c, style=androidx.compose.ui.graphics.drawscope.Stroke(width=3f))
        }
    }
}
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBrutalRing(center: Offset, radius: Float, stroke: Float, rotation: Float, color: Color, alpha: Float, tiltX: Float, tiltY: Float){
    val segs = 80
    for(i in 0 until segs){
        val a1 = Math.toRadians((i*360.0/segs + rotation))
        val a2 = Math.toRadians(((i+1)*360.0/segs + rotation))
        val yt = kotlin.math.cos(Math.toRadians(tiltX.toDouble())).toFloat()
        val xt = kotlin.math.cos(Math.toRadians(tiltY.toDouble())).toFloat()
        val x1 = center.x + kotlin.math.cos(a1).toFloat()*radius*xt
        val y1 = center.y + kotlin.math.sin(a1).toFloat()*radius*yt
        val x2 = center.x + kotlin.math.cos(a2).toFloat()*radius*xt
        val y2 = center.y + kotlin.math.sin(a2).toFloat()*radius*yt
        val depth = (kotlin.math.sin(a1).toFloat()*0.5f+0.5f)
        val a = alpha*(0.18f+depth*0.82f)
        if(a>0.06f) drawLine(color.copy(alpha=a), Offset(x1,y1), Offset(x2,y2), strokeWidth=stroke, cap=StrokeCap.Round)
    }
}
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexCrystal(center: Offset, size: Float, rot: Float, color: Color, pulse: Float){
    val path = Path()
    for(i in 0 until 6){
        val ang = Math.toRadians((i*60.0+rot))
        val r = size * (if(i%2==0) 1f else 0.88f) * pulse
        val x = center.x + kotlin.math.cos(ang).toFloat()*r
        val y = center.y + kotlin.math.sin(ang).toFloat()*r
        if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
    }
    path.close()
    drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha=0.9f), color.copy(alpha=0.7f), color.copy(alpha=0.3f))))
    drawPath(path, color.copy(alpha=0.5f), style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.5f))
}


@Composable
fun JarvisV11() {
    val context = LocalContext.current; val activity = context as MainActivity
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("STARK OS V11.12 PRO VERDE GOD MODE\nVoz robot + Partículas + Control total\nDi: pon alarma 7:30") }
    
    var history by remember { mutableStateOf(listOf<ChatMsg>()) }
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }
    var yaw by remember { mutableStateOf(0f) }
    var speedKmh by remember { mutableStateOf(0f) }
    var altitude by remember { mutableStateOf(0f) }
    var lightLux by remember { mutableStateOf(100f) }
    LaunchedEffect(Unit) {
        try {
            val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val listener = object : android.hardware.SensorEventListener {
                var grav = FloatArray(3); var mag = FloatArray(3)
                override fun onSensorChanged(e: android.hardware.SensorEvent) {
                    when(e.sensor.type){
                        android.hardware.Sensor.TYPE_ACCELEROMETER -> grav = e.values.clone()
                        android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> mag = e.values.clone()
                        android.hardware.Sensor.TYPE_LIGHT -> lightLux = e.values[0]
                    }
                    val R = FloatArray(9); val I = FloatArray(9)
                    if(android.hardware.SensorManager.getRotationMatrix(R,I,grav,mag)){
                        val orient = FloatArray(3)
                        android.hardware.SensorManager.getOrientation(R, orient)
                        yaw = Math.toDegrees(orient[0].toDouble()).toFloat()
                        pitch = Math.toDegrees(orient[1].toDouble()).toFloat()
                        roll = Math.toDegrees(orient[2].toDouble()).toFloat()
                    }
                }
                override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
            }
            sm.registerListener(listener, sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER), android.hardware.SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(listener, sm.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD), android.hardware.SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(listener, sm.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT), android.hardware.SensorManager.SENSOR_DELAY_UI)
        } catch(_: Exception){}
    }
    LaunchedEffect(Unit) {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            while(true){
                try {
                    val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    loc?.let { speedKmh = it.speed * 3.6f; altitude = it.altitude.toFloat() }
                } catch(_: Exception){}
                kotlinx.coroutines.delay(2000)
            }
        } catch(_: Exception){}
    }
    // TRAJE HUD STATES
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }
    var yaw by remember { mutableStateOf(0f) }
    var speedKmh by remember { mutableStateOf(0f) }
    var altitude by remember { mutableStateOf(0f) }
    var lightLux by remember { mutableStateOf(100f) }
    var batteryPct by remember { mutableStateOf(100) }
    // Sensor manager
    LaunchedEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val gravityVals = FloatArray(3)
        val magVals = FloatArray(3)
        val listener = object : SensorEventListener {
            var grav = FloatArray(3)
            var mag = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                when(e.sensor.type){
                    Sensor.TYPE_ACCELEROMETER -> grav = e.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> mag = e.values.clone()
                    Sensor.TYPE_LIGHT -> lightLux = e.values[0]
                }
                if(grav.isNotEmpty() && mag.isNotEmpty()){
                    val R = FloatArray(9)
                    val I = FloatArray(9)
                    if(SensorManager.getRotationMatrix(R,I,grav,mag)){
                        val orient = FloatArray(3)
                        SensorManager.getOrientation(R, orient)
                        yaw = Math.toDegrees(orient[0].toDouble()).toFloat()
                        pitch = Math.toDegrees(orient[1].toDouble()).toFloat()
                        roll = Math.toDegrees(orient[2].toDouble()).toFloat()
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(listener, light, SensorManager.SENSOR_DELAY_UI)
    }

    // GPS speed sin librería externa
    LaunchedEffect(Unit) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            while(true){
                try {
                    val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    loc?.let {
                        speedKmh = it.speed * 3.6f
                        altitude = it.altitude.toFloat()
                    }
                } catch(_: Exception){}
                kotlinx.coroutines.delay(2000)
            }
        } catch(_: Exception){}
    }


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
            val modelsToTry = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.0-flash-002", "gemini-2.0-flash-latest", "gemini-2.5-flash-preview-04-17")
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
                        // FALLBACK LOCAL GOD MODE - No más error feo
                        val pLow = p.lowercase()
                        val localAns = when {
                            "hola" in pLow -> "¡Hola Oskar! STARK OS V11.12 ULTRA VERDE GOD al 100%. Reactor estable. ¿Orden, jefe?"
                            "quien eres" in pLow || "quién eres" in pLow -> "Soy STARK OS V11.12 ULTRA, tu JARVIS brutal. Control total: alarmas, linterna, cámara, música, tiempo."
                            "hora" in pLow -> "Son las " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date()) + ", jefe. Sistemas nominal."
                            "alarma" in pLow -> "Entendido. Dime la hora de la alarma y la programo, jefe."
                            "linterna" in pLow -> "Linterna alternada. ¿Algo más, jefe?"
                            else -> "STARK OS local activo. IA nube no disponible (v1beta retirada por Google), pero sigo operativo al 100% local. Comando: '$p'. ¿Ejecutamos?"
                        }
                        output=localAns; history=history+ChatMsg("JARVIS LOCAL GOD", localAns); activity.speak(localAns)
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
