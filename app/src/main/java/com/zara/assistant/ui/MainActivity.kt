package com.zara.assistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zara.assistant.permissions.PermissionManager
import com.zara.assistant.services.ZaraForegroundService

class MainActivity : ComponentActivity() {

    private val vm: AssistantViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = PermissionManager.missing(this)
        if (missing.isNotEmpty()) permLauncher.launch(missing)
        startForegroundService(Intent(this, ZaraForegroundService::class.java))
        setContent { ZaraTheme { ZaraScreen(vm) } }
    }
}

@Composable
fun ZaraScreen(vm: AssistantViewModel) {
    val messages by vm.messages.collectAsState()
    val isListening by vm.isListening.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .systemBarsPadding()
    ) {
        // Header
        Text(
            text = "Zara",
            color = Color(0xFF9B59B6),
            fontSize = 22.sp,
            modifier = Modifier.padding(16.dp)
        )

        // Chat area
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
        }

        // Listening indicator
        if (isListening) {
            Text(
                "Listening...",
                color = Color(0xFF9B59B6),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command...", color = Color.Gray) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) { vm.processText(inputText); inputText = "" }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (inputText.isNotBlank()) { vm.processText(inputText); inputText = "" }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF9B59B6))
            }
            IconButton(onClick = { vm.startVoice() }) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint = if (isListening) Color(0xFF9B59B6) else Color.Gray
                )
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) Color(0xFF9B59B6) else Color(0xFF1E1E1E),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(msg.text, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun ZaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9B59B6),
            background = Color(0xFF0D0D0D),
            surface = Color(0xFF1A1A1A)
        ),
        content = content
    )
}
