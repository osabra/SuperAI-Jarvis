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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
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


@Composable
fun ReactorV11(isListening: Boolean, isLoading: Boolean, rms: Float) {
    var smoothRms by remember { mutableStateOf(0f) }
    LaunchedEffect(rms){ smoothRms = (smoothRms*0.75f + rms*0.25f).coerceIn(0f, 10f) }
    val inf = rememberInfiniteTransition(label="v13fix")
    val rotY by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing=LinearEasing)), label="rotY")
    val rotX by inf.animateFloat(-12f, 12f, infiniteRepeatable(tween(2800, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="rotX")
    val pulse by inf.animateFloat(0.92f, 1.22f, infiniteRepeatable(tween(700, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val spin1 by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3800, easing=LinearEasing)), label="s1")
    val spin2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(5200, easing=LinearEasing)), label="s2")
    val spin3 by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7600, easing=LinearEasing)), label="s3")

    Box(Modifier.size(400.dp).graphicsLayer{
        rotationY = rotY * 0.18f + spin1*0.05f
        rotationX = rotX
        scaleX = pulse * (1f + smoothRms*0.025f)
        scaleY = pulse * (1f + smoothRms*0.025f)
        cameraDistance = 20f
    }, contentAlignment = Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            val c = center
            val baseR = size.minDimension*0.46f
            val mainCol = if(isListening) Color(0xFF00FF88) else if(isLoading) Color(0xFF8A2BE2) else Color(0xFF00E5FF)
            drawCircle(Brush.radialGradient(listOf(mainCol.copy(alpha=0.28f + smoothRms*0.04f), Color.Transparent), center=c, radius=baseR*1.8f), radius=baseR*1.8f, center=c)
            drawCircle(Brush.radialGradient(listOf(mainCol.copy(alpha=0.15f), Color.Transparent), center=c, radius=baseR*1.4f), radius=baseR*1.4f, center=c)
            draw3DRing(center=c, radius=baseR*1.05f, stroke=22f, rotation=spin1, color=mainCol, alpha=0.85f, tiltX=68f, tiltY=12f)
            draw3DRing(center=c, radius=baseR*0.88f, stroke=16f, rotation=spin2, color=Color(0xFF00FF88), alpha=0.75f, tiltX=22f, tiltY=74f)
            draw3DRing(center=c, radius=baseR*0.72f, stroke=13f, rotation=spin3, color=Color(0xFF00E5FF), alpha=0.65f, tiltX=78f, tiltY=45f)
            draw3DRing(center=c, radius=baseR*0.56f, stroke=10f, rotation=spin1*1.4f, color=Color.White, alpha=0.5f, tiltX=35f, tiltY=35f)
            val crystalR = baseR*0.38f * pulse
            drawCircle(Color(0xFF1A1A1A), radius=crystalR*1.08f, center=c, style=Stroke(width=8f))
            drawCircle(Color(0xFF4A4A4A), radius=crystalR*1.08f, center=c, style=Stroke(width=3f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=0.95f), mainCol.copy(alpha=0.8f), mainCol.copy(alpha=0.4f), Color(0xFF001122)), center=c, radius=crystalR), radius=crystalR, center=c)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=0.9f), Color.Transparent), center=Offset(c.x-crystalR*0.25f, c.y-crystalR*0.25f), radius=crystalR*0.5f), radius=crystalR*0.5f, center=Offset(c.x-crystalR*0.25f, c.y-crystalR*0.25f))
            drawCircle(Color.White, radius=crystalR*0.12f * (1f+smoothRms*0.1f), center=c)
            drawCircle(mainCol, radius=crystalR*0.18f * (1f+smoothRms*0.1f), center=c, style=Stroke(width=3f))
        }
    }
}

fun DrawScope.draw3DRing(center: Offset, radius: Float, stroke: Float, rotation: Float, color: Color, alpha: Float, tiltX: Float, tiltY: Float){
    val segs = 72
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
        val a = alpha*(0.15f+depth*0.85f)
        if(a>0.06f){
            drawLine(color.copy(alpha=a), Offset(x1,y1), Offset(x2,y2), strokeWidth=stroke, cap=StrokeCap.Round)
        }
    }
}
