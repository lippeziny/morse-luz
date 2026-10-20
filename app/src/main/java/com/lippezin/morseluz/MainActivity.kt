package com.lippezin.morseluz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val AppBackground = Color(0xFFF6F7F5)
private val Ink = Color(0xFF1D2421)
private val SecondaryInk = Color(0xFF69736E)
private val Accent = Color(0xFF176B57)
private val PaleAccent = Color(0xFFE5F1EC)
private val Border = Color(0xFFE2E7E3)

private enum class AppTab { SEND, RECEIVE }
private enum class PermissionAction { SEND, RECEIVE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(246, 247, 245)
        window.navigationBarColor = android.graphics.Color.rgb(246, 247, 245)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent { MorseLuzApp() }
    }
}

@Composable
private fun MorseLuzApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.SEND) }
    var message by remember { mutableStateOf("") }
    var speedMs by remember { mutableStateOf(180) }
    var isSending by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var receiveText by remember { mutableStateOf("") }
    var signalLevel by remember { mutableStateOf(0f) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var sendStatus by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var permissionAction by remember { mutableStateOf<PermissionAction?>(null) }
    var sendJob by remember { mutableStateOf<Job?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val startSending: () -> Unit = {
        val normalized = MorseCodec.normalize(message)
        if (normalized.isBlank()) {
            sendStatus = "Digite uma mensagem com letras ou números."
        } else {
            sendStatus = null
            sendJob?.cancel()
            sendJob = scope.launch {
                isSending = true
                try {
                    transmitMessage(context, normalized, speedMs)
                    sendStatus = "Mensagem enviada."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    sendStatus = error.message ?: "Não foi possível usar o flash deste aparelho."
                } finally {
                    isSending = false
                    sendJob = null
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            permissionMessage = null
            when (permissionAction) {
                PermissionAction.SEND -> startSending()
                PermissionAction.RECEIVE -> {
                    receiveText = ""
                    signalLevel = 0f
                    cameraError = null
                    isListening = true
                }
                null -> Unit
            }
        } else {
            permissionMessage = "A permissão da câmera é necessária para usar o flash ou ler a luz."
        }
        permissionAction = null
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = AppBackground,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            outline = Border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Header()
            TabSwitcher(
                selected = selectedTab,
                onSelect = { tab ->
                    if (tab != selectedTab) {
                        if (tab == AppTab.SEND) isListening = false
                        if (tab == AppTab.RECEIVE) {
                            sendJob?.cancel()
                            isSending = false
                        }
                        selectedTab = tab
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (permissionMessage != null) {
                Text(
                    text = permissionMessage.orEmpty(),
                    color = Color(0xFF8B4A2F),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    AppTab.SEND -> SendScreen(
                        message = message,
                        onMessageChange = { message = it.take(240); sendStatus = null },
                        speedMs = speedMs,
                        onSpeedChange = { speedMs = it },
                        isSending = isSending,
                        sendStatus = sendStatus,
                        onSend = {
                            if (!hasCameraPermission) {
                                permissionAction = PermissionAction.SEND
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else startSending()
                        },
                        onStop = { sendJob?.cancel() }
                    )
                    AppTab.RECEIVE -> ReceiveScreen(
                        isListening = isListening,
                        receiveText = receiveText,
                        signalLevel = signalLevel,
                        cameraError = cameraError,
                        hasCameraPermission = hasCameraPermission,
                        onToggleListening = {
                            if (isListening) {
                                isListening = false
                            } else if (!hasCameraPermission) {
                                permissionAction = PermissionAction.RECEIVE
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                receiveText = ""
                                signalLevel = 0f
                                cameraError = null
                                isListening = true
                            }
                        },
                        onText = { receiveText = it },
                        onLevel = { signalLevel = it },
                        onCameraError = { cameraError = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(PaleAccent),
            contentAlignment = Alignment.Center
        ) {
            Text("· —", color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Morse Luz", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Mensagens por luz, sem internet", fontSize = 12.sp, color = SecondaryInk)
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Border, CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("OFFLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun TabSwitcher(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabButton("Enviar", selected == AppTab.SEND, Modifier.weight(1f)) { onSelect(AppTab.SEND) }
        TabButton("Receber", selected == AppTab.RECEIVE, Modifier.weight(1f)) { onSelect(AppTab.RECEIVE) }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else SecondaryInk,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SendScreen(
    message: String,
    onMessageChange: (String) -> Unit,
    speedMs: Int,
    onSpeedChange: (Int) -> Unit,
    isSending: Boolean,
    sendStatus: String?,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Envie em código Morse", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Escreva uma mensagem e transforme cada ponto e traço em pulsos de luz.",
                fontSize = 14.sp,
                color = SecondaryInk,
                lineHeight = 20.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("SUA MENSAGEM")
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex.: Olá, mundo!", color = Color(0xFF9AA39E)) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = Accent
                )
            )
            Text(
                "${message.length}/240 caracteres",
                fontSize = 11.sp,
                color = SecondaryInk,
                modifier = Modifier.align(Alignment.End)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prévia Morse", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("A–Z · 0–9", fontSize = 11.sp, color = SecondaryInk)
                }
                Text(
                    MorseCodec.preview(message),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = if (message.isBlank()) Color(0xFF9AA39E) else Accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionLabel("VELOCIDADE DOS PULSOS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(140 to "Rápido", 180 to "Normal", 240 to "Lento").forEach { (speed, label) ->
                    FilterChip(
                        selected = speedMs == speed,
                        onClick = { onSpeedChange(speed) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (sendStatus != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSending) PaleAccent else Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSending) PaleAccent else Border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isSending) Accent else SecondaryInk))
                    Spacer(Modifier.width(9.dp))
                    Text(sendStatus, fontSize = 13.sp, color = Ink)
                }
            }
        }

        if (isSending) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3F38)),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text("Parar transmissão", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onSend,
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = Color(0xFFD8DFDB),
                    disabledContentColor = Color(0xFF8A9590)
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text("Piscar mensagem", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("↗", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            "Aponte o flash para o outro celular. Para receber, abra a aba Receber no segundo aparelho.",
            fontSize = 12.sp,
            color = SecondaryInk,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ReceiveScreen(
    isListening: Boolean,
    receiveText: String,
    signalLevel: Float,
    cameraError: String?,
    hasCameraPermission: Boolean,
    onToggleListening: () -> Unit,
    onText: (String) -> Unit,
    onLevel: (Float) -> Unit,
    onCameraError: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Leia a luz em tempo real", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "A câmera identifica os pulsos do flash e converte o Morse em texto.",
                fontSize = 14.sp,
                color = SecondaryInk,
                lineHeight = 20.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF17211D))
        ) {
            if (isListening && hasCameraPermission) {
                LiveCameraPanel(onLevel = onLevel, onText = onText, onError = onCameraError)
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("◉", color = Color.White, fontSize = 34.sp)
                    Text(
                        if (hasCameraPermission) "Câmera em espera" else "Câmera não autorizada",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Inicie a leitura e centralize o flash do outro aparelho.",
                        color = Color(0xFFD2DBD6),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(112.dp)
                    .border(1.5.dp, Color.White.copy(alpha = if (isListening) 0.9f else 0.25f), RoundedCornerShape(22.dp))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isListening) Color(0xFF7DE3A6) else Color(0xFF9AA39E)))
                Spacer(Modifier.width(6.dp))
                Text(if (isListening) "CÂMERA AO VIVO" else "VISUALIZAÇÃO", fontSize = 9.sp, color = Color.White, letterSpacing = 0.8.sp)
            }
        }

        if (isListening) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Intensidade da luz", fontSize = 12.sp, color = SecondaryInk)
                    Spacer(Modifier.weight(1f))
                    Text(if (signalLevel > 0.05f) "Sinal detectado" else "Aguardando flash…", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Border)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(signalLevel.coerceIn(0.02f, 1f))
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
        }

        if (cameraError != null) {
            Text(cameraError, color = Color(0xFF8B4A2F), fontSize = 12.sp, lineHeight = 17.sp)
        }

        Button(
            onClick = onToggleListening,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) Color(0xFF29352F) else Accent
            )
        ) {
            Text(if (isListening) "Parar leitura" else "Iniciar leitura", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mensagem recebida", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.weight(1f))
                    if (receiveText.isNotBlank()) {
                        TextButton(
                            onClick = { clipboard.setText(AnnotatedString(receiveText.trim())) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Copiar", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    text = receiveText.ifBlank { "O texto decodificado aparecerá aqui." },
                    color = if (receiveText.isBlank()) Color(0xFF9AA39E) else Ink,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (receiveText.isBlank()) FontWeight.Normal else FontWeight.Medium
                )
            }
        }

        Text(
            "Dica: mantenha o flash emissor centralizado no quadro e reduza a luz ambiente direta sobre a câmera.",
            fontSize = 12.sp,
            color = SecondaryInk,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun LiveCameraPanel(
    onLevel: (Float) -> Unit,
    onText: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView = this
            }
        },
        update = { view -> if (previewView !== view) previewView = view }
    )

    DisposableEffect(lifecycleOwner, previewView) {
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val selectedPreview = previewView

        if (selectedPreview != null) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    if (disposed) return@addListener
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(selectedPreview.surfaceProvider)
                    }
                    val analyzerUseCase = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analyzerUseCase.setAnalyzer(
                        analyzerExecutor,
                        FlashMorseAnalyzer(onLevel = onLevel, onText = onText)
                    )
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzerUseCase
                    )
                } catch (error: Exception) {
                    onError(error.message ?: "Não foi possível abrir a câmera deste aparelho.")
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdownNow()
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        fontSize = 10.sp,
        color = SecondaryInk,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp
    )
}

private suspend fun transmitMessage(context: Context, message: String, unitMs: Int) {
    val pulses = MorseCodec.transmission(message, unitMs)
    check(pulses.isNotEmpty()) { "Não encontrei caracteres válidos para transmitir." }

    val cameraManager = context.getSystemService(CameraManager::class.java)
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: error("Este aparelho não possui flash compatível.")

    try {
        for (pulse in pulses) {
            currentCoroutineContext().ensureActive()
            cameraManager.setTorchMode(cameraId, true)
            delay(pulse.onDurationMs)
            cameraManager.setTorchMode(cameraId, false)
            delay(pulse.gapAfterMs)
        }
    } finally {
        runCatching { cameraManager.setTorchMode(cameraId, false) }
    }
}
