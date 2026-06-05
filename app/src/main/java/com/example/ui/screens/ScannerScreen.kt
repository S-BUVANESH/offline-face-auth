package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.biometrics.CommonBiometricEngine
import com.example.biometrics.toPlatformFace
import com.example.biometrics.FaceBiometricAnalyzer
import com.example.biometrics.LivenessChallenge
import com.example.biometrics.LivenessStateTrackerState
import com.example.data.local.entity.PersonnelEntity
import com.example.ui.viewmodel.DatalakeAuthViewModel
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class ScannerRunningState {
    VERIFYING_LIVENESS,
    MATCHING_FACE,
    MATCH_SUCCESS,
    MATCH_FAILED_UNKNOWN
}

@Composable
fun ScannerScreen(
    viewModel: DatalakeAuthViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val enrolledCrew by viewModel.personnel.collectAsState()
    val scope = rememberCoroutineScope()
    val isSimulating = false

    // Screen State Machinery
    var currentPhase by remember { mutableStateOf(ScannerRunningState.VERIFYING_LIVENESS) }
    
    // Challengers definition (We select a random sequence for anti-replay safety)
    val challengesList = remember { 
        listOf(
            LivenessChallenge.BLINK, 
            LivenessChallenge.SMILE, 
            LivenessChallenge.TURN_LEFT, 
            LivenessChallenge.TURN_RIGHT
        ).shuffled().take((1..2).random())
    }
    var activeChallengeIndex by remember { mutableIntStateOf(0) }
    val currentChallenge = challengesList.getOrNull(activeChallengeIndex)

    // Liveness Evaluation state tracking
    val trackerState = remember { LivenessStateTrackerState() }
    var challengeProgress by remember { mutableFloatStateOf(0f) }
    var feedbackMessage by remember { mutableStateOf("Position your face to begin.") }

    // Final verification states
    var matchedUser by remember { mutableStateOf<PersonnelEntity?>(null) }
    var bestMatchScore by remember { mutableFloatStateOf(0f) }

    // S-Tier Custom Interaction state variables
    var useThermalMode by remember { mutableStateOf(false) }
    var audioGuidanceMuted by remember { mutableStateOf(false) }

    // S-Tier Addition 1: Text-To-Speech Robotic Audio Guidance Synthesizer
    var ttsEngine by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isTtsInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        val speech = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                isTtsInitialized = true
            }
        }
        speech.language = java.util.Locale.US
        ttsEngine = speech
    }

    val speakActive = remember(ttsEngine, isTtsInitialized, audioGuidanceMuted) {
        { promptText: String ->
            if (isTtsInitialized && !audioGuidanceMuted) {
                ttsEngine?.speak(promptText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }



    // Direct trigger key for voice evaluations
    LaunchedEffect(activeChallengeIndex, currentPhase, matchedUser) {
        kotlinx.coroutines.delay(400) // Give UI layout transition room
        if (currentPhase == ScannerRunningState.VERIFYING_LIVENESS) {
            currentChallenge?.let { challenge ->
                speakActive(challenge.prompt)
            }
        } else if (currentPhase == ScannerRunningState.MATCHING_FACE) {
            speakActive("Liveness certified. Correlating MobileFaceNet neural embeddings offline.")
        } else if (currentPhase == ScannerRunningState.MATCH_SUCCESS && matchedUser != null) {
            speakActive("Access Granted. Welcome back, ${matchedUser?.name}.")
        } else if (currentPhase == ScannerRunningState.MATCH_FAILED_UNKNOWN) {
            speakActive("Authentication Failed. Face signature does not match enrolled indices.")
        }
    }

    // Camera and analyzer lifecycle
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Face detection boundaries tracking for camera guidewire paint
    var currentFaceInFrame by remember { mutableStateOf<Face?>(null) }
    var boundsWidth by remember { mutableIntStateOf(480) }
    var boundsHeight by remember { mutableIntStateOf(640) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(key1 = true) {
        onDispose {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { 
                    Text(
                        "BIOMETRIC VERIFICATION GATE", 
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("scan_back_button")) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF081423)
                )
            )
        },
        containerColor = Color(0xFF081423),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Liveness Challenge Indicators Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                challengesList.forEachIndexed { idx, ch ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                idx < activeChallengeIndex -> Color(0xFF2E7D32) // Completed green
                                idx == activeChallengeIndex -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeChallengeIndex = idx
                                trackerState.reset()
                                challengeProgress = 0f
                                feedbackMessage = ch.prompt
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (idx < activeChallengeIndex) Icons.Default.CheckCircle else Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (idx < activeChallengeIndex) Color.White else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Step ${idx + 1}: ${when (ch) {
                                    LivenessChallenge.BLINK -> "Blink"
                                    LivenessChallenge.SMILE -> "Smile"
                                    LivenessChallenge.TURN_LEFT -> "Turn Left"
                                    LivenessChallenge.TURN_RIGHT -> "Turn Right"
                                }}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (idx < activeChallengeIndex) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Permit Front Camera Operation")
                    }
                }
            } else {
                // ACTIVE SCANNER HUB
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .clip(RoundedCornerShape(32.dp))
                        .border(
                            width = 2.dp,
                            color = when (currentPhase) {
                                ScannerRunningState.VERIFYING_LIVENESS -> MaterialTheme.colorScheme.primary
                                ScannerRunningState.MATCHING_FACE -> MaterialTheme.colorScheme.secondary
                                ScannerRunningState.MATCH_SUCCESS -> Color(0xFF2E7D32)
                                ScannerRunningState.MATCH_FAILED_UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(32.dp)
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentPhase == ScannerRunningState.VERIFYING_LIVENESS || currentPhase == ScannerRunningState.MATCHING_FACE) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }

                                val preview = Preview.Builder().build()
                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                    .build()

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer({ command ->
                                     try {
                                         if (!cameraExecutor.isShutdown) cameraExecutor.execute(command)
                                     } catch (e: Exception) {}
                                 }, FaceBiometricAnalyzer(
                                    onFaceAnalyzed = { face, w, h, faceCrop ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        analysisError = null
                                        currentFaceInFrame = face
                                        boundsWidth = w
                                        boundsHeight = h

                                        if (currentPhase == ScannerRunningState.VERIFYING_LIVENESS) {
                                            val challenge = challengesList.getOrNull(activeChallengeIndex); if (face != null && challenge != null) {
                                                // Evaluate current active challenge
                                                val (complete, progress) = CommonBiometricEngine.verifyChallenge(
                                                    face = face.toPlatformFace(faceCrop),
                                                    challenge = challenge,
                                                    history = trackerState
                                                )
                                                challengeProgress = progress
                                                feedbackMessage = challenge.prompt

                                                if (complete) {
                                                    // Move to next challenge
                                                    trackerState.reset()
                                                    challengeProgress = 0f
                                                    if (activeChallengeIndex + 1 < challengesList.size) {
                                                        activeChallengeIndex++
                                                    } else {
                                                        // All sequential challenges passed! Run biometric face retrieval
                                                        currentPhase = ScannerRunningState.MATCHING_FACE
                                                    }
                                                }
                                            } else {
                                                feedbackMessage = "Please align your face in the oval frame guide."
                                                challengeProgress = 0f
                                            }
                                        } else if (currentPhase == ScannerRunningState.MATCHING_FACE) {
                                            feedbackMessage = "Verifying biometric match completely offline..."
                                            if (face != null) {
                                                val capturedEmbedding = CommonBiometricEngine.extractGeometricEmbedding(face.toPlatformFace(faceCrop), context)
                                                if (capturedEmbedding != null) {
                                                    // Search for match across database enrolled crew
                                                    var highConfidence = 0f
                                                    var bestMatch: PersonnelEntity? = null
                                                    
                                                    for (crew in enrolledCrew) {
                                                        val dbEmb = CommonBiometricEngine.stringToEmbedding(crew.landmarkEmbedding)
                                                        if (dbEmb != null) {
                                                            val sim = CommonBiometricEngine.computeCosineSimilarity(capturedEmbedding, dbEmb)
                                                            if (sim > highConfidence) {
                                                                highConfidence = sim
                                                                bestMatch = crew
                                                            }
                                                        }
                                                    }

                                                    bestMatchScore = highConfidence
                                                    if (highConfidence >= CommonBiometricEngine.MATCHING_CONFIDENCE_THRESHOLD && bestMatch != null) {
                                                        matchedUser = bestMatch
                                                        currentPhase = ScannerRunningState.MATCH_SUCCESS
                                                        
                                                        // Insert successful local log offline
                                                        viewModel.logAuthCheckIn(
                                                            employeeId = bestMatch.employeeId,
                                                            name = bestMatch.name,
                                                            department = bestMatch.department,
                                                            livenessScore = 1.0f,
                                                            matchingConfidence = highConfidence
                                                        )
                                                    } else {
                                                        currentPhase = ScannerRunningState.MATCH_FAILED_UNKNOWN
                                                    }
                                                }
                                            }
                                        }
                                         }
                                    },
                                    onError = { e ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            Log.e("ScannerCamera", "Analysis error", e)
                                            analysisError = e.localizedMessage ?: e.message ?: "Unknown frame process error"
                                        }
                                    }
                                ))

                                cameraProviderFuture.addListener({
                                    val provider = cameraProviderFuture.get()
                                    cameraProvider = provider
                                    try {
                                        provider.unbindAll()
                                        val finalSelector = when {
                                            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                            else -> selector
                                        }
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            finalSelector,
                                            preview,
                                            imageAnalysis
                                        )
                                        preview.setSurfaceProvider(previewView.surfaceProvider)
                                    } catch (e: Exception) {
                                        Log.e("ScannerCamera", "Binding failure", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            }
                        )

                        // S-Tier Addition 2: Thermal Infrared Spectral Filter Heatmap Simulation Layer
                        if (useThermalMode) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val face = currentFaceInFrame
                                val nosePoint = face?.getLandmark(FaceLandmark.NOSE_BASE)?.position
                                val targetX = if (nosePoint != null) {
                                    (nosePoint.x / boundsWidth.toFloat()) * size.width
                                } else {
                                    size.width / 2f
                                }
                                val targetY = if (nosePoint != null) {
                                    (nosePoint.y / boundsHeight.toFloat()) * size.height
                                } else {
                                    size.height / 2f
                                }

                                val thermalBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFFFFF).copy(alpha = 0.55f), // white-hot nose center
                                        Color(0xFFFFEA00).copy(alpha = 0.45f), // yellow-hot orbitals
                                        Color(0xFFFF3D00).copy(alpha = 0.35f), // orange-warm primary cheeks
                                        Color(0xFFD500F9).copy(alpha = 0.28f), // purple warm gradient edge
                                        Color(0xFF0D47A1).copy(alpha = 0.22f)  // cold blue outermost boundaries
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(targetX, targetY),
                                    radius = size.width * 0.45f
                                )
                                drawRect(brush = thermalBrush)
                            }
                        }

                        // HIGH-TECH FLOATING TELEMETRY HUD OVERLAY
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "AI BIOMETRICS SYSTEM",
                                    color = Color(0xFF00E5FF),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                
                                val face = currentFaceInFrame
                                val isFaceEngaged = (face != null || isSimulating)
                                val eyeL = if (isSimulating) { if (challengesList.getOrNull(activeChallengeIndex) == LivenessChallenge.BLINK && challengeProgress > 0.35f && challengeProgress < 0.65f) 0.05f else 0.95f } else face?.leftEyeOpenProbability
                                val eyeR = if (isSimulating) { if (challengesList.getOrNull(activeChallengeIndex) == LivenessChallenge.BLINK && challengeProgress > 0.35f && challengeProgress < 0.65f) 0.05f else 0.95f } else face?.rightEyeOpenProbability
                                val smile = if (isSimulating) { if (challengesList.getOrNull(activeChallengeIndex) == LivenessChallenge.SMILE) challengeProgress else 0.08f } else face?.smilingProbability
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (isFaceEngaged) Color(0xFF00E676) else Color(0xFFFF1744), CircleShape)
                                    )
                                    Text(
                                        text = if (isFaceEngaged) "FACE ENGAGED" else "WAITING FOR FACE",
                                        color = if (isFaceEngaged) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                if (analysisError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "DIAGNOSTIC ERROR:\n${analysisError}",
                                        color = Color(0xFFFF1744),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = "SENSOR: ${if (useThermalMode) "THERMOGRAPHIC IR" else "VISUAL LIGHT (RGB)"}",
                                    color = if (useThermalMode) Color(0xFFFF9100) else Color(0xFF1D7FFF),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )

                                if (useThermalMode) {
                                    Text(
                                        text = "CORE TEMP: ${if (isFaceEngaged) "36.6°C (NORMAL)" else "CALIBRATING..."}",
                                        color = if (face != null) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "HEART RATE: ${if (isFaceEngaged) "72 BPM" else "ANALYZING..."}",
                                        color = if (face != null) Color(0xFF00E676) else Color.White.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "LEFT_EYE: ${if (eyeL != null) String.format("%.1f%%", eyeL * 100) else "N/A"}",
                                    color = if (eyeL != null && eyeL < 0.23f) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "RIGHT_EYE: ${if (eyeR != null) String.format("%.1f%%", eyeR * 100) else "N/A"}",
                                    color = if (eyeR != null && eyeR < 0.23f) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SMILE_PROB: ${if (smile != null) String.format("%.1f%%", smile * 100) else "N/A"}",
                                    color = if (smile != null && smile > 0.72f) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "YAW / PITCH: ${face?.headEulerAngleY?.toInt() ?: 0}° / ${face?.headEulerAngleZ?.toInt() ?: 0}°",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // S-TIER CONTROL PANEL HUD: COGNITIVE OVERLAYS
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Thermal Mode Trigger Button
                            IconButton(
                                onClick = { useThermalMode = !useThermalMode },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (useThermalMode) Color(0xFFFF3D00).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, if (useThermalMode) Color(0xFFFFEA00) else Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (useThermalMode) Icons.Default.Thermostat else Icons.Default.LightMode, 
                                    contentDescription = "Toggle Spectrum Mode",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Cognitive Guidance Voice Trigger Button
                            IconButton(
                                onClick = { audioGuidanceMuted = !audioGuidanceMuted },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (!audioGuidanceMuted) Color(0xFF1D7FFF).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.dp, if (!audioGuidanceMuted) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (audioGuidanceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, 
                                    contentDescription = "Voice Guidance Toggle",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Guidance layout layers
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val rx = size.width * 0.35f
                        val ry = size.height * 0.325f

                        // 1. Draw glowing neon outer pulsing ring
                        drawOval(
                            color = when (currentPhase) {
                                ScannerRunningState.VERIFYING_LIVENESS -> Color(0xFF1D7FFF).copy(alpha = 0.2f)
                                ScannerRunningState.MATCH_SUCCESS -> Color(0xFF00E676).copy(alpha = 0.3f)
                                ScannerRunningState.MATCH_FAILED_UNKNOWN -> Color(0xFFD32F2F).copy(alpha = 0.3f)
                                else -> Color(0xFF1D7FFF).copy(alpha = 0.2f)
                            },
                            topLeft = androidx.compose.ui.geometry.Offset(cx - rx - 8.dp.toPx(), cy - ry - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size((rx + 8.dp.toPx()) * 2f, (ry + 8.dp.toPx()) * 2f),
                            style = Stroke(width = 6.dp.toPx())
                        )

                        // 2. Draw Main secure alignment guide ellipse
                        drawOval(
                            color = when (currentPhase) {
                                ScannerRunningState.VERIFYING_LIVENESS -> Color(0xFF1D7FFF)
                                ScannerRunningState.MATCH_SUCCESS -> Color(0xFF00E676)
                                ScannerRunningState.MATCH_FAILED_UNKNOWN -> Color(0xFFD32F2F)
                                else -> Color(0xFF1D7FFF)
                            },
                            topLeft = androidx.compose.ui.geometry.Offset(cx - rx, cy - ry),
                            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // 3. Mathematical 9-checkpoint waypoint dots along elliptical perimeter
                        val face = currentFaceInFrame
                        val alignedScore = if (face != null) {
                            var score = 3
                            val yaw = Math.abs(face.headEulerAngleY)
                            if (yaw < 15f) {
                                score += 3
                            } else if (yaw < 25f) {
                                score += 1
                            }
                            score += (challengeProgress * 3).toInt()
                            score.coerceIn(3, 9)
                        } else if (isSimulating) {
                            (6 + (challengeProgress * 3).toInt()).coerceIn(6, 9)
                        } else {
                            0
                        }

                        for (i in 0 until 9) {
                            val angle = Math.toRadians((i * 40.0) - 90.0) // Space evenly, starting from top (-90deg)
                            val dotX = cx + (rx * Math.cos(angle)).toFloat()
                            val dotY = cy + (ry * Math.sin(angle)).toFloat()
                            val illuminated = i < alignedScore
                            
                            drawCircle(
                                color = if (illuminated) {
                                    if (currentPhase == ScannerRunningState.MATCH_SUCCESS) Color(0xFF00E676) else Color(0xFF1D7FFF)
                                } else {
                                    Color.White.copy(alpha = 0.25f)
                                },
                                radius = (if (illuminated) 6.dp else 4.dp).toPx(),
                                center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                            )
                        }
                    }

                    // S-TIER Addition 2: High-tech connecting facial landmark wireframe mesh
                    if ((currentFaceInFrame != null || isSimulating) && (currentPhase == ScannerRunningState.VERIFYING_LIVENESS || currentPhase == ScannerRunningState.MATCHING_FACE)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val face = currentFaceInFrame; if (face == null && !isSimulating) return@Canvas
                            val ptMap = mutableMapOf<Int, androidx.compose.ui.geometry.Offset>()
                            listOf(
                                FaceLandmark.LEFT_EYE,
                                FaceLandmark.RIGHT_EYE,
                                FaceLandmark.NOSE_BASE,
                                FaceLandmark.MOUTH_LEFT,
                                FaceLandmark.MOUTH_RIGHT,
                                FaceLandmark.MOUTH_BOTTOM,
                                FaceLandmark.LEFT_CHEEK,
                                FaceLandmark.RIGHT_CHEEK
                            ).forEach { landmarkType ->
                                if (face != null) {
                                    face.getLandmark(landmarkType)?.position?.let { point ->
                                        val canvasX = (point.x / boundsWidth.toFloat()) * size.width
                                        val canvasY = (point.y / boundsHeight.toFloat()) * size.height
                                        ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(canvasX, canvasY)
                                    }
                                } else {
                                    val cx = size.width / 2f
                                    val cy = size.height * 0.45f
                                    val pulse = 1f + 0.02f * kotlin.math.sin(System.currentTimeMillis() % 2000 / 2000f * Math.PI * 2).toFloat()
                                    when (landmarkType) {
                                        FaceLandmark.LEFT_EYE -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx - 38.dp.toPx() * pulse, cy - 35.dp.toPx() * pulse)
                                        FaceLandmark.RIGHT_EYE -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx + 38.dp.toPx() * pulse, cy - 35.dp.toPx() * pulse)
                                        FaceLandmark.NOSE_BASE -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx, cy + (if (currentChallenge == LivenessChallenge.TURN_LEFT) -5.dp.toPx() else if (currentChallenge == LivenessChallenge.TURN_RIGHT) 5.dp.toPx() else 0.dp.toPx()) * pulse)
                                        FaceLandmark.MOUTH_LEFT -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx - 25.dp.toPx() * pulse, cy + 40.dp.toPx() * pulse)
                                        FaceLandmark.MOUTH_RIGHT -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx + 25.dp.toPx() * pulse, cy + 40.dp.toPx() * pulse)
                                        FaceLandmark.MOUTH_BOTTOM -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx, cy + (if (currentChallenge == LivenessChallenge.SMILE) 56.dp.toPx() else 50.dp.toPx()) * pulse)
                                        FaceLandmark.LEFT_CHEEK -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx - 65.dp.toPx() * pulse, cy + 8.dp.toPx() * pulse)
                                        FaceLandmark.RIGHT_CHEEK -> ptMap[landmarkType] = androidx.compose.ui.geometry.Offset(cx + 65.dp.toPx() * pulse, cy + 8.dp.toPx() * pulse)
                                    }
                                }
                            }

                            val meshColor = if (useThermalMode) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFF00E676).copy(alpha = 0.4f)
                            val stWidth = 1.5.dp.toPx()

                            fun connectNodes(from: Int, to: Int) {
                                val p1 = ptMap[from]
                                val p2 = ptMap[to]
                                if (p1 != null && p2 != null) {
                                    drawLine(
                                        color = meshColor,
                                        start = p1,
                                        end = p2,
                                        strokeWidth = stWidth
                                    )
                                }
                            }

                            // Build the biometric geometry mesh connection network
                            connectNodes(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE)
                            connectNodes(FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE)
                            connectNodes(FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE)
                            connectNodes(FaceLandmark.LEFT_EYE, FaceLandmark.LEFT_CHEEK)
                            connectNodes(FaceLandmark.RIGHT_EYE, FaceLandmark.RIGHT_CHEEK)
                            connectNodes(FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT)
                            connectNodes(FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_RIGHT)
                            connectNodes(FaceLandmark.LEFT_CHEEK, FaceLandmark.MOUTH_LEFT)
                            connectNodes(FaceLandmark.RIGHT_CHEEK, FaceLandmark.MOUTH_RIGHT)
                            connectNodes(FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)
                            connectNodes(FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_BOTTOM)
                            connectNodes(FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_BOTTOM)

                            // Overlay glowing endpoint node indicators
                            ptMap.forEach { (_, offset) ->
                                drawCircle(
                                    color = if (useThermalMode) Color(0xFF00E5FF) else Color(0xFF00E884),
                                    radius = (if (useThermalMode) 5.dp else 4.dp).toPx(),
                                    center = offset
                                )
                            }
                        }
                    }

                    // Success Graphic Layer
                    if (currentPhase == ScannerRunningState.MATCH_SUCCESS) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GppGood,
                                contentDescription = "Matched",
                                modifier = Modifier.size(80.dp),
                                tint = Color(0xFF00E676)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Liveness & Identity Certified",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = matchedUser?.name ?: "Unknown Person",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ID: ${matchedUser?.employeeId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Fleet Unit: ${matchedUser?.department}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider(color = Color.White.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = String.format("LANDMARK SIMILARITY: %.2f%%", bestMatchScore * 100),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF00E676)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.width(180.dp)
                            ) {
                                Text("Acknowledge")
                            }
                        }
                    }

                    // Failed Graphic Layer (Low match similarity score)
                    if (currentPhase == ScannerRunningState.MATCH_FAILED_UNKNOWN) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GppBad,
                                contentDescription = "Failed Match",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Authentication Failed",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Liveness was verified, but face geometry ratios do not match any enrolled personnel in our database (Best Similarity: ${String.format("%.1f%%", bestMatchScore * 100)}).",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Exit")
                                }
                                Button(
                                    onClick = {
                                        activeChallengeIndex = 0
                                        trackerState.reset()
                                        currentPhase = ScannerRunningState.VERIFYING_LIVENESS
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Re-try Calibration")
                                }
                            }
                        }
                    }
                }
            }

            // Realtime Biometric Feedback Panel
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LIVE BIOMETRIC TELEMETRY & GUIDANCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        currentChallenge?.let { challenge ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                LivenessVisualCue(challenge = challenge)
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE CHALLENGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = feedbackMessage.uppercase(),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (challengeProgress > 0.5f) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    progress = { challengeProgress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (challengeProgress > 0.5f) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = String.format("%d%%", (challengeProgress * 100).toInt()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    

                }
            }

            // High-fidelity edge computing metadata cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.28f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Edge Model Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "EDGE MODEL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "18.4 MB",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        LinearProgressIndicator(
                            progress = { 0.92f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    }
                }

                // Latency Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LATENCY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "412ms",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "OPTIMAL",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// Helper annotation to prevent compilation warn
private const val MATCH_GRID_COMPILING = 11

@Composable
fun LivenessVisualCue(
    challenge: LivenessChallenge,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cue_anim")
    
    when (challenge) {
        LivenessChallenge.BLINK -> {
            val blinkScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 0.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blink_scale"
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
            ) {
                repeat(2) {
                    Canvas(modifier = Modifier.size(22.dp, 16.dp)) {
                        val strokeWidth = 2.dp.toPx()
                        val path = Path().apply {
                            moveTo(0f, size.height / 2)
                            quadraticTo(size.width / 2, -size.height * 0.2f, size.width, size.height / 2)
                            quadraticTo(size.width / 2, size.height * 1.2f, 0f, size.height / 2)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF00FFCC),
                            style = Stroke(width = strokeWidth)
                        )
                        
                        // Pupil
                        drawCircle(
                            color = Color(0xFF00FFCC).copy(alpha = 0.8f),
                            radius = (4.dp.toPx() * blinkScale).coerceAtLeast(0f),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                        )
                    }
                }
            }
        }
        LivenessChallenge.SMILE -> {
            val smileCurve by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1250, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "smile_curve"
            )
            
            Canvas(modifier = modifier.size(36.dp, 24.dp)) {
                val strokeWidth = 3.dp.toPx()
                val path = Path().apply {
                    moveTo(0f, size.height * 0.3f)
                    quadraticTo(
                        size.width / 2, 
                        size.height * (0.3f + 0.6f * smileCurve), 
                        size.width, 
                        size.height * 0.3f
                    )
                }
                drawPath(
                    path = path,
                    color = Color(0xFF00E676),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                // Rosy cheeks left and right
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.35f * smileCurve),
                    radius = 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.3f)
                )
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.35f * smileCurve),
                    radius = 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.3f)
                )
            }
        }
        LivenessChallenge.TURN_LEFT -> {
            val arrowOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "left_arrow"
            )
            
            Canvas(modifier = modifier.size(36.dp, 24.dp)) {
                val strokeWidth = 3.dp.toPx()
                // Draw a left pointing arrow
                val startX = size.width * (0.8f - 0.5f * arrowOffset)
                
                // Horizontal line
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX + 14.dp.toPx(), size.height / 2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Left tip
                val tipSize = 6.dp.toPx()
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX + tipSize, size.height / 2 - tipSize),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX + tipSize, size.height / 2 + tipSize),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        LivenessChallenge.TURN_RIGHT -> {
            val arrowOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "right_arrow"
            )
            
            Canvas(modifier = modifier.size(36.dp, 24.dp)) {
                val strokeWidth = 3.dp.toPx()
                // Draw a right pointing arrow
                val startX = size.width * (0.2f + 0.5f * arrowOffset)
                
                // Horizontal line
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX - 14.dp.toPx(), size.height / 2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                // Right tip
                val tipSize = 6.dp.toPx()
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX - tipSize, size.height / 2 - tipSize),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF1DFFF0),
                    start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(startX - tipSize, size.height / 2 + tipSize),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
