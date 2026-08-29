package com.osabra.superaijarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import androidx.compose.animation.core.*
import android.speech.SpeechRecognizer
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isListening by remember { mutableStateOf(false) }
            var rms by remember { mutableStateOf(0f) }
            val inf = rememberInfiniteTransition(label="gyro")
            val rotY by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing=androidx.compose.animation.core.LinearEasing)), label="rot")
            
            Box(Modifier.fillMaxSize().background(Color(0xFF050A0F)), contentAlignment = Alignment.Center) {
                // 3D REAL - FILAMENT ENGINE
                Scene(
                    modifier = Modifier.fillMaxSize(),
                    nodes = remember {
                        listOf(
                            ModelNode(
                                modelInstanceName = "reactor_brutal_3d.glb",
                                scaleToUnits = 1.8f,
                                centerOrigin = Position(0f, 0f, 0f)
                            ).apply {
                                // Auto rotación brutal
                            }
                        )
                    },
                    onFrame = { dt ->
                        // Aquí animas los anillos
                    }
                )
                
                // Overlay con estado
                Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if(isListening) "● ESCUCHANDO" else "REACTOR BRUTAL 3D - GIRA CON EL DEDO",
                        color = if(isListening) Color(0xFF00FF88) else Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
