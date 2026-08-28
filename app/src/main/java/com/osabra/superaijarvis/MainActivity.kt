package com.osabra.superaijarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La API Key viene de GitHub Secrets, inyectada en BuildConfig
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        setContent {
            var inputText by remember { mutableStateOf("") }
            var responseText by remember { mutableStateOf("Hola, soy Jarvis. ¿En qué te ayudo, Oskar?") }
            var isLoading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "SUPER AI JARVIS",
                        color = Color(0xFF00FFFF),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (apiKey.isEmpty()) "⚠️ Sin API Key" else "✅ Conectado a Gemini",
                        color = if (apiKey.isEmpty()) Color.Red else Color.Green,
                        style = MaterialTheme.typography.labelSmall
                    )
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Card(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                    ) {
                        Text(
                            text = responseText,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Escribe tu orden...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Cyan,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    
                    Spacer(Modifier.height(10.dp))
                    
                    Button(
                        onClick = {
                            if (inputText.isBlank() || apiKey.isBlank()) return@Button
                            val prompt = inputText
                            inputText = ""
                            responseText = "Pensando..."
                            isLoading = true
                            scope.launch {
                                try {
                                    val model = GenerativeModel("gemini-1.5-flash", apiKey)
                                    val result = model.generateContent(prompt)
                                    responseText = result.text ?: "Sin respuesta de Gemini"
                                } catch (e: Exception) {
                                    responseText = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Enviando..." else "Enviar a Gemini")
                    }
                }
            }
        }
    }
}
