package com.osabra.superaijarvis
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiKey = BuildConfig.GEMINI_API_KEY
        setContent {
            var text by remember { mutableStateOf("") }
            var response by remember { mutableStateOf("Hola, soy Jarvis. ¿En qué te ayudo?") }
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(20.dp)) {
                    Text("SUPER AI JARVIS", color = Color.Cyan, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(20.dp))
                    Text(response, color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Escribe...") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val prompt = text; text = ""; response = "Pensando..."; scope.launch {
                            try {
                                val model = GenerativeModel("gemini-1.5-flash", apiKey)
                                val result = model.generateContent(prompt)
                                response = result.text ?: "Sin respuesta"
                            } catch (e: Exception) { response = "Error: ${e.message}" }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Enviar a Gemini") }
                }
            }
        }
    }
}
